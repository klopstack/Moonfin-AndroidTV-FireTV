package org.jellyfin.androidtv.data.repository

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jellyfin.androidtv.auth.repository.UserRepository
import org.jellyfin.androidtv.util.AccessScheduleEvaluator
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.AccessSchedule
import org.jellyfin.sdk.model.api.UserPolicy
import org.moonfin.server.emby.EmbyApiException
import timber.log.Timber
import java.time.Duration
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

sealed class AccessScheduleStatus {
	data object Allowed : AccessScheduleStatus()
	data class Denied(val nextAccessStart: LocalDateTime?) : AccessScheduleStatus()
}

interface AccessScheduleRepository {
	val status: StateFlow<AccessScheduleStatus>
	val forceBlockOverlay: SharedFlow<Unit>
	val loginDeniedNextAccess: StateFlow<LocalDateTime?>

	fun evaluatePolicy(policy: UserPolicy?, now: LocalDateTime = LocalDateTime.now()): AccessScheduleStatus
	fun checkNow(): AccessScheduleStatus
	fun setLoginDenied(nextAccessStart: LocalDateTime?)
	fun consumeLoginDenied(): LocalDateTime?
	fun hasPendingLoginDenied(): Boolean
	fun clearPendingLoginDenied()
	fun requestBlockedOverlay()
	fun isScheduleRelatedApiError(error: Throwable, userId: UUID? = null, responseBody: String? = null): Boolean
	fun isCurrentlyDenied(): Boolean
	fun cacheUserPolicy(userId: UUID, policy: UserPolicy?)
	fun evaluateCachedPolicyForUser(userId: UUID, now: LocalDateTime = LocalDateTime.now()): AccessScheduleStatus?
}

@Serializable
private data class CachedAccessSchedulePolicy(
	val isAdministrator: Boolean = false,
	val accessSchedules: List<AccessSchedule> = emptyList(),
)

