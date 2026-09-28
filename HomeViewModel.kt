package com.example.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusLockApplication
import com.example.data.model.ActiveFocusSession
import com.example.data.model.StudySchedule
import com.example.data.repository.StreakStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val activeSession: ActiveFocusSession = ActiveFocusSession(),
    val streakStats: StreakStats = StreakStats(),
    val nextSchedule: StudySchedule? = null,
    val totalBlockedAppsCount: Int = 0,
    val isFirstTimeSetupDone: Boolean = true
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusLockApplication
    private val focusRepository = app.focusRepository
    private val sessionManager = app.focusSessionManager
    private val settingsRepository = app.settingsRepository

    val activeSession: StateFlow<ActiveFocusSession> = sessionManager.sessionState

    val uiState: StateFlow<HomeUiState> = combine(
        sessionManager.sessionState,
        focusRepository.allSessions,
        focusRepository.enabledSchedules,
        focusRepository.activeBlockedApps,
        settingsRepository.appSettings
    ) { session, sessions, schedules, blockedApps, settings ->
        val streak = focusRepository.calculateStreak(sessions)
        val next = findNextSchedule(schedules)
        HomeUiState(
            activeSession = session,
            streakStats = streak,
            nextSchedule = next,
            totalBlockedAppsCount = blockedApps.size,
            isFirstTimeSetupDone = settings.isFirstTimeSetupDone
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HomeUiState()
    )

    fun startFocusSession(
        subject: String,
        durationMinutes: Int,
        breakDurationMinutes: Int,
        selectedPackages: List<String>,
        isStrictMode: Boolean,
        allowAppsDuringBreak: Boolean
    ) {
        viewModelScope.launch {
            val packagesToBlock = if (selectedPackages.isNotEmpty()) {
                selectedPackages
            } else {
                focusRepository.getActiveBlockedPackageNames()
            }
            sessionManager.startFocusSession(
                subject = subject,
                durationMinutes = durationMinutes,
                breakDurationMinutes = breakDurationMinutes,
                blockedPackages = packagesToBlock,
                isStrictMode = isStrictMode,
                allowAppsDuringBreak = allowAppsDuringBreak
            )
        }
    }

    fun pauseSession() = sessionManager.pauseSession()
    fun resumeSession() = sessionManager.resumeSession()
    fun startBreak() = sessionManager.startBreak()
    fun resumeStudyFromBreak() = sessionManager.resumeStudyFromBreak()
    fun endSession(isCompleted: Boolean, reason: String? = null) =
        sessionManager.endSession(isCompleted, reason)

    private fun findNextSchedule(schedules: List<StudySchedule>): StudySchedule? {
        if (schedules.isEmpty()) return null
        val cal = Calendar.getInstance()
        val currentMinutes = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

        // Today's day of week: 1=Mon, ..., 7=Sun
        val todayDow = when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1
            Calendar.TUESDAY -> 2
            Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4
            Calendar.FRIDAY -> 5
            Calendar.SATURDAY -> 6
            Calendar.SUNDAY -> 7
            else -> 1
        }

        // Search for upcoming schedule today
        val upcomingToday = schedules
            .filter { it.getDaysList().isEmpty() || it.getDaysList().contains(todayDow) }
            .filter { (it.startHour * 60 + it.startMinute) > currentMinutes }
            .minByOrNull { (it.startHour * 60 + it.startMinute) }

        if (upcomingToday != null) return upcomingToday

        // Otherwise find first upcoming schedule tomorrow or earliest
        return schedules.minByOrNull { it.startHour * 60 + it.startMinute }
    }
}
