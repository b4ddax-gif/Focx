package com.example

import com.example.data.model.ActiveFocusSession
import com.example.data.model.StudySchedule
import com.example.data.model.StudySession
import com.example.data.repository.FocusRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FocusLockLogicTest {

    @Test
    fun testTimerCalculations() {
        val now = System.currentTimeMillis()
        val plannedSeconds = 45 * 60L
        val remainingSeconds = 30 * 60L

        val session = ActiveFocusSession(
            isActive = true,
            plannedDurationSeconds = plannedSeconds,
            remainingSeconds = remainingSeconds,
            sessionStartTimeMillis = now - 15 * 60 * 1000L,
            sessionEndTimeMillis = now + 30 * 60 * 1000L
        )

        assertEquals(15 * 60L, session.elapsedSeconds)
        assertEquals("30:00", session.formattedRemainingTime)
        assertEquals(0.333f, session.progress, 0.01f)
    }

    @Test
    fun testBreakCalculations() {
        val breakTotalSeconds = 10 * 60L
        val breakRemainingSeconds = 4 * 60L

        val session = ActiveFocusSession(
            isActive = true,
            isInBreak = true,
            breakDurationSeconds = breakTotalSeconds,
            breakRemainingSeconds = breakRemainingSeconds
        )

        assertEquals("04:00", session.formattedRemainingTime)
        assertEquals(0.6f, session.breakProgress, 0.01f)
    }

    @Test
    fun testStudySessionCompletionPercentage() {
        val fullSession = StudySession(
            subject = "Physics",
            startTimestamp = 1000L,
            endTimestamp = 3700L,
            plannedDurationSeconds = 2700L, // 45 min
            actualDurationSeconds = 2700L,
            isCompleted = true
        )
        assertEquals(100, fullSession.completionPercentage)
        assertTrue(fullSession.isCompleted)

        val partialSession = StudySession(
            subject = "Calculus",
            startTimestamp = 1000L,
            endTimestamp = 2000L,
            plannedDurationSeconds = 2000L,
            actualDurationSeconds = 1000L,
            isCompleted = false,
            interruptedReason = "Stopped early"
        )
        assertEquals(50, partialSession.completionPercentage)
        assertFalse(partialSession.isCompleted)
    }

    @Test
    fun testPlannerScheduleLogic() {
        val schedule = StudySchedule(
            subject = "Organic Chemistry",
            startHour = 4,
            startMinute = 0,
            durationMinutes = 60,
            breakDurationMinutes = 10,
            daysOfWeek = "1,2,3,4,5"
        )

        assertEquals("4:00 AM", schedule.getFormattedTime())
        assertEquals("5:00 AM", schedule.getEndTimeFormatted())
        assertEquals(listOf(1, 2, 3, 4, 5), schedule.getDaysList())

        val afternoonSchedule = StudySchedule(
            subject = "History",
            startHour = 14,
            startMinute = 30,
            durationMinutes = 45,
            daysOfWeek = "6,7"
        )
        assertEquals("2:30 PM", afternoonSchedule.getFormattedTime())
        assertEquals("3:15 PM", afternoonSchedule.getEndTimeFormatted())
        assertEquals(listOf(6, 7), afternoonSchedule.getDaysList())
    }

    @Test
    fun testStreakCalculations() {
        // Mock DAOs are not needed for pure calculation testing
        val dummyRepo = FocusRepository(
            blockedAppDao = FakeBlockedAppDao(),
            studyScheduleDao = FakeStudyScheduleDao(),
            studySessionDao = FakeStudySessionDao()
        )

        val cal = Calendar.getInstance()

        // Today session
        val todaySession = StudySession(
            subject = "Math",
            startTimestamp = cal.timeInMillis,
            endTimestamp = cal.timeInMillis + 3600000L,
            plannedDurationSeconds = 3600L,
            actualDurationSeconds = 3600L,
            isCompleted = true
        )

        // Yesterday session
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val yesterdaySession = StudySession(
            subject = "Math",
            startTimestamp = cal.timeInMillis,
            endTimestamp = cal.timeInMillis + 3600000L,
            plannedDurationSeconds = 3600L,
            actualDurationSeconds = 3600L,
            isCompleted = true
        )

        // 2 days ago session
        cal.add(Calendar.DAY_OF_YEAR, -1)
        val twoDaysAgoSession = StudySession(
            subject = "Math",
            startTimestamp = cal.timeInMillis,
            endTimestamp = cal.timeInMillis + 3600000L,
            plannedDurationSeconds = 3600L,
            actualDurationSeconds = 3600L,
            isCompleted = true
        )

        val streakStats = dummyRepo.calculateStreak(
            listOf(todaySession, yesterdaySession, twoDaysAgoSession)
        )

        assertEquals(3, streakStats.currentStreakDays)
        assertEquals(3, streakStats.longestStreakDays)
        assertEquals(3, streakStats.totalStudyDays)
        assertEquals(3, streakStats.completedSessionsCount)
        assertEquals(0, streakStats.interruptedSessionsCount)
        assertEquals(3600L * 3, streakStats.totalStudySeconds)
    }
}

// Minimal fakes for FocusRepository unit test instantiation
private class FakeBlockedAppDao : com.example.data.db.BlockedAppDao {
    override fun getAllBlockedApps() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.data.model.BlockedApp>())
    override fun getActiveBlockedApps() = kotlinx.coroutines.flow.flowOf(emptyList<com.example.data.model.BlockedApp>())
    override suspend fun getActiveBlockedPackageNames() = emptyList<String>()
    override suspend fun insertOrUpdate(app: com.example.data.model.BlockedApp) {}
    override suspend fun insertAll(apps: List<com.example.data.model.BlockedApp>) {}
    override suspend fun setBlocked(packageName: String, isBlocked: Boolean) {}
    override suspend fun setAllBlocked(isBlocked: Boolean) {}
    override suspend fun delete(packageName: String) {}
    override suspend fun clearAll() {}
}

private class FakeStudyScheduleDao : com.example.data.db.StudyScheduleDao {
    override fun getAllSchedules() = kotlinx.coroutines.flow.flowOf(emptyList<StudySchedule>())
    override fun getEnabledSchedules() = kotlinx.coroutines.flow.flowOf(emptyList<StudySchedule>())
    override suspend fun getScheduleById(id: Long) = null
    override suspend fun insertSchedule(schedule: StudySchedule) = 1L
    override suspend fun updateSchedule(schedule: StudySchedule) {}
    override suspend fun setEnabled(id: Long, isEnabled: Boolean) {}
    override suspend fun deleteSchedule(id: Long) {}
    override suspend fun clearAll() {}
}

private class FakeStudySessionDao : com.example.data.db.StudySessionDao {
    override fun getAllSessions() = kotlinx.coroutines.flow.flowOf(emptyList<StudySession>())
    override fun getSessionsSince(sinceTimestamp: Long) = kotlinx.coroutines.flow.flowOf(emptyList<StudySession>())
    override fun getCompletedSessions() = kotlinx.coroutines.flow.flowOf(emptyList<StudySession>())
    override suspend fun getSessionsForDay(startDayTimestamp: Long, endDayTimestamp: Long) = emptyList<StudySession>()
    override suspend fun insertSession(session: StudySession) = 1L
    override fun getTotalCompletedStudySeconds() = kotlinx.coroutines.flow.flowOf(0L)
    override suspend fun clearAll() {}
}
