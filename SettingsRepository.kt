package com.example.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_lock_settings")

class SettingsRepository(private val context: Context) {

    private object PreferencesKeys {
        val STRICT_MODE = booleanPreferencesKey("strict_mode")
        val ALLOW_APPS_DURING_BREAK = booleanPreferencesKey("allow_apps_during_break")
        val DEFAULT_FOCUS_MINUTES = intPreferencesKey("default_focus_minutes")
        val DEFAULT_BREAK_MINUTES = intPreferencesKey("default_break_minutes")
        val SOUND_VIBRATION = booleanPreferencesKey("sound_vibration")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val AUTO_START_SCHEDULES = booleanPreferencesKey("auto_start_schedules")
        val AMOLED_DARK_THEME = booleanPreferencesKey("amoled_dark_theme")
        val FIRST_TIME_SETUP_DONE = booleanPreferencesKey("first_time_setup_done")
    }

    val appSettings: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            isStrictMode = preferences[PreferencesKeys.STRICT_MODE] ?: false,
            allowAppsDuringBreak = preferences[PreferencesKeys.ALLOW_APPS_DURING_BREAK] ?: true,
            defaultFocusDurationMinutes = preferences[PreferencesKeys.DEFAULT_FOCUS_MINUTES] ?: 45,
            defaultBreakDurationMinutes = preferences[PreferencesKeys.DEFAULT_BREAK_MINUTES] ?: 5,
            isSoundVibrationEnabled = preferences[PreferencesKeys.SOUND_VIBRATION] ?: true,
            isNotificationsEnabled = preferences[PreferencesKeys.NOTIFICATIONS] ?: true,
            autoStartSchedules = preferences[PreferencesKeys.AUTO_START_SCHEDULES] ?: false,
            isAmoledDarkTheme = preferences[PreferencesKeys.AMOLED_DARK_THEME] ?: true,
            isFirstTimeSetupDone = preferences[PreferencesKeys.FIRST_TIME_SETUP_DONE] ?: false
        )
    }

    suspend fun setStrictMode(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.STRICT_MODE] = enabled }
    }

    suspend fun setAllowAppsDuringBreak(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.ALLOW_APPS_DURING_BREAK] = enabled }
    }

    suspend fun setDefaultFocusMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_FOCUS_MINUTES] = minutes }
    }

    suspend fun setDefaultBreakMinutes(minutes: Int) {
        context.dataStore.edit { it[PreferencesKeys.DEFAULT_BREAK_MINUTES] = minutes }
    }

    suspend fun setSoundVibration(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.SOUND_VIBRATION] = enabled }
    }

    suspend fun setNotifications(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.NOTIFICATIONS] = enabled }
    }

    suspend fun setAutoStartSchedules(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AUTO_START_SCHEDULES] = enabled }
    }

    suspend fun setAmoledDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.AMOLED_DARK_THEME] = enabled }
    }

    suspend fun setFirstTimeSetupDone(done: Boolean) {
        context.dataStore.edit { it[PreferencesKeys.FIRST_TIME_SETUP_DONE] = done }
    }

    suspend fun resetPreferences() {
        context.dataStore.edit { it.clear() }
    }
}
