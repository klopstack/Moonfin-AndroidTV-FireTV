package org.jellyfin.androidtv.util

import org.jellyfin.sdk.model.api.AccessSchedule
import org.jellyfin.sdk.model.api.DynamicDayOfWeek
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object AccessScheduleHelper {
	private val timeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

	fun isAccessAllowed(
		schedules: List<AccessSchedule>,
		now: ZonedDateTime = ZonedDateTime.now(),
	): Boolean {
		if (schedules.isEmpty()) return true
		return schedules.any { schedule -> isWithinSchedule(schedule, now) }
	}

	fun isWithinSchedule(schedule: AccessSchedule, moment: ZonedDateTime): Boolean {
		if (!matchesDay(schedule.dayOfWeek, moment.dayOfWeek)) return false

		val currentHour = moment.hour + moment.minute / 60.0 + moment.second / 3600.0
		return currentHour >= schedule.startHour && currentHour < schedule.endHour
	}

	fun matchesDay(scheduleDay: DynamicDayOfWeek, day: DayOfWeek): Boolean = when (scheduleDay) {
		DynamicDayOfWeek.EVERYDAY -> true
		DynamicDayOfWeek.WEEKDAY -> day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY
		DynamicDayOfWeek.WEEKEND -> day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY
		DynamicDayOfWeek.SUNDAY -> day == DayOfWeek.SUNDAY
		DynamicDayOfWeek.MONDAY -> day == DayOfWeek.MONDAY
		DynamicDayOfWeek.TUESDAY -> day == DayOfWeek.TUESDAY
		DynamicDayOfWeek.WEDNESDAY -> day == DayOfWeek.WEDNESDAY
		DynamicDayOfWeek.THURSDAY -> day == DayOfWeek.THURSDAY
		DynamicDayOfWeek.FRIDAY -> day == DayOfWeek.FRIDAY
		DynamicDayOfWeek.SATURDAY -> day == DayOfWeek.SATURDAY
	}

	fun findNextAccessStart(
		schedules: List<AccessSchedule>,
		from: ZonedDateTime = ZonedDateTime.now(),
	): ZonedDateTime? {
		if (schedules.isEmpty()) return null

		var candidate: ZonedDateTime? = null
		for (dayOffset in 0..7) {
			val date = from.toLocalDate().plusDays(dayOffset.toLong())
			for (schedule in schedules) {
				if (!matchesDay(schedule.dayOfWeek, date.dayOfWeek)) continue

				val start = scheduleStart(schedule, date, from.zone)
				if (!start.isAfter(from)) continue

				if (candidate == null || start.isBefore(candidate)) {
					candidate = start
				}
			}
		}
		return candidate
	}

	fun formatNextAccessTime(
		schedules: List<AccessSchedule>,
		from: ZonedDateTime = ZonedDateTime.now(),
	): String? {
		val nextStart = findNextAccessStart(schedules, from) ?: return null
		val timeText = nextStart.format(timeFormatter)
		val today = from.toLocalDate()
		val nextDate = nextStart.toLocalDate()

		return when {
			nextDate == today -> "today at $timeText"
			nextDate == today.plusDays(1) -> "tomorrow at $timeText"
			else -> "${nextDate.dayOfWeek.name.lowercase().replaceFirstChar { it.titlecase() }} at $timeText"
		}
	}

	private fun scheduleStart(schedule: AccessSchedule, date: LocalDate, zone: ZoneId): ZonedDateTime {
		val totalMinutes = (schedule.startHour * 60).toLong()
		val hour = (totalMinutes / 60).toInt()
		val minute = (totalMinutes % 60).toInt()
		return date.atTime(LocalTime.of(hour, minute)).atZone(zone)
	}
}
