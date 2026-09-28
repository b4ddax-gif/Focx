package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.example.FocusLockApplication
import com.example.ui.screens.focus.FocusBlockedActivity

class FocusAccessibilityService : AccessibilityService() {

    private var lastInterceptTimestamp = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return
        }

        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName) return

        val app = application as? FocusLockApplication ?: return
        val currentSession = app.focusSessionManager.sessionState.value

        if (!currentSession.isActive || currentSession.isPaused) return

        // If in break and user allowed apps during break, do not block
        if (currentSession.isInBreak && currentSession.allowAppsDuringBreak) return

        if (currentSession.blockedPackageNames.contains(packageName)) {
            val now = System.currentTimeMillis()
            if (now - lastInterceptTimestamp > 1200L) {
                lastInterceptTimestamp = now
                val intent = Intent(this, FocusBlockedActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(FocusBlockedActivity.EXTRA_BLOCKED_PACKAGE, packageName)
                    putExtra(FocusBlockedActivity.EXTRA_SUBJECT, currentSession.subject)
                    putExtra(FocusBlockedActivity.EXTRA_REMAINING_TIME, currentSession.formattedRemainingTime)
                }
                startActivity(intent)
            }
        }
    }

    override fun onInterrupt() {
        // Accessibility service interrupted
    }
}
