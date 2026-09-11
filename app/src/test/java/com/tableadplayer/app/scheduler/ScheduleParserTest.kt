package com.tableadplayer.app.scheduler

import com.tableadplayer.app.data.local.ScheduleEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleParserTest {
    @Test
    fun parsesIsoDatesAndHmTimes() {
        val entity = ScheduleEntity(
            playlistId = "p1",
            startDate = "2026-09-11",
            endDate = "2026-09-30",
            startTime = "9:00",
            endTime = "17:30:00",
            daysOfWeek = "1,2,3,4,5",
            timezone = "UTC",
            createdAt = 0L,
            updatedAt = 0L,
        )
        val window = ScheduleParser.fromEntity(entity)
        assertEquals(LocalDate.parse("2026-09-11"), window.startDate)
        assertEquals(LocalDate.parse("2026-09-30"), window.endDate)
        assertEquals(LocalTime.of(9, 0), window.startTime)
        assertEquals(LocalTime.of(17, 30, 0), window.endTime)
        assertEquals(
            setOf(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY,
            ),
            window.daysOfWeek,
        )
    }

    @Test
    fun sundayAsZeroOrSeven() {
        assertEquals(setOf(DayOfWeek.SUNDAY), ScheduleParser.parseDaysOfWeek("0"))
        assertEquals(setOf(DayOfWeek.SUNDAY), ScheduleParser.parseDaysOfWeek("7"))
    }

    @Test
    fun englishDayNames() {
        assertEquals(
            setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY),
            ScheduleParser.parseDaysOfWeek("sat,SUNDAY"),
        )
    }

    @Test
    fun blankAndInvalidFieldsBecomeUnconstrained() {
        val entity = ScheduleEntity(
            startDate = "not-a-date",
            endTime = "",
            daysOfWeek = " ",
            timezone = "Not/AZone",
            createdAt = 0L,
            updatedAt = 0L,
        )
        val window = ScheduleParser.fromEntity(entity, fallbackZone = ZoneId.of("UTC"))
        assertTrue(window.isUnconstrained)
        assertEquals(ZoneId.of("UTC"), window.zone)
    }

    @Test
    fun emptyDaysMeansEveryDay() {
        assertTrue(ScheduleParser.parseDaysOfWeek(null).isEmpty())
        assertTrue(ScheduleParser.parseDaysOfWeek("").isEmpty())
    }
}
