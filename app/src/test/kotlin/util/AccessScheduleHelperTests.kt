package org.jellyfin.androidtv.util

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.jellyfin.sdk.model.api.AccessSchedule
import org.jellyfin.sdk.model.api.DynamicDayOfWeek
import org.jellyfin.sdk.model.serializer.toUUID
import java.time.ZoneId
import java.time.ZonedDateTime

class AccessScheduleHelperTests : FunSpec({
	val userId = "00000000-0000-0000-0000-000000000001".toUUID()
	val zone = ZoneId.of("UTC")

	fun schedule(
		day: DynamicDayOfWeek,
		startHour: Double,
		endHour: Double,
	) = AccessSchedule(
		id = 1,
		userId = userId,
		dayOfWeek = day,
		startHour = startHour,
		endHour = endHour,
	)

	test("allows access inside everyday schedule window") {
		val schedules = listOf(schedule(DynamicDayOfWeek.EVERYDAY, 8.0, 20.0))
		val moment = ZonedDateTime.of(2026, 6, 18, 10, 0, 0, 0, zone)

		AccessScheduleHelper.isAccessAllowed(schedules, moment) shouldBe true
	}

	test("denies access outside everyday schedule window") {
		val schedules = listOf(schedule(DynamicDayOfWeek.EVERYDAY, 8.0, 20.0))
		val moment = ZonedDateTime.of(2026, 6, 18, 21, 0, 0, 0, zone)

		AccessScheduleHelper.isAccessAllowed(schedules, moment) shouldBe false
	}

	test("finds next access start after denied window") {
		val schedules = listOf(schedule(DynamicDayOfWeek.EVERYDAY, 8.0, 20.0))
		val moment = ZonedDateTime.of(2026, 6, 18, 21, 0, 0, 0, zone)

		val next = AccessScheduleHelper.findNextAccessStart(schedules, moment)
		next?.hour shouldBe 8
		next?.dayOfMonth shouldBe 19
	}

	test("empty schedules always allow access") {
		AccessScheduleHelper.isAccessAllowed(emptyList()) shouldBe true
	}
})
