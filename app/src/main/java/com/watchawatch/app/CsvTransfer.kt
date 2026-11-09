package com.watchawatch.app

import java.time.OffsetDateTime
import java.util.UUID
import kotlin.math.roundToLong

data class ImportSummary(
    val addedProjects: Int,
    val addedEntries: Int,
    val skippedDuplicates: Int
)

internal fun importTimeEntries(csv: String, current: AppState): Pair<AppState, ImportSummary> {
    val records = parseCsvRecords(csv.removePrefix("\uFEFF"))
    require(records.isNotEmpty()) { "The CSV file is empty." }
    require(records.size <= MAX_IMPORT_ROWS + 1) { "The CSV contains too many rows (maximum $MAX_IMPORT_ROWS)." }
    require(!current.forceTimer) { "Manual imports are disabled while Force Timer is enabled." }

    val headers = records.first().map { it.trim().lowercase() }
    val hasCustomField = headers == EXTENDED_HEADERS
    require(headers == EXPECTED_HEADERS || hasCustomField) {
        "This CSV format is not supported. Choose a WatchaWatch export with date, project, task, duration_minutes, and note columns."
    }

    val projectsByName = current.projects.associateBy { normalize(it.name) }.toMutableMap()
    val existingFingerprints = current.entries.mapNotNull { entry ->
        val projectName = current.projects.find { it.id == entry.projectId }?.name ?: return@mapNotNull null
        fingerprint(projectName, entry.task, entry.startMs, entry.durationMs, entry.note)
    }.toMutableSet()
    val importedEntries = mutableListOf<TimeEntry>()
    val createdProjects = mutableListOf<Project>()
    var skipped = 0

    records.drop(1).forEachIndexed { index, row ->
        val rowNumber = index + 2
        if (row.all { it.isBlank() }) return@forEachIndexed
        require(row.size == headers.size) {
            "Row $rowNumber has ${row.size} columns; expected ${headers.size}."
        }

        val timestamp = row[0].trim()
        val projectName = row[1].trim()
        val task = row[2].trim()
        val minutes = row[3].trim().toDoubleOrNull()
        val note = row[4].trim()
        val customFieldValue = if (hasCustomField) row[5].trim() else ""
        val billRate = if (hasCustomField) row[6].trim().toLongOrNull() else 0
        val laborCost = if (hasCustomField) row[7].trim().toLongOrNull() else 0
        val currencyCode = if (hasCustomField) row[8].trim().uppercase() else ""

        require(projectName.isNotEmpty() && projectName.length <= MAX_PROJECT_NAME_LENGTH) {
            "Row $rowNumber has an empty or overlong project name (maximum $MAX_PROJECT_NAME_LENGTH characters)."
        }
        require(task.isNotEmpty() && task.length <= MAX_TASK_LENGTH) {
            "Row $rowNumber has an empty or overlong task (maximum $MAX_TASK_LENGTH characters)."
        }
        require(note.length <= MAX_NOTE_LENGTH) {
            "Row $rowNumber has a note longer than $MAX_NOTE_LENGTH characters."
        }
        require(customFieldValue.length <= MAX_CUSTOM_FIELD_LENGTH) {
            "Row $rowNumber has a custom field longer than $MAX_CUSTOM_FIELD_LENGTH characters."
        }
        require(billRate != null && billRate >= 0 && laborCost != null && laborCost >= 0) {
            "Row $rowNumber has an invalid historical rate."
        }
        require(!hasCustomField || currencyCode.matches(Regex("[A-Z]{3}"))) {
            "Row $rowNumber has an invalid three-letter currency code."
        }
        require(minutes != null && minutes.isFinite() && minutes > 0 && minutes <= MAX_ENTRY_MINUTES) {
            "Row $rowNumber has an invalid duration; enter more than 0 and at most $MAX_ENTRY_MINUTES minutes."
        }

        val startMs = try {
            OffsetDateTime.parse(timestamp).toInstant().toEpochMilli()
        } catch (_: Exception) {
            throw IllegalArgumentException("Row $rowNumber has an invalid date. Use the date from a WatchaWatch CSV export.")
        }
        val durationMs = (minutes * 60_000).roundToLong()
        require(durationMs > 0) { "Row $rowNumber has a duration too small to import." }
        val endMs = try {
            Math.addExact(startMs, durationMs)
        } catch (_: ArithmeticException) {
            throw IllegalArgumentException("Row $rowNumber has a date and duration outside the supported range.")
        }
        val project = projectsByName.getOrPut(normalize(projectName)) {
            Project(
                UUID.randomUUID().toString(), projectName,
                (current.projects.size + createdProjects.size) % PROJECT_COLOR_COUNT,
                currencyCode = currencyCode.ifEmpty { "USD" }
            )
                .also(createdProjects::add)
        }
        val importedEntry = TimeEntry(
            UUID.randomUUID().toString(), project.id, task, startMs, endMs, durationMs, note,
            customFieldValue, billRate, laborCost,
            currencyCode.ifEmpty { project.currencyCode }
        )
        require(!isTimesheetEntryLocked(importedEntry, current.timesheetLockEnabled, current.timesheetLockAfterDays)) {
            "Row $rowNumber is older than the active timesheet lock."
        }
        val key = fingerprint(project.name, task, startMs, durationMs, note)
        if (!existingFingerprints.add(key)) {
            skipped++
        } else {
            importedEntries += importedEntry
        }
    }

    val updated = current.copy(
        projects = current.projects + createdProjects,
        entries = importedEntries.sortedByDescending { it.startMs } + current.entries
    )
    return updated to ImportSummary(createdProjects.size, importedEntries.size, skipped)
}

