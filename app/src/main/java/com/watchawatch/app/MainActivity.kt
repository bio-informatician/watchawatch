package com.watchawatch.app

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Analytics
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

private val Teal = Color(0xFF0D766E)
private val TealLight = Color(0xFFB7E5DC)
private val Orange = Color(0xFFE77839)
private val Ink = Color(0xFF193A38)
private val Muted = Color(0xFF607471)
private val Canvas = Color(0xFFF4F8F6)
private val Palette = listOf(Color(0xFF0D9488), Color(0xFFE77839), Color(0xFF6375D6), Color(0xFFBA6593), Color(0xFF648B4D))
private const val MAX_IMPORT_BYTES = 10 * 1024 * 1024
private const val MAX_TASK_LENGTH = 500
private const val MAX_NOTE_LENGTH = 5_000
private const val MAX_CUSTOM_FIELD_VALUE_LENGTH = 500
private const val MAX_ENTRY_MINUTES = 10_080

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
private data class ReceiptDraft(val projectId: String, val title: String, val amountCents: Long)
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

private class TimeRepository(context: Context) {
    private val preferences = context.getSharedPreferences("watchawatch", Context.MODE_PRIVATE)

    fun load(): AppState {
        val saved = preferences.getString("state", null) ?: return AppState()
        return runCatching {
            val root = JSONObject(saved)
            val projects = root.getJSONArray("projects").toList { json ->
                Project(
                    json.getString("id"), json.getString("name"), json.getInt("color"),
                    json.optLong("billRateCentsPerHour"), json.optLong("laborCostCentsPerHour"),
                    json.optLong("budgetMinutes"), json.optLong("retainerCents"),
                    json.optString("currencyCode", "USD"), json.optInt("budgetAlertPercent", 80)
                )
            }
            val entries = root.getJSONArray("entries").toList { json ->
                TimeEntry(
                    json.getString("id"), json.getString("projectId"), json.getString("task"),
                    json.getLong("startMs"), json.getLong("endMs"), json.getLong("durationMs"),
                    json.optString("note"), json.optString("customFieldValue"),
                    json.optLong("billRateCentsPerHour"), json.optLong("laborCostCentsPerHour"),
                    json.optString("currencyCode", "USD")
                )
            }
            val expenses = root.optJSONArray("expenses")?.toList { json ->
                ProjectExpense(
                    json.getString("id"), json.getString("projectId"), json.getString("title"),
                    json.getLong("amountCents"), json.getLong("createdAtMs"), json.optString("receiptFileName"),
                    json.optString("currencyCode", "USD")
                )
            }.orEmpty()
            val timerJson = root.optJSONObject("active")
            AppState(
                projects = projects.ifEmpty { AppState().projects },
                entries = entries,
                active = timerJson?.let {
                    ActiveTimer(
                        it.getString("projectId"), it.getString("task"), it.getLong("startedAtMs"),
                        it.getLong("elapsedBeforeMs"), it.getBoolean("running"), it.optString("note"),
                        it.optString("customFieldValue"), it.optLong("billRateCentsPerHour"),
                        it.optLong("laborCostCentsPerHour"), it.optString("currencyCode", "USD")
                    )
                },
                dailyGoalMinutes = root.optInt("dailyGoalMinutes", 240),
                darkMode = root.optBoolean("darkMode", false),
                forceTimer = root.optBoolean("forceTimer", false),
                requireTaskDescription = root.optBoolean("requireTaskDescription", false),
                requireEntryNote = root.optBoolean("requireEntryNote", false),
                timesheetLockEnabled = root.optBoolean("timesheetLockEnabled", false),
                timesheetLockAfterDays = root.optInt("timesheetLockAfterDays", 30).coerceIn(1, 365),
                ptoAllowanceDays = root.optInt("ptoAllowanceDays", 20).coerceIn(0, 365),
                ptoUsedDays = root.optInt("ptoUsedDays", 0).coerceIn(0, 365),
                customFieldName = root.optString("customFieldName"),
                expenses = expenses
            )
        }.getOrElse { error ->
            Log.e("WatchaWatch", "Could not read saved time-tracking data", error)
            AppState()
        }
    }

    fun save(state: AppState) {
        val root = JSONObject()
        root.put("projects", JSONArray().apply {
            state.projects.forEach { project ->
                put(
                    JSONObject().put("id", project.id).put("name", project.name).put("color", project.color)
                        .put("billRateCentsPerHour", project.billRateCentsPerHour)
                        .put("laborCostCentsPerHour", project.laborCostCentsPerHour)
                        .put("budgetMinutes", project.budgetMinutes).put("retainerCents", project.retainerCents)
                        .put("currencyCode", project.currencyCode).put("budgetAlertPercent", project.budgetAlertPercent)
                )
            }
        })
        root.put("entries", JSONArray().apply {
            state.entries.forEach { entry ->
                put(
                    JSONObject().put("id", entry.id).put("projectId", entry.projectId)
                        .put("task", entry.task).put("startMs", entry.startMs).put("endMs", entry.endMs)
                        .put("durationMs", entry.durationMs).put("note", entry.note)
                        .put("customFieldValue", entry.customFieldValue)
                        .put("billRateCentsPerHour", entry.billRateCentsPerHour)
                        .put("laborCostCentsPerHour", entry.laborCostCentsPerHour)
                        .put("currencyCode", entry.currencyCode)
                )
            }
        })
        root.put("expenses", JSONArray().apply {
            state.expenses.forEach { expense ->
                put(
                    JSONObject().put("id", expense.id).put("projectId", expense.projectId)
                        .put("title", expense.title).put("amountCents", expense.amountCents)
                        .put("createdAtMs", expense.createdAtMs).put("receiptFileName", expense.receiptFileName)
                        .put("currencyCode", expense.currencyCode)
                )
            }
        })
        root.put("active", state.active?.let {
            JSONObject().put("projectId", it.projectId).put("task", it.task)
                .put("startedAtMs", it.startedAtMs).put("elapsedBeforeMs", it.elapsedBeforeMs)
                .put("running", it.running).put("note", it.note).put("customFieldValue", it.customFieldValue)
                .put("billRateCentsPerHour", it.billRateCentsPerHour)
                .put("laborCostCentsPerHour", it.laborCostCentsPerHour).put("currencyCode", it.currencyCode)
        })
        root.put("dailyGoalMinutes", state.dailyGoalMinutes)
        root.put("darkMode", state.darkMode)
        root.put("forceTimer", state.forceTimer)
        root.put("requireTaskDescription", state.requireTaskDescription)
        root.put("requireEntryNote", state.requireEntryNote)
        root.put("timesheetLockEnabled", state.timesheetLockEnabled)
        root.put("timesheetLockAfterDays", state.timesheetLockAfterDays)
        root.put("ptoAllowanceDays", state.ptoAllowanceDays)
        root.put("ptoUsedDays", state.ptoUsedDays)
        root.put("customFieldName", state.customFieldName)
        preferences.edit().putString("state", root.toString()).apply()
    }

    fun clear(): Boolean = preferences.edit().clear().commit()

    private inline fun <T> JSONArray.toList(read: (JSONObject) -> T): List<T> =
        (0 until length()).map { read(getJSONObject(it)) }
}

class TrackerViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TimeRepository(application)
    var state by mutableStateOf(repository.load())
        private set

    private fun update(next: AppState) {
        state = next
        repository.save(next)
    }

    fun start(projectId: String, task: String = "Focus session", note: String = "", customFieldValue: String = ""): Boolean {
        if (state.active != null) return false
        val cleanTask = task.trim()
        val cleanNote = note.trim()
        val cleanCustomField = customFieldValue.trim()
        if (cleanTask.length > MAX_TASK_LENGTH || cleanNote.length > MAX_NOTE_LENGTH ||
            cleanCustomField.length > MAX_CUSTOM_FIELD_VALUE_LENGTH
        ) return false
        if (state.requireTaskDescription && cleanTask.isEmpty()) return false
        if (state.requireEntryNote && cleanNote.isEmpty()) return false
        update(
            state.copy(
                active = ActiveTimer(
                    projectId, cleanTask.ifEmpty { "Focus session" }, System.currentTimeMillis(), 0, true,
                    cleanNote, cleanCustomField,
                    state.projects.find { it.id == projectId }?.billRateCentsPerHour ?: 0,
                    state.projects.find { it.id == projectId }?.laborCostCentsPerHour ?: 0,
                    state.projects.find { it.id == projectId }?.currencyCode ?: "USD"
                )
            )
        )
        return true
    }

    fun toggleTimer() {
        val active = state.active ?: return
        val now = System.currentTimeMillis()
        update(
            state.copy(
                active = if (active.running) {
                    active.copy(elapsedBeforeMs = elapsed(active, now), running = false)
                } else {
                    active.copy(startedAtMs = now, running = true)
                }
            )
        )
    }

    fun stopTimer() {
        val active = state.active ?: return
        val now = System.currentTimeMillis()
        val duration = elapsed(active, now)
        if (duration > 0) {
            update(
                state.copy(
                    active = null,
                    entries = listOf(
                        TimeEntry(
                            UUID.randomUUID().toString(), active.projectId, active.task, now - duration, now,
                            duration, active.note, active.customFieldValue, active.billRateCentsPerHour,
                            active.laborCostCentsPerHour, active.currencyCode
                        )
                    ) + state.entries
                )
            )
        } else update(state.copy(active = null))
    }

    fun addProject(name: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || state.projects.any { it.name.equals(cleanName, ignoreCase = true) }) return false
        update(state.copy(projects = state.projects + Project(UUID.randomUUID().toString(), cleanName, state.projects.size % Palette.size)))
        return true
    }

    fun deleteProject(projectId: String) {
        if (state.projects.size <= 1 || state.active?.projectId == projectId) return
        update(state.copy(projects = state.projects.filterNot { it.id == projectId }))
    }

    fun addManualEntry(projectId: String, task: String, minutes: Int, note: String, customFieldValue: String = ""): Boolean {
        if (state.forceTimer || minutes <= 0 || minutes > MAX_ENTRY_MINUTES) return false
        val cleanTask = task.trim()
        val cleanNote = note.trim()
        if (cleanTask.length > MAX_TASK_LENGTH || cleanNote.length > MAX_NOTE_LENGTH ||
            customFieldValue.length > MAX_CUSTOM_FIELD_VALUE_LENGTH
        ) return false
        if (state.requireTaskDescription && cleanTask.isEmpty()) return false
        if (state.requireEntryNote && cleanNote.isEmpty()) return false
        val end = System.currentTimeMillis()
        val duration = minutes * 60_000L
        val project = state.projects.find { it.id == projectId } ?: return false
        update(
            state.copy(
                entries = listOf(
                    TimeEntry(
                        UUID.randomUUID().toString(), projectId, cleanTask.ifEmpty { "Manual entry" },
                        end - duration, end, duration, cleanNote, customFieldValue.trim(),
                        project.billRateCentsPerHour, project.laborCostCentsPerHour, project.currencyCode
                    )
                ) + state.entries
            )
        )
        return true
    }

    fun importCsv(csv: String): ImportSummary {
        check(!state.forceTimer) { "Manual imports are disabled while Force Timer is enabled." }
        val (updated, summary) = importTimeEntries(csv, state)
        update(updated)
        return summary
    }

    fun deleteAllData() {
        val receiptDirectory = File(getApplication<Application>().filesDir, "receipts")
        if (receiptDirectory.exists()) {
            check(receiptDirectory.isDirectory) { "Could not delete saved receipts." }
            val receipts = receiptDirectory.listFiles() ?: error("Could not read saved receipts.")
            check(receipts.all { it.isFile && it.delete() }) { "Could not delete saved receipts." }
            check(receiptDirectory.delete()) { "Could not delete saved receipts." }
        }
        check(repository.clear()) { "Could not delete the saved data. Please try again." }
        state = AppState()
    }

    fun setGoal(minutes: Int) = update(state.copy(dailyGoalMinutes = minutes.coerceIn(30, 720)))
    fun toggleDarkMode() = update(state.copy(darkMode = !state.darkMode))
    fun setForceTimer(enabled: Boolean) = update(state.copy(forceTimer = enabled))
    fun setTaskRequired(enabled: Boolean) = update(state.copy(requireTaskDescription = enabled))
    fun setNoteRequired(enabled: Boolean) = update(state.copy(requireEntryNote = enabled))
    fun setTimesheetLock(enabled: Boolean) = update(state.copy(timesheetLockEnabled = enabled))
    fun setTimesheetLockAfterDays(days: Int) = update(state.copy(timesheetLockAfterDays = days.coerceIn(1, 365)))
    fun setPtoAllowanceDays(days: Int) {
        val allowance = days.coerceIn(0, 365)
        update(state.copy(ptoAllowanceDays = allowance, ptoUsedDays = state.ptoUsedDays.coerceAtMost(allowance)))
    }
    fun setPtoUsedDays(days: Int) = update(state.copy(ptoUsedDays = days.coerceIn(0, state.ptoAllowanceDays)))
    fun setCustomFieldName(name: String) = update(state.copy(customFieldName = name.trim().take(40)))

    fun updateProject(project: Project): Boolean {
        if (project.name.isBlank() || state.projects.any { it.id != project.id && it.name.equals(project.name.trim(), true) }) return false
        update(state.copy(projects = state.projects.map { if (it.id == project.id) project.copy(name = project.name.trim()) else it }))
        return true
    }

    fun addExpense(projectId: String, title: String, amountCents: Long, receiptFileName: String = ""): Boolean {
        if (title.isBlank() || amountCents <= 0 || state.projects.none { it.id == projectId }) return false
        update(
            state.copy(
                expenses = listOf(
                    ProjectExpense(
                        UUID.randomUUID().toString(), projectId, title.trim(), amountCents,
                        System.currentTimeMillis(), receiptFileName,
                        state.projects.find { it.id == projectId }?.currencyCode ?: "USD"
                    )
                ) + state.expenses
            )
        )
        return true
    }

    fun elapsedNow(now: Long = System.currentTimeMillis()): Long = state.active?.let { elapsed(it, now) } ?: 0L

    private fun elapsed(active: ActiveTimer, now: Long): Long =
        active.elapsedBeforeMs + if (active.running) (now - active.startedAtMs).coerceAtLeast(0) else 0
}

