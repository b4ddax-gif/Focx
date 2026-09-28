package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_sessions")
data class StudySession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String,
    val startTimestamp: Long,
    val endTimestamp: Long,
    val plannedDurationSeconds: Long,
    val actualDurationSeconds: Long,
    val breakDurationSeconds: Long = 0,
    val isCompleted: Boolean = true,
    val interruptedReason: String? = null
) {
    val completionPercentage: Int
        get() {
            if (plannedDurationSeconds <= 0) return 100
            val pct = ((actualDurationSeconds.toDouble() / plannedDurationSeconds) * 100).toInt()
            return pct.coerceIn(0, 100)
        }
}
