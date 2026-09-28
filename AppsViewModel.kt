package com.example.ui.screens.apps

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.FocusLockApplication
import com.example.data.model.BlockedApp
import com.example.manager.InstalledAppItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppItemUi(
    val packageName: String,
    val appName: String,
    val isBlocked: Boolean,
    val isSystemEssential: Boolean,
    val systemCategory: String?,
    val icon: android.graphics.drawable.Drawable?
)

data class AppsUiState(
    val isLoading: Boolean = true,
    val searchQuery: String = "",
    val apps: List<AppItemUi> = emptyList(),
    val totalBlockedCount: Int = 0,
    val totalAppsCount: Int = 0
)

class AppsViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as FocusLockApplication
    private val focusRepository = app.focusRepository
    private val installedAppsManager = app.installedAppsManager

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<AppsUiState> = combine(
        _installedApps,
        focusRepository.allBlockedApps,
        _searchQuery,
        _isLoading
    ) { installed, blockedInDb, query, loading ->
        val blockedMap = blockedInDb.associate { it.packageName to it.isBlocked }

        val items = installed.map { item ->
            val isBlocked = blockedMap[item.packageName] ?: false
            AppItemUi(
                packageName = item.packageName,
                appName = item.appName,
                isBlocked = if (item.isSystemEssential) false else isBlocked,
                isSystemEssential = item.isSystemEssential,
                systemCategory = item.systemCategory,
                icon = item.icon
            )
        }

        val filtered = if (query.isBlank()) {
            items
        } else {
            items.filter {
                it.appName.contains(query, ignoreCase = true) ||
                        it.packageName.contains(query, ignoreCase = true)
            }
        }

        AppsUiState(
            isLoading = loading,
            searchQuery = query,
            apps = filtered,
            totalBlockedCount = items.count { it.isBlocked },
            totalAppsCount = items.size
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = AppsUiState()
    )

    init {
        loadInstalledApps()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleAppBlock(packageName: String, currentBlocked: Boolean) {
        viewModelScope.launch {
            val appItem = _installedApps.value.find { it.packageName == packageName } ?: return@launch
            if (appItem.isSystemEssential) return@launch // Never block system essential apps

            val newBlockedState = !currentBlocked
            focusRepository.insertOrUpdateApp(
                BlockedApp(
                    packageName = packageName,
                    appName = appItem.appName,
                    isBlocked = newBlockedState
                )
            )
        }
    }

    fun selectAllDistractingApps() {
        viewModelScope.launch {
            val appsToBlock = _installedApps.value
                .filter { !it.isSystemEssential }
                .map {
                    BlockedApp(
                        packageName = it.packageName,
                        appName = it.appName,
                        isBlocked = true
                    )
                }
            focusRepository.insertAllApps(appsToBlock)
        }
    }

    fun clearAllBlockedApps() {
        viewModelScope.launch {
            focusRepository.setAllAppsBlocked(false)
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = installedAppsManager.getInstalledLaunchableApps()
            _installedApps.value = apps
            _isLoading.value = false
        }
    }
}
