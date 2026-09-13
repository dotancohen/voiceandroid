package com.dotancohen.voiceandroid.ui

import com.dotancohen.voiceandroid.ui.screens.IssuesScreen
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.dotancohen.voiceandroid.ui.screens.ImportAudioScreen
import com.dotancohen.voiceandroid.ui.screens.NoteDetailScreen
import com.dotancohen.voiceandroid.ui.screens.NotesScreen
import com.dotancohen.voiceandroid.ui.screens.SettingsScreen
import com.dotancohen.voiceandroid.ui.screens.SyncSettingsScreen
import com.dotancohen.voiceandroid.ui.screens.AdvancedSettingsScreen
import com.dotancohen.voiceandroid.ui.screens.MissingDataScreen
import com.dotancohen.voiceandroid.ui.screens.MicrophoneSettingsScreen
import com.dotancohen.voiceandroid.ui.screens.PlaybackSettingsScreen
import com.dotancohen.voiceandroid.ui.screens.RecorderSettingsScreen
import com.dotancohen.voiceandroid.ui.screens.TranscriptionSettingsScreen
import com.dotancohen.voiceandroid.ui.screens.TranscriptionQueueScreen
import com.dotancohen.voiceandroid.ui.screens.TrashScreen
import com.dotancohen.voiceandroid.data.VoiceRepository
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.dotancohen.voiceandroid.ui.screens.TagHierarchyScreen
import com.dotancohen.voiceandroid.ui.screens.TagManagementScreen
import com.dotancohen.voiceandroid.viewmodel.SharedFilterViewModel

sealed class Screen(val route: String, val title: String) {
    data object Notes : Screen("notes", "Notes")
    /**
     * A note, optionally opened with its recorder running. Recording happens
     * inside the note it belongs to, so there is no separate recorder
     * screen: the "New voice recording" button makes the note first and
     * comes here with `record=true`.
     */
    data object NoteDetail : Screen("note/{noteId}?record={record}", "Note") {
        fun createRoute(noteId: String, record: Boolean = false) = "note/$noteId?record=$record"
    }
    data object Settings : Screen("settings", "Settings")
    data object SyncSettings : Screen("sync_settings", "Sync Settings")
    data object TagManagement : Screen("tags/{noteId}", "Manage Tags") {
        fun createRoute(noteId: String) = "tags/$noteId"
    }
    data object TagHierarchy : Screen("tag_hierarchy", "Manage Tags")
    data object ImportAudio : Screen("import_audio", "Import Audio")
    data object RecorderSettings : Screen("recorder_settings", "Recorder")
    data object MicrophoneSettings : Screen("microphone_settings", "Microphones")
    data object PlaybackSettings : Screen("playback_settings", "Playback")
    data object TranscriptionSettings : Screen("transcription_settings", "Transcription")
    data object AdvancedSettings : Screen("advanced_settings", "Advanced")
    data object Trash : Screen("trash", "Trash")
    data object Issues : Screen("issues", "Issues")
    data object TranscriptionQueue : Screen("transcription_queue", "Transcription queue")
    data object MissingData : Screen("missing_data", "Missing data")
}

