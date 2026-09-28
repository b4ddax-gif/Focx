package com.example.ui.screens.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusLockApplication
import com.example.data.model.StudySession
import com.example.data.repository.StreakStats
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

enum class HistoryFilter {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    ALL_TIME
}

data class HistoryUiState(
    val selectedFilter: HistoryFilter = HistoryFilter.ALL_TIME,
    val filteredSessions: List<StudySession> = emptyList(),
    val streakStats: StreakStats = StreakStats(),
    val totalSessionsCount: Int = 0
)

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusLockApplication
    private val focusRepository = app.focusRepository

    private val _selectedFilter = MutableStateFlow(HistoryFilter.ALL_TIME)
    val selectedFilter: StateFlow<HistoryFilter> = _selectedFilter.asStateFlow()

    val uiState: StateFlow<HistoryUiState> = combine(
        focusRepository.allSessions,
        _selectedFilter
    ) { allSessions, filter ->
        val streak = focusRepository.calculateStreak(allSessions)

        val cal = Calendar.getInstance()
        val now = System.currentTimeMillis()

        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfWeek = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val startOfMonth = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val filtered = when (filter) {
            HistoryFilter.TODAY -> allSessions.filter { it.startTimestamp >= startOfToday }
            HistoryFilter.THIS_WEEK -> allSessions.filter { it.startTimestamp >= startOfWeek }
            HistoryFilter.THIS_MONTH -> allSessions.filter { it.startTimestamp >= startOfMonth }
            HistoryFilter.ALL_TIME -> allSessions
        }

        HistoryUiState(
            selectedFilter = filter,
            filteredSessions = filtered,
            streakStats = streak,
            totalSessionsCount = allSessions.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = HistoryUiState()
    )

    fun setFilter(filter: HistoryFilter) {
        _selectedFilter.value = filter
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            focusRepository.clearAllHistory()
        }
    }
}
