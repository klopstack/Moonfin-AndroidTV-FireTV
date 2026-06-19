package org.jellyfin.androidtv.util

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.util.AuthorizationHeaderBuilder
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import timber.log.Timber

/**
 * Reads Jellyfin authentication error response bodies.
 *
 * The Jellyfin Kotlin SDK throws [org.jellyfin.sdk.api.client.exception.InvalidStatusException]
 * without preserving the HTTP response body, so schedule-related 403 responses must be
 * inspected separately when cached policy data is unavailable.
 */
class JellyfinAuthenticationHelper(
	private val okHttpClient: OkHttpClient,
	private val clientInfo: ClientInfo,
) {
	private val json = Json { ignoreUnknownKeys = true }

	suspend fun readAuthenticateByNameErrorBody(
		serverAddress: String,
		username: String,
		password: String,
		deviceInfo: DeviceInfo,
	): String? {
		val baseUrl = serverAddress.trimEnd('/')
		val requestBody = json.encodeToString(
			mapOf(
				"Username" to username,
				"Pw" to password,
			)
		)

		val authorization = AuthorizationHeaderBuilder.buildHeader(
			clientName = clientInfo.name,
			clientVersion = clientInfo.version,
			deviceId = deviceInfo.id,
			deviceName = deviceInfo.name,
			accessToken = null,
		)

		val request = Request.Builder()
			.url("$baseUrl/Users/AuthenticateByName")
			.post(requestBody.toRequestBody("application/json".toMediaType()))
			.header("Authorization", authorization)
			.header("Accept", "application/json")
			.header("Content-Type", "application/json")
			.build()

		return try {
			okHttpClient.newCall(request).execute().use { response ->
				if (response.isSuccessful) null else response.body?.string()
			}
		} catch (e: Exception) {
			Timber.d(e, "Unable to read Jellyfin authentication error body")
			null
		}
	}

	companion object {
		fun createClient(httpClientOptions: HttpClientOptions): OkHttpClient {
			return OkHttpClient.Builder()
				.connectTimeout(java.time.Duration.ofMillis(httpClientOptions.connectTimeout.inWholeMilliseconds))
				.readTimeout(java.time.Duration.ofMillis(httpClientOptions.socketTimeout.inWholeMilliseconds))
				.writeTimeout(java.time.Duration.ofMillis(httpClientOptions.socketTimeout.inWholeMilliseconds))
				.build()
		}
	}
}
