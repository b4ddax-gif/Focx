package com.example

import android.app.Application
import com.example.data.db.FocusLockDatabase
import com.example.data.repository.FocusRepository
import com.example.data.repository.SettingsRepository
import com.example.manager.FocusSessionManager
import com.example.manager.InstalledAppsManager

class FocusLockApplication : Application() {

    lateinit var database: FocusLockDatabase
        private set

    lateinit var focusRepository: FocusRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var installedAppsManager: InstalledAppsManager
        private set

    lateinit var focusSessionManager: FocusSessionManager
        private set

    override fun onCreate() {
        super.onCreate()

        database = FocusLockDatabase.getInstance(this)
        focusRepository = FocusRepository(
            blockedAppDao = database.blockedAppDao(),
            studyScheduleDao = database.studyScheduleDao(),
            studySessionDao = database.studySessionDao()
        )
        settingsRepository = SettingsRepository(this)
        installedAppsManager = InstalledAppsManager(this)
        focusSessionManager = FocusSessionManager.getInstance(this, focusRepository)
    }
}
