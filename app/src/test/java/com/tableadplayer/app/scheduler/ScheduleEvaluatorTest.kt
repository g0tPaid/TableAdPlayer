package com.tableadplayer.app.scheduler

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleEvaluatorTest {
    private val utc: ZoneId = ZoneOffset.UTC

    private fun at(
        date: String,
        time: String,
        zone: ZoneId = utc,
    ): ZonedDateTime {
        val d = LocalDate.parse(date)
        val t = LocalTime.parse(time)
        return ZonedDateTime.of(d, t, zone)
    }

    @Test
    fun nullSchedulePlaysNormally() {
        assertTrue(ScheduleEvaluator.isActive(null, at("2026-09-11", "12:00:00")))
    }

    @Test
    fun unconstrainedSchedulePlaysNormally() {
        val open = ScheduleWindow(zone = utc)
        assertTrue(open.isUnconstrained)
        assertTrue(ScheduleEvaluator.isActive(open, at("2026-09-11", "00:00:00")))
    }

    @Test
    fun dateBeforeStartIsInactive() {
        val window = ScheduleWindow(
            startDate = LocalDate.parse("2026-09-11"),
            endDate = LocalDate.parse("2026-09-20"),
            zone = utc,
        )
        assertFalse(ScheduleEvaluator.isActive(window, at("2026-09-10", "23:59:59")))
        assertTrue(ScheduleEvaluator.isActive(window, at("2026-09-11", "00:00:00")))
        assertTrue(ScheduleEvaluator.isActive(window, at("2026-09-20", "23:59:59")))
        assertFalse(ScheduleEvaluator.isActive(window, at("2026-09-21", "00:00:00")))
    }

    @Test
    fun weekdaysOnly() {
        val weekdays = ScheduleWindow(
            daysOfWeek = setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            zone = utc,
        )
        // 2026-09-11 is a Friday
        assertTrue(ScheduleEvaluator.isActive(weekdays, at("2026-09-11", "09:00:00")))
        // 2026-09-12 is a Saturday
        assertFalse(ScheduleEvaluator.isActive(weekdays, at("2026-09-12", "09:00:00")))
        // 2026-09-13 is a Sunday
        assertFalse(ScheduleEvaluator.isActive(weekdays, at("2026-09-13", "09:00:00")))
        // 2026-09-14 is a Monday
        assertTrue(ScheduleEvaluator.isActive(weekdays, at("2026-09-14", "09:00:00")))
    }

    @Test
    fun daytimeWindow() {
        val office = ScheduleWindow(
            startTime = LocalTime.parse("09:00"),
            endTime = LocalTime.parse("17:00"),
            zone = utc,
        )
        assertFalse(ScheduleEvaluator.isActive(office, at("2026-09-11", "08:59:59")))
        assertTrue(ScheduleEvaluator.isActive(office, at("2026-09-11", "09:00:00")))
        assertTrue(ScheduleEvaluator.isActive(office, at("2026-09-11", "16:59:59")))
        assertFalse(ScheduleEvaluator.isActive(office, at("2026-09-11", "17:00:00")))
    }

    @Test
    fun overnightWindowWrapsMidnight() {
        val night = ScheduleWindow(
            startTime = LocalTime.parse("22:00"),
            endTime = LocalTime.parse("06:00"),
            zone = utc,
        )
        assertTrue(ScheduleEvaluator.isActive(night, at("2026-09-11", "22:00:00")))
        assertTrue(ScheduleEvaluator.isActive(night, at("2026-09-11", "23:30:00")))
        assertTrue(ScheduleEvaluator.isActive(night, at("2026-09-12", "05:59:59")))
        assertFalse(ScheduleEvaluator.isActive(night, at("2026-09-12", "06:00:00")))
        assertFalse(ScheduleEvaluator.isActive(night, at("2026-09-11", "12:00:00")))
        assertFalse(ScheduleEvaluator.isActive(night, at("2026-09-11", "21:59:59")))
    }

    @Test
    fun equalStartAndEndTimesAreAllDay() {
        val allDay = ScheduleWindow(
            startTime = LocalTime.parse("08:00"),
            endTime = LocalTime.parse("08:00"),
            zone = utc,
        )
        assertTrue(ScheduleEvaluator.isActive(allDay, at("2026-09-11", "00:00:00")))
        assertTrue(ScheduleEvaluator.isActive(allDay, at("2026-09-11", "23:59:59")))
    }

    @Test
    fun combinedDateDayAndTime() {
        val window = ScheduleWindow(
            startDate = LocalDate.parse("2026-09-01"),
            endDate = LocalDate.parse("2026-09-30"),
            startTime = LocalTime.parse("18:00"),
            endTime = LocalTime.parse("21:00"),
            daysOfWeek = setOf(DayOfWeek.FRIDAY),
            zone = utc,
        )
        assertTrue(ScheduleEvaluator.isActive(window, at("2026-09-11", "18:00:00")))
        assertFalse(ScheduleEvaluator.isActive(window, at("2026-09-11", "17:59:59")))
        assertFalse(ScheduleEvaluator.isActive(window, at("2026-09-10", "19:00:00"))) // Thursday
        assertFalse(ScheduleEvaluator.isActive(window, at("2026-08-28", "19:00:00"))) // Friday but before startDate
    }

    @Test
    fun zoneConversionUsesScheduleZone() {
        val tokyo = ZoneId.of("Asia/Tokyo")
        val window = ScheduleWindow(
            startTime = LocalTime.parse("00:00"),
            endTime = LocalTime.parse("01:00"),
            zone = tokyo,
        )
        // 2026-09-10 15:30 UTC = 2026-09-11 00:30 JST → inside
        val utcInstant = ZonedDateTime.of(LocalDate.parse("2026-09-10"), LocalTime.parse("15:30"), utc)
        assertTrue(ScheduleEvaluator.isActive(window, utcInstant))
        // 2026-09-10 16:00 UTC = 2026-09-11 01:00 JST → exclusive end
        val utcEnd = ZonedDateTime.of(LocalDate.parse("2026-09-10"), LocalTime.parse("16:00"), utc)
        assertFalse(ScheduleEvaluator.isActive(window, utcEnd))
    }

    @Test
    fun anyPlayableFalseWhenEveryItemIsOutOfWindow() {
        val past = ScheduleWindow(endDate = LocalDate.parse("2020-01-01"), zone = utc)
        val now = at("2026-09-11", "12:00:00")
        assertFalse(ScheduleEvaluator.anyPlayable(listOf(past, past), now))
        assertTrue(ScheduleEvaluator.anyPlayable(listOf(past, null), now))
    }
}
