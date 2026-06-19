package org.jellyfin.androidtv.util

import android.content.Context
import org.jellyfin.androidtv.R
import org.jellyfin.sdk.model.api.AccessSchedule
import org.jellyfin.sdk.model.api.DynamicDayOfWeek
import org.jellyfin.sdk.model.api.UserPolicy
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * Evaluates Jellyfin user access schedules using the same rules as the server
 * ([UserEntityExtensions.IsParentalScheduleAllowed](https://github.com/jellyfin/jellyfin/blob/master/Jellyfin.Data/UserEntityExtensions.cs)).
 */
object AccessScheduleEvaluator {
	private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a")

	fun isAccessAllowed(policy: UserPolicy?, now: LocalDateTime = LocalDateTime.now()): Boolean {
		if (policy == null || policy.isAdministrator) return true

		val schedules = policy.accessSchedules
		if (schedules.isNullOrEmpty()) return true

		return schedules.any { schedule -> isScheduleAllowed(schedule, now) }
	}

	fun getNextAccessStart(policy: UserPolicy?, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
		if (policy == null || policy.isAdministrator) return null
		if (policy.accessSchedules.isNullOrEmpty()) return null
		if (isAccessAllowed(policy, now)) return null

		var probe = now.plusMinutes(1).truncatedTo(ChronoUnit.MINUTES)
		val end = now.plusDays(8)

		while (probe.isBefore(end)) {
			if (isAccessAllowed(policy, probe)) return probe
			probe = probe.plusMinutes(15)
		}

		return null
	}

	fun formatNextAccessMessage(context: Context, nextStart: LocalDateTime?, now: LocalDateTime = LocalDateTime.now()): String? {
		if (nextStart == null) return null

		val time = nextStart.format(timeFormatter)
		return when {
			nextStart.toLocalDate() == now.toLocalDate() ->
				context.getString(R.string.access_schedule_resumes_today, time)
			nextStart.toLocalDate() == now.toLocalDate().plusDays(1) ->
				context.getString(R.string.access_schedule_resumes_tomorrow, time)
			else -> {
				val dayFormatter = DateTimeFormatter.ofPattern("EEEE")
				context.getString(R.string.access_schedule_resumes_on_day, nextStart.format(dayFormatter), time)
			}
		}
	}

	private fun isScheduleAllowed(schedule: AccessSchedule, now: LocalDateTime): Boolean {
		val hour = now.hour + (now.minute / 60.0) + (now.second / 3600.0)
		val dayOfWeek = now.dayOfWeek

		return schedule.dayOfWeek.contains(dayOfWeek)
			&& hour >= schedule.startHour
			&& hour <= schedule.endHour
	}

	private fun DynamicDayOfWeek.contains(dayOfWeek: DayOfWeek): Boolean = when (this) {
		DynamicDayOfWeek.EVERYDAY -> true
		DynamicDayOfWeek.WEEKDAY -> dayOfWeek in DayOfWeek.MONDAY..DayOfWeek.FRIDAY
		DynamicDayOfWeek.WEEKEND -> dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY
		DynamicDayOfWeek.SUNDAY -> dayOfWeek == DayOfWeek.SUNDAY
		DynamicDayOfWeek.MONDAY -> dayOfWeek == DayOfWeek.MONDAY
		DynamicDayOfWeek.TUESDAY -> dayOfWeek == DayOfWeek.TUESDAY
		DynamicDayOfWeek.WEDNESDAY -> dayOfWeek == DayOfWeek.WEDNESDAY
		DynamicDayOfWeek.THURSDAY -> dayOfWeek == DayOfWeek.THURSDAY
		DynamicDayOfWeek.FRIDAY -> dayOfWeek == DayOfWeek.FRIDAY
		DynamicDayOfWeek.SATURDAY -> dayOfWeek == DayOfWeek.SATURDAY
	}
}
