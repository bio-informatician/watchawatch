package com.watchawatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class LocalInsightsTest {
    @Test
    fun auditFindsMissingProjectDescriptionAndRunawayDuration() {
        val entries = listOf(
            TimeEntry("missing-project", "removed", "", 0, 0, 0),
            TimeEntry("long", "work", "Deep work", 0, 13 * 60 * 60_000L, 13 * 60 * 60_000L)
        )

        val findings = auditEntries(entries, listOf(Project("work", "Work", 0)))

        assertEquals(
            setOf(
                "Project is missing",
                "Description is missing",
                "Duration is zero or invalid",
                "Continuous session exceeds 12 hours"
            ),
            findings.map { it.message }.toSet()
        )
    }

    @Test
    fun timesheetLockOnlyAppliesToEntriesOlderThanConfiguredWindow() {
        val now = 40L * 24 * 60 * 60 * 1000
        val oldEntry = TimeEntry("old", "work", "Work", now - 31L * 24 * 60 * 60 * 1000, now, 1000)
        val recentEntry = oldEntry.copy(id = "recent", startMs = now - 2L * 24 * 60 * 60 * 1000)

        assertTrue(isTimesheetEntryLocked(oldEntry, true, 30, now))
        assertFalse(isTimesheetEntryLocked(recentEntry, true, 30, now))
        assertFalse(isTimesheetEntryLocked(oldEntry, false, 30, now))
    }

    @Test
    fun forecastUsesSevenDayAverageAndReportsDaysToBudget() {
        val today = LocalDate.of(2026, 11, 2)
        val entries = (0..6).map { day ->
            val start = today.minusDays(day.toLong()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            TimeEntry("entry-$day", "work", "Work", start, start + 60 * 60_000L, 60 * 60_000L)
        }

        val forecast = forecastProject(entries, "work", 14 * 60L, today, ZoneOffset.UTC)

        assertEquals(420L, forecast.loggedMinutes)
        assertEquals(60L, forecast.projectedMinutesPerDay)
        assertEquals(7, forecast.daysUntilBudget)
    }

    @Test
    fun forecastOmitsBudgetDateWhenNoRecentActivityExists() {
        val forecast = forecastProject(emptyList(), "work", 100, LocalDate.of(2026, 11, 2), ZoneOffset.UTC)

        assertEquals(0L, forecast.loggedMinutes)
        assertNull(forecast.daysUntilBudget)
    }
}
