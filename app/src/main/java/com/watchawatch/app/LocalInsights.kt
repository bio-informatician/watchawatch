package com.watchawatch.app

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class AuditFinding(
    val entryId: String,
    val message: String
)

data class ProjectForecast(
    val loggedMinutes: Long,
    val projectedMinutesPerDay: Long,
    val daysUntilBudget: Int?
)

internal fun auditEntries(
    entries: List<TimeEntry>,
    projects: List<Project>,
    maxContinuousMs: Long = 12 * 60 * 60 * 1000L
): List<AuditFinding> {
    val projectIds = projects.mapTo(mutableSetOf()) { it.id }
    return entries.flatMap { entry ->
        buildList {
            if (entry.projectId !in projectIds) add(AuditFinding(entry.id, "Project is missing"))
            if (entry.task.isBlank()) add(AuditFinding(entry.id, "Description is missing"))
            if (entry.durationMs <= 0 || entry.endMs <= entry.startMs) {
                add(AuditFinding(entry.id, "Duration is zero or invalid"))
            } else if (entry.durationMs > maxContinuousMs) {
                add(AuditFinding(entry.id, "Continuous session exceeds 12 hours"))
            }
        }
    }
}

internal fun isTimesheetEntryLocked(
    entry: TimeEntry,
    enabled: Boolean,
    lockAfterDays: Int,
    nowMs: Long = System.currentTimeMillis()
): Boolean {
    if (!enabled) return false
    val cutoff = nowMs - lockAfterDays.coerceAtLeast(0) * 24L * 60 * 60 * 1000
    return entry.startMs < cutoff
}

internal fun forecastProject(
    entries: List<TimeEntry>,
    projectId: String,
    budgetMinutes: Long,
    today: LocalDate = LocalDate.now(),
    zone: ZoneId = ZoneId.systemDefault()
): ProjectForecast {
    val projectEntries = entries.filter { it.projectId == projectId && it.durationMs > 0 }
    val logged = projectEntries.sumOf { it.durationMs / 60_000L }
    val firstDay = today.minusDays(6)
    val recentMinutes = projectEntries
        .filter {
            val date = Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate()
            date in firstDay..today
        }
        .sumOf { it.durationMs / 60_000L }
    val dailyAverage = recentMinutes / 7
    val daysRemaining = if (budgetMinutes > logged && dailyAverage > 0) {
        ((budgetMinutes - logged + dailyAverage - 1) / dailyAverage).toInt()
    } else null
    return ProjectForecast(logged, dailyAverage, daysRemaining)
}
