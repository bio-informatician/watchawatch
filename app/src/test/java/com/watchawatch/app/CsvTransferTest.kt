package com.watchawatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset

class CsvTransferTest {
    @Test
    fun parsesQuotedCommasQuotesAndMultilineNotes() {
        val csv = "date,project,task,duration_minutes,note\r\n" +
            "\"2026-11-02T10:00:00+01:00\",\"Home, office\",\"Read \"\"design\"\" notes\",25,\"first line\r\nsecond line\"\r\n"

        val (updated, summary) = importTimeEntries(csv, AppState())

        assertEquals(1, summary.addedEntries)
        assertEquals("Home, office", updated.projects.last().name)
        assertEquals("Read \"design\" notes", updated.entries.first().task)
        assertEquals("first line\nsecond line", updated.entries.first().note)
    }

    @Test
    fun mergesProjectsAndSkipsExistingEntries() {
        val existing = AppState(
            projects = listOf(Project("work", "Work", 0)),
            entries = listOf(TimeEntry("old", "work", "Plan", 1000, 1_201_000, 1_200_000, ""))
        )
        val csv = "date,project,task,duration_minutes,note\n" +
            "\"1970-01-01T01:00:01+01:00\",work,Plan,20,\n" +
            "\"2026-11-02T10:00:00+01:00\",Work,Review,15,\n"

        val (updated, summary) = importTimeEntries(csv, existing)

        assertEquals(0, summary.addedProjects)
        assertEquals(1, summary.addedEntries)
        assertEquals(1, summary.skippedDuplicates)
        assertEquals(1, updated.projects.size)
        assertEquals(2, updated.entries.size)
    }

    @Test
    fun rejectsInvalidHeaderAndMalformedQuotes() {
        assertThrows(IllegalArgumentException::class.java) {
            importTimeEntries("date,project,task,duration,note\n", AppState())
        }
        assertThrows(IllegalArgumentException::class.java) {
            importTimeEntries("date,project,task,duration_minutes,note\n\"unfinished", AppState())
        }
    }

    @Test
    fun rejectsRowsWithInvalidValuesWithoutChangingCurrentState() {
        val original = AppState()
        val csv = "date,project,task,duration_minutes,note\n" +
            "\"2026-11-02T10:00:00+01:00\",New,Review,-1,\n"

        assertThrows(IllegalArgumentException::class.java) {
            importTimeEntries(csv, original)
        }
        assertEquals(1, original.projects.size)
        assertEquals(0, original.entries.size)
    }

    @Test
    fun preservesFractionalMinuteDurations() {
        val csv = "date,project,task,duration_minutes,note\n" +
            "\"2026-11-02T10:00:00+01:00\",Work,Quick check,0.5,\n"

        val (updated, summary) = importTimeEntries(csv, AppState())

        assertEquals(1, summary.addedEntries)
        assertEquals(30_000L, updated.entries.first().durationMs)
    }

    @Test
    fun importsHistoricalMetadataRatesAndCurrency() {
        val csv = "date,project,task,duration_minutes,note,custom_field,bill_rate_cents_per_hour,labor_cost_cents_per_hour,currency_code\n" +
            "\"2026-11-02T10:00:00+01:00\",Consulting,Review,60,Details,ACME-42,15000,8000,EUR\n"

        val (updated, summary) = importTimeEntries(csv, AppState())

        assertEquals(1, summary.addedEntries)
        assertEquals("ACME-42", updated.entries.single().customFieldValue)
        assertEquals(15_000L, updated.entries.single().billRateCentsPerHour)
        assertEquals(8_000L, updated.entries.single().laborCostCentsPerHour)
        assertEquals("EUR", updated.entries.single().currencyCode)
        assertEquals("EUR", updated.projects.last().currencyCode)
    }

    @Test
    fun forceTimerAndTimesheetLockRejectManualImports() {
        val csv = "date,project,task,duration_minutes,note\n" +
            "\"2026-11-02T10:00:00+01:00\",Work,Review,15,\n"
        assertThrows(IllegalArgumentException::class.java) {
            importTimeEntries(csv, AppState(forceTimer = true))
        }

        val oldDate = Instant.ofEpochMilli(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000)
            .atOffset(ZoneOffset.UTC).toString()
        val oldCsv = "date,project,task,duration_minutes,note\n\"$oldDate\",Work,Review,15,\n"
        assertThrows(IllegalArgumentException::class.java) {
            importTimeEntries(oldCsv, AppState(timesheetLockEnabled = true, timesheetLockAfterDays = 30))
        }
    }
}
