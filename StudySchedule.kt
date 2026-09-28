package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "study_schedules")
data class StudySchedule(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val subject: String,
    val startHour: Int,
    val startMinute: Int,
    val durationMinutes: Int = 45,
    val daysOfWeek: String = "1,2,3,4,5", // 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun
    val breakDurationMinutes: Int = 5,
    val isEnabled: Boolean = true,
    val blockedPackages: String = "" // Comma-separated or empty for all selected
) {
    fun getFormattedTime(): String {
        val h = if (startHour == 0) 12 else if (startHour > 12) startHour - 12 else startHour
        val amPm = if (startHour < 12) "AM" else "PM"
        val m = String.format("%02d", startMinute)
        return "$h:$m $amPm"
    }

    fun getEndTimeFormatted(): String {
        val totalMinutes = startHour * 60 + startMinute + durationMinutes
        val endH24 = (totalMinutes / 60) % 24
        val endM = totalMinutes % 60
        val h = if (endH24 == 0) 12 else if (endH24 > 12) endH24 - 12 else endH24
        val amPm = if (endH24 < 12) "AM" else "PM"
        val m = String.format("%02d", endM)
        return "$h:$m $amPm"
    }

    fun getDaysList(): List<Int> {
        if (daysOfWeek.isBlank()) return emptyList()
        return daysOfWeek.split(",").mapNotNull { it.trim().toIntOrNull() }
    }
}
