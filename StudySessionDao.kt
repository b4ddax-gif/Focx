package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.StudySession
import kotlinx.coroutines.flow.Flow

@Dao
interface StudySessionDao {
    @Query("SELECT * FROM study_sessions ORDER BY startTimestamp DESC")
    fun getAllSessions(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE startTimestamp >= :sinceTimestamp ORDER BY startTimestamp DESC")
    fun getSessionsSince(sinceTimestamp: Long): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE isCompleted = 1 ORDER BY startTimestamp DESC")
    fun getCompletedSessions(): Flow<List<StudySession>>

    @Query("SELECT * FROM study_sessions WHERE startTimestamp >= :startDayTimestamp AND startTimestamp <= :endDayTimestamp")
    suspend fun getSessionsForDay(startDayTimestamp: Long, endDayTimestamp: Long): List<StudySession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySession): Long

    @Query("SELECT SUM(actualDurationSeconds) FROM study_sessions WHERE isCompleted = 1")
    fun getTotalCompletedStudySeconds(): Flow<Long?>

    @Query("DELETE FROM study_sessions")
    suspend fun clearAll()
}
