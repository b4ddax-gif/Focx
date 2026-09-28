package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.service.ScheduleAlarmReceiver
import com.example.ui.MainApp
import com.example.ui.theme.FocusLockTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingScheduleIntent(intent)

        setContent {
            FocusLockTheme {
                MainApp()
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingScheduleIntent(intent)
    }

    private fun handleIncomingScheduleIntent(intent: android.content.Intent?) {
        if (intent == null) return
        val autoStartScheduleId = intent.getLongExtra(ScheduleAlarmReceiver.EXTRA_AUTO_START_SCHEDULE_ID, -1L)
        if (autoStartScheduleId > 0) {
            val subject = intent.getStringExtra(ScheduleAlarmReceiver.EXTRA_SUBJECT) ?: "Study"
            val durationMins = intent.getIntExtra(ScheduleAlarmReceiver.EXTRA_DURATION_MINS, 45)
            val breakMins = intent.getIntExtra(ScheduleAlarmReceiver.EXTRA_BREAK_MINS, 5)

            val app = application as? FocusLockApplication
            app?.focusSessionManager?.startFocusSession(
                subject = subject,
                durationMinutes = durationMins,
                breakDurationMinutes = breakMins,
                blockedPackages = emptyList(),
                isStrictMode = false,
                allowAppsDuringBreak = true
            )
        }
    }
}
