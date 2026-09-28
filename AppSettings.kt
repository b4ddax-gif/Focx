package com.example.data.model

data class AppSettings(
    val isStrictMode: Boolean = false,
    val allowAppsDuringBreak: Boolean = true,
    val defaultFocusDurationMinutes: Int = 45,
    val defaultBreakDurationMinutes: Int = 5,
    val isSoundVibrationEnabled: Boolean = true,
    val isNotificationsEnabled: Boolean = true,
    val autoStartSchedules: Boolean = false,
    val isAmoledDarkTheme: Boolean = true,
    val isFirstTimeSetupDone: Boolean = false
)
