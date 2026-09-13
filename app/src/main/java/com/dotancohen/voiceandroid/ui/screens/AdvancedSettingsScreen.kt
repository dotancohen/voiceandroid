package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Transcribe
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.util.TimeFormat
import com.dotancohen.voiceandroid.util.UiPreferences
import java.util.Locale
import kotlin.math.roundToInt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.viewmodel.SnapshotsViewModel

/**
 * Settings → Advanced: the settings most people never need to change.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    /** Told when the interface size changed, so every screen is redrawn. */
    onUiSizeChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val prefs = remember { UiPreferences(context) }
    var uiSizeMode by remember { mutableStateOf(prefs.uiSizeMode) }
    var iconSize by remember { mutableStateOf(prefs.iconSize) }
    var spotlightMs by remember { mutableIntStateOf(prefs.spotlightDurationMs) }
    var listLines by remember { mutableIntStateOf(prefs.notesListLines) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Time format", style = MaterialTheme.typography.titleMedium)
            Text(
                "How every date and time in the application is written. Each choice below shows " +
                    "the same moment, Wednesday 3 September 2026 at 14:05, so they can be compared.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            var timeFormat by remember {
                mutableStateOf(
                    prefs.timeFormat ?: TimeFormat.DEFAULT_PATTERN
                )
            }
            var customFormat by remember { mutableStateOf(prefs.timeFormatCustom) }
            var formatMenuOpen by remember { mutableStateOf(false) }
            val currentLabel = TimeFormat.PRESETS.firstOrNull { it.second == timeFormat }?.first
                ?: timeFormat
            Box {
                OutlinedButton(onClick = { formatMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(currentLabel)
                }
                DropdownMenu(expanded = formatMenuOpen, onDismissRequest = { formatMenuOpen = false }) {
                    for ((label, pattern) in TimeFormat.PRESETS) {
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                timeFormat = pattern
                                prefs.timeFormat = pattern
                                formatMenuOpen = false
                            }
                        )
                    }
                }
            }
            // The free-form pattern appears only for the Custom choice, so
            // the screen stays quiet for everybody else.
            if (timeFormat == TimeFormat.CUSTOM) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = customFormat,
                    onValueChange = {
                        customFormat = it
                        prefs.timeFormatCustom = it
                    },
                    label = { Text("Advanced date format") },
                    supportingText = {
                        Text(
                            "SimpleDateFormat pattern; the token -N becomes the number of days " +
                                "ago (e.g. yyyy-MM-dd HH:mm EEE -N)"
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Now: " + TimeFormat.format(
                        context.getSharedPreferences("voice_settings", android.content.Context.MODE_PRIVATE),
                        System.currentTimeMillis()
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Interface size", style = MaterialTheme.typography.titleMedium)
            Text(
                "How big everything is drawn: the text, the icons and the space around them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (mode in UiPreferences.UI_SIZES) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = uiSizeMode == mode,
                        onClick = {
                            uiSizeMode = mode
                            prefs.uiSizeMode = mode
                            onUiSizeChanged()
                        }
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(UiPreferences.uiSizeTitle(mode), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            UiPreferences.uiSizeDescription(mode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Tags", style = MaterialTheme.typography.titleMedium)
            var colouredTags by remember { mutableStateOf(prefs.colouredTags) }
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Switch(
                    checked = colouredTags,
                    onCheckedChange = {
                        colouredTags = it
                        prefs.colouredTags = it
                    }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text("Draw Tags in their own colours", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Each Tag has a colour of its own, calculated from its name unless you " +
                            "choose one under Settings → Manage Tags. Turn this off to show Tags " +
                            "as plain text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Icon size", style = MaterialTheme.typography.titleMedium)
            Text(
                "How big the marks on a note are drawn: the recording and transcription marks in " +
                    "the notes list, and the transcribe mark inside a note. Separate from the " +
                    "interface size, so the marks can be easy to see and to hit without everything " +
                    "else growing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            for (size in UiPreferences.ICON_SIZES) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RadioButton(
                        selected = iconSize == size,
                        onClick = {
                            iconSize = size
                            prefs.iconSize = size
                            onUiSizeChanged()
                        }
                    )
                    Column(modifier = Modifier.padding(start = 4.dp)) {
                        Text(UiPreferences.iconSizeTitle(size), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            UiPreferences.iconSizeDescription(size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    // The mark itself, at the size this choice draws it, so
                    // the choice is made by looking rather than by guessing.
                    Icon(
                        imageVector = Icons.Default.Transcribe,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(18.dp * UiPreferences.iconScaleFor(size)),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Spotlight duration", style = MaterialTheme.typography.titleMedium)
            Text(
                "When you leave a note, its row in the list is pointed out by two dots that travel " +
                    "from the middle of the row to its edges. This is how long that takes. Zero turns it off.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (spotlightMs == 0) "Off" else String.format(Locale.US, "%.2f seconds", spotlightMs / 1000f),
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = spotlightMs.toFloat(),
                onValueChange = { value ->
                    // Whole twentieths of a second, kept as it moves so that
                    // leaving the screen mid-drag cannot lose the setting
                    spotlightMs = (value / STEP_MS).roundToInt() * STEP_MS
                    prefs.spotlightDurationMs = spotlightMs
                },
                valueRange = 0f..UiPreferences.MAX_SPOTLIGHT_MS.toFloat(),
                steps = (UiPreferences.MAX_SPOTLIGHT_MS / STEP_MS) - 1,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text("Lines in the Notes list", style = MaterialTheme.typography.titleMedium)
            Text(
                "How many lines of a note's text, and of its transcription, each row of the list " +
                    "shows. More lines say more about each note and fewer notes fit on the screen. " +
                    "A preview of a note (hold Next or Previous inside a note) shows twice as many.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (listLines == 1) "1 line of each" else "$listLines lines of each",
                style = MaterialTheme.typography.bodyMedium
            )
            Slider(
                value = listLines.toFloat(),
                onValueChange = { value ->
                    listLines = value.roundToInt().coerceIn(1, UiPreferences.MAX_LIST_LINES)
                    prefs.notesListLines = listLines
                },
                valueRange = 1f..UiPreferences.MAX_LIST_LINES.toFloat(),
                steps = UiPreferences.MAX_LIST_LINES - 2,
                modifier = Modifier.fillMaxWidth()
            )

            // Move this device to another account (Stage 1): deliberate, no shortcut
            val moveViewModel: SnapshotsViewModel = viewModel()
            val moveMessage by moveViewModel.moveMessage.collectAsState()
            val noteCount by moveViewModel.noteCount.collectAsState()
            val currentAccount by moveViewModel.accountId.collectAsState()
            LaunchedEffect(Unit) { moveViewModel.loadNoteCount() }
            var moveCode by remember { mutableStateOf("") }
            var typedCurrent by remember { mutableStateOf("") }
            Text("Move this device to another account", style = MaterialTheme.typography.titleMedium)
            Text(
                "For merging two accounts: the $noteCount notes on this phone join the other account, tags with one path become one, and nothing is deleted. Paste the code shown by a device of the other account, then type this account's full id by hand.",
                style = MaterialTheme.typography.bodySmall
            )
            OutlinedTextField(value = moveCode, onValueChange = { moveCode = it }, label = { Text("The other account's code") }, placeholder = { Text("voice://pair?...") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = typedCurrent, onValueChange = { typedCurrent = it }, label = { Text("This account's id, typed in full") }, placeholder = { Text(currentAccount) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(
                onClick = { moveViewModel.moveToAccount(moveCode, typedCurrent) },
                enabled = moveCode.startsWith("voice://pair?") && typedCurrent.trim().length == 32,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Move $noteCount notes to the other account") }
            moveMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

            // Snapshots: a copy of the database before every sync, kept five deep
            Spacer(modifier = Modifier.height(24.dp))
            Text("Snapshots", style = MaterialTheme.typography.titleMedium)
            Text(
                "A copy of the notes database is taken before every sync and kept five deep. " +
                    "Restoring one brings the notes back as they were; the state being replaced is kept as the newest snapshot.",
                style = MaterialTheme.typography.bodySmall
            )
            val snapshotsViewModel: SnapshotsViewModel = viewModel()
            val snapshots by snapshotsViewModel.snapshots.collectAsState()
            val snapshotMessage by snapshotsViewModel.message.collectAsState()
            val accountId by snapshotsViewModel.accountId.collectAsState()
            var confirmRestore by remember { mutableStateOf<String?>(null) }
            Text("Account $accountId", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { snapshotsViewModel.takeSnapshot() }, modifier = Modifier.fillMaxWidth()) {
                Text("Take a snapshot now")
            }
            snapshotMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            snapshots.forEach { snapshot ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(snapshot.name, style = MaterialTheme.typography.bodyMedium)
                        Text("${snapshot.noteCount} notes, ${snapshot.sizeBytes / 1024} KB", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { confirmRestore = snapshot.name }) { Text("Restore") }
                }
            }
            confirmRestore?.let { name ->
                AlertDialog(
                    onDismissRequest = { confirmRestore = null },
                    title = { Text("Restore $name?") },
                    text = { Text("The notes database is replaced with this snapshot. The current state is kept as the newest snapshot, so this can be undone.") },
                    confirmButton = {
                        TextButton(onClick = { snapshotsViewModel.restore(name); confirmRestore = null }) { Text("Restore") }
                    },
                    dismissButton = { TextButton(onClick = { confirmRestore = null }) { Text("Cancel") } }
                )
            }
        }
    }
}

private const val STEP_MS = 50
