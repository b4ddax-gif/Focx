package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.FocusLockApplication
import com.example.MainActivity
import com.example.R
import com.example.manager.PermissionHelper
import com.example.ui.screens.focus.FocusBlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Core Foreground Service that uses Android's UsageStatsManager to detect foreground
 * application transitions in real time and automatically redirects blocked applications
 * back to the Focus Lock screen during active study sessions.
 */
class AppBlockingService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var blockingMonitorJob: Job? = null
    private var lastInterceptedPackage: String? = null
    private var lastInterceptTimestamp = 0L

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_BLOCKING -> {
                startForeground(NOTIFICATION_ID, buildBlockingNotification())
                startMonitoringLoop()
            }
            ACTION_STOP_BLOCKING -> {
                stopMonitoringLoop()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoringLoop()
        serviceScope.cancel()
    }

    /**
     * Continuous monitoring loop using UsageStatsManager.
     * Evaluates foreground app changes at a lightweight ~500ms cadence.
     */
    private fun startMonitoringLoop() {
        blockingMonitorJob?.cancel()
        blockingMonitorJob = serviceScope.launch {
            val app = application as? FocusLockApplication ?: return@launch

            while (isActive) {
                delay(MONITOR_INTERVAL_MS)

                val sessionState = app.focusSessionManager.sessionState.value
                // If study session is not active or is paused, skip check
                if (!sessionState.isActive || sessionState.isPaused) {
                    continue
                }

                // If currently on break and user configured "Allow blocked apps during breaks", skip
                if (sessionState.isInBreak && sessionState.allowAppsDuringBreak) {
                    continue
                }

                // Query current foreground application package using UsageStatsManager
                val currentForegroundPkg = getForegroundAppPackageName(applicationContext)

                if (currentForegroundPkg != null &&
                    currentForegroundPkg != packageName &&
                    !isSystemExemptPackage(currentForegroundPkg)
                ) {
                    // Check if current foreground application is on the user's blocklist
                    val isBlocked = sessionState.blockedPackageNames.contains(currentForegroundPkg)

                    if (isBlocked) {
                        val now = System.currentTimeMillis()
                        val isSamePackage = (currentForegroundPkg == lastInterceptedPackage)
                        val timeSinceLast = now - lastInterceptTimestamp

                        // Prevent rapid multi-trigger spam, debounce threshold 1000ms
                        if (!isSamePackage || timeSinceLast > DEBOUNCE_INTERVAL_MS) {
                            lastInterceptedPackage = currentForegroundPkg
                            lastInterceptTimestamp = now

                            Log.d(TAG, "Blocked app detected: $currentForegroundPkg. Redirecting to Focus Lock screen.")
                            redirectToFocusLock(
                                blockedPackage = currentForegroundPkg,
                                subject = sessionState.subject,
                                remainingTime = sessionState.formattedRemainingTime
                            )
                        }
                    } else {
                        // Reset last intercepted package if user is in an allowed app
                        if (currentForegroundPkg != lastInterceptedPackage) {
                            lastInterceptedPackage = null
                        }
                    }
                }
            }
        }
    }

    private fun stopMonitoringLoop() {
        blockingMonitorJob?.cancel()
        blockingMonitorJob = null
        lastInterceptedPackage = null
    }

    /**
     * Intercepts distracting app and brings Focus Lock overlay to the front.
     */
    private fun redirectToFocusLock(blockedPackage: String, subject: String, remainingTime: String) {
        val lockIntent = Intent(applicationContext, FocusBlockedActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(FocusBlockedActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            putExtra(FocusBlockedActivity.EXTRA_SUBJECT, subject)
            putExtra(FocusBlockedActivity.EXTRA_REMAINING_TIME, remainingTime)
        }
        try {
            startActivity(lockIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch FocusBlockedActivity: ${e.message}", e)
        }
    }

    /**
     * Inspects UsageStatsManager for the top active application.
     */
    private fun getForegroundAppPackageName(context: Context): String? {
        val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
            ?: return null

        val now = System.currentTimeMillis()
        val queryWindowStart = now - USAGE_LOOKBACK_WINDOW_MS

        val usageEvents = usageStatsManager.queryEvents(queryWindowStart, now)
        val event = UsageEvents.Event()
        var latestForegroundPackage: String? = null
        var latestTimestamp = 0L

        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                if (event.timeStamp >= latestTimestamp) {
                    latestTimestamp = event.timeStamp
                    latestForegroundPackage = event.packageName
                }
            }
        }

        if (latestForegroundPackage != null) {
            return latestForegroundPackage
        }

        // Secondary fallback to queryUsageStats
        val statsList = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            queryWindowStart,
            now
        )
        if (!statsList.isNullOrEmpty()) {
            val mostRecent = statsList.maxByOrNull { it.lastTimeUsed }
            return mostRecent?.packageName
        }

        return null
    }

    /**
     * Ensures system components, dialer, emergency functions, and settings are never blocked.
     */
    private fun isSystemExemptPackage(pkg: String): Boolean {
        if (pkg == packageName) return true
        if (pkg.contains("systemui", ignoreCase = true)) return true
        if (pkg.contains("settings", ignoreCase = true)) return true
        if (pkg.contains("dialer", ignoreCase = true) || pkg.contains("telecom", ignoreCase = true)) return true
        if (pkg.contains("emergency", ignoreCase = true)) return true
        return false
    }

    private fun buildBlockingNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_BLOCKING_SERVICE)
            .setSmallIcon(R.drawable.ic_focus_lock_logo)
            .setContentTitle("Focus Lock Active")
            .setContentText("App blocking is actively protecting your study session.")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_BLOCKING_SERVICE,
                "Focus Lock App Blocker",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Monitors foreground app usage and enforces study distractions blocking"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val TAG = "AppBlockingService"
        const val CHANNEL_BLOCKING_SERVICE = "focus_lock_app_blocking_channel"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START_BLOCKING = "com.b4ddax.focuslock.ACTION_START_BLOCKING"
        const val ACTION_STOP_BLOCKING = "com.b4ddax.focuslock.ACTION_STOP_BLOCKING"

        private const val MONITOR_INTERVAL_MS = 600L
        private const val USAGE_LOOKBACK_WINDOW_MS = 8000L
        private const val DEBOUNCE_INTERVAL_MS = 1200L

        fun start(context: Context) {
            val intent = Intent(context, AppBlockingService::class.java).apply {
                action = ACTION_START_BLOCKING
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error starting AppBlockingService: ${e.message}", e)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, AppBlockingService::class.java).apply {
                action = ACTION_STOP_BLOCKING
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping AppBlockingService: ${e.message}", e)
            }
        }
    }
}
