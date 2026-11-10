package com.watchawatch.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID
import kotlin.math.abs
import kotlin.math.hypot

class FloatingTimerService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) }
    private val windowManager by lazy { getSystemService(WindowManager::class.java) }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private var wakeLock: PowerManager.WakeLock? = null
    private var bubble: View? = null
    private var timerLabel: TextView? = null
    private var captionLabel: TextView? = null
    private var dismissTarget: View? = null
    private var dismissParams: WindowManager.LayoutParams? = null
    private var isOverDismissTarget: Boolean = false
    private var isAppInForeground: Boolean = false
    private var currentTimer: ActiveTimerInfo? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }
    private var isBubbleVisible: Boolean = true
    private var isScreenOn: Boolean = true
    private var isReceiverRegistered: Boolean = false

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    handler.removeCallbacks(ticker)
                }
                Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> {
                    isScreenOn = true
                    val timer = currentTimer ?: readActiveTimer()
                    if (timer != null) {
                        currentTimer = timer
                        updateNotification()
                        handler.removeCallbacks(ticker)
                        ticker.run()
                        if (isBubbleVisible && !isAppInForeground && Settings.canDrawOverlays(this@FloatingTimerService)) {
                            ensureBubbleAttached()
                        }
                    }
                }
            }
        }
    }

    private val ticker = object : Runnable {
        override fun run() {
            val timer = currentTimer ?: return
            val elapsed = timer.elapsedAt(System.currentTimeMillis())
            timerLabel?.text = formatOverlayTime(elapsed)
            val taskName = timer.task.trim().ifBlank { "FOCUS" }
            val displayTask = if (taskName.length > 9) taskName.take(8) + "…" else taskName
            captionLabel?.text = displayTask.uppercase(Locale.getDefault())
            bubble?.contentDescription =
                "Floating focus timer, ${formatOverlayTime(elapsed)} elapsed. Tap to return to WatchaWatch."

            if (isScreenOn && timer.running) {
                handler.postDelayed(this, TICK_INTERVAL_MS)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        preferences.registerOnSharedPreferenceChangeListener(this)
        isBubbleVisible = preferences.getBoolean(BUBBLE_PREF_KEY, true)

        runCatching {
            val powerManager = getSystemService(PowerManager::class.java)
            wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "watchawatch:timer_wakelock")?.apply {
                setReferenceCounted(false)
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(screenReceiver, filter)
        }
        isReceiverRegistered = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_APP_FOREGROUND -> {
                isAppInForeground = true
                removeBubble()
                removeDismissTarget()
                return START_STICKY
            }
            ACTION_APP_BACKGROUND -> {
                isAppInForeground = false
                if (isBubbleVisible && Settings.canDrawOverlays(this) && currentTimer != null) {
                    showBubble()
                }
                return START_STICKY
            }
            ACTION_SHOW_BUBBLE -> {
                isBubbleVisible = true
                isAppInForeground = false
                preferences.edit().putBoolean(BUBBLE_PREF_KEY, true).apply()
                if (Settings.canDrawOverlays(this) && currentTimer != null) {
                    showBubble()
                }
                updateNotification()
                return START_STICKY
            }
            ACTION_HIDE_FLOATING_TIMER -> {
                dismissBubble()
                return START_STICKY
            }
            ACTION_TOGGLE_BUBBLE -> {
                isBubbleVisible = !isBubbleVisible
                preferences.edit().putBoolean(BUBBLE_PREF_KEY, isBubbleVisible).apply()
                if (isBubbleVisible) {
                    if (!isAppInForeground && Settings.canDrawOverlays(this)) showBubble()
                } else {
                    removeBubble()
                }
                updateNotification()
                return START_STICKY
            }
            ACTION_TOGGLE_TIMER -> {
                handleToggleTimer()
                return START_STICKY
            }
            ACTION_STOP_TIMER -> {
                handleStopTimer()
                return START_NOT_STICKY
            }
        }

        val initialTimer = readActiveTimer() ?: run {
            updateWakeLock(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        currentTimer = initialTimer
        updateWakeLock(initialTimer.running)
        isBubbleVisible = preferences.getBoolean(BUBBLE_PREF_KEY, true)

        val notification = createNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        if (isBubbleVisible && !isAppInForeground && Settings.canDrawOverlays(this)) {
            showBubble()
        }
        refreshTimer()
        return START_STICKY
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == STATE_KEY) refreshTimer()
    }

    private fun handleToggleTimer() {
        val raw = preferences.getString(STATE_KEY, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            val active = root.optJSONObject("active") ?: return
            val now = System.currentTimeMillis()
            val wasRunning = active.getBoolean("running")
            val startedAtMs = active.getLong("startedAtMs")
            val elapsedBeforeMs = active.getLong("elapsedBeforeMs")

            if (wasRunning) {
                val newElapsed = elapsedBeforeMs + (now - startedAtMs).coerceAtLeast(0)
                active.put("elapsedBeforeMs", newElapsed)
                active.put("running", false)
            } else {
                active.put("startedAtMs", now)
                active.put("running", true)
            }
            root.put("active", active)
            preferences.edit().putString(STATE_KEY, root.toString()).apply()
        }.onFailure {
            android.util.Log.e(TAG, "Error toggling timer state", it)
        }
    }

    private fun handleStopTimer() {
        val raw = preferences.getString(STATE_KEY, null) ?: return
        runCatching {
            val root = JSONObject(raw)
            val active = root.optJSONObject("active") ?: return
            val now = System.currentTimeMillis()
            val wasRunning = active.getBoolean("running")
            val startedAtMs = active.getLong("startedAtMs")
            val elapsedBeforeMs = active.getLong("elapsedBeforeMs")
            val duration = elapsedBeforeMs + if (wasRunning) (now - startedAtMs).coerceAtLeast(0) else 0

            if (duration > 0) {
                val entries = root.optJSONArray("entries") ?: JSONArray()
                val newEntry = JSONObject().apply {
                    put("id", UUID.randomUUID().toString())
                    put("projectId", active.getString("projectId"))
                    put("task", active.getString("task"))
                    put("startMs", now - duration)
                    put("endMs", now)
                    put("durationMs", duration)
                    put("note", active.optString("note", ""))
                    put("customFieldValue", active.optString("customFieldValue", ""))
                    put("billRateCentsPerHour", active.optLong("billRateCentsPerHour", 0))
                    put("laborCostCentsPerHour", active.optLong("laborCostCentsPerHour", 0))
                    put("currencyCode", active.optString("currencyCode", "USD"))
                }
                val updatedEntries = JSONArray()
                updatedEntries.put(newEntry)
                for (i in 0 until entries.length()) {
                    updatedEntries.put(entries.get(i))
                }
                root.put("entries", updatedEntries)
            }
            root.remove("active")
            preferences.edit().putString(STATE_KEY, root.toString()).apply()
        }.onFailure {
            android.util.Log.e(TAG, "Error stopping timer", it)
        }

        updateWakeLock(false)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateWakeLock(running: Boolean) {
        runCatching {
            if (running) {
                if (wakeLock?.isHeld == false) {
                    wakeLock?.acquire(4 * 60 * 60 * 1000L) // 4 hours maximum timeout
                }
            } else {
                if (wakeLock?.isHeld == true) {
                    wakeLock?.release()
                }
            }
        }
    }

    private fun refreshTimer() {
        val timer = readActiveTimer()
        if (timer == null) {
            updateWakeLock(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        currentTimer = timer
        updateWakeLock(timer.running)
        if (isBubbleVisible && !isAppInForeground && Settings.canDrawOverlays(this)) {
            ensureBubbleAttached()
        } else {
            removeBubble()
        }
        updateNotification()
        handler.removeCallbacks(ticker)
        if (isScreenOn) {
            ticker.run()
        }
    }

    private fun updateNotification() {
        if (currentTimer == null) return
        val notification = createNotification()
        runCatching {
            notificationManager.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun readActiveTimer(): ActiveTimerInfo? {
        val state = preferences.getString(STATE_KEY, null) ?: return null
        return runCatching {
            val root = JSONObject(state)
            val active = root.optJSONObject("active") ?: return null
            val projectId = active.optString("projectId", "")
            val task = active.optString("task", "Focus session").ifBlank { "Focus session" }
            val projects = root.optJSONArray("projects")
            var projectName = "Focus project"
            if (projects != null) {
                for (i in 0 until projects.length()) {
                    val p = projects.getJSONObject(i)
                    if (p.optString("id") == projectId) {
                        projectName = p.optString("name", projectName)
                        break
                    }
                }
            }
            ActiveTimerInfo(
                projectId = projectId,
                projectName = projectName,
                task = task,
                startedAtMs = active.getLong("startedAtMs"),
                elapsedBeforeMs = active.getLong("elapsedBeforeMs"),
                running = active.getBoolean("running")
            )
        }.getOrNull()
    }

    private fun ensureBubbleAttached() {
        if (isAppInForeground) {
            removeBubble()
            return
        }
        val b = bubble
        if (b == null) {
            showBubble()
        } else if (!b.isAttachedToWindow) {
            runCatching { windowManager.removeViewImmediate(b) }
            bubble = null
            showBubble()
        }
    }

    private fun removeBubble() {
        bubble?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { android.util.Log.w(TAG, "Could not remove floating timer", it) }
        }
        bubble = null
        timerLabel = null
        captionLabel = null
        bubbleParams = null
    }

    private fun dismissBubble() {
        isBubbleVisible = false
        preferences.edit().putBoolean(BUBBLE_PREF_KEY, false).apply()
        removeBubble()
        removeDismissTarget()
        updateNotification()
        runCatching {
            Toast.makeText(this@FloatingTimerService, "Floating timer removed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showDismissTarget() {
        if (dismissTarget != null) return
        val density = resources.displayMetrics.density
        val targetSize = (72 * density).toInt()

        val bg = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(AndroidColor.parseColor("#D91F2937"))
            setStroke((2 * density).toInt(), AndroidColor.parseColor("#9CA3AF"))
        }

        val targetLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = bg
            elevation = 16 * density

            val icon = TextView(context).apply {
                text = "✕"
                textSize = 20f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(AndroidColor.WHITE)
                gravity = Gravity.CENTER
            }
            val label = TextView(context).apply {
                text = "Remove"
                textSize = 9f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(AndroidColor.parseColor("#E5E7EB"))
                gravity = Gravity.CENTER
                setPadding(0, (2 * density).toInt(), 0, 0)
            }
            addView(icon)
            addView(label)
        }

        val params = WindowManager.LayoutParams(
            targetSize,
            targetSize,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = (36 * density).toInt()
        }

        runCatching {
            windowManager.addView(targetLayout, params)
            dismissTarget = targetLayout
            dismissParams = params
        }.onFailure {
            android.util.Log.w(TAG, "Could not add dismiss target", it)
        }
    }

    private fun updateDismissTargetState(isOver: Boolean) {
        val target = dismissTarget as? LinearLayout ?: return
        val density = resources.displayMetrics.density
        val bg = target.background as? GradientDrawable ?: return
        val label = target.getChildAt(1) as? TextView
        if (isOver) {
            bg.setColor(AndroidColor.parseColor("#E6EF4444"))
            bg.setStroke((3 * density).toInt(), AndroidColor.WHITE)
            label?.text = "Drop to close"
            target.scaleX = 1.15f
            target.scaleY = 1.15f
        } else {
            bg.setColor(AndroidColor.parseColor("#D91F2937"))
            bg.setStroke((2 * density).toInt(), AndroidColor.parseColor("#9CA3AF"))
            label?.text = "Remove"
            target.scaleX = 1.0f
            target.scaleY = 1.0f
        }
    }

    private fun removeDismissTarget() {
        dismissTarget?.let { view ->
            runCatching { windowManager.removeView(view) }
        }
        dismissTarget = null
        dismissParams = null
        isOverDismissTarget = false
    }

    private fun showBubble() {
        if (isAppInForeground) return
        if (bubble != null && bubble?.isAttachedToWindow == true) return
        if (!isBubbleVisible || !Settings.canDrawOverlays(this)) return

        val density = resources.displayMetrics.density
        val size = (86 * density).toInt()

        val circle = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(AndroidColor.rgb(13, 118, 110), AndroidColor.rgb(16, 43, 63))
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke((2 * density).toInt(), AndroidColor.rgb(255, 158, 68))
        }

        val taskName = (currentTimer?.task?.trim()?.ifBlank { "FOCUS" } ?: "FOCUS")
        val displayTask = if (taskName.length > 9) taskName.take(8) + "…" else taskName

        val caption = TextView(this).apply {
            setTextColor(AndroidColor.rgb(194, 239, 229))
            textSize = 8.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.12f
            text = displayTask.uppercase(Locale.getDefault())
        }
        captionLabel = caption

        val time = TextView(this).apply {
            setTextColor(AndroidColor.WHITE)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            text = formatOverlayTime(currentTimer?.elapsedAt(System.currentTimeMillis()) ?: 0L)
        }
        timerLabel = time

        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(caption, LinearLayout.LayoutParams(-1, -2))
            addView(time, LinearLayout.LayoutParams(-1, -2))
        }

        val closeBtn = TextView(this).apply {
            text = "✕"
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(AndroidColor.WHITE)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(AndroidColor.argb(180, 15, 23, 42))
                setStroke((1 * density).toInt(), AndroidColor.argb(200, 255, 255, 255))
            }
            isClickable = true
            isFocusable = false
            contentDescription = "Close floating timer"
            setOnClickListener {
                dismissBubble()
            }
        }

        val content = FloatingTimerBubble(this).apply {
            background = circle
            elevation = 12 * density
            contentDescription = "Floating focus timer. Tap to return to WatchaWatch."
            isFocusable = true
            isClickable = true
            setOnClickListener { openApp() }

            val centerParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER
            )
            addView(textContainer, centerParams)

            val closeSize = (22 * density).toInt()
            val closeParams = FrameLayout.LayoutParams(closeSize, closeSize, Gravity.TOP or Gravity.END).apply {
                topMargin = (2 * density).toInt()
                rightMargin = (2 * density).toInt()
            }
            addView(closeBtn, closeParams)
        }

        val savedX = preferences.getInt("bubble_x", (18 * density).toInt())
        val defaultY = (resources.displayMetrics.heightPixels * 0.35f).toInt().coerceAtLeast((48 * density).toInt())
        val savedY = preferences.getInt("bubble_y", defaultY)

        val layoutParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        content.setOnTouchListener(OverlayTouchListener(content, layoutParams, closeBtn))
        bubbleParams = layoutParams
        bubble = content
        runCatching {
            windowManager.addView(content, layoutParams)
        }.onFailure { error ->
            android.util.Log.w(TAG, "Could not add floating timer window", error)
            bubble = null
        }
    }

    private inner class OverlayTouchListener(
        private val view: View,
        private val params: WindowManager.LayoutParams,
        private val closeButton: View
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0f
        private var startY = 0f
        private var dragged = false

        override fun onTouch(target: View, event: MotionEvent): Boolean {
            val closeLocation = IntArray(2)
            closeButton.getLocationOnScreen(closeLocation)
            val isInsideClose = event.rawX >= closeLocation[0] &&
                    event.rawX <= closeLocation[0] + closeButton.width &&
                    event.rawY >= closeLocation[1] &&
                    event.rawY <= closeLocation[1] + closeButton.height

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isInsideClose) {
                        return false
                    }
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x.toFloat()
                    startY = params.y.toFloat()
                    dragged = false
                    isOverDismissTarget = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (!dragged && (abs(deltaX) > touchSlop || abs(deltaY) > touchSlop)) {
                        dragged = true
                        showDismissTarget()
                    }
                    if (dragged) {
                        val display = resources.displayMetrics
                        val screenW = display.widthPixels
                        val screenH = display.heightPixels
                        val density = display.density

                        params.x = (startX + deltaX).toInt().coerceIn(0, (screenW - view.width).coerceAtLeast(0))
                        params.y = (startY + deltaY).toInt().coerceIn(0, (screenH - view.height).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(view, params) }
                            .onFailure { android.util.Log.w(TAG, "Could not move floating timer", it) }

                        val targetCenterX = screenW / 2f
                        val targetCenterY = screenH - 72 * density
                        val distToTarget = hypot(event.rawX - targetCenterX, event.rawY - targetCenterY)

                        val inBottomCenter = distToTarget < 95 * density
                        val inBottomLeftCorner = event.rawX < 100 * density && event.rawY > screenH - 140 * density
                        val inBottomRightCorner = event.rawX > screenW - 100 * density && event.rawY > screenH - 140 * density
                        val inTopLeftCorner = event.rawX < 85 * density && event.rawY < 120 * density
                        val inTopRightCorner = event.rawX > screenW - 85 * density && event.rawY < 120 * density

                        val shouldDismiss = inBottomCenter || inBottomLeftCorner || inBottomRightCorner || inTopLeftCorner || inTopRightCorner
                        if (shouldDismiss != isOverDismissTarget) {
                            isOverDismissTarget = shouldDismiss
                            updateDismissTargetState(shouldDismiss)
                        }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    val wasOverDismiss = isOverDismissTarget
                    removeDismissTarget()
                    if (wasOverDismiss) {
                        dismissBubble()
                    } else if (dragged) {
                        preferences.edit()
                            .putInt("bubble_x", params.x)
                            .putInt("bubble_y", params.y)
                            .apply()
                    } else {
                        target.performClick()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    removeDismissTarget()
                    return true
                }
            }
            return false
        }
    }

    private fun openApp() {
        isAppInForeground = true
        removeBubble()
        removeDismissTarget()
        val intent = Intent().setClassName(this, "com.watchawatch.app.MainActivity").apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun getClockIcon(): Int {
        val resId = resources.getIdentifier("ic_stat_watch", "drawable", packageName)
        return if (resId != 0) resId else android.R.drawable.ic_menu_recent_history
    }

    private fun createNotification(): Notification {
        val timer = currentTimer
        val projectName = timer?.projectName ?: "Focus project"
        val task = timer?.task ?: "Focus session"
        val isRunning = timer?.running == true
        val elapsed = timer?.elapsedAt(System.currentTimeMillis()) ?: 0L

        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent().setClassName(this, "com.watchawatch.app.MainActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleTimer = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingTimerService::class.java).setAction(ACTION_TOGGLE_TIMER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopTimer = PendingIntent.getService(
            this,
            2,
            Intent(this, FloatingTimerService::class.java).setAction(ACTION_STOP_TIMER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleBubble = PendingIntent.getService(
            this,
            3,
            Intent(this, FloatingTimerService::class.java).setAction(ACTION_TOGGLE_BUBBLE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val clockIcon = getClockIcon()
        val builder = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(clockIcon)
            .setContentTitle("🎯 $task")
            .setContentText("📁 $projectName • ${if (isRunning) "Running" else "Paused"}")
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STOPWATCH)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setSubText(projectName)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            @Suppress("DEPRECATION")
            builder.setPriority(Notification.PRIORITY_HIGH)
        }

        val statusLabel = if (isRunning) "Active (Tracking)" else "Paused"
        val bigStyle = Notification.BigTextStyle(builder)
            .setBigContentTitle("🎯 $task")
            .bigText("🎯 Task: $task\n📁 Project: $projectName\n⏱️ Duration: ${formatOverlayTime(elapsed)}\n⚡ Status: $statusLabel")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            bigStyle.setSummaryText(projectName)
        }

        if (isRunning) {
            val baseTime = System.currentTimeMillis() - elapsed
            builder.setUsesChronometer(true)
            builder.setWhen(baseTime)
            builder.addAction(Notification.Action.Builder(null, "⏸️ Pause", toggleTimer).build())
        } else {
            builder.setContentText("⏸️ Paused at ${formatOverlayTime(elapsed)} • $projectName")
            builder.setUsesChronometer(false)
            builder.addAction(Notification.Action.Builder(null, "▶️ Resume", toggleTimer).build())
        }

        builder.addAction(Notification.Action.Builder(null, "⏹️ Finish", stopTimer).build())

        val bubbleActionLabel = if (isBubbleVisible) "Hide bubble" else "Show bubble"
        builder.addAction(Notification.Action.Builder(null, bubbleActionLabel, toggleBubble).build())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val publicBuilder = Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(clockIcon)
                .setContentTitle("🎯 $task")
                .setContentText("📁 $projectName • ${if (isRunning) "Running" else "Paused"}")
                .setContentIntent(openApp)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setCategory(Notification.CATEGORY_STOPWATCH)
            if (isRunning) {
                publicBuilder.setUsesChronometer(true)
                publicBuilder.setWhen(System.currentTimeMillis() - elapsed)
            }
            builder.setPublicVersion(publicBuilder.build())
        }
        return builder.build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { notificationManager.deleteNotificationChannel("watchawatch_focus_timer") }
            runCatching { notificationManager.deleteNotificationChannel("watchawatch_lockscreen_task_v3") }
            runCatching { notificationManager.deleteNotificationChannel("watchawatch_lockscreen_timer_v6") }
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Active Focus Timer & Lock Screen",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Displays live active task and timer continuously on lock screen and status bar."
                setShowBadge(true)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setBypassDnd(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        if (isReceiverRegistered) {
            runCatching { unregisterReceiver(screenReceiver) }
            isReceiverRegistered = false
        }
        handler.removeCallbacks(ticker)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        removeBubble()
        removeDismissTarget()
        updateWakeLock(false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    data class ActiveTimerInfo(
        val projectId: String,
        val projectName: String,
        val task: String,
        val startedAtMs: Long,
        val elapsedBeforeMs: Long,
        val running: Boolean
    ) {
        fun elapsedAt(now: Long): Long =
            elapsedBeforeMs + if (running) (now - startedAtMs).coerceAtLeast(0) else 0
    }

    companion object {
        const val ACTION_APP_FOREGROUND = "com.watchawatch.app.APP_FOREGROUND"
        const val ACTION_APP_BACKGROUND = "com.watchawatch.app.APP_BACKGROUND"
        const val ACTION_SHOW_BUBBLE = "com.watchawatch.app.SHOW_BUBBLE"
        const val ACTION_TOGGLE_TIMER = "com.watchawatch.app.TOGGLE_TIMER"
        const val ACTION_STOP_TIMER = "com.watchawatch.app.STOP_TIMER"
        const val ACTION_TOGGLE_BUBBLE = "com.watchawatch.app.TOGGLE_BUBBLE"
        const val ACTION_HIDE_FLOATING_TIMER = "com.watchawatch.app.HIDE_FLOATING_TIMER"
        const val NOTIFICATION_CHANNEL_ID = "watchawatch_lockscreen_timer_v10"
        const val NOTIFICATION_ID = 101
        private const val PREFERENCES_NAME = "watchawatch"
        private const val STATE_KEY = "state"
        private const val BUBBLE_PREF_KEY = "bubble_visible"
        private const val TICK_INTERVAL_MS = 1_000L
        private const val TAG = "WatchaWatchOverlay"

        fun start(context: Context) {
            val intent = Intent(context, FloatingTimerService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, FloatingTimerService::class.java))
        }
    }
}

private class FloatingTimerBubble(context: Context) : FrameLayout(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

internal fun formatOverlayTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(Locale.getDefault(), seconds / 3600, (seconds / 60) % 60, seconds % 60)
}
