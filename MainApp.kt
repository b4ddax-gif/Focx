package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.FocusLockApplication
import com.example.data.model.StudySchedule
import com.example.ui.screens.apps.AppsScreen
import com.example.ui.screens.apps.AppsViewModel
import com.example.ui.screens.focus.ActiveFocusScreen
import com.example.ui.screens.focus.BreakScreen
import com.example.ui.screens.focus.SessionCompleteScreen
import com.example.ui.screens.history.HistoryScreen
import com.example.ui.screens.history.HistoryViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.onboarding.FirstTimeSetupScreen
import com.example.ui.screens.onboarding.PermissionHelpScreen
import com.example.ui.screens.planner.AddEditScheduleScreen
import com.example.ui.screens.planner.PlannerScreen
import com.example.ui.screens.planner.PlannerViewModel
import com.example.ui.screens.settings.AboutScreen
import com.example.ui.screens.settings.PrivacyScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.theme.AmoledBlack
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlinx.coroutines.launch

enum class MainNavTab(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    PLANNER("Planner", Icons.Filled.CalendarMonth, Icons.Outlined.CalendarMonth),
    APPS("Apps", Icons.Filled.Apps, Icons.Outlined.Apps),
    HISTORY("History", Icons.Filled.History, Icons.Outlined.History),
    SETTINGS("Settings", Icons.Filled.Settings, Icons.Outlined.Settings)
}

enum class SubScreen {
    NONE,
    ADD_EDIT_SCHEDULE,
    ABOUT,
    PRIVACY,
    PERMISSION_HELP
}

@Composable
fun MainApp() {
    val context = LocalContext.current
    val app = context.applicationContext as FocusLockApplication
    val coroutineScope = rememberCoroutineScope()

    val homeViewModel: HomeViewModel = viewModel()
    val appsViewModel: AppsViewModel = viewModel()
    val plannerViewModel: PlannerViewModel = viewModel()
    val historyViewModel: HistoryViewModel = viewModel()
    val settingsViewModel: SettingsViewModel = viewModel()

    val activeSession by homeViewModel.activeSession.collectAsState()
    val lastCompletedSession by app.focusSessionManager.lastCompletedSession.collectAsState()
    val appSettings by settingsViewModel.appSettings.collectAsState()

    var currentTab by remember { mutableStateOf(MainNavTab.HOME) }
    var currentSubScreen by remember { mutableStateOf(SubScreen.NONE) }
    var editingSchedule by remember { mutableStateOf<StudySchedule?>(null) }

    // First Time Setup Onboarding
    if (!appSettings.isFirstTimeSetupDone) {
        if (currentSubScreen == SubScreen.PERMISSION_HELP) {
            PermissionHelpScreen(onBack = { currentSubScreen = SubScreen.NONE })
        } else {
            FirstTimeSetupScreen(
                onComplete = {
                    coroutineScope.launch {
                        app.settingsRepository.setFirstTimeSetupDone(true)
                    }
                },
                onNavigateToHelp = { currentSubScreen = SubScreen.PERMISSION_HELP }
            )
        }
        return
    }

    // Active Focus Session Screen (Full-Screen Focus Mode)
    if (activeSession.isActive) {
        if (activeSession.isInBreak) {
            BreakScreen(
                session = activeSession,
                onResumeStudy = homeViewModel::resumeStudyFromBreak
            )
        } else {
            ActiveFocusScreen(
                session = activeSession,
                onPause = homeViewModel::pauseSession,
                onResume = homeViewModel::resumeSession,
                onStartBreak = homeViewModel::startBreak,
                onEndSession = { completed, reason ->
                    homeViewModel.endSession(completed, reason)
                }
            )
        }
        return
    }

    // Session Complete Screen
    if (lastCompletedSession != null) {
        SessionCompleteScreen(
            session = lastCompletedSession!!,
            onDone = { app.focusSessionManager.clearLastCompletedSession() },
            onViewHistory = {
                app.focusSessionManager.clearLastCompletedSession()
                currentTab = MainNavTab.HISTORY
            }
        )
        return
    }

    // Sub-screens
    when (currentSubScreen) {
        SubScreen.ADD_EDIT_SCHEDULE -> {
            BackHandler {
                currentSubScreen = SubScreen.NONE
                editingSchedule = null
            }
            AddEditScheduleScreen(
                schedule = editingSchedule,
                onSave = { schedule ->
                    plannerViewModel.saveSchedule(schedule) {
                        currentSubScreen = SubScreen.NONE
                        editingSchedule = null
                    }
                },
                onBack = {
                    currentSubScreen = SubScreen.NONE
                    editingSchedule = null
                }
            )
            return
        }
        SubScreen.ABOUT -> {
            BackHandler { currentSubScreen = SubScreen.NONE }
            AboutScreen(onBack = { currentSubScreen = SubScreen.NONE })
            return
        }
        SubScreen.PRIVACY -> {
            BackHandler { currentSubScreen = SubScreen.NONE }
            PrivacyScreen(onBack = { currentSubScreen = SubScreen.NONE })
            return
        }
        SubScreen.PERMISSION_HELP -> {
            BackHandler { currentSubScreen = SubScreen.NONE }
            PermissionHelpScreen(onBack = { currentSubScreen = SubScreen.NONE })
            return
        }
        SubScreen.NONE -> {
            // Regular tab content below
        }
    }

    // Main scaffold with bottom navigation
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack),
        containerColor = AmoledBlack,
        bottomBar = {
            NavigationBar(
                containerColor = DarkSurface,
                contentColor = TextPrimary
            ) {
                MainNavTab.values().forEach { tab ->
                    val isSelected = currentTab == tab
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) tab.selectedIcon else tab.unselectedIcon,
                                contentDescription = tab.title
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            )
                        },
                        modifier = Modifier.testTag("nav_${tab.name.lowercase()}"),
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AmoledBlack,
                            selectedTextColor = CyanAccent,
                            indicatorColor = CyanAccent,
                            unselectedIconColor = TextSecondary,
                            unselectedTextColor = TextSecondary
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                MainNavTab.HOME -> {
                    HomeScreen(
                        viewModel = homeViewModel,
                        onNavigateToApps = { currentTab = MainNavTab.APPS },
                        onNavigateToPlanner = { currentTab = MainNavTab.PLANNER }
                    )
                }
                MainNavTab.PLANNER -> {
                    PlannerScreen(
                        viewModel = plannerViewModel,
                        onAddSchedule = {
                            editingSchedule = null
                            currentSubScreen = SubScreen.ADD_EDIT_SCHEDULE
                        },
                        onEditSchedule = { schedule ->
                            editingSchedule = schedule
                            currentSubScreen = SubScreen.ADD_EDIT_SCHEDULE
                        }
                    )
                }
                MainNavTab.APPS -> {
                    AppsScreen(viewModel = appsViewModel)
                }
                MainNavTab.HISTORY -> {
                    HistoryScreen(viewModel = historyViewModel)
                }
                MainNavTab.SETTINGS -> {
                    SettingsScreen(
                        viewModel = settingsViewModel,
                        onNavigateToAbout = { currentSubScreen = SubScreen.ABOUT },
                        onNavigateToPrivacy = { currentSubScreen = SubScreen.PRIVACY },
                        onNavigateToPermissionHelp = { currentSubScreen = SubScreen.PERMISSION_HELP }
                    )
                }
            }
        }
    }
}
