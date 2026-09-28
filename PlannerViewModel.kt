package com.example.ui.screens.planner

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusLockApplication
import com.example.data.model.StudySchedule
import com.example.service.ScheduleAlarmReceiver
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlannerViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusLockApplication
    private val focusRepository = app.focusRepository

    val schedules: StateFlow<List<StudySchedule>> = focusRepository.allSchedules.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = emptyList()
    )

    fun toggleScheduleEnabled(schedule: StudySchedule) {
        viewModelScope.launch {
            val updatedState = !schedule.isEnabled
            focusRepository.setScheduleEnabled(schedule.id, updatedState)
            if (updatedState) {
                ScheduleAlarmReceiver.scheduleAlarm(app, schedule.copy(isEnabled = true))
            } else {
                ScheduleAlarmReceiver.cancelAlarm(app, schedule.id)
            }
        }
    }

    fun saveSchedule(schedule: StudySchedule, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            val id = if (schedule.id == 0L) {
                focusRepository.saveSchedule(schedule)
            } else {
                focusRepository.updateSchedule(schedule)
                schedule.id
            }

            if (schedule.isEnabled) {
                ScheduleAlarmReceiver.scheduleAlarm(app, schedule.copy(id = id))
            } else {
                ScheduleAlarmReceiver.cancelAlarm(app, id)
            }
            onSaved()
        }
    }

    fun duplicateSchedule(schedule: StudySchedule) {
        viewModelScope.launch {
            val duplicate = schedule.copy(
                id = 0L,
                subject = "${schedule.subject} (Copy)",
                isEnabled = true
            )
            val newId = focusRepository.saveSchedule(duplicate)
            ScheduleAlarmReceiver.scheduleAlarm(app, duplicate.copy(id = newId))
        }
    }

    fun deleteSchedule(scheduleId: Long) {
        viewModelScope.launch {
            ScheduleAlarmReceiver.cancelAlarm(app, scheduleId)
            focusRepository.deleteSchedule(scheduleId)
        }
    }
}
