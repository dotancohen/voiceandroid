package com.dotancohen.voiceandroid.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.viewmodel.RecordingState
import com.dotancohen.voiceandroid.viewmodel.RecordingViewModel

/**
 * Records a voice message: elapsed time (hh:mm:ss), a live waveform, a large
 * red Record/Pause button, and a toolbar with Save, Restart and Trash.
 * Saving creates a note with the recording attached and opens it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordingScreen(
    onCancel: () -> Unit,
    onSaved: (noteId: String) -> Unit,
    viewModel: RecordingViewModel = viewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val elapsed by viewModel.elapsedSeconds.collectAsState()
    val waveform by viewModel.waveform.collectAsState()
    val error by viewModel.error.collectAsState()
    val savedNoteId by viewModel.savedNoteId.collectAsState()
    var showTrashConfirm by remember { mutableStateOf(false) }
    var showRestartConfirm by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.record()
    }

    fun recordOrPause() {
        when (state) {
            RecordingState.Recording -> viewModel.pause()
            RecordingState.Idle, RecordingState.Paused -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.record()
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            else -> {}
        }
    }

    LaunchedEffect(savedNoteId) { savedNoteId?.let { onSaved(it) } }

    val hasRecording = state == RecordingState.Recording || state == RecordingState.Paused

    // Back behaves like Trash: ask first if something was recorded
    BackHandler { if (hasRecording) showTrashConfirm = true else { viewModel.trash(); onCancel() } }

    if (showTrashConfirm) {
        AlertDialog(
            onDismissRequest = { showTrashConfirm = false },
            title = { Text("Discard recording?") },
            text = { Text("The recording is thrown away and no note is created.") },
            confirmButton = { TextButton(onClick = { showTrashConfirm = false; viewModel.trash(); onCancel() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { showTrashConfirm = false }) { Text("Keep") } }
        )
    }
    if (showRestartConfirm) {
        AlertDialog(
            onDismissRequest = { showRestartConfirm = false },
            title = { Text("Start over?") },
            text = { Text("What was recorded so far is thrown away and recording starts again from 00:00:00.") },
            confirmButton = { TextButton(onClick = { showRestartConfirm = false; viewModel.restart(); recordOrPause() }) { Text("Start over") } },
            dismissButton = { TextButton(onClick = { showRestartConfirm = false }) { Text("Keep") } }
        )
    }
    error?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Recording") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.clearError() }) { Text("OK") } }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("New voice recording") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatHms(elapsed),
                style = MaterialTheme.typography.displayLarge
            )
            Text(
                text = when (state) {
                    RecordingState.Idle -> "Press the red button to start · ${viewModel.micName} · ${viewModel.formatTitle}"
                    RecordingState.Recording -> "Recording · ${viewModel.micName}"
                    RecordingState.Paused -> "Paused"
                    RecordingState.Saving -> "Saving…"
                    RecordingState.Saved -> "Saved"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(24.dp))

            Waveform(samples = waveform, modifier = Modifier.fillMaxWidth().height(180.dp))

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { recordOrPause() },
                enabled = state != RecordingState.Saving && state != RecordingState.Saved,
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F), contentColor = Color.White),
                modifier = Modifier.size(120.dp)
            ) {
                Icon(
                    imageVector = if (state == RecordingState.Recording) Icons.Filled.Pause else Icons.Filled.Mic,
                    contentDescription = if (state == RecordingState.Recording) "Pause" else "Record",
                    modifier = Modifier.size(56.dp)
                )
            }
            Text(
                text = if (state == RecordingState.Recording) "Pause" else if (state == RecordingState.Paused) "Resume" else "Record",
                style = MaterialTheme.typography.labelLarge
            )

            Spacer(modifier = Modifier.height(32.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                Button(onClick = { viewModel.save() }, enabled = hasRecording) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Save")
                }
                OutlinedButton(onClick = { showRestartConfirm = true }, enabled = hasRecording) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Restart")
                }
                OutlinedButton(onClick = { if (hasRecording) showTrashConfirm = true else { viewModel.trash(); onCancel() } }) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                    Text("Trash")
                }
            }
        }
    }
}

/** Live level bars, newest on the right. */
@Composable
private fun Waveform(samples: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val faint = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val slots = 120
        val slotWidth = size.width / slots
        val mid = size.height / 2
        drawLine(faint, Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2f)
        val start = slots - samples.size
        samples.forEachIndexed { i, level ->
            val x = (start + i + 0.5f) * slotWidth
            val half = (level.coerceIn(0.02f, 1f) * mid)
            drawLine(color, Offset(x, mid - half), Offset(x, mid + half), strokeWidth = slotWidth * 0.6f, cap = StrokeCap.Round)
        }
    }
}

fun formatHms(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
}
