package com.example.data.repository

import com.example.data.db.BlockedAppDao
import com.example.data.db.StudyScheduleDao
import com.example.data.db.StudySessionDao
import com.example.data.model.BlockedApp
import com.example.data.model.StudySchedule
import com.example.data.model.StudySession
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

data class StreakStats(
    val currentStreakDays: Int = 0,
    val longestStreakDays: Int = 0,
    val totalStudyDays: Int = 0,
    val completedSessionsCount: Int = 0,
    val interruptedSessionsCount: Int = 0,
    val totalStudySeconds: Long = 0L,
    val todayStudySeconds: Long = 0L
)

class FocusRepository(
    private val blockedAppDao: BlockedAppDao,
    private val studyScheduleDao: StudyScheduleDao,
    private val studySessionDao: StudySessionDao
) {
    // Blocked Apps
    val allBlockedApps: Flow<List<BlockedApp>> = blockedAppDao.getAllBlockedApps()
    val activeBlockedApps: Flow<List<BlockedApp>> = blockedAppDao.getActiveBlockedApps()

    suspend fun getActiveBlockedPackageNames(): List<String> =
        blockedAppDao.getActiveBlockedPackageNames()

    suspend fun insertOrUpdateApp(app: BlockedApp) = blockedAppDao.insertOrUpdate(app)

    suspend fun insertAllApps(apps: List<BlockedApp>) = blockedAppDao.insertAll(apps)

    suspend fun setAppBlocked(packageName: String, isBlocked: Boolean) =
        blockedAppDao.setBlocked(packageName, isBlocked)

    suspend fun setAllAppsBlocked(isBlocked: Boolean) =
        blockedAppDao.setAllBlocked(isBlocked)

    suspend fun removeApp(packageName: String) = blockedAppDao.delete(packageName)

    // Schedules
    val allSchedules: Flow<List<StudySchedule>> = studyScheduleDao.getAllSchedules()
    val enabledSchedules: Flow<List<StudySchedule>> = studyScheduleDao.getEnabledSchedules()

    suspend fun getScheduleById(id: Long): StudySchedule? =
        studyScheduleDao.getScheduleById(id)

    suspend fun saveSchedule(schedule: StudySchedule): Long =
        studyScheduleDao.insertSchedule(schedule)

    suspend fun updateSchedule(schedule: StudySchedule) =
        studyScheduleDao.updateSchedule(schedule)

    suspend fun setScheduleEnabled(id: Long, isEnabled: Boolean) =
        studyScheduleDao.setEnabled(id, isEnabled)

    suspend fun deleteSchedule(id: Long) =
        studyScheduleDao.deleteSchedule(id)

    // Sessions
    val allSessions: Flow<List<StudySession>> = studySessionDao.getAllSessions()
    val totalStudySeconds: Flow<Long?> = studySessionDao.getTotalCompletedStudySeconds()

    suspend fun recordSession(session: StudySession): Long =
        studySessionDao.insertSession(session)

    suspend fun clearAllHistory() {
        studySessionDao.clearAll()
    }

    /**
     * Calculates streak stats accurately based on calendar days.
     */
    fun calculateStreak(sessions: List<StudySession>): StreakStats {
        val completed = sessions.filter { it.isCompleted && it.actualDurationSeconds > 0 }
        if (completed.isEmpty()) {
            val totalSeconds = sessions.sumOf { it.actualDurationSeconds }
            return StreakStats(
                interruptedSessionsCount = sessions.count { !it.isCompleted },
                totalStudySeconds = totalSeconds
            )
        }

        // Normalize timestamps to day key (YYYY-MM-DD or day epoch)
        val dayCalendar = Calendar.getInstance()
        val distinctDays = completed.map { session ->
            dayCalendar.timeInMillis = session.startTimestamp
            val year = dayCalendar.get(Calendar.YEAR)
            val dayOfYear = dayCalendar.get(Calendar.DAY_OF_YEAR)
            year * 1000 + dayOfYear
        }.distinct().sortedDescending()

        val todayCalendar = Calendar.getInstance()
        val todayKey = todayCalendar.get(Calendar.YEAR) * 1000 + todayCalendar.get(Calendar.DAY_OF_YEAR)
        todayCalendar.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdayKey = todayCalendar.get(Calendar.YEAR) * 1000 + todayCalendar.get(Calendar.DAY_OF_YEAR)

        var currentStreak = 0
        if (distinctDays.isNotEmpty()) {
            val mostRecent = distinctDays[0]
            if (mostRecent == todayKey || mostRecent == yesterdayKey) {
                // Streak is active!
                currentStreak = 1
                var checkCalendar = Calendar.getInstance()
                // set to most recent
                if (mostRecent == yesterdayKey) {
                    checkCalendar.add(Calendar.DAY_OF_YEAR, -1)
                }

                for (i in 1 until distinctDays.size) {
                    checkCalendar.add(Calendar.DAY_OF_YEAR, -1)
                    val expectedKey = checkCalendar.get(Calendar.YEAR) * 1000 + checkCalendar.get(Calendar.DAY_OF_YEAR)
                    if (distinctDays[i] == expectedKey) {
                        currentStreak++
                    } else {
                        break
                    }
                }
            }
        }

        // Calculate longest streak
        var longestStreak = 0
        if (distinctDays.isNotEmpty()) {
            val sortedAsc = distinctDays.sorted()
            var run = 1
            longestStreak = 1
            val cal = Calendar.getInstance()

            for (i in 1 until sortedAsc.size) {
                // parse previous
                val prevYear = sortedAsc[i - 1] / 1000
                val prevDay = sortedAsc[i - 1] % 1000
                cal.set(Calendar.YEAR, prevYear)
                cal.set(Calendar.DAY_OF_YEAR, prevDay)
                cal.add(Calendar.DAY_OF_YEAR, 1)
                val expected = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR)

                if (sortedAsc[i] == expected) {
                    run++
                    if (run > longestStreak) longestStreak = run
                } else {
                    run = 1
                }
            }
        }

        val totalSeconds = sessions.sumOf { it.actualDurationSeconds }
        val startOfToday = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val todaySeconds = sessions
            .filter { it.startTimestamp >= startOfToday }
            .sumOf { it.actualDurationSeconds }

        return StreakStats(
            currentStreakDays = currentStreak,
            longestStreakDays = maxOf(longestStreak, currentStreak),
            totalStudyDays = distinctDays.size,
            completedSessionsCount = completed.size,
            interruptedSessionsCount = sessions.count { !it.isCompleted },
            totalStudySeconds = totalSeconds,
            todayStudySeconds = todaySeconds
        )
    }
}
