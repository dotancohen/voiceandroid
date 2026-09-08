package com.dotancohen.voiceandroid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
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
import com.dotancohen.voiceandroid.audio.RecorderPreferences
import com.dotancohen.voiceandroid.viewmodel.RecorderSettingsViewModel

/**
 * Settings → Recorder: the microphones the phone has, a friendly name for
 * each, which one to record with, a live level meter to find where each
 * microphone hears the user best, and what the toolbar "New" button does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecorderSettingsScreen(
    onBack: () -> Unit,
    viewModel: RecorderSettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val mics by viewModel.mics.collectAsState()
    val defaultAction by viewModel.defaultNewAction.collectAsState()
    val recordingFormat by viewModel.recordingFormat.collectAsState()
    val testingKey by viewModel.testingMicKey.collectAsState()
    val level by viewModel.level.collectAsState()
    val peak by viewModel.peak.collectAsState()
    var pendingTest by remember { mutableStateOf<String?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val key = pendingTest
        pendingTest = null
        if (granted && key != null) viewModel.test(mics.firstOrNull { it.key == key })
    }

    fun startTest(key: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.test(mics.firstOrNull { it.key == key })
        } else {
            pendingTest = key
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Stop the meter when leaving the screen
    DisposableEffect(Unit) { onDispose { viewModel.test(null) } }

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
                Text("A new note")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = defaultAction == RecorderPreferences.ACTION_RECORDING, onClick = { viewModel.setDefaultNewAction(RecorderPreferences.ACTION_RECORDING) })
                Text("A new voice recording")
            }
            Text(
                "Long-press the New button to choose the other one.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
            Text("Microphones", style = MaterialTheme.typography.titleMedium)
            Text(
                "Give each microphone a name, choose the one to record with, and press Test while speaking: " +
                    "the bar shows how loudly that microphone hears you, and the peak shows the loudest point so far. " +
                    "Speak near each edge and corner of the phone to learn where each microphone hears best.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = mics.none { it.selected }, onClick = { viewModel.selectMic(null) })
                Text("System default microphone")
            }

            if (mics.isEmpty()) {
                Text("No microphones reported. Press Refresh.", color = MaterialTheme.colorScheme.error)
            }

            for (mic in mics) {
                var name by remember(mic.key, mic.friendlyName) { mutableStateOf(mic.friendlyName) }
                val testing = testingKey == mic.key
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = mic.selected, onClick = { viewModel.selectMic(mic.key) })
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it; viewModel.rename(mic, it) },
                                    label = { Text("Friendly name") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Text(mic.technicalName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            TextButton(onClick = { if (testing) viewModel.test(null) else startTest(mic.key) }) {
                                Text(if (testing) "Stop test" else "Test")
                            }
                            if (testing) {
                                Column(modifier = Modifier.weight(1f)) {
                                    LinearProgressIndicator(progress = { level }, modifier = Modifier.fillMaxWidth())
                                    Text("now ${(level * 100).toInt()}%  ·  peak ${(peak * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