private enum class Page(val label: String) {
    Today("Today"), Reports("Reports"), Projects("Projects"), Settings("Settings")
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val tracker: TrackerViewModel = viewModel()
            val state = tracker.state
            var pendingReceipt by remember { mutableStateOf<ReceiptDraft?>(null) }
            val overlayPermissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) {
                if (Settings.canDrawOverlays(this)) {
                    runCatching {
                        FloatingTimerService.start(this)
                        moveTaskToBack(true)
                    }.onFailure { error ->
                        Toast.makeText(this, "Could not start the floating timer: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    }
                } else {
                    Toast.makeText(this, "Allow WatchaWatch to display over other apps, then try again.", Toast.LENGTH_LONG).show()
                }
            }
            val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
                            writer.write("date,project,task,duration_minutes,note,custom_field,bill_rate_cents_per_hour,labor_cost_cents_per_hour,currency_code\n")
                            state.entries.sortedBy { it.startMs }.forEach { entry ->
                                val project = state.projects.find { it.id == entry.projectId }?.name ?: "Archived project"
                                writer.write(
                                    listOf(
                                        csv(dateTime(entry.startMs)), csv(project), csv(entry.task),
                                        csv(formatMinutes(entry.durationMs)), csv(entry.note), csv(entry.customFieldValue),
                                        entry.billRateCentsPerHour.toString(), entry.laborCostCentsPerHour.toString(),
                                        csv(entry.currencyCode)
                                    ).joinToString(",") + "\n"
                                )
                            }
                        } ?: error("Could not open the selected file")
                    }.onFailure { error ->
                        Toast.makeText(this, "Export failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    }.onSuccess {
                        Toast.makeText(this, "Time entries exported", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                if (uri != null) {
                    runCatching {
                        val csv = readCsv(this, uri)
                        tracker.importCsv(csv)
                    }.onFailure { error ->
                        Toast.makeText(this, "Import failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    }.onSuccess { summary ->
                        Toast.makeText(
                            this,
                            "Imported ${summary.addedEntries} session${if (summary.addedEntries == 1) "" else "s"}; " +
                                "skipped ${summary.skippedDuplicates} duplicate${if (summary.skippedDuplicates == 1) "" else "s"}.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
            val receiptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                val draft = pendingReceipt
                pendingReceipt = null
                if (uri != null && draft != null) {
                    var receiptFileName: String? = null
                    runCatching {
                        val fileName = saveReceipt(this, uri)
                        receiptFileName = fileName
                        check(tracker.addExpense(draft.projectId, draft.title, draft.amountCents, fileName)) {
                            "Could not save this expense."
                        }
                    }.onFailure { error ->
                        receiptFileName?.let { File(File(filesDir, "receipts"), it).delete() }
                        Toast.makeText(this, "Receipt import failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                    }.onSuccess {
                        Toast.makeText(this, "Expense and receipt saved on this device.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            WatchaWatchTheme(darkMode = state.darkMode) {
                WatchaWatchApp(
                    tracker = tracker,
                    onExport = { exportLauncher.launch("watchawatch-time-entries.csv") },
                    onImport = {
                        importLauncher.launch(
                            arrayOf("text/csv", "text/comma-separated-values", "application/csv", "application/vnd.ms-excel", "text/plain", "application/octet-stream")
                        )
                    },
                    onDeleteAll = {
                        runCatching { tracker.deleteAllData() }
                            .onFailure { error ->
                                Toast.makeText(this, "Could not delete data: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                            }.onSuccess {
                                Toast.makeText(this, "Local data deleted", Toast.LENGTH_SHORT).show()
                            }
                    },
                    onFloatAndMinimize = {
                        if (state.active == null) {
                            Toast.makeText(this, "Start a focus timer before minimizing.", Toast.LENGTH_SHORT).show()
                        } else if (Settings.canDrawOverlays(this)) {
                            runCatching {
                                FloatingTimerService.start(this)
                                moveTaskToBack(true)
                            }.onFailure { error ->
                                Toast.makeText(this, "Could not start the floating timer: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                            }
                        } else {
                            Toast.makeText(this, "Allow WatchaWatch to display over other apps to use the floating timer.", Toast.LENGTH_LONG).show()
                            overlayPermissionLauncher.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        }
                    },
                    onManageOverlayPermission = {
                        startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                        )
                    },
                    onPickReceipt = { projectId, title, amountCents ->
                        pendingReceipt = ReceiptDraft(projectId, title, amountCents)
                        receiptLauncher.launch(arrayOf("image/*"))
                    },
                    onViewReceipt = { receiptFileName ->
                        runCatching {
                            val receipt = File(File(filesDir, "receipts"), receiptFileName)
                            check(receipt.isFile) { "Receipt file is missing." }
                            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", receipt)
                            startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "image/*")
                                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                    "Open receipt with"
                                )
                            )
                        }.onFailure { error ->
                            Toast.makeText(this, "Could not open receipt: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun WatchaWatchTheme(darkMode: Boolean, content: @Composable () -> Unit) {
    val light = lightColorScheme(
        primary = Teal, onPrimary = Color.White, secondary = Orange, onSecondary = Color.White,
        background = Canvas, onBackground = Ink, surface = Color.White, onSurface = Ink,
        surfaceVariant = Color(0xFFE8F1EE), onSurfaceVariant = Muted, outline = Color(0xFFD8E5E0)
    )
    val dark = darkColorScheme(
        primary = Color(0xFF7BD3C5), onPrimary = Color(0xFF003731), secondary = Color(0xFFFFB58D),
        onSecondary = Color(0xFF4F2108), background = Color(0xFF101A19), onBackground = Color(0xFFE4F2EF),
        surface = Color(0xFF1A2624), onSurface = Color(0xFFE4F2EF),
        surfaceVariant = Color(0xFF273633), onSurfaceVariant = Color(0xFFB7C9C4), outline = Color(0xFF40534E)
    )
    MaterialTheme(colorScheme = if (darkMode) dark else light, content = content)
}

@Composable
private fun WatchaWatchApp(
    tracker: TrackerViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteAll: () -> Unit,
    onFloatAndMinimize: () -> Unit,
    onManageOverlayPermission: () -> Unit,
    onPickReceipt: (String, String, Long) -> Unit,
    onViewReceipt: (String) -> Unit
) {
    var page by remember { mutableStateOf(Page.Today) }
    var showManual by remember { mutableStateOf(false) }
    var showAddProject by remember { mutableStateOf(false) }
    var pendingStartProject by remember { mutableStateOf<String?>(null) }
    val state = tracker.state
    val startProject: (String) -> Unit = { projectId ->
        if (state.requireTaskDescription || state.requireEntryNote || state.customFieldName.isNotBlank()) {
            pendingStartProject = projectId
        } else {
            tracker.start(projectId)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Page.entries.forEach { item ->
                    NavigationBarItem(
                        selected = page == item,
                        onClick = { page = item },
                        icon = {
                            Icon(
                                when (item) {
                                    Page.Today -> Icons.Rounded.Home
                                    Page.Reports -> Icons.Rounded.Analytics
                                    Page.Projects -> Icons.Rounded.Folder
                                    Page.Settings -> Icons.Rounded.Tune
                                },
                                contentDescription = item.label
                            )
                        },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { insets ->
        when (page) {
            Page.Today -> TodayPage(
                tracker, Modifier.padding(insets),
                onManual = { if (!state.forceTimer) showManual = true },
                onProject = startProject,
                onFloatAndMinimize = onFloatAndMinimize
            )
            Page.Reports -> ReportsPage(state, tracker.elapsedNow(), Modifier.padding(insets))
            Page.Projects -> ProjectsPage(
                state, tracker, Modifier.padding(insets),
                onAdd = { showAddProject = true },
                onProject = startProject,
                onPickReceipt = onPickReceipt,
                onViewReceipt = onViewReceipt
            )
            Page.Settings -> SettingsPage(
                state, tracker, onExport, onImport, onDeleteAll, onManageOverlayPermission, Modifier.padding(insets)
            )
        }
    }

    if (showManual) {
        ManualEntryDialog(
            projects = state.projects,
            requiredTask = state.requireTaskDescription,
            requiredNote = state.requireEntryNote,
            customFieldName = state.customFieldName,
            onDismiss = { showManual = false },
            onSave = { projectId, task, minutes, note, customFieldValue ->
                tracker.addManualEntry(projectId, task, minutes, note, customFieldValue).also { saved ->
                    if (saved) showManual = false
                }
            }
        )
    }
    pendingStartProject?.let { projectId ->
        TimerDetailsDialog(
            requireTask = state.requireTaskDescription,
            requireNote = state.requireEntryNote,
            customFieldName = state.customFieldName,
            onDismiss = { pendingStartProject = null },
            onStart = { task, note, customFieldValue ->
                tracker.start(projectId, task, note, customFieldValue).also { started ->
                    if (started) pendingStartProject = null
                }
            }
        )
    }
    if (showAddProject) {
        AddProjectDialog(
            onDismiss = { showAddProject = false },
            onAdd = { name ->
                tracker.addProject(name).also { added -> if (added) showAddProject = false }
            }
        )
    }
}

@Composable
private fun TodayPage(
    tracker: TrackerViewModel,
    modifier: Modifier = Modifier,
    onManual: () -> Unit,
    onProject: (String) -> Unit,
    onFloatAndMinimize: () -> Unit
) {
    val state = tracker.state
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.active?.running) {
        while (state.active?.running == true) {
            now = System.currentTimeMillis()
            delay(1000)
        }
        now = System.currentTimeMillis()
    }
    val active = state.active
    val elapsed = tracker.elapsedNow(now)
    val today = entriesForDay(state.entries, LocalDate.now())
    val todayMs = today.sumOf { it.durationMs } + if (active != null && isToday(active.startedAtMs)) elapsed else 0
    val progress = (todayMs.toFloat() / (state.dailyGoalMinutes * 60_000f)).coerceIn(0f, 1f)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background,
                        if (state.darkMode) Color(0xFF142321) else Color(0xFFE5F3EF)
                    )
                )
            ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.CenterVertically) {
                BrandMark(Modifier.size(54.dp))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        "WATCHAWATCH",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 2.1.sp
                    )
                    Text("Good ${greeting()}.", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Make time count.", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                }
            }
        }
        item { FocusStreakCard(focusStreakDays(state.entries, active)) }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(28.dp)
            ) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("TODAY", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 1.4.sp)
                            Text(formatDuration(todayMs), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                        }
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(66.dp)) {
                            androidx.compose.material3.CircularProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxSize().semantics { contentDescription = "Daily goal ${(progress * 100).toInt()} percent complete" },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeWidth = 7.dp,
                                strokeCap = StrokeCap.Round
                            )
                            Text("${(progress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .5f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Daily goal", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${state.dailyGoalMinutes / 60}h ${state.dailyGoalMinutes % 60}m", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
        item {
            val heroShape = RoundedCornerShape(30.dp)
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(heroShape)
                    .background(
                        Brush.linearGradient(
                            if (active != null) {
                                listOf(Color(0xFF087D73), Color(0xFF14505C), Color(0xFF28385F))
                            } else {
                                listOf(Color(0xFF174E59), Color(0xFF26395E))
                            }
                        )
                    )
            ) {
                Canvas(Modifier.align(Alignment.TopEnd).size(190.dp).padding(end = 4.dp, top = 4.dp)) {
                    drawCircle(
                        color = Color.White.copy(alpha = 0.045f),
                        radius = size.minDimension * 0.48f,
                        center = Offset(size.width * 0.78f, size.height * 0.16f)
                    )
                    drawCircle(
                        color = Color.White.copy(alpha = 0.07f),
                        radius = size.minDimension * 0.33f,
                        center = Offset(size.width * 0.78f, size.height * 0.16f),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
                Column(
                    Modifier.fillMaxWidth().padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        if (active == null) "READY WHEN YOU ARE" else if (active.running) "FOCUSING ON" else "TIMER PAUSED",
                        style = MaterialTheme.typography.labelMedium,
                        letterSpacing = 1.5.sp,
                        color = Color.White.copy(alpha = .78f)
                    )
                    Text(
                        if (active == null) "Start a session" else active.task,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text(
                        formatClock(if (active == null) 0 else elapsed),
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (active == null) {
                            Button(
                                onClick = { onProject(state.projects.first().id) },
                                modifier = Modifier.height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Orange)
                            ) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Start focus")
                            }
                        } else {
                            FilledTonalButton(
                                onClick = tracker::toggleTimer,
                                modifier = Modifier.height(50.dp),
                                colors = ButtonDefaults.filledTonalButtonColors(
                                    containerColor = Color.White.copy(alpha = .18f),
                                    contentColor = Color.White
                                )
                            ) {
                                Icon(if (active.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (active.running) "Pause" else "Resume")
                            }
                            Button(
                                onClick = tracker::stopTimer,
                                modifier = Modifier.height(50.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Orange)
                            ) {
                                Icon(Icons.Rounded.Stop, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Finish")
                            }
                        }
                        }
                        if (active != null) {
                            OutlinedButton(
                                onClick = onFloatAndMinimize,
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .6f))
                            ) {
                                Icon(Icons.Rounded.PictureInPictureAlt, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Float timer & minimize")
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Quick start", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (!state.forceTimer) {
                    TextButton(onClick = onManual) {
                        Icon(Icons.Rounded.EditNote, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add time")
                    }
                }
            }
        }
        items(state.projects.take(4), key = { "quick-${it.id}" }) { project ->
            ProjectRow(
                project = project,
                subtitle = if (active?.projectId == project.id) "Currently tracking" else "Tap to start a focus session",
                action = { if (active == null) onProject(project.id) },
                active = active?.projectId == project.id
            )
        }
        item {
            Text("Recent sessions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            if (today.isEmpty()) {
                EmptyState("No time logged today", "Start a timer or add a session manually.")
            } else {
                today.take(4).forEach { entry ->
                    EntryRow(entry, state.projects)
                }
            }
        }
    }
}

@Composable
private fun ReportsPage(state: AppState, currentElapsed: Long, modifier: Modifier = Modifier) {
    val week = (0..6).map { offset -> LocalDate.now().with(java.time.DayOfWeek.MONDAY).plusDays(offset.toLong()) }
    val dailyTotals = week.map { day ->
        entriesForDay(state.entries, day).sumOf { it.durationMs } +
            if (day == LocalDate.now() && state.active?.let { isToday(it.startedAtMs) } == true) currentElapsed else 0L
    }
    val weekTotal = dailyTotals.sum()
    val projectsTotals = state.projects.map { project ->
        project to (state.entries.filter { entry ->
            entry.projectId == project.id && Instant.ofEpochMilli(entry.startMs)
                .atZone(ZoneId.systemDefault()).toLocalDate() in week
        }.sumOf { it.durationMs } +
            if (state.active?.projectId == project.id) currentElapsed else 0L)
    }.sortedByDescending { it.second }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 24.dp, 20.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text("Your reports", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("A clearer picture of where your hours go.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Text("THIS WEEK", style = MaterialTheme.typography.labelMedium, letterSpacing = 1.3.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatDuration(weekTotal), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                        dailyTotals.forEachIndexed { index, total ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.width(26.dp).height((20 + (total.toFloat() / (dailyTotals.maxOrNull()?.coerceAtLeast(1L) ?: 1L) * 70).toInt()).dp)
                                        .background(if (week[index] == LocalDate.now()) Teal else TealLight, RoundedCornerShape(10.dp))
                                        .semantics { contentDescription = "${week[index].dayOfWeek.name.lowercase().take(3)} ${formatDuration(total)}" }
                                )
                                Text(week[index].dayOfWeek.name.take(1), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            Text("By project", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    val max = projectsTotals.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L
                    projectsTotals.forEach { (project, amount) ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(project.name, fontWeight = FontWeight.Medium)
                                Text(formatDuration(amount), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { amount.toFloat() / max },
                                modifier = Modifier.fillMaxWidth().height(8.dp).semantics { contentDescription = "${project.name}: ${formatDuration(amount)}" },
                                color = Palette[project.color % Palette.size],
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }
                }
            }
        }
        item {
            val average = if (dailyTotals.isEmpty()) 0 else weekTotal / dailyTotals.size
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(20.dp)) {
                Row(Modifier.fillMaxWidth().padding(18.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text("Daily average", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(formatDuration(average), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Sessions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${state.entries.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectsPage(
    state: AppState,
    tracker: TrackerViewModel,
    modifier: Modifier = Modifier,
    onAdd: () -> Unit,
    onProject: (String) -> Unit,
    onPickReceipt: (String, String, Long) -> Unit,
    onViewReceipt: (String) -> Unit
) {
    var editingProjectId by remember { mutableStateOf<String?>(null) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 24.dp, 20.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Projects", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text("${state.projects.size} spaces for your focus", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onAdd, modifier = Modifier.height(48.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(4.dp))
                    Text("New")
                }
            }
        }
        items(state.projects, key = { it.id }) { project ->
            val projectEntries = state.entries.filter { it.projectId == project.id }
            val duration = projectEntries.sumOf { it.durationMs } +
                if (state.active?.projectId == project.id) tracker.elapsedNow() else 0
            val forecast = forecastProject(state.entries, project.id, project.budgetMinutes)
            val projectExpenses = state.expenses.filter { it.projectId == project.id }
            val currencyCodes = (projectEntries.map { it.currencyCode } + projectExpenses.map { it.currencyCode }).distinct()
            Card(shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(44.dp).background(Palette[project.color % Palette.size], CircleShape), contentAlignment = Alignment.Center) {
                            Text(project.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Text("${projectEntries.size} sessions", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(formatDuration(duration), fontWeight = FontWeight.SemiBold)
                        IconButton(onClick = { editingProjectId = project.id }, modifier = Modifier.size(48.dp)) {
                            Icon(Icons.Rounded.Tune, contentDescription = "Project settings for ${project.name}", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (project.budgetMinutes > 0) {
                        val budgetProgress = (forecast.loggedMinutes.toFloat() / project.budgetMinutes).coerceIn(0f, 1f)
                        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(
                                "Budget: ${formatDuration(forecast.loggedMinutes * 60_000)} / ${formatDuration(project.budgetMinutes * 60_000)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            androidx.compose.material3.LinearProgressIndicator(
                                progress = { budgetProgress },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (budgetProgress >= project.budgetAlertPercent / 100f) Orange else MaterialTheme.colorScheme.primary
                            )
                            if (budgetProgress >= project.budgetAlertPercent / 100f) {
                                Text(
                                    "Budget alert: ${project.budgetAlertPercent}% threshold reached.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Orange
                                )
                            }
                            if (forecast.daysUntilBudget != null) {
                                Text(
                                    "At the recent pace, budget is reached in about ${forecast.daysUntilBudget} days.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (project.retainerCents > 0) {
                                Text(
                                    "Fixed retainer: ${formatMoney(project.retainerCents, project.currencyCode)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    currencyCodes.forEach { currencyCode ->
                        val revenueCents = projectEntries.filter { it.currencyCode == currencyCode }.sumOf {
                            (it.durationMs.toDouble() / 3_600_000.0 * it.billRateCentsPerHour).toLong()
                        }
                        val laborCents = projectEntries.filter { it.currencyCode == currencyCode }.sumOf {
                            (it.durationMs.toDouble() / 3_600_000.0 * it.laborCostCentsPerHour).toLong()
                        }
                        val expenseCents = projectExpenses.filter { it.currencyCode == currencyCode }.sumOf { it.amountCents }
                        Text(
                            "Revenue ${formatMoney(revenueCents, currencyCode)} · " +
                                "Costs ${formatMoney(laborCents + expenseCents, currencyCode)} · " +
                                "Margin ${formatMoney(revenueCents - laborCents - expenseCents, currencyCode)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (state.active?.projectId == project.id) {
                            Text("Timer running", color = MaterialTheme.colorScheme.primary)
                        } else {
                            TextButton(onClick = { if (state.active == null) onProject(project.id) }, enabled = state.active == null) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                                Text("Start timer")
                            }
                        }
                        if (state.projects.size > 1 && state.active?.projectId != project.id) {
                            IconButton(onClick = { tracker.deleteProject(project.id) }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ${project.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        editingProjectId?.let { projectId ->
                            val project = state.projects.find { it.id == projectId }
                            if (project != null) {
                                ProjectDetailsDialog(
                                    project = project,
                                    expenses = state.expenses.filter { it.projectId == projectId },
                                    onDismiss = { editingProjectId = null },
                                    onSave = tracker::updateProject,
                                    onAddExpense = { title, amountCents -> tracker.addExpense(projectId, title, amountCents) },
                                    onPickReceipt = { title, amountCents -> onPickReceipt(projectId, title, amountCents) },
                                    onViewReceipt = onViewReceipt
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(
    state: AppState,
    tracker: TrackerViewModel,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onDeleteAll: () -> Unit,
    onManageOverlayPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    var confirmDelete by remember { mutableStateOf(false) }
    var showAudit by remember { mutableStateOf(false) }
    val auditFindings = remember(state.entries, state.projects) { auditEntries(state.entries, state.projects) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp, 24.dp, 20.dp, 32.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        item {
            Text("Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Make WatchaWatch work your way.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(Icons.Rounded.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("Daily focus goal", fontWeight = FontWeight.SemiBold)
                            Text("${state.dailyGoalMinutes / 60}h ${state.dailyGoalMinutes % 60}m per day", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Slider(
                        value = state.dailyGoalMinutes.toFloat(),
                        onValueChange = { tracker.setGoal((it / 15).toInt() * 15) },
                        valueRange = 30f..720f,
                        steps = 45,
                        modifier = Modifier.semantics { contentDescription = "Daily goal in minutes" }
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("30 min", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("12 hours", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            SettingRow(
                title = "Dark appearance",
                description = if (state.darkMode) "Easy on the eyes after hours" else "Use the light color theme",
                icon = if (state.darkMode) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                action = tracker::toggleDarkMode,
                trailing = if (state.darkMode) "On" else "Off"
            )
        }
        item {
            Text("Optional work controls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "Each control is off unless you enable it. Changes stay on this device.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    FeatureSwitch(
                        "Force Timer",
                        "Disable manual entries and CSV imports; capture new time with the live timer.",
                        state.forceTimer,
                        tracker::setForceTimer
                    )
                    FeatureSwitch(
                        "Require task description",
                        "Ask for a task before a timer starts or an entry is saved.",
                        state.requireTaskDescription,
                        tracker::setTaskRequired
                    )
                    FeatureSwitch(
                        "Require a note",
                        "Require a note when starting a timer or saving an entry.",
                        state.requireEntryNote,
                        tracker::setNoteRequired
                    )
                    FeatureSwitch(
                        "Lock older timesheets",
                        "Reject imported entries outside the rolling lock window.",
                        state.timesheetLockEnabled,
                        tracker::setTimesheetLock
                    )
                    if (state.timesheetLockEnabled) {
                        Text("Lock window: ${state.timesheetLockAfterDays} days", fontWeight = FontWeight.Medium)
                        Slider(
                            value = state.timesheetLockAfterDays.toFloat(),
                            onValueChange = { tracker.setTimesheetLockAfterDays(it.toInt()) },
                            valueRange = 1f..365f,
                            steps = 363,
                            modifier = Modifier.semantics { contentDescription = "Timesheet lock window in days" }
                        )
                    }
                    HorizontalDivider()
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Custom entry field", fontWeight = FontWeight.SemiBold)
                        Text("Optional metadata label shown when starting a timer or adding time.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = state.customFieldName,
                            onValueChange = tracker::setCustomFieldName,
                            label = { Text("Field name (for example, Client code)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        item {
            SettingRow(
                title = "Run local time audit",
                description = "${auditFindings.size} issue${if (auditFindings.size == 1) "" else "s"} found in saved entries",
                icon = Icons.Rounded.Analytics,
                action = { showAudit = true },
                trailing = "Review"
            )
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Time off", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${(state.ptoAllowanceDays - state.ptoUsedDays).coerceAtLeast(0)} days remaining of ${state.ptoAllowanceDays}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Annual allowance")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { tracker.setPtoAllowanceDays(state.ptoAllowanceDays - 1) }) { Text("−") }
                            Text("${state.ptoAllowanceDays} days", fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { tracker.setPtoAllowanceDays(state.ptoAllowanceDays + 1) }) { Text("+") }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Days used")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { tracker.setPtoUsedDays(state.ptoUsedDays - 1) }) { Text("−") }
                            Text("${state.ptoUsedDays} days", fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { tracker.setPtoUsedDays(state.ptoUsedDays + 1) }) { Text("+") }
                        }
                    }
                }
            }
        }
        item {
            Text("Optional permissions", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                "WatchaWatch does not request permissions on launch. Enable Android overlay access only if you choose to use the floating timer.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SettingRow(
                title = "Floating timer overlay",
                description = "Open Android settings to enable or revoke display-over-apps access.",
                icon = Icons.Rounded.PictureInPictureAlt,
                action = onManageOverlayPermission,
                trailing = "Android"
            )
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Anti-surveillance", fontWeight = FontWeight.SemiBold)
                    Text(
                        "This app does not record keystrokes, screenshots, app or website activity, or location. Time tracking is limited to timers and entries you choose to create.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            SettingRow(
                title = "Import time entries",
                description = if (state.forceTimer) "Unavailable while Force Timer is enabled" else "Merge a WatchaWatch CSV backup from your device",
                icon = Icons.Rounded.FileUpload,
                action = onImport,
                trailing = if (state.forceTimer) "Off" else "CSV",
                enabled = !state.forceTimer
            )
        }
        item {
            SettingRow(
                title = "Export time entries",
                description = "Save a CSV file you can open anywhere",
                icon = Icons.Rounded.FileDownload,
                action = onExport,
                trailing = "CSV"
            )
        }
        item {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("Private by design", fontWeight = FontWeight.SemiBold)
                    }
                    Text(
                        "Projects, settings, time entries, expenses, and receipt images are saved in Android app-private storage until you delete them or uninstall. This app has no internet permission, account, ads, analytics, or app server, so it does not upload your tracking data. If you choose View receipt, Android shares that image temporarily with the viewer you select. Android cloud/device backups are disabled.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Import reads only the CSV you choose and merges new sessions, skipping duplicates. Export uses Android's file picker; a destination you choose may be a cloud provider. Exported files remain outside this app, even if you delete local data.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Data is not encrypted separately by WatchaWatch. Protect your device with a screen lock. This notice describes the app's current behavior; a commercial release needs an identified data controller, contact details, purposes, retention terms, and jurisdiction-specific legal review.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        item {
            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Rounded.DeleteForever, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Delete all local data")
            }
        }
        item {
            Text("WATCHAWATCH  ·  VERSION 1.0", modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete all local data?") },
            text = { Text("This permanently removes all saved projects, time entries, settings, and any active timer from this app on this device. Export a backup first if you may need it.") },
            confirmButton = {
                TextButton(
                    onClick = { confirmDelete = false; onDeleteAll() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete everything") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
    if (showAudit) {
        AlertDialog(
            onDismissRequest = { showAudit = false },
            title = { Text("Local time audit") },
            text = {
                if (auditFindings.isEmpty()) {
                    Text("No missing descriptions, invalid or zero-duration entries, missing projects, or sessions over 12 hours were found.")
                } else {
                    Column(
                        Modifier.height(320.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        auditFindings.forEach { finding ->
                            Text("• ${finding.message} (entry ${finding.entryId.take(8)})")
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAudit = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun ProjectRow(project: Project, subtitle: String, action: () -> Unit, active: Boolean) {
    Card(
        onClick = action,
        enabled = !active,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primary.copy(alpha = .12f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Palette[project.color % Palette.size], CircleShape), contentAlignment = Alignment.Center) {
                Text(project.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(project.name, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (active) Icon(Icons.Rounded.Check, "Timer running", tint = MaterialTheme.colorScheme.primary)
            else Icon(Icons.Rounded.PlayArrow, "Start timer", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun EntryRow(entry: TimeEntry, projects: List<Project>) {
    val project = projects.find { it.id == entry.projectId }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(Modifier.size(10.dp).background(Palette[(project?.color ?: 0) % Palette.size], CircleShape))
        Column(Modifier.weight(1f)) {
            Text(entry.task, fontWeight = FontWeight.Medium)
            Text("${project?.name ?: "Archived project"} · ${timeOfDay(entry.startMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(formatDuration(entry.durationMs), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingRow(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    action: () -> Unit,
    trailing: String,
    enabled: Boolean = true
) {
    Card(
        onClick = action,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                trailing,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun FeatureSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BrandMark(modifier: Modifier = Modifier) {
    Canvas(modifier.semantics { contentDescription = "WatchaWatch clock logo" }) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = size.minDimension * 0.48f
        drawCircle(
            brush = Brush.linearGradient(
                listOf(Color(0xFF0D9488), Color(0xFF15536A), Color(0xFF273B64))
            ),
            radius = radius,
            center = center
        )
        drawCircle(
            color = Color(0xFFFFE9C9),
            radius = radius * 0.76f,
            center = center,
            style = Stroke(width = size.minDimension * 0.045f)
        )
        drawArc(
            color = Color(0xFFFFBE71),
            startAngle = 218f,
            sweepAngle = 245f,
            useCenter = false,
            topLeft = Offset(center.x - radius * 0.66f, center.y - radius * 0.66f),
            size = androidx.compose.ui.geometry.Size(radius * 1.32f, radius * 1.32f),
            style = Stroke(width = size.minDimension * 0.05f, cap = StrokeCap.Round)
        )
        for (tick in 0 until 12) {
            val angle = Math.toRadians((tick * 30 - 90).toDouble())
            val inner = radius * 0.55f
            val outer = radius * 0.65f
            drawLine(
                color = Color.White.copy(alpha = if (tick % 3 == 0) 0.95f else 0.65f),
                start = Offset(center.x + cos(angle).toFloat() * inner, center.y + sin(angle).toFloat() * inner),
                end = Offset(center.x + cos(angle).toFloat() * outer, center.y + sin(angle).toFloat() * outer),
                strokeWidth = size.minDimension * if (tick % 3 == 0) 0.045f else 0.028f,
                cap = StrokeCap.Round
            )
        }
        drawLine(
            color = Color.White,
            start = center,
            end = Offset(center.x, center.y - radius * 0.39f),
            strokeWidth = size.minDimension * 0.065f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = Color(0xFFFFBE71),
            start = center,
            end = Offset(center.x + radius * 0.34f, center.y + radius * 0.2f),
            strokeWidth = size.minDimension * 0.065f,
            cap = StrokeCap.Round
        )
        drawCircle(color = Color(0xFFFFBE71), radius = size.minDimension * 0.075f, center = center)
    }
}

@Composable
private fun FocusStreakCard(days: Int) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(if (days == 1) "1 day in a row" else "$days-day focus streak", fontWeight = FontWeight.SemiBold)
                Text(
                    if (days == 0) "Log a session today to start your streak."
                    else "A little focus, day after day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text("$days", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun AddProjectDialog(onDismiss: () -> Unit, onAdd: (String) -> Boolean) {
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create a project") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it; error = null }, label = { Text("Project name") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(), isError = error != null
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (!onAdd(name)) error = "A project with that name already exists." },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ManualEntryDialog(
    projects: List<Project>,
    requiredTask: Boolean,
    requiredNote: Boolean,
    customFieldName: String,
    onDismiss: () -> Unit,
    onSave: (String, String, Int, String, String) -> Boolean
) {
    var projectId by remember(projects) { mutableStateOf(projects.first().id) }
    var task by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("30") }
    var note by remember { mutableStateOf("") }
    var customFieldValue by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    val selectedProject = projects.find { it.id == projectId } ?: projects.first()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add time manually") },
        text = {
            Column(
                Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                        Text(selectedProject.name, modifier = Modifier.weight(1f))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        projects.forEach { project ->
                            DropdownMenuItem(text = { Text(project.name) }, onClick = { projectId = project.id; menuOpen = false })
                        }
                    }
                }
                OutlinedTextField(
                    value = task,
                    onValueChange = { task = it; error = null },
                    label = { Text(if (requiredTask) "Task (required)" else "What did you work on?") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = minutes, onValueChange = { minutes = it.filter(Char::isDigit).take(4) },
                    label = { Text("Duration in minutes") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it; error = null },
                    label = { Text(if (requiredNote) "Note (required)" else "Note (optional)") },
                    maxLines = 2, modifier = Modifier.fillMaxWidth()
                )
                if (customFieldName.isNotBlank()) {
                    OutlinedTextField(
                        value = customFieldValue,
                        onValueChange = { customFieldValue = it },
                        label = { Text(customFieldName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!onSave(projectId, task, minutes.toIntOrNull() ?: 0, note, customFieldValue)) {
                        error = when {
                            requiredTask && task.isBlank() -> "Enter a task description."
                            requiredNote && note.isBlank() -> "Enter a note."
                            else -> "Could not save this entry."
                        }
                    }
                },
                enabled = (minutes.toIntOrNull() ?: 0) > 0
            ) { Text("Save entry") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ProjectDetailsDialog(
    project: Project,
    expenses: List<ProjectExpense>,
    onDismiss: () -> Unit,
    onSave: (Project) -> Boolean,
    onAddExpense: (String, Long) -> Boolean,
    onPickReceipt: (String, Long) -> Unit,
    onViewReceipt: (String) -> Unit
) {
    var name by remember(project.id) { mutableStateOf(project.name) }
    var currency by remember(project.id) { mutableStateOf(project.currencyCode) }
    var billRate by remember(project.id) { mutableStateOf((project.billRateCentsPerHour / 100.0).toString()) }
    var laborCost by remember(project.id) { mutableStateOf((project.laborCostCentsPerHour / 100.0).toString()) }
    var budgetHours by remember(project.id) { mutableStateOf((project.budgetMinutes / 60.0).toString()) }
    var retainer by remember(project.id) { mutableStateOf((project.retainerCents / 100.0).toString()) }
    var alertPercent by remember(project.id) { mutableIntStateOf(project.budgetAlertPercent) }
    var expenseTitle by remember(project.id) { mutableStateOf("") }
    var expenseAmount by remember(project.id) { mutableStateOf("") }
    var error by remember(project.id) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Project budget & billing") },
        text = {
            Column(
                Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Project name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    currency.uppercase(), { currency = it.take(3).uppercase() },
                    label = { Text("Currency code (for example USD, EUR)") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    billRate, { billRate = it.filter { char -> char.isDigit() || char == '.' }.take(12) },
                    label = { Text("Client billing rate per hour") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    laborCost, { laborCost = it.filter { char -> char.isDigit() || char == '.' }.take(12) },
                    label = { Text("Internal labor cost per hour") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    budgetHours, { budgetHours = it.filter { char -> char.isDigit() || char == '.' }.take(12) },
                    label = { Text("Budget hours (0 disables)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    retainer, { retainer = it.filter { char -> char.isDigit() || char == '.' }.take(12) },
                    label = { Text("Fixed retainer amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Budget alert at $alertPercent%")
                Slider(
                    value = alertPercent.toFloat(),
                    onValueChange = { alertPercent = (it / 5).toInt() * 5 },
                    valueRange = 50f..100f,
                    steps = 9,
                    modifier = Modifier.semantics { contentDescription = "Project budget alert percentage" }
                )
                HorizontalDivider()
                Text("Project expenses", fontWeight = FontWeight.SemiBold)
                expenses.take(5).forEach {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${it.title}: ${formatMoney(it.amountCents, it.currencyCode)}",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        if (it.receiptFileName.isNotBlank()) {
                            TextButton(onClick = { onViewReceipt(it.receiptFileName) }) { Text("View receipt") }
                        }
                    }
                }
                OutlinedTextField(
                    expenseTitle, { expenseTitle = it },
                    label = { Text("New expense description") },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    expenseAmount, { expenseAmount = it.filter { char -> char.isDigit() || char == '.' }.take(12) },
                    label = { Text("Expense amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = {
                        val cents = expenseAmount.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                            ?.let { (it * 100).toLong() } ?: 0L
                        if (onAddExpense(expenseTitle, cents)) {
                            expenseTitle = ""
                            expenseAmount = ""
                            error = null
                        } else {
                            error = "Enter an expense description and positive amount."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Add expense") }
                OutlinedButton(
                    onClick = {
                        val cents = expenseAmount.toDoubleOrNull()?.takeIf { it.isFinite() && it > 0 }
                            ?.let { (it * 100).toLong() } ?: 0L
                        if (expenseTitle.isBlank() || cents <= 0) {
                            error = "Enter an expense description and positive amount first."
                        } else {
                            onPickReceipt(expenseTitle.trim(), cents)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Attach receipt image") }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val billCents = billRate.toDoubleOrNull()
                    val costCents = laborCost.toDoubleOrNull()
                    val budget = budgetHours.toDoubleOrNull()
                    val retainerCents = retainer.toDoubleOrNull()
                    val valid = name.isNotBlank() && currency.matches(Regex("[A-Z]{3}")) &&
                        listOfNotNull(billCents, costCents, budget, retainerCents).all { it.isFinite() && it >= 0 } &&
                        billCents != null && costCents != null && budget != null && retainerCents != null
                    if (valid) {
                        val saved = onSave(
                            project.copy(
                                name = name.trim(),
                                currencyCode = currency,
                                billRateCentsPerHour = (billCents * 100).toLong(),
                                laborCostCentsPerHour = (costCents * 100).toLong(),
                                budgetMinutes = (budget * 60).toLong(),
                                retainerCents = (retainerCents * 100).toLong(),
                                budgetAlertPercent = alertPercent
                            )
                        )
                        if (saved) onDismiss() else error = "A project with that name already exists."
                    } else {
                        error = "Enter a project name, a three-letter currency code, and valid non-negative amounts."
                    }
                }
            ) { Text("Save settings") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun TimerDetailsDialog(
    requireTask: Boolean,
    requireNote: Boolean,
    customFieldName: String,
    onDismiss: () -> Unit,
    onStart: (String, String, String) -> Boolean
) {
    var task by remember { mutableStateOf(if (requireTask) "" else "Focus session") }
    var note by remember { mutableStateOf("") }
    var customFieldValue by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start a focus timer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = task,
                    onValueChange = { task = it; error = null },
                    label = { Text(if (requireTask) "Task (required)" else "Task") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it; error = null },
                    label = { Text(if (requireNote) "Note (required)" else "Note (optional)") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                if (customFieldName.isNotBlank()) {
                    OutlinedTextField(
                        value = customFieldValue,
                        onValueChange = { customFieldValue = it },
                        label = { Text(customFieldName) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!onStart(task, note, customFieldValue)) {
                        error = when {
                            requireTask && task.isBlank() -> "Enter a task description."
                            requireNote && note.isBlank() -> "Enter a note."
                            else -> "Could not start the timer."
                        }
                    }
                }
            ) { Text("Start timer") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun entriesForDay(entries: List<TimeEntry>, day: LocalDate): List<TimeEntry> {
    val zone = ZoneId.systemDefault()
    return entries.filter { Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate() == day }.sortedByDescending { it.startMs }
}

private fun focusStreakDays(entries: List<TimeEntry>, active: ActiveTimer?): Int {
    val zone = ZoneId.systemDefault()
    val activeDays = entries.mapTo(mutableSetOf()) {
        Instant.ofEpochMilli(it.startMs).atZone(zone).toLocalDate()
    }
    active?.let { activeDays += Instant.ofEpochMilli(it.startedAtMs).atZone(zone).toLocalDate() }
    var day = LocalDate.now()
    if (day !in activeDays) day = day.minusDays(1)
    var streak = 0
    while (day in activeDays) {
        streak++
        day = day.minusDays(1)
    }
    return streak
}

private fun isToday(timestamp: Long): Boolean =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()

private fun formatDuration(milliseconds: Long): String {
    val minutes = (milliseconds.coerceAtLeast(0) / 60_000)
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (hours > 0) "${hours}h ${remainder}m" else "${remainder}m"
}

private fun formatClock(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(Locale.getDefault(), seconds / 3600, (seconds / 60) % 60, seconds % 60)
}

private fun greeting(): String = when (java.time.LocalTime.now().hour) {
    in 5..11 -> "morning"
    in 12..17 -> "afternoon"
    else -> "evening"
}

private fun timeOfDay(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))

private fun dateTime(timestamp: Long): String =
    Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

private fun csv(value: String): String {
    val safeValue = if (value.trimStart().firstOrNull()?.let { it in "=+-@" } == true) "'$value" else value
    return "\"${safeValue.replace("\"", "\"\"")}\""
}

private fun formatMinutes(milliseconds: Long): String =
    "%.3f".format(Locale.ROOT, milliseconds / 60_000.0).trimEnd('0').trimEnd('.')

private fun formatMoney(cents: Long, currencyCode: String): String =
    "$currencyCode ${"%.2f".format(Locale.ROOT, cents / 100.0)}"

private fun readCsv(context: Context, uri: Uri): String {
    val input = context.contentResolver.openInputStream(uri) ?: error("Could not open the selected file.")
    return input.use { stream ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            total += count
            require(total <= MAX_IMPORT_BYTES) { "The selected file is too large (maximum 10 MB)." }
            output.write(buffer, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }
}

private fun saveReceipt(context: Context, uri: Uri): String {
    val mimeType = context.contentResolver.getType(uri)
    require(mimeType == null || mimeType.startsWith("image/")) { "Choose an image file for the receipt." }
    val directory = File(context.filesDir, "receipts")
    check((directory.exists() || directory.mkdirs()) && directory.isDirectory) {
        "Could not create private receipt storage."
    }
    val receipt = File(directory, "${UUID.randomUUID()}.image")
    try {
        val input = context.contentResolver.openInputStream(uri) ?: error("Could not open the selected image.")
        input.use { stream ->
            receipt.outputStream().use { output ->
                val buffer = ByteArray(8_192)
                var total = 0
                while (true) {
                    val count = stream.read(buffer)
                    if (count < 0) break
                    total += count
                    require(total <= MAX_IMPORT_BYTES) { "Receipt image is too large (maximum 10 MB)." }
                    output.write(buffer, 0, count)
                }
                require(total > 0) { "The selected receipt image is empty." }
            }
        }
        return receipt.name
    } catch (error: Exception) {
        receipt.delete()
        throw error
    }
}
