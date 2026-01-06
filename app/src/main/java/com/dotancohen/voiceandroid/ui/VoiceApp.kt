package com.dotancohen.voiceandroid.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.dotancohen.voiceandroid.ui.screens.NoteDetailScreen
import com.dotancohen.voiceandroid.ui.screens.NotesScreen
import com.dotancohen.voiceandroid.ui.screens.SettingsScreen
import com.dotancohen.voiceandroid.ui.screens.SyncSettingsScreen
import com.dotancohen.voiceandroid.viewmodel.SharedFilterViewModel

sealed class Screen(val route: String, val title: String) {
    data object Notes : Screen("notes", "Notes")
    data object NoteDetail : Screen("note/{noteId}", "Note") {
        fun createRoute(noteId: String) = "note/$noteId"
    }
    data object Settings : Screen("settings", "Settings")
    data object SyncSettings : Screen("sync_settings", "Sync Settings")
}

@Composable
fun VoiceApp() {
    val navController = rememberNavController()
    val screens = listOf(Screen.Notes, Screen.Settings)

    // Get SharedFilterViewModel scoped to activity
    val context = LocalContext.current
    val sharedFilterViewModel: SharedFilterViewModel = viewModel(
        viewModelStoreOwner = context as ComponentActivity
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                screens.forEach { screen ->
                    val isSelected = currentDestination?.hierarchy?.any { it.route == screen.route } == true

                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = when (screen) {
                                    is Screen.Notes -> Icons.AutoMirrored.Filled.List
                                    is Screen.Settings -> Icons.Default.Settings
                                    else -> Icons.Default.Settings
                                },
                                contentDescription = screen.title
                            )
                        },
                        label = { Text(screen.title) },
                        selected = isSelected,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Notes.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Notes.route) {
                NotesScreen(
                    sharedFilterViewModel = sharedFilterViewModel,
                    onNoteClick = { noteId ->
                        navController.navigate(Screen.NoteDetail.createRoute(noteId))
                    }
                )
            }
            composable(
                route = Screen.NoteDetail.route,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
                NoteDetailScreen(
                    noteId = noteId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onNavigateToSyncSettings = {
                        navController.navigate(Screen.SyncSettings.route)
                    }
                )
            }
            composable(Screen.SyncSettings.route) {
                SyncSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
