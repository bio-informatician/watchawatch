package com.watchawatch.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Detailed representation of a session and its relationship
 * to subsequent work (resumption time and break duration).
 */
data class SessionInterval(
    val entry: TimeEntry,
    val nextStartMs: Long? = null,
    val gapMs: Long? = null,
    val nextTask: String? = null,
    val nextProjectId: String? = null
) {
    val hasSubsequentSession: Boolean get() = nextStartMs != null
    val isBackToBack: Boolean get() = gapMs != null && gapMs <= 60_000L
}

data class DaySessionsGroup(
    val date: LocalDate,
    val sessions: List<SessionInterval>,
    val totalDurationMs: Long,
    val isToday: Boolean = false,
    val isYesterday: Boolean = false
)

data class ActivitySessionsSummary(
    val project: Project,
    val sessions: List<SessionInterval>,
    val totalDurationMs: Long,
    val averageDurationMs: Long,
    val sessionCount: Int
)

object SessionTimelineLogic {

    /**
     * Calculates intervals between consecutive sessions.
     * Takes entries, sorts chronologically by startMs.
     * Computes next session start time and gap duration.
     */
    fun computeIntervals(entries: List<TimeEntry>): List<SessionInterval> {
        if (entries.isEmpty()) return emptyList()
        val sorted = entries.sortedBy { it.startMs }
        return sorted.mapIndexed { index, current ->
            val next = if (index + 1 < sorted.size) sorted[index + 1] else null
            val nextStart = next?.startMs
            val gap = if (next != null) (next.startMs - current.endMs).coerceAtLeast(0L) else null
            SessionInterval(
                entry = current,
                nextStartMs = nextStart,
                gapMs = gap,
                nextTask = next?.task,
                nextProjectId = next?.projectId
            )
        }
    }

    /**
     * Groups sessions by day (most recent day first).
     * Within each day, sessions are ordered chronologically with
     * precise intervals calculated.
     */
    fun groupByDay(entries: List<TimeEntry>, zoneId: ZoneId = ZoneId.systemDefault()): List<DaySessionsGroup> {
        if (entries.isEmpty()) return emptyList()
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)

        val byDay = entries.groupBy { Instant.ofEpochMilli(it.startMs).atZone(zoneId).toLocalDate() }
        val sortedDays = byDay.keys.sortedDescending()

        return sortedDays.map { day ->
            val dayEntries = byDay[day].orEmpty()
            val intervals = computeIntervals(dayEntries)
            val total = dayEntries.sumOf { it.durationMs }
            DaySessionsGroup(
                date = day,
                sessions = intervals,
                totalDurationMs = total,
                isToday = (day == today),
                isYesterday = (day == yesterday)
            )
        }
    }

    /**
     * Filters and summarizes sessions for a specific project/activity.
     */
    fun groupByActivity(
        entries: List<TimeEntry>,
        project: Project
    ): ActivitySessionsSummary {
        val filtered = entries.filter { it.projectId == project.id }
        val intervals = computeIntervals(filtered)
        val total = filtered.sumOf { it.durationMs }
        val avg = if (filtered.isNotEmpty()) total / filtered.size else 0L
        return ActivitySessionsSummary(
            project = project,
            sessions = intervals.reversed(), // newest first
            totalDurationMs = total,
            averageDurationMs = avg,
            sessionCount = filtered.size
        )
    }

    /**
     * Formats gap duration into a human-readable string.
     */
    fun formatGap(gapMs: Long): String {
        val minutes = gapMs / 60_000
        val hours = minutes / 60
        val remMins = minutes % 60
        return when {
            gapMs < 60_000 -> "< 1 min break"
            hours > 0 && remMins > 0 -> "${hours}h ${remMins}m break"
            hours > 0 -> "${hours}h break"
            else -> "${remMins}m break"
        }
    }

    /**
     * Formats time of day from epoch millis.
     */
    fun formatTime(timestamp: Long, zoneId: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(timestamp).atZone(zoneId).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

    /**
     * Formats date label (e.g. "Today · Nov 9", "Yesterday · Nov 8", or "Nov 7, 2026").
     */
    fun formatDateLabel(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): String {
        val today = LocalDate.now(zoneId)
        val yesterday = today.minusDays(1)
        val formatter = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
        return when (date) {
            today -> "Today · ${date.format(formatter)}"
            yesterday -> "Yesterday · ${date.format(formatter)}"
            else -> date.format(formatter)
        }
    }

    /**
     * Formats session resumption description.
     */
    fun formatResumption(interval: SessionInterval, zoneId: ZoneId = ZoneId.systemDefault()): String? {
        val nextStart = interval.nextStartMs ?: return null
        val gap = interval.gapMs ?: 0L
        val timeStr = formatTime(nextStart, zoneId)
        return if (gap <= 60_000L) {
            "Started again at $timeStr (Immediately)"
        } else {
            "Started again at $timeStr (${formatGap(gap)})"
        }
    }
}
