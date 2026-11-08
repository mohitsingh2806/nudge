package com.mohit.nudge.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.mohit.nudge.ui.history.HistoryScreen
import com.mohit.nudge.ui.medicines.EditMedicineScreen
import com.mohit.nudge.ui.medicines.MedicinesScreen
import com.mohit.nudge.ui.settings.SettingsScreen
import com.mohit.nudge.ui.today.TodayScreen

private enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    TODAY("today", "Today", Icons.Filled.Home),
    MEDICINES("medicines", "Medicines", Icons.AutoMirrored.Filled.List),
    HISTORY("history", "History", Icons.Filled.DateRange),
    SETTINGS("settings", "Settings", Icons.Filled.Settings),
}

@Composable
fun NudgeAppUi() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (Tab.entries.any { it.route == route }) {
                NavigationBar {
                    Tab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = route == tab.route,
                            onClick = {
                                nav.navigate(tab.route) {
                                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = Tab.TODAY.route,
            modifier = Modifier.padding(bottom = padding.calculateBottomPadding()),
        ) {
            composable(Tab.TODAY.route) { TodayScreen(onAddMedicine = { nav.navigate("edit/0") }) }
            composable(Tab.MEDICINES.route) { MedicinesScreen(onEdit = { id -> nav.navigate("edit/$id") }) }
            composable(Tab.HISTORY.route) { HistoryScreen() }
            composable(Tab.SETTINGS.route) { SettingsScreen() }
            composable(
                route = "edit/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType }),
            ) { entry ->
                EditMedicineScreen(
                    id = entry.arguments?.getLong("id") ?: 0L,
                    onDone = { nav.popBackStack() },
                )
            }
        }
    }
}
