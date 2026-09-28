package com.example.data.model

data class ActiveFocusSession(
    val isActive: Boolean = false,
    val isPaused: Boolean = false,
    val isInBreak: Boolean = false,
    val subject: String = "General Study",
    val plannedDurationSeconds: Long = 45 * 60L,
    val remainingSeconds: Long = 45 * 60L,
    val sessionStartTimeMillis: Long = 0L,
    val sessionEndTimeMillis: Long = 0L,
    val breakDurationSeconds: Long = 5 * 60L,
    val breakRemainingSeconds: Long = 0L,
    val breakEndTimeMillis: Long = 0L,
    val totalBreakTakenSeconds: Long = 0L,
    val blockedPackageNames: List<String> = emptyList(),
    val isStrictMode: Boolean = false,
    val allowAppsDuringBreak: Boolean = true
) {
    val progress: Float
        get() {
            if (plannedDurationSeconds <= 0) return 0f
            val elapsed = (plannedDurationSeconds - remainingSeconds).coerceAtLeast(0)
            return (elapsed.toFloat() / plannedDurationSeconds.toFloat()).coerceIn(0f, 1f)
        }

    val breakProgress: Float
        get() {
            if (breakDurationSeconds <= 0) return 0f
            val elapsed = (breakDurationSeconds - breakRemainingSeconds).coerceAtLeast(0)
            return (elapsed.toFloat() / breakDurationSeconds.toFloat()).coerceIn(0f, 1f)
        }

    val elapsedSeconds: Long
        get() = (plannedDurationSeconds - remainingSeconds).coerceAtLeast(0)

    val formattedRemainingTime: String
        get() {
            val totalSec = if (isInBreak) breakRemainingSeconds else remainingSeconds
            val minutes = totalSec / 60
            val seconds = totalSec % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}
