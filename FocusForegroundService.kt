package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.FocusLockApplication
import com.example.MainActivity
import com.example.R
import com.example.ui.screens.focus.FocusBlockedActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private var monitorJob: Job? = null
    private var notificationJob: Job? = null

    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannels()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_FOCUS -> {
                startForegroundWithNotification()
                startMonitoring()
            }
            ACTION_STOP_FOCUS -> {
                stopMonitoring()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PAUSE -> {
                val app = application as? FocusLockApplication
                app?.focusSessionManager?.pauseSession()
            }
            ACTION_RESUME -> {
                val app = application as? FocusLockApplication
                app?.focusSessionManager?.resumeSession()
            }
            ACTION_BREAK -> {
                val app = application as? FocusLockApplication
                app?.focusSessionManager?.startBreak()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        stopMonitoring()
        serviceScope.cancel()
    }

    private fun startForegroundWithNotification() {
        val notification = buildOngoingNotification()
        startForeground(NOTIFICATION_ID, notification)

        notificationJob?.cancel()
        notificationJob = serviceScope.launch {
            val app = application as? FocusLockApplication ?: return@launch
            while (isActive) {
                delay(1000L)
                val current = app.focusSessionManager.sessionState.value
                if (current.isActive) {
                    notificationManager.notify(NOTIFICATION_ID, buildOngoingNotification())
                } else {
                    break
                }
            }
        }
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            val app = application as? FocusLockApplication ?: return@launch
            var lastBlockedAppAlertTime = 0L

            while (isActive) {
                delay(750L)
                val current = app.focusSessionManager.sessionState.value
                if (!current.isActive || current.isPaused) continue

                // If in break and user allowed apps during breaks, do not block
                if (current.isInBreak && current.allowAppsDuringBreak) {
                    continue
                }

                val foregroundPackage = ForegroundAppDetector.getForegroundAppPackage(applicationContext)
                if (foregroundPackage != null && foregroundPackage != packageName) {
                    val isBlocked = current.blockedPackageNames.contains(foregroundPackage)
                    if (isBlocked) {
                        val now = System.currentTimeMillis()
                        // Prevent rapid multi-firing
                        if (now - lastBlockedAppAlertTime > 1200L) {
                            lastBlockedAppAlertTime = now
                            launchBlockerOverlay(foregroundPackage, current.subject, current.formattedRemainingTime)
                        }
                    }
                }
            }
        }
    }

    private fun stopMonitoring() {
        monitorJob?.cancel()
        monitorJob = null
        notificationJob?.cancel()
        notificationJob = null
    }

    private fun launchBlockerOverlay(blockedPackage: String, subject: String, remainingTime: String) {
        val intent = Intent(applicationContext, FocusBlockedActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(FocusBlockedActivity.EXTRA_BLOCKED_PACKAGE, blockedPackage)
            putExtra(FocusBlockedActivity.EXTRA_SUBJECT, subject)
            putExtra(FocusBlockedActivity.EXTRA_REMAINING_TIME, remainingTime)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun buildOngoingNotification(): Notification {
        val app = application as? FocusLockApplication
        val session = app?.focusSessionManager?.sessionState?.value

        val title = if (session?.isInBreak == true) {
            "Break Time — ${session.formattedRemainingTime}"
        } else if (session?.isPaused == true) {
            "Focus Paused — ${session.subject}"
        } else {
            "Focusing on ${session?.subject ?: "Study"} — ${session?.formattedRemainingTime ?: "00:00"}"
        }

        val content = if (session?.isInBreak == true) {
            if (session.allowAppsDuringBreak) "Apps temporarily unlocked" else "Break active (apps still locked)"
        } else {
            "${session?.blockedPackageNames?.size ?: 0} distracting apps blocked"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, CHANNEL_FOCUS_ACTIVE)
            .setSmallIcon(R.drawable.ic_focus_lock_logo)
            .setContentTitle(title)
            .setContentText(content)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        if (session != null && session.isActive) {
            if (!session.isInBreak) {
                if (session.isPaused) {
                    val resumeIntent = Intent(this, FocusForegroundService::class.java).apply {
                        action = ACTION_RESUME
                    }
                    val resumePending = PendingIntent.getService(
                        this, 1, resumeIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(0, "Resume", resumePending)
                } else if (!session.isStrictMode) {
                    val pauseIntent = Intent(this, FocusForegroundService::class.java).apply {
                        action = ACTION_PAUSE
                    }
                    val pausePending = PendingIntent.getService(
                        this, 2, pauseIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    builder.addAction(0, "Pause", pausePending)
                }

                val breakIntent = Intent(this, FocusForegroundService::class.java).apply {
                    action = ACTION_BREAK
                }
                val breakPending = PendingIntent.getService(
                    this, 3, breakIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(0, "Take Break", breakPending)
            }
        }

        return builder.build()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val activeChannel = NotificationChannel(
                CHANNEL_FOCUS_ACTIVE,
                "Active Focus Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows live timer and status of active focus session"
                setShowBadge(false)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_FOCUS_ALERTS,
                "Focus Reminders & Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when study schedules start, breaks begin or end, and sessions complete"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannel(activeChannel)
            notificationManager.createNotificationChannel(alertsChannel)
        }
    }

    companion object {
        const val CHANNEL_FOCUS_ACTIVE = "focus_active_session_channel"
        const val CHANNEL_FOCUS_ALERTS = "focus_alerts_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_FOCUS = "com.b4ddax.focuslock.ACTION_START_FOCUS"
        const val ACTION_STOP_FOCUS = "com.b4ddax.focuslock.ACTION_STOP_FOCUS"
        const val ACTION_PAUSE = "com.b4ddax.focuslock.ACTION_PAUSE"
        const val ACTION_RESUME = "com.b4ddax.focuslock.ACTION_RESUME"
        const val ACTION_BREAK = "com.b4ddax.focuslock.ACTION_BREAK"

        fun notifyBreakStarted(context: Context) {
            sendNotification(
                context,
                title = "Break Started",
                content = "Take a deep breath and rest. Focus Lock will resume automatically.",
                notificationId = 1002
            )
        }

        fun notifyBreakEnded(context: Context) {
            sendNotification(
                context,
                title = "Break Ended",
                content = "Time to get back to studying. Distraction blocking is active.",
                notificationId = 1003
            )
        }

        fun notifySessionCompleted(context: Context, subject: String, seconds: Long) {
            val mins = seconds / 60
            sendNotification(
                context,
                title = "Focus Session Complete! 🎉",
                content = "Great job! You focused on $subject for $mins minutes.",
                notificationId = 1004
            )
        }

        private fun sendNotification(context: Context, title: String, content: String, notificationId: Int) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, notificationId, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder = NotificationCompat.Builder(context, CHANNEL_FOCUS_ALERTS)
                .setSmallIcon(R.drawable.ic_focus_lock_logo)
                .setContentTitle(title)
                .setContentText(content)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)

            nm.notify(notificationId, builder.build())
        }
    }
}
