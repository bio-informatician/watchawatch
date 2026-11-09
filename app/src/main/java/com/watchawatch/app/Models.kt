package com.watchawatch.app

data class Project(
    val id: String,
    val name: String,
    val color: Int,
    val billRateCentsPerHour: Long = 0,
    val laborCostCentsPerHour: Long = 0,
    val budgetMinutes: Long = 0,
    val retainerCents: Long = 0,
    val currencyCode: String = "USD",
    val budgetAlertPercent: Int = 80
)

data class TimeEntry(
    val id: String,
    val projectId: String,
    val task: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val note: String = "",
    val customFieldValue: String = "",
    val billRateCentsPerHour: Long = 0,
    val laborCostCentsPerHour: Long = 0,
    val currencyCode: String = "USD"
)

data class ActiveTimer(
    val projectId: String,
    val task: String,
    val startedAtMs: Long,
    val elapsedBeforeMs: Long,
    val running: Boolean,
    val note: String = "",
    val customFieldValue: String = "",
    val billRateCentsPerHour: Long = 0,
    val laborCostCentsPerHour: Long = 0,
    val currencyCode: String = "USD"
)

data class ProjectExpense(
    val id: String,
    val projectId: String,
    val title: String,
    val amountCents: Long,
    val createdAtMs: Long,
    val receiptFileName: String = "",
    val currencyCode: String = "USD"
)

data class ReceiptDraft(val projectId: String, val title: String, val amountCents: Long)

data class AppState(
    val projects: List<Project> = listOf(Project("general", "Personal", 0)),
    val entries: List<TimeEntry> = emptyList(),
    val active: ActiveTimer? = null,
    val dailyGoalMinutes: Int = 240,
    val darkMode: Boolean = false,
    val forceTimer: Boolean = false,
    val requireTaskDescription: Boolean = false,
    val requireEntryNote: Boolean = false,
    val timesheetLockEnabled: Boolean = false,
    val timesheetLockAfterDays: Int = 30,
    val ptoAllowanceDays: Int = 20,
    val ptoUsedDays: Int = 0,
    val customFieldName: String = "",
    val expenses: List<ProjectExpense> = emptyList()
)
