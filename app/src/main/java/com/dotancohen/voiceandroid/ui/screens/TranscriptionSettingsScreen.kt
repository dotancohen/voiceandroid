package com.dotancohen.voiceandroid.ui.screens

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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.transcription.OnDeviceTranscriber
import com.dotancohen.voiceandroid.transcription.TranscriptionPreferences
import com.dotancohen.voiceandroid.transcription.TranscriptionStage
import com.dotancohen.voiceandroid.viewmodel.TranscriptionSettingsViewModel

/**
 * Settings → Transcription: which Whisper model runs on the phone (download,
 * delete, choose), the language to assume, and greedy versus beam search.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionSettingsScreen(
    onBack: () -> Unit,
    viewModel: TranscriptionSettingsViewModel = viewModel()
) {
    val models by viewModel.models.collectAsState()
    val language by viewModel.language.collectAsState()
    val beamSize by viewModel.beamSize.collectAsState()
    val message by viewModel.message.collectAsState()
    val current by OnDeviceTranscriber.current.collectAsState()
    val queued by OnDeviceTranscriber.queued.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcription") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("On this phone", style = MaterialTheme.typography.titleMedium)
            Text(
                "Recordings are transcribed on the phone itself with Whisper; nothing is sent anywhere. " +
                    "Download a model, choose it, then open a note and tap Transcribe on device under its player. " +
                    "A ten-minute recording takes a few minutes with the large models.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            current?.let { job ->
                Spacer(modifier = Modifier.height(12.dp))
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            when (job.stage) {
                                TranscriptionStage.Done -> "Finished: ${job.filename}"
                                TranscriptionStage.Failed -> "Failed: ${job.filename}"
                                else -> "Working on ${job.filename}"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(job.message, style = MaterialTheme.typography.bodySmall)
                        if (job.stage < TranscriptionStage.Done) {
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        if (queued.isNotEmpty()) Text("${queued.size} more waiting", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Language", style = MaterialTheme.typography.titleMedium)
            for ((code, title) in TranscriptionPreferences.LANGUAGES) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = language == code, onClick = { viewModel.setLanguage(code) })
                    Text(title)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Decoding", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = beamSize > 1, onClick = { viewModel.setBeamSize(5) })
                Column {
                    Text("Beam search (5 beams)")
                    Text("Most accurate. Several times slower than greedy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = beamSize <= 1, onClick = { viewModel.setBeamSize(1) })
                Column {
                    Text("Greedy")
                    Text("Fastest. Fine for clear speech.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Models", style = MaterialTheme.typography.titleMedium)
            Text(
                "Models are stored in the app's private storage and removed when the app is uninstalled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            for (item in models) {
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = item.selected,
                                onClick = { viewModel.select(item.model) },
                                enabled = item.installed
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.model.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    item.model.sizeText + if (item.installed) " · downloaded" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(item.model.description, style = MaterialTheme.typography.bodySmall)
                        item.progress?.let { p ->
                            Spacer(modifier = Modifier.height(4.dp))
                            LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                            Text("${(p * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                        }
                        Row {
                            when {
                                item.progress != null -> TextButton(onClick = { viewModel.cancelDownload(item.model) }) { Text("Cancel") }
                                item.installed -> TextButton(onClick = { viewModel.delete(item.model) }) { Text("Delete") }
                                else -> TextButton(onClick = { viewModel.download(item.model) }) { Text("Download") }
                            }
                        }
                    }
                }
            }
        }
    }
}