class AccessScheduleRepositoryImpl(
	private val userRepository: UserRepository,
	context: Context,
) : AccessScheduleRepository {
	companion object {
		private const val PREFS_NAME = "access_schedule_policies"
	}

	private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	private val json = Json { ignoreUnknownKeys = true }
	private val memoryCache = mutableMapOf<UUID, CachedAccessSchedulePolicy>()
	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

	private val _status = MutableStateFlow<AccessScheduleStatus>(AccessScheduleStatus.Allowed)
	override val status = _status.asStateFlow()

	private val _forceBlockOverlay = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
	override val forceBlockOverlay = _forceBlockOverlay.asSharedFlow()

	private val _loginDeniedNextAccess = MutableStateFlow<LocalDateTime?>(null)
	override val loginDeniedNextAccess = _loginDeniedNextAccess.asStateFlow()
	private val loginDeniedPending = AtomicBoolean(false)

	init {
		userRepository.currentUser
			.map { user -> evaluatePolicy(user?.policy) }
			.distinctUntilChanged()
			.onEach { _status.value = it }
			.launchIn(scope)

		scope.launch {
			while (isActive) {
				val policy = userRepository.currentUser.value?.policy
				val now = LocalDateTime.now()
				val nextCheck = AccessScheduleEvaluator.getNextStatusChange(policy, now)
				val delayMs = nextCheck?.let {
					Duration.between(now, it).toMillis().coerceIn(1_000L, 86_400_000L)
				} ?: 60_000L
				delay(delayMs)
				_status.value = checkNow()
			}
		}
	}

	override fun evaluatePolicy(policy: UserPolicy?, now: LocalDateTime): AccessScheduleStatus {
		return if (AccessScheduleEvaluator.isAccessAllowed(policy, now)) {
			AccessScheduleStatus.Allowed
		} else {
			AccessScheduleStatus.Denied(AccessScheduleEvaluator.getNextAccessStart(policy, now))
		}
	}

	override fun checkNow(): AccessScheduleStatus = evaluatePolicy(userRepository.currentUser.value?.policy)

	override fun setLoginDenied(nextAccessStart: LocalDateTime?) {
		loginDeniedPending.set(true)
		_loginDeniedNextAccess.value = nextAccessStart
	}

	override fun hasPendingLoginDenied(): Boolean = loginDeniedPending.get()

	override fun clearPendingLoginDenied() {
		loginDeniedPending.set(false)
		_loginDeniedNextAccess.value = null
	}

	override fun consumeLoginDenied(): LocalDateTime? {
		if (!loginDeniedPending.getAndSet(false)) return null
		val value = _loginDeniedNextAccess.value
		_loginDeniedNextAccess.value = null
		return value
	}

	override fun isCurrentlyDenied(): Boolean = status.value is AccessScheduleStatus.Denied

	override fun requestBlockedOverlay() {
		_forceBlockOverlay.tryEmit(Unit)
	}

	override fun cacheUserPolicy(userId: UUID, policy: UserPolicy?) {
		val schedules = policy?.accessSchedules
		if (policy == null || schedules.isNullOrEmpty()) {
			memoryCache.remove(userId)
			prefs.edit().remove(userId.toString()).apply()
			return
		}

		val cached = CachedAccessSchedulePolicy(
			isAdministrator = policy.isAdministrator,
			accessSchedules = schedules,
		)
		memoryCache[userId] = cached
		try {
			prefs.edit().putString(userId.toString(), json.encodeToString(cached)).apply()
		} catch (e: Exception) {
			Timber.e(e, "Failed to persist cached access schedule policy for user $userId")
		}
	}

	override fun evaluateCachedPolicyForUser(userId: UUID, now: LocalDateTime): AccessScheduleStatus? {
		val cached = memoryCache[userId] ?: loadCachedPolicy(userId) ?: return null
		return evaluatePolicy(cached.toUserPolicy(), now)
	}

	override fun isScheduleRelatedApiError(error: Throwable, userId: UUID?, responseBody: String?): Boolean {
		val combinedText = buildString {
			append(collectThrowableMessages(error))
			responseBody?.let {
				append(' ')
				append(it)
			}
		}
		if (containsScheduleDenialText(combinedText)) return true

		when (error) {
			is InvalidStatusException -> {
				if (error.status != 403) return false
				return userId?.let { evaluateCachedPolicyForUser(it) }
					.let { it is AccessScheduleStatus.Denied }
			}
			is EmbyApiException -> {
				if (error.statusCode != 403) return false
				return containsScheduleDenialText(error.message.orEmpty())
			}
		}

		return false
	}

	private fun loadCachedPolicy(userId: UUID): CachedAccessSchedulePolicy? {
		val jsonString = prefs.getString(userId.toString(), null) ?: return null
		return try {
			json.decodeFromString<CachedAccessSchedulePolicy>(jsonString).also {
				memoryCache[userId] = it
			}
		} catch (e: Exception) {
			Timber.e(e, "Failed to load cached access schedule policy for user $userId")
			null
		}
	}

	private fun CachedAccessSchedulePolicy.toUserPolicy() = UserPolicy(
		isAdministrator = isAdministrator,
		isHidden = false,
		enableCollectionManagement = false,
		enableSubtitleManagement = false,
		enableLyricManagement = false,
		isDisabled = false,
		enableUserPreferenceAccess = true,
		accessSchedules = accessSchedules,
		enableRemoteControlOfOtherUsers = false,
		enableSharedDeviceControl = true,
		enableRemoteAccess = true,
		enableLiveTvManagement = false,
		enableLiveTvAccess = true,
		enableMediaPlayback = true,
		enableAudioPlaybackTranscoding = true,
		enableVideoPlaybackTranscoding = true,
		enablePlaybackRemuxing = true,
		forceRemoteSourceTranscoding = false,
		enableContentDeletion = false,
		enableContentDownloading = true,
		enableSyncTranscoding = true,
		enableMediaConversion = true,
		enableAllDevices = true,
		enableAllChannels = true,
		enableAllFolders = true,
		invalidLoginAttemptCount = 0,
		loginAttemptsBeforeLockout = -1,
		maxActiveSessions = 0,
		enablePublicSharing = true,
		remoteClientBitrateLimit = 0,
		authenticationProviderId = "",
		passwordResetProviderId = "",
		syncPlayAccess = org.jellyfin.sdk.model.api.SyncPlayUserAccessType.CREATE_AND_JOIN_GROUPS,
	)
}

internal fun containsScheduleDenialText(text: String): Boolean = AccessScheduleEvaluator.isScheduleDenialMessage(text)

private fun collectThrowableMessages(error: Throwable): String = buildString {
	var current: Throwable? = error
	while (current != null) {
		current.message?.let { message ->
			if (isNotEmpty()) append(' ')
			append(message)
		}
		current = current.cause
	}
}
