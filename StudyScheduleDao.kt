package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.StudySchedule
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyScheduleDao {
    @Query("SELECT * FROM study_schedules ORDER BY startHour ASC, startMinute ASC")
    fun getAllSchedules(): Flow<List<StudySchedule>>

    @Query("SELECT * FROM study_schedules WHERE isEnabled = 1 ORDER BY startHour ASC, startMinute ASC")
    fun getEnabledSchedules(): Flow<List<StudySchedule>>

    @Query("SELECT * FROM study_schedules WHERE id = :id")
    suspend fun getScheduleById(id: Long): StudySchedule?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSchedule(schedule: StudySchedule): Long

    @Update
    suspend fun updateSchedule(schedule: StudySchedule)

    @Query("UPDATE study_schedules SET isEnabled = :isEnabled WHERE id = :id")
    suspend fun setEnabled(id: Long, isEnabled: Boolean)

    @Query("DELETE FROM study_schedules WHERE id = :id")
    suspend fun deleteSchedule(id: Long)

    @Query("DELETE FROM study_schedules")
    suspend fun clearAll()
}
