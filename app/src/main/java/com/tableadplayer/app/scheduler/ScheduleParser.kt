package com.tableadplayer.app.scheduler

import com.tableadplayer.app.data.local.ScheduleEntity
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.DateTimeException
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

object ScheduleParser {
    private val DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME_HM: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
    private val TIME_HMS: DateTimeFormatter = DateTimeFormatter.ofPattern("H:mm:ss")

    fun fromEntity(entity: ScheduleEntity, fallbackZone: ZoneId = ZoneId.of("UTC")): ScheduleWindow {
        val zone = parseZone(entity.timezone) ?: fallbackZone
        return ScheduleWindow(
            startDate = parseDate(entity.startDate),
            endDate = parseDate(entity.endDate),
            startTime = parseTime(entity.startTime),
            endTime = parseTime(entity.endTime),
            daysOfWeek = parseDaysOfWeek(entity.daysOfWeek),
            zone = zone,
        )
    }

    fun parseDate(raw: String?): LocalDate? {
        if (raw.isNullOrBlank()) return null
        return try {
            LocalDate.parse(raw.trim(), DATE)
        } catch (_: DateTimeParseException) {
            null
        }
    }

    fun parseTime(raw: String?): LocalTime? {
        if (raw.isNullOrBlank()) return null
        val value = raw.trim()
        return try {
            when {
                value.count { it == ':' } >= 2 -> LocalTime.parse(value, TIME_HMS)
                else -> LocalTime.parse(value, TIME_HM)
            }
        } catch (_: DateTimeParseException) {
            try {
                LocalTime.parse(value)
            } catch (_: DateTimeParseException) {
                null
            }
        }
    }

    fun parseZone(raw: String?): ZoneId? {
        if (raw.isNullOrBlank()) return null
        return try {
            ZoneId.of(raw.trim())
        } catch (_: DateTimeException) {
            null
        }
    }

    /**
     * Accepts ISO numbers (`1=Mon … 7=Sun`), `0` as Sunday, and English names
     * (`MON,TUE` / `monday`). Empty → no day filter.
     */
    fun parseDaysOfWeek(raw: String?): Set<DayOfWeek> {
        if (raw.isNullOrBlank()) return emptySet()
        val parts = raw.split(',', ';', ' ')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val days = LinkedHashSet<DayOfWeek>()
        for (part in parts) {
            parseDay(part)?.let { days += it }
        }
        return days
    }

    private fun parseDay(raw: String): DayOfWeek? {
        val token = raw.trim().uppercase()
        token.toIntOrNull()?.let { n ->
            return when (n) {
                0, 7 -> DayOfWeek.SUNDAY
                in 1..6 -> DayOfWeek.of(n)
                else -> null
            }
        }
        return when (token.take(3)) {
            "MON" -> DayOfWeek.MONDAY
            "TUE" -> DayOfWeek.TUESDAY
            "WED" -> DayOfWeek.WEDNESDAY
            "THU" -> DayOfWeek.THURSDAY
            "FRI" -> DayOfWeek.FRIDAY
            "SAT" -> DayOfWeek.SATURDAY
            "SUN" -> DayOfWeek.SUNDAY
            else -> null
        }
    }
}
