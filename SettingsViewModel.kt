package com.example.ui.screens.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusLockApplication
import com.example.data.model.AppSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusLockApplication
    private val settingsRepository = app.settingsRepository
    private val focusRepository = app.focusRepository

    val appSettings: StateFlow<AppSettings> = settingsRepository.appSettings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AppSettings()
    )

    fun setStrictMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setStrictMode(enabled) }
    }

    fun setAllowAppsDuringBreak(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAllowAppsDuringBreak(enabled) }
    }

    fun setDefaultFocusMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setDefaultFocusMinutes(minutes) }
    }

    fun setDefaultBreakMinutes(minutes: Int) {
        viewModelScope.launch { settingsRepository.setDefaultBreakMinutes(minutes) }
    }

    fun setSoundVibration(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setSoundVibration(enabled) }
    }

    fun setNotifications(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setNotifications(enabled) }
    }

    fun setAutoStartSchedules(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAutoStartSchedules(enabled) }
    }

    fun resetPreferences() {
        viewModelScope.launch { settingsRepository.resetPreferences() }
    }

    fun clearAllStudyHistory() {
        viewModelScope.launch { focusRepository.clearAllHistory() }
    }
}
