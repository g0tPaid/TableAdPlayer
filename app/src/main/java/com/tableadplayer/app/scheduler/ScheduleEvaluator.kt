package com.tableadplayer.app.scheduler

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Inclusive date window + time-of-day window + days-of-week filter.
 * Null/empty fields mean "no constraint". A fully empty window plays normally.
 */
data class ScheduleWindow(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val startTime: LocalTime? = null,
    val endTime: LocalTime? = null,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val zone: ZoneId = ZoneId.of("UTC"),
) {
    val isUnconstrained: Boolean
        get() = startDate == null &&
            endDate == null &&
            startTime == null &&
            endTime == null &&
            daysOfWeek.isEmpty()
}

object ScheduleEvaluator {
    /** How long the engine waits when every item is outside its window. */
    const val POLL_MS: Long = 15_000L

    /**
     * @return true when [item] should play at [now].
     * A null or unconstrained [ScheduleWindow] always plays.
     */
    fun isActive(schedule: ScheduleWindow?, now: ZonedDateTime): Boolean {
        if (schedule == null || schedule.isUnconstrained) return true
        val local = now.withZoneSameInstant(schedule.zone)
        if (!inDateRange(schedule, local.toLocalDate())) return false
        if (!inDaysOfWeek(schedule, local.dayOfWeek)) return false
        if (!inTimeRange(schedule, local.toLocalTime())) return false
        return true
    }

    fun anyPlayable(schedules: List<ScheduleWindow?>, now: ZonedDateTime): Boolean =
        schedules.any { isActive(it, now) }

    private fun inDateRange(schedule: ScheduleWindow, date: LocalDate): Boolean {
        val start = schedule.startDate
        val end = schedule.endDate
        if (start != null && date.isBefore(start)) return false
        if (end != null && date.isAfter(end)) return false
        return true
    }

    private fun inDaysOfWeek(schedule: ScheduleWindow, day: DayOfWeek): Boolean {
        if (schedule.daysOfWeek.isEmpty()) return true
        return day in schedule.daysOfWeek
    }

    /**
     * Inclusive start, exclusive end. When start > end the window wraps midnight
     * (e.g. 22:00–06:00). Equal start and end means all day.
     */
    private fun inTimeRange(schedule: ScheduleWindow, time: LocalTime): Boolean {
        val start = schedule.startTime
        val end = schedule.endTime
        if (start == null && end == null) return true
        if (start != null && end == null) return !time.isBefore(start)
        if (start == null && end != null) return time.isBefore(end)
        check(start != null && end != null)
        if (start == end) return true
        return if (start.isBefore(end)) {
            !time.isBefore(start) && time.isBefore(end)
        } else {
            !time.isBefore(start) || time.isBefore(end)
        }
    }
}
