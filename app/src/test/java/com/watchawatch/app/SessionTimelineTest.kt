package com.watchawatch.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class SessionTimelineTest {

    private val zoneId = ZoneId.of("UTC")

    private fun makeEntry(
        id: String,
        projectId: String,
        task: String,
        startIso: String,
        endIso: String
    ): TimeEntry {
        val startMs = Instant.parse(startIso).toEpochMilli()
        val endMs = Instant.parse(endIso).toEpochMilli()
        return TimeEntry(
            id = id,
            projectId = projectId,
            task = task,
            startMs = startMs,
            endMs = endMs,
            durationMs = endMs - startMs
        )
    }

    @Test
    fun testEmptyEntries() {
        val intervals = SessionTimelineLogic.computeIntervals(emptyList())
        assertTrue(intervals.isEmpty())

        val dayGroups = SessionTimelineLogic.groupByDay(emptyList(), zoneId)
        assertTrue(dayGroups.isEmpty())

        val project = Project("study", "مطالعه", 0)
        val activitySummary = SessionTimelineLogic.groupByActivity(emptyList(), project)
        assertEquals(0, activitySummary.sessionCount)
        assertEquals(0L, activitySummary.totalDurationMs)
        assertEquals(0L, activitySummary.averageDurationMs)
        assertTrue(activitySummary.sessions.isEmpty())
    }

    @Test
    fun testSingleSessionHasNoNextSession() {
        val entry = makeEntry("1", "study", "Reading Book", "2026-11-09T10:00:00Z", "2026-11-09T11:30:00Z")
        val intervals = SessionTimelineLogic.computeIntervals(listOf(entry))

        assertEquals(1, intervals.size)
        val item = intervals[0]
        assertEquals("Reading Book", item.entry.task)
        assertEquals(5400000L, item.entry.durationMs) // 1h 30m
        assertNull(item.nextStartMs)
        assertNull(item.gapMs)
        assertFalse(item.hasSubsequentSession)
        assertFalse(item.isBackToBack)
    }

    @Test
    fun testStudySessionIntervalsAndResumption() {
        // Study 1: 10:00 to 11:30 (1h 30m)
        val entry1 = makeEntry("1", "study", "Reading Chapter 1", "2026-11-09T10:00:00Z", "2026-11-09T11:30:00Z")
        // Gap: 30 minutes break until 12:00
        // Study 2: 12:00 to 13:00 (1h)
        val entry2 = makeEntry("2", "study", "Reading Chapter 2", "2026-11-09T12:00:00Z", "2026-11-09T13:00:00Z")
        // Gap: 2 hours break until 15:00
        // Study 3: 15:00 to 16:30 (1h 30m)
        val entry3 = makeEntry("3", "study", "Summary Writing", "2026-11-09T15:00:00Z", "2026-11-09T16:30:00Z")

        // Pass in random order to verify chronological sorting
        val intervals = SessionTimelineLogic.computeIntervals(listOf(entry2, entry3, entry1))

        assertEquals(3, intervals.size)

        // Session 1 checks
        val s1 = intervals[0]
        assertEquals("Reading Chapter 1", s1.entry.task)
        assertEquals(Instant.parse("2026-11-09T12:00:00Z").toEpochMilli(), s1.nextStartMs)
        assertEquals(1800000L, s1.gapMs) // 30 minutes
        assertTrue(s1.hasSubsequentSession)
        assertFalse(s1.isBackToBack)
        assertEquals("Reading Chapter 2", s1.nextTask)

        // Resumption string formatting
        val resumption1 = SessionTimelineLogic.formatResumption(s1, zoneId)
        assertNotNull(resumption1)
        assertTrue(resumption1!!.contains("12:00 PM"))
        assertTrue(resumption1.contains("30m break"))

        // Session 2 checks
        val s2 = intervals[1]
        assertEquals("Reading Chapter 2", s2.entry.task)
        assertEquals(Instant.parse("2026-11-09T15:00:00Z").toEpochMilli(), s2.nextStartMs)
        assertEquals(7200000L, s2.gapMs) // 2 hours
        val resumption2 = SessionTimelineLogic.formatResumption(s2, zoneId)
        assertNotNull(resumption2)
        assertTrue(resumption2!!.contains("3:00 PM"))
        assertTrue(resumption2.contains("2h break"))

        // Session 3 checks (latest)
        val s3 = intervals[2]
        assertNull(s3.nextStartMs)
        assertNull(s3.gapMs)
        assertNull(SessionTimelineLogic.formatResumption(s3, zoneId))
    }

    @Test
    fun testBackToBackSessions() {
        val entry1 = makeEntry("1", "work", "Task A", "2026-11-09T09:00:00Z", "2026-11-09T10:00:00Z")
        val entry2 = makeEntry("2", "work", "Task B", "2026-11-09T10:00:00Z", "2026-11-09T11:00:00Z")

        val intervals = SessionTimelineLogic.computeIntervals(listOf(entry1, entry2))
        assertEquals(2, intervals.size)
        assertTrue(intervals[0].isBackToBack)
        assertEquals(0L, intervals[0].gapMs)

        val resumption = SessionTimelineLogic.formatResumption(intervals[0], zoneId)
        assertNotNull(resumption)
        assertTrue(resumption!!.contains("Immediately"))
    }

    @Test
    fun testGroupByDay() {
        val day1Entry1 = makeEntry("1", "study", "Read", "2026-11-08T10:00:00Z", "2026-11-08T11:00:00Z")
        val day1Entry2 = makeEntry("2", "study", "Notes", "2026-11-08T11:30:00Z", "2026-11-08T12:00:00Z")
        val day2Entry1 = makeEntry("3", "work", "Code", "2026-11-09T08:00:00Z", "2026-11-09T09:00:00Z")

        val groups = SessionTimelineLogic.groupByDay(listOf(day1Entry1, day1Entry2, day2Entry1), zoneId)

        assertEquals(2, groups.size)
        // Most recent day first: 2026-11-09
        assertEquals(LocalDate.parse("2026-11-09"), groups[0].date)
        assertEquals(1, groups[0].sessions.size)
        assertEquals(3600000L, groups[0].totalDurationMs)

        // Previous day: 2026-11-08
        assertEquals(LocalDate.parse("2026-11-08"), groups[1].date)
        assertEquals(2, groups[1].sessions.size)
        assertEquals(5400000L, groups[1].totalDurationMs) // 1h + 30m
        assertEquals(1800000L, groups[1].sessions[0].gapMs) // 30 min break
    }

    @Test
    fun testGroupByActivityFilter() {
        val studyProject = Project("study", "مطالعه", 0)
        val workProject = Project("work", "کار", 1)

        val e1 = makeEntry("1", "study", "Read Paper", "2026-11-09T09:00:00Z", "2026-11-09T10:00:00Z")
        val e2 = makeEntry("2", "work", "Write Code", "2026-11-09T10:30:00Z", "2026-11-09T11:30:00Z")
        val e3 = makeEntry("3", "study", "Read Book", "2026-11-09T12:00:00Z", "2026-11-09T13:30:00Z")

        val studySummary = SessionTimelineLogic.groupByActivity(listOf(e1, e2, e3), studyProject)

        assertEquals("مطالعه", studySummary.project.name)
        assertEquals(2, studySummary.sessionCount)
        assertEquals(9000000L, studySummary.totalDurationMs) // 1h + 1.5h = 2.5h (9,000,000 ms)
        assertEquals(4500000L, studySummary.averageDurationMs) // 1h 15m average

        // Reversed order for activity history (latest session first)
        assertEquals("Read Book", studySummary.sessions[0].entry.task)
        assertEquals("Read Paper", studySummary.sessions[1].entry.task)
        // Interval on session 1 points to session 3
        assertEquals(Instant.parse("2026-11-09T12:00:00Z").toEpochMilli(), studySummary.sessions[1].nextStartMs)
        assertEquals(7200000L, studySummary.sessions[1].gapMs) // 2h gap between study sessions
    }

    @Test
    fun testFormatGap() {
        assertEquals("< 1 min break", SessionTimelineLogic.formatGap(30_000L))
        assertEquals("15m break", SessionTimelineLogic.formatGap(15 * 60_000L))
        assertEquals("1h break", SessionTimelineLogic.formatGap(60 * 60_000L))
        assertEquals("2h 30m break", SessionTimelineLogic.formatGap(150 * 60_000L))
    }
}
