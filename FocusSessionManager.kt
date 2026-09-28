package com.example.manager

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.data.model.ActiveFocusSession
import com.example.data.model.StudySession
import com.example.data.repository.FocusRepository
import com.example.service.FocusForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class FocusSessionManager private constructor(
    private val context: Context,
    private val focusRepository: FocusRepository
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("active_focus_session_state", Context.MODE_PRIVATE)

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var tickerJob: Job? = null

    private val _sessionState = MutableStateFlow(ActiveFocusSession())
    val sessionState: StateFlow<ActiveFocusSession> = _sessionState.asStateFlow()

    // Last completed session for showing in the Session Complete screen
    private val _lastCompletedSession = MutableStateFlow<StudySession?>(null)
    val lastCompletedSession: StateFlow<StudySession?> = _lastCompletedSession.asStateFlow()

    init {
        restoreState()
    }

    @Synchronized
    fun startFocusSession(
        subject: String,
        durationMinutes: Int,
        breakDurationMinutes: Int,
        blockedPackages: List<String>,
        isStrictMode: Boolean,
        allowAppsDuringBreak: Boolean
    ) {
        val now = System.currentTimeMillis()
        val plannedSec = durationMinutes * 60L
        val endMillis = now + (plannedSec * 1000L)

        val newSession = ActiveFocusSession(
            isActive = true,
            isPaused = false,
            isInBreak = false,
            subject = subject.ifBlank { "General Study" },
            plannedDurationSeconds = plannedSec,
            remainingSeconds = plannedSec,
            sessionStartTimeMillis = now,
            sessionEndTimeMillis = endMillis,
            breakDurationSeconds = breakDurationMinutes * 60L,
            breakRemainingSeconds = 0L,
            breakEndTimeMillis = 0L,
            totalBreakTakenSeconds = 0L,
            blockedPackageNames = blockedPackages,
            isStrictMode = isStrictMode,
            allowAppsDuringBreak = allowAppsDuringBreak
        )

        _sessionState.value = newSession
        saveState(newSession)
        startService()
        startTicker()
    }

    fun pauseSession() {
        val current = _sessionState.value
        if (!current.isActive || current.isPaused || current.isStrictMode) return

        // Compute remaining seconds from timestamps
        val now = System.currentTimeMillis()
        val remaining = ((current.sessionEndTimeMillis - now) / 1000L).coerceAtLeast(0)

        val updated = current.copy(
            isPaused = true,
            remainingSeconds = remaining
        )
        _sessionState.value = updated
        saveState(updated)
        stopTicker()
    }

    fun resumeSession() {
        val current = _sessionState.value
        if (!current.isActive || !current.isPaused) return

        val now = System.currentTimeMillis()
        val newEndMillis = now + (current.remainingSeconds * 1000L)

        val updated = current.copy(
            isPaused = false,
            sessionEndTimeMillis = newEndMillis
        )
        _sessionState.value = updated
        saveState(updated)
        startTicker()
    }

    fun startBreak() {
        val current = _sessionState.value
        if (!current.isActive || current.isInBreak) return

        val now = System.currentTimeMillis()
        // Save current remaining study time
        val studyRemaining = ((current.sessionEndTimeMillis - now) / 1000L).coerceAtLeast(0)
        val breakSec = current.breakDurationSeconds
        val breakEndMillis = now + (breakSec * 1000L)

        val updated = current.copy(
            isInBreak = true,
            remainingSeconds = studyRemaining,
            breakRemainingSeconds = breakSec,
            breakEndTimeMillis = breakEndMillis
        )
        _sessionState.value = updated
        saveState(updated)
        FocusForegroundService.notifyBreakStarted(context)
    }

    fun resumeStudyFromBreak() {
        val current = _sessionState.value
        if (!current.isActive || !current.isInBreak) return

        val now = System.currentTimeMillis()
        val breakElapsed = if (current.breakEndTimeMillis > 0) {
            val breakRem = ((current.breakEndTimeMillis - now) / 1000L).coerceAtLeast(0)
            (current.breakDurationSeconds - breakRem).coerceAtLeast(0)
        } else 0L

        val newStudyEndMillis = now + (current.remainingSeconds * 1000L)

        val updated = current.copy(
            isInBreak = false,
            sessionEndTimeMillis = newStudyEndMillis,
            breakRemainingSeconds = 0L,
            breakEndTimeMillis = 0L,
            totalBreakTakenSeconds = current.totalBreakTakenSeconds + breakElapsed
        )
        _sessionState.value = updated
        saveState(updated)
        FocusForegroundService.notifyBreakEnded(context)
    }

    fun endSession(isCompleted: Boolean, reason: String? = null) {
        val current = _sessionState.value
        if (!current.isActive) return

        stopTicker()
        stopService()

        val now = System.currentTimeMillis()
        val actualDurationSeconds = if (isCompleted) {
            current.plannedDurationSeconds
        } else {
            val elapsed = (current.plannedDurationSeconds - current.remainingSeconds).coerceAtLeast(0)
            elapsed
        }

        val sessionRecord = StudySession(
            subject = current.subject,
            startTimestamp = current.sessionStartTimeMillis,
            endTimestamp = now,
            plannedDurationSeconds = current.plannedDurationSeconds,
            actualDurationSeconds = actualDurationSeconds,
            breakDurationSeconds = current.totalBreakTakenSeconds,
            isCompleted = isCompleted,
            interruptedReason = reason
        )

        _lastCompletedSession.value = sessionRecord

        scope.launch {
            focusRepository.recordSession(sessionRecord)
        }

        _sessionState.value = ActiveFocusSession()
        clearSavedState()

        if (isCompleted) {
            FocusForegroundService.notifySessionCompleted(context, current.subject, actualDurationSeconds)
        }
    }

    fun clearLastCompletedSession() {
        _lastCompletedSession.value = null
    }

    private fun startTicker() {
        stopTicker()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                updateRemainingTime()
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun updateRemainingTime() {
        val current = _sessionState.value
        if (!current.isActive || current.isPaused) return

        val now = System.currentTimeMillis()

        if (current.isInBreak) {
            val breakRem = ((current.breakEndTimeMillis - now) / 1000L).coerceAtLeast(0)
            if (breakRem <= 0) {
                // Break ended automatically!
                resumeStudyFromBreak()
            } else {
                _sessionState.value = current.copy(breakRemainingSeconds = breakRem)
            }
        } else {
            val studyRem = ((current.sessionEndTimeMillis - now) / 1000L).coerceAtLeast(0)
            if (studyRem <= 0) {
                // Session completed successfully!
                endSession(isCompleted = true)
            } else {
                _sessionState.value = current.copy(remainingSeconds = studyRem)
            }
        }
    }

    private fun startService() {
        val intent = Intent(context, FocusForegroundService::class.java).apply {
            action = FocusForegroundService.ACTION_START_FOCUS
        }
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        com.example.service.AppBlockingService.start(context)
    }

    private fun stopService() {
        val intent = Intent(context, FocusForegroundService::class.java).apply {
            action = FocusForegroundService.ACTION_STOP_FOCUS
        }
        try {
            context.startService(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        com.example.service.AppBlockingService.stop(context)
    }

    private fun saveState(session: ActiveFocusSession) {
        prefs.edit().apply {
            putBoolean("isActive", session.isActive)
            putBoolean("isPaused", session.isPaused)
            putBoolean("isInBreak", session.isInBreak)
            putString("subject", session.subject)
            putLong("plannedDurationSeconds", session.plannedDurationSeconds)
            putLong("remainingSeconds", session.remainingSeconds)
            putLong("sessionStartTimeMillis", session.sessionStartTimeMillis)
            putLong("sessionEndTimeMillis", session.sessionEndTimeMillis)
            putLong("breakDurationSeconds", session.breakDurationSeconds)
            putLong("breakRemainingSeconds", session.breakRemainingSeconds)
            putLong("breakEndTimeMillis", session.breakEndTimeMillis)
            putLong("totalBreakTakenSeconds", session.totalBreakTakenSeconds)
            putStringSet("blockedPackageNames", session.blockedPackageNames.toSet())
            putBoolean("isStrictMode", session.isStrictMode)
            putBoolean("allowAppsDuringBreak", session.allowAppsDuringBreak)
            apply()
        }
    }

    private fun restoreState() {
        val isActive = prefs.getBoolean("isActive", false)
        if (!isActive) return

        val now = System.currentTimeMillis()
        val isPaused = prefs.getBoolean("isPaused", false)
        val isInBreak = prefs.getBoolean("isInBreak", false)
        val subject = prefs.getString("subject", "General Study") ?: "General Study"
        val plannedDurationSeconds = prefs.getLong("plannedDurationSeconds", 45 * 60L)
        val sessionEndTimeMillis = prefs.getLong("sessionEndTimeMillis", 0L)
        val sessionStartTimeMillis = prefs.getLong("sessionStartTimeMillis", now)
        val breakDurationSeconds = prefs.getLong("breakDurationSeconds", 5 * 60L)
        val breakEndTimeMillis = prefs.getLong("breakEndTimeMillis", 0L)
        val totalBreakTakenSeconds = prefs.getLong("totalBreakTakenSeconds", 0L)
        val blockedPackages = prefs.getStringSet("blockedPackageNames", emptySet())?.toList() ?: emptyList()
        val isStrictMode = prefs.getBoolean("isStrictMode", false)
        val allowAppsDuringBreak = prefs.getBoolean("allowAppsDuringBreak", true)

        if (!isPaused && sessionEndTimeMillis > 0 && sessionEndTimeMillis <= now) {
            // Already expired while app was dead
            val record = StudySession(
                subject = subject,
                startTimestamp = sessionStartTimeMillis,
                endTimestamp = sessionEndTimeMillis,
                plannedDurationSeconds = plannedDurationSeconds,
                actualDurationSeconds = plannedDurationSeconds,
                breakDurationSeconds = totalBreakTakenSeconds,
                isCompleted = true
            )
            scope.launch {
                focusRepository.recordSession(record)
            }
            clearSavedState()
            return
        }

        val remainingSeconds = if (isPaused) {
            prefs.getLong("remainingSeconds", 0L)
        } else {
            ((sessionEndTimeMillis - now) / 1000L).coerceAtLeast(0)
        }

        val breakRemainingSeconds = if (isInBreak && breakEndTimeMillis > now) {
            ((breakEndTimeMillis - now) / 1000L).coerceAtLeast(0)
        } else 0L

        val restored = ActiveFocusSession(
            isActive = true,
            isPaused = isPaused,
            isInBreak = isInBreak && breakRemainingSeconds > 0,
            subject = subject,
            plannedDurationSeconds = plannedDurationSeconds,
            remainingSeconds = remainingSeconds,
            sessionStartTimeMillis = sessionStartTimeMillis,
            sessionEndTimeMillis = sessionEndTimeMillis,
            breakDurationSeconds = breakDurationSeconds,
            breakRemainingSeconds = breakRemainingSeconds,
            breakEndTimeMillis = breakEndTimeMillis,
            totalBreakTakenSeconds = totalBreakTakenSeconds,
            blockedPackageNames = blockedPackages,
            isStrictMode = isStrictMode,
            allowAppsDuringBreak = allowAppsDuringBreak
        )

        _sessionState.value = restored
        if (!isPaused) {
            startTicker()
            startService()
        }
    }

    private fun clearSavedState() {
        prefs.edit().clear().apply()
    }

    companion object {
        @Volatile
        private var INSTANCE: FocusSessionManager? = null

        fun getInstance(context: Context, focusRepository: FocusRepository): FocusSessionManager {
            return INSTANCE ?: synchronized(this) {
                val instance = FocusSessionManager(context.applicationContext, focusRepository)
                INSTANCE = instance
                instance
            }
        }
    }
}
