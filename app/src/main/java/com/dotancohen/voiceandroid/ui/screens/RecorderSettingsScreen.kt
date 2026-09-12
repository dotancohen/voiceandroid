package com.dotancohen.voiceandroid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.BuildConfig
import com.dotancohen.voiceandroid.audio.RecorderPreferences
import com.dotancohen.voiceandroid.viewmodel.RecorderSettingsViewModel

/**
 * Settings → Recorder: the recording format, what the New button does,
 * what happens during a telephone call, and a way in to the microphones,
 * which have a screen of their own.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderSettingsScreen(
    onBack: () -> Unit,
    onNavigateToMicrophones: () -> Unit,
    viewModel: RecorderSettingsViewModel = viewModel()
) {
    val mics by viewModel.mics.collectAsState()
    val defaultAction by viewModel.defaultNewAction.collectAsState()
    val recordingFormat by viewModel.recordingFormat.collectAsState()
    val startImmediately by viewModel.startImmediately.collectAsState()
    val duringCall by viewModel.duringCall.collectAsState()
    val transcribeWhenSaved by viewModel.transcribeWhenSaved.collectAsState()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recorder") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.refresh() }) { Text("Refresh") }
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
            Text("The New button creates", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = defaultAction == RecorderPreferences.ACTION_NOTE, onClick = { viewModel.setDefaultNewAction(RecorderPreferences.ACTION_NOTE) })
                Text("A new Note")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = defaultAction == RecorderPreferences.ACTION_RECORDING, onClick = { viewModel.setDefaultNewAction(RecorderPreferences.ACTION_RECORDING) })
                Text("A new voice Recording")
            }
            Text(
                "Long-press the New button to choose the other one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = startImmediately, onCheckedChange = { viewModel.setStartImmediately(it) })
                Column(modifier = Modifier.weight(1f)) {
                    Text("Start recording as soon as the screen opens")
                    Text(
                        "No button to press: the recording begins with the screen. " +
                            "The first time, the phone still asks for permission to use the microphone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Still being tried out: debug builds only.
            if (BuildConfig.DEV_FEATURES) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Checkbox(
                        checked = transcribeWhenSaved,
                        onCheckedChange = { viewModel.setTranscribeWhenSaved(it) }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Transcribe every Recording as it is saved")
                        Text(
                            "The recording goes straight into the transcription queue when you " +
                                "press Save, without opening the Transcribe dialogue. Under trial: " +
                                "this setting is not in a release build.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text("During a telephone call", style = MaterialTheme.typography.titleMedium)
            Text(
                "Android gives the microphone to the telephone, so a call cannot be recorded. " +
                    "This is what happens to a recording that is already running.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for (behaviour in RecorderPreferences.CALL_BEHAVIOURS) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = duringCall == behaviour,
                        onClick = { viewModel.setDuringCall(behaviour) }
                    )
                    Text(RecorderPreferences.callBehaviourTitle(behaviour))
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Recording format", style = MaterialTheme.typography.titleMedium)
            for (format in RecorderPreferences.FORMATS) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    RadioButton(selected = recordingFormat == format, onClick = { viewModel.setRecordingFormat(format) })
                    Column {
                        Text(RecorderPreferences.formatTitle(format))
                        Text(
                            RecorderPreferences.formatDescription(format),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            // The microphones have a screen of their own: naming them and
            // testing each one is a job in itself, and it filled this screen
            // so that the settings under it were never seen.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onNavigateToMicrophones)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Microphones", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = mics.firstOrNull { it.selected }?.friendlyName
                                ?: "System default microphone",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Choose one, name them, and test where each hears best.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
