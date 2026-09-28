package com.example.service

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.FocusLockApplication
import com.example.MainActivity
import com.example.R
import com.example.data.model.StudySchedule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Calendar

class ScheduleAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduleId = intent.getLongExtra(EXTRA_SCHEDULE_ID, -1L)
        val subject = intent.getStringExtra(EXTRA_SUBJECT) ?: "Study Time"
        val durationMins = intent.getIntExtra(EXTRA_DURATION_MINS, 45)
        val breakMins = intent.getIntExtra(EXTRA_BREAK_MINS, 5)

        val app = context.applicationContext as? FocusLockApplication
        val repo = app?.focusRepository

        CoroutineScope(Dispatchers.IO).launch {
            val schedule = if (scheduleId > 0) repo?.getScheduleById(scheduleId) else null
            // Check day of week
            val todayDayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) // 1=Sun, 2=Mon...
            // Convert to 1=Mon, ..., 7=Sun
            val convertedDay = when (todayDayOfWeek) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 1
            }

            if (schedule != null) {
                val days = schedule.getDaysList()
                if (days.isNotEmpty() && !days.contains(convertedDay)) {
                    // Not scheduled for today, reschedule for tomorrow
                    rescheduleNext(context, schedule)
                    return@launch
                }
            }

            showStudyNotification(context, scheduleId, subject, durationMins, breakMins)

            // Reschedule next occurrence
            if (schedule != null) {
                rescheduleNext(context, schedule)
            }
        }
    }

    private fun showStudyNotification(
        context: Context,
        scheduleId: Long,
        subject: String,
        durationMins: Int,
        breakMins: Int
    ) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                FocusForegroundService.CHANNEL_FOCUS_ALERTS,
                "Focus Reminders & Alerts",
                NotificationManager.IMPORTANCE_HIGH
            )
            nm.createNotificationChannel(channel)
        }

        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_AUTO_START_SCHEDULE_ID, scheduleId)
            putExtra(EXTRA_SUBJECT, subject)
            putExtra(EXTRA_DURATION_MINS, durationMins)
            putExtra(EXTRA_BREAK_MINS, breakMins)
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context,
            scheduleId.toInt(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, FocusForegroundService.CHANNEL_FOCUS_ALERTS)
            .setSmallIcon(R.drawable.ic_focus_lock_logo)
            .setContentTitle("Study time started — $subject")
            .setContentText("Your scheduled $durationMins-minute session is ready. Tap to start Focus Mode.")
            .setContentIntent(openAppPendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .addAction(0, "Start Focus", openAppPendingIntent)

        nm.notify((2000 + scheduleId).toInt(), builder.build())
    }

    companion object {
        const val EXTRA_SCHEDULE_ID = "extra_schedule_id"
        const val EXTRA_SUBJECT = "extra_subject"
        const val EXTRA_DURATION_MINS = "extra_duration_mins"
        const val EXTRA_BREAK_MINS = "extra_break_mins"
        const val EXTRA_AUTO_START_SCHEDULE_ID = "extra_auto_start_schedule_id"

        fun scheduleAlarm(context: Context, schedule: StudySchedule) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return

            val intent = Intent(context, ScheduleAlarmReceiver::class.java).apply {
                putExtra(EXTRA_SCHEDULE_ID, schedule.id)
                putExtra(EXTRA_SUBJECT, schedule.subject)
                putExtra(EXTRA_DURATION_MINS, schedule.durationMinutes)
                putExtra(EXTRA_BREAK_MINS, schedule.breakDurationMinutes)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                schedule.id.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, schedule.startHour)
                set(Calendar.MINUTE, schedule.startMinute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                } else {
                    am.setExact(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
                }
            } catch (e: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pendingIntent)
            }
        }

        fun cancelAlarm(context: Context, scheduleId: Long) {
            val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
            val intent = Intent(context, ScheduleAlarmReceiver::class.java)
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                scheduleId.toInt(),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pendingIntent != null) {
                am.cancel(pendingIntent)
            }
        }

        private fun rescheduleNext(context: Context, schedule: StudySchedule) {
            scheduleAlarm(context, schedule)
        }
    }
}