@Composable
fun VoiceApp(
    pendingRoute: String? = null,
    onRouteConsumed: () -> Unit = {}
) {
    val navController = rememberNavController()

    // Navigation requested from outside the UI (ADB automation in debug builds)
    LaunchedEffect(pendingRoute) {
        if (pendingRoute != null) {
            navController.navigate(pendingRoute) { launchSingleTop = true }
            onRouteConsumed()
        }
    }
    // Get SharedFilterViewModel scoped to activity
    val context = LocalContext.current
    val sharedFilterViewModel: SharedFilterViewModel = viewModel(
        viewModelStoreOwner = context as ComponentActivity
    )

    // How big everything is drawn (Settings → Advanced). Held here, around
    // the whole navigation graph, so that switching size on the notes screen
    // changes every screen at once.
    val uiSize = remember { UiSizeState(context) }
    // Everything below is drawn at the chosen size, the Scaffold included, so
    // that the space the system bars take is measured at that size too.
    ScaledUi(large = uiSize.large.value) {
    // The marks on a note have a size of their own, on top of that.
    CompositionLocalProvider(LocalIconScale provides uiSize.iconScale.value) {

    // No bottom bar: every screen is reached from the toolbar of the screen
    // it belongs to, and Settings from the notes toolbar. A permanent bar
    // for two destinations spent a strip of every screen saying where the
    // user already was.
    Scaffold { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Screen.Notes.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Notes.route) {
                val scope = rememberCoroutineScope()
                NotesScreen(
                    sharedFilterViewModel = sharedFilterViewModel,
                    onNoteClick = { noteId ->
                        navController.navigate(Screen.NoteDetail.createRoute(noteId))
                    },
                    onTagNote = { noteId ->
                        navController.navigate(Screen.TagManagement.createRoute(noteId))
                    },
                    onNewNote = {
                        scope.launch {
                            VoiceRepository.getInstance(context).createNote("").onSuccess { id ->
                                navController.navigate(Screen.NoteDetail.createRoute(id))
                            }
                        }
                    },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onPairWithAnotherDevice = {
                        com.dotancohen.voiceandroid.data.PairingRequests.openReader.value = true
                        navController.navigate(Screen.SyncSettings.route)
                    },
                    uiSizeOffersSwitch = uiSize.offersSwitch,
                    uiIsLarge = uiSize.large.value,
                    onToggleUiSize = { uiSize.toggle() },
                    onNewRecording = {
                        // The recording is made inside a note, so the note
                        // comes first and the recorder opens in it.
                        scope.launch {
                            VoiceRepository.getInstance(context).createNote("").onSuccess { id ->
                                navController.navigate(Screen.NoteDetail.createRoute(id, record = true))
                            }
                        }
                    },
                    onNavigateToTranscriptionSettings = { navController.navigate(Screen.TranscriptionSettings.route) }
                )
            }
            composable(Screen.RecorderSettings.route) {
                RecorderSettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToMicrophones = {
                        navController.navigate(Screen.MicrophoneSettings.route)
                    }
                )
            }
            composable(Screen.MicrophoneSettings.route) {
                MicrophoneSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.PlaybackSettings.route) {
                PlaybackSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.TranscriptionSettings.route) {
                TranscriptionSettingsScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.TranscriptionQueue.route) {
                TranscriptionQueueScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { noteId ->
                        navController.navigate(Screen.NoteDetail.createRoute(noteId))
                    }
                )
            }
            composable(Screen.Trash.route) {
                TrashScreen(
                    onBack = { navController.popBackStack() },
                    onOpenNote = { noteId ->
                        navController.navigate(Screen.NoteDetail.createRoute(noteId))
                    }
                )
            }
            composable(Screen.Issues.route) {
                IssuesScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.MissingData.route) {
                MissingDataScreen(onBack = { navController.popBackStack() })
            }
            composable(Screen.AdvancedSettings.route) {
                AdvancedSettingsScreen(
                    onBack = { navController.popBackStack() },
                    // The size setting is read once at startup, so it has to
                    // be picked up again when the user changes it.
                    onUiSizeChanged = { uiSize.refresh() }
                )
            }
            composable(
                route = Screen.NoteDetail.route,
                arguments = listOf(
                    navArgument("noteId") { type = NavType.StringType },
                    navArgument("record") { type = NavType.BoolType; defaultValue = false }
                )
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
                val visibleNoteIds by sharedFilterViewModel.visibleNoteIds.collectAsState()
                NoteDetailScreen(
                    noteId = noteId,
                    onBack = { navController.popBackStack() },
                    onNavigateToTags = { navController.navigate(Screen.TagManagement.createRoute(noteId)) },
                    onNavigateToTranscriptionSettings = { navController.navigate(Screen.TranscriptionSettings.route) },
                    startRecording = backStackEntry.arguments?.getBoolean("record") == true,
                    visibleNoteIds = visibleNoteIds,
                    onOpenNote = { target ->
                        // Replace this note rather than stacking notes on top
                        // of each other: Back from the fifth note stepped
                        // through should return to the list, not walk back
                        // through the four before it.
                        navController.navigate(Screen.NoteDetail.createRoute(target)) {
                            popUpTo(Screen.NoteDetail.route) { inclusive = true }
                        }
                    }
                )
            }
            composable(
                route = Screen.TagManagement.route,
                arguments = listOf(navArgument("noteId") { type = NavType.StringType })
            ) { backStackEntry ->
                val noteId = backStackEntry.arguments?.getString("noteId") ?: return@composable
                TagManagementScreen(
                    noteId = noteId,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    onNavigateToSyncSettings = {
                        navController.navigate(Screen.SyncSettings.route)
                    },
                    onNavigateToManageTags = {
                        navController.navigate(Screen.TagHierarchy.route)
                    },
                    onNavigateToImportAudio = {
                        navController.navigate(Screen.ImportAudio.route)
                    },
                    onNavigateToRecorder = {
                        navController.navigate(Screen.RecorderSettings.route)
                    },
                    onNavigateToPlayback = {
                        navController.navigate(Screen.PlaybackSettings.route)
                    },
                    onNavigateToTranscription = {
                        navController.navigate(Screen.TranscriptionSettings.route)
                    },
                    onNavigateToAdvanced = {
                        navController.navigate(Screen.AdvancedSettings.route)
                    },
                    onNavigateToTrash = {
                        navController.navigate(Screen.Trash.route)
                    },
                    onNavigateToIssues = {
                        navController.navigate(Screen.Issues.route)
                    },
                    onNavigateToTranscriptionQueue = {
                        navController.navigate(Screen.TranscriptionQueue.route)
                    },
                    onNavigateToMissingData = {
                        navController.navigate(Screen.MissingData.route)
                    }
                )
            }
            composable(Screen.SyncSettings.route) {
                SyncSettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.TagHierarchy.route) {
                TagHierarchyScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable(Screen.ImportAudio.route) {
                ImportAudioScreen(
                    onBack = { navController.popBackStack() },
                    onImportComplete = {
                        navController.popBackStack()
                    }
                )
            }
        }
    }
    }
    }
}
