package com.watchawatch.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.graphics.Color as AndroidColor
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

class FloatingTimerService : Service(), SharedPreferences.OnSharedPreferenceChangeListener {
    private val handler = Handler(Looper.getMainLooper())
    private val preferences by lazy { getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE) }
    private lateinit var windowManager: WindowManager
    private var bubble: View? = null
    private var timerLabel: TextView? = null
    private var currentTimer: OverlayTimer? = null
    private var bubbleParams: WindowManager.LayoutParams? = null
    private val touchSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop }

    private val ticker = object : Runnable {
        override fun run() {
            val timer = currentTimer ?: return
            val elapsed = timer.elapsedAt(System.currentTimeMillis())
            timerLabel?.text = formatOverlayTime(elapsed)
            bubble?.contentDescription =
                "Floating focus timer, ${formatOverlayTime(elapsed)} elapsed. Tap to return to WatchaWatch."

            handler.postDelayed(this, TICK_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WindowManager::class.java)
        createNotificationChannel()
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HIDE_FLOATING_TIMER) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        val initialTimer = readActiveTimer() ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        currentTimer = initialTimer
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
        showBubble()
        refreshTimer()
        return START_NOT_STICKY
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (key == STATE_KEY) refreshTimer()
    }

    private fun refreshTimer() {
        val timer = readActiveTimer()
        if (timer == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        currentTimer = timer
        if (bubble == null && Settings.canDrawOverlays(this)) showBubble()
        handler.removeCallbacks(ticker)
        ticker.run()
    }

    private fun readActiveTimer(): OverlayTimer? {
        val state = preferences.getString(STATE_KEY, null) ?: return null
        return runCatching {
            val active = org.json.JSONObject(state).optJSONObject("active") ?: return null
            OverlayTimer(
                startedAtMs = active.getLong("startedAtMs"),
                elapsedBeforeMs = active.getLong("elapsedBeforeMs"),
                running = active.getBoolean("running")
            )
        }.getOrNull()
    }

    private fun showBubble() {
        if (bubble != null) return
        val density = resources.displayMetrics.density
        val size = (92 * density).toInt()
        val circle = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(AndroidColor.rgb(13, 118, 110), AndroidColor.rgb(20, 54, 75))
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke((2 * density).toInt(), AndroidColor.rgb(255, 190, 113))
        }
        val time = TextView(this).apply {
            setTextColor(AndroidColor.WHITE)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = false
            text = formatOverlayTime(0)
        }
        timerLabel = time
        val caption = TextView(this).apply {
            setTextColor(AndroidColor.rgb(210, 242, 232))
            textSize = 8f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = 0.16f
            text = getString(R.string.overlay_focus_time)
        }
        val content = FloatingTimerBubble(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = circle
            elevation = 12 * density
            contentDescription = "Floating focus timer. Tap to return to WatchaWatch."
            isFocusable = true
            isClickable = true
            setOnClickListener { openApp() }
            addView(caption, LinearLayout.LayoutParams(-1, (20 * density).toInt()))
            addView(time, LinearLayout.LayoutParams(-1, (25 * density).toInt()))
        }
        val layoutParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = (18 * density).toInt()
            y = (resources.displayMetrics.heightPixels * 0.35f).toInt().coerceAtLeast((48 * density).toInt())
        }
        content.setOnTouchListener(OverlayTouchListener(content, layoutParams))
        bubbleParams = layoutParams
        bubble = content
        windowManager.addView(content, layoutParams)
    }

    private inner class OverlayTouchListener(
        private val view: View,
        private val params: WindowManager.LayoutParams
    ) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0f
        private var startY = 0f
        private var dragged = false

        override fun onTouch(target: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = params.x.toFloat()
                    startY = params.y.toFloat()
                    dragged = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = event.rawX - downX
                    val deltaY = event.rawY - downY
                    if (!dragged && (kotlin.math.abs(deltaX) > touchSlop || kotlin.math.abs(deltaY) > touchSlop)) {
                        dragged = true
                    }
                    if (dragged) {
                        val display = resources.displayMetrics
                        params.x = (startX - deltaX).toInt().coerceIn(0, (display.widthPixels - view.width).coerceAtLeast(0))
                        params.y = (startY + deltaY).toInt().coerceIn(0, (display.heightPixels - view.height).coerceAtLeast(0))
                        runCatching { windowManager.updateViewLayout(view, params) }
                            .onFailure { android.util.Log.w(TAG, "Could not move floating timer", it) }
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragged) target.performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> return true
            }
            return false
        }
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    private fun createNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val hideBubble = PendingIntent.getService(
            this,
            1,
            Intent(this, FloatingTimerService::class.java).setAction(ACTION_HIDE_FLOATING_TIMER),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_watch)
            .setContentTitle("WatchaWatch focus timer")
            .setContentText(getString(R.string.notification_timer_running))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, "Hide floating timer", hideBubble).build())
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    NOTIFICATION_CHANNEL_ID,
                    "Focus timer",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Keeps the floating focus timer available while WatchaWatch is minimized."
                    setShowBadge(false)
                }
            )
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        bubble?.let { view ->
            runCatching { windowManager.removeView(view) }
                .onFailure { android.util.Log.w(TAG, "Could not remove floating timer", it) }
        }
        bubble = null
        timerLabel = null
        bubbleParams = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private data class OverlayTimer(
        val startedAtMs: Long,
        val elapsedBeforeMs: Long,
        val running: Boolean
    ) {
        fun elapsedAt(now: Long): Long =
            elapsedBeforeMs + if (running) (now - startedAtMs).coerceAtLeast(0) else 0
    }

    companion object {
        const val ACTION_HIDE_FLOATING_TIMER = "com.watchawatch.app.HIDE_FLOATING_TIMER"
        const val NOTIFICATION_CHANNEL_ID = "watchawatch_focus_timer"
        const val NOTIFICATION_ID = 101
        private const val PREFERENCES_NAME = "watchawatch"
        private const val STATE_KEY = "state"
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
    }
}

private class FloatingTimerBubble(context: Context) : LinearLayout(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

internal fun formatOverlayTime(milliseconds: Long): String {
    val seconds = milliseconds.coerceAtLeast(0) / 1000
    return "%02d:%02d:%02d".format(Locale.getDefault(), seconds / 3600, (seconds / 60) % 60, seconds % 60)
}
