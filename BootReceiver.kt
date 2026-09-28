package com.example.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.FocusLockApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == "android.intent.action.QUICKBOOT_POWERON"
        ) {
            val app = context.applicationContext as? FocusLockApplication ?: return

            // Restore active focus session if still valid
            app.focusSessionManager // triggers init which restores saved state

            // Reschedule all enabled alarms
            CoroutineScope(Dispatchers.IO).launch {
                val enabledSchedules = app.focusRepository.enabledSchedules.firstOrNull() ?: emptyList()
                for (schedule in enabledSchedules) {
                    ScheduleAlarmReceiver.scheduleAlarm(context, schedule)
                }
            }
        }
    }
}