internal fun parseCsvRecords(input: String): List<List<String>> {
    val records = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var quoteClosed = false
    var index = 0

    fun finishField() {
        row += field.toString()
        field.clear()
        quoteClosed = false
    }

    fun finishRecord() {
        finishField()
        if (row.any(String::isNotBlank)) records += row
        row = mutableListOf()
        require(records.size <= MAX_IMPORT_ROWS + 1) {
            "The CSV contains too many rows (maximum $MAX_IMPORT_ROWS)."
        }
    }

    while (index < input.length) {
        val char = input[index]
        if (inQuotes) {
            if (char == '"') {
                if (input.getOrNull(index + 1) == '"') {
                    field.append('"')
                    index++
                } else {
                    inQuotes = false
                    quoteClosed = true
                }
            } else if (char == '\r') {
                if (input.getOrNull(index + 1) == '\n') index++
                field.append('\n')
            } else {
                field.append(char)
            }
        } else {
            if (quoteClosed && char != ',' && char != '\n' && char != '\r' && char != ' ' && char != '\t') {
                throw IllegalArgumentException("The CSV contains unexpected text after a quoted value.")
            }
            when (char) {
                '"' -> {
                    require(field.isEmpty() && !quoteClosed) { "The CSV contains an unexpected quote." }
                    inQuotes = true
                }
                ',' -> finishField()
                '\n', '\r' -> {
                    if (char == '\r' && input.getOrNull(index + 1) == '\n') index++
                    finishRecord()
                }
                else -> if (!quoteClosed || (char != ' ' && char != '\t')) field.append(char)
            }
        }
        index++
    }

    require(!inQuotes) { "The CSV has an unterminated quoted value." }
    if (field.isNotEmpty() || row.isNotEmpty() || quoteClosed) finishRecord()
    return records
}

private data class EntryFingerprint(
    val project: String,
    val task: String,
    val startMs: Long,
    val durationMs: Long,
    val note: String
)

private fun fingerprint(project: String, task: String, startMs: Long, durationMs: Long, note: String) =
    EntryFingerprint(normalize(project), normalize(task), startMs, durationMs, normalize(note))

private fun normalize(value: String) = value.trim().lowercase()

private const val MAX_IMPORT_ROWS = 100_000
private const val MAX_ENTRY_MINUTES = 10_080L
private const val MAX_PROJECT_NAME_LENGTH = 120
private const val MAX_TASK_LENGTH = 500
private const val MAX_NOTE_LENGTH = 5_000
private const val MAX_CUSTOM_FIELD_LENGTH = 500
private const val PROJECT_COLOR_COUNT = 5
private val EXPECTED_HEADERS = listOf("date", "project", "task", "duration_minutes", "note")
private val EXTENDED_HEADERS = EXPECTED_HEADERS + listOf(
    "custom_field", "bill_rate_cents_per_hour", "labor_cost_cents_per_hour", "currency_code"
)
