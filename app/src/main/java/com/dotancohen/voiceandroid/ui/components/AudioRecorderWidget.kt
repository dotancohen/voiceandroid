package com.dotancohen.voiceandroid.ui.components

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.dotancohen.voiceandroid.audio.RecorderPreferences
import com.dotancohen.voiceandroid.audio.RecordingState
import com.dotancohen.voiceandroid.audio.VoiceRecorder
import com.dotancohen.voiceandroid.ui.theme.RecordRed

/**
 * Records into the note this is shown in: the counterpart of
 * [AudioPlayerWidget], in the same place on the screen and with the same
 * shape.
 *
 * The elapsed time in hours, minutes and seconds; a live waveform; a large
 * red Record/Pause button with a small Discard to its left and a small Save
 * to its right. Discard asks first, and its question is where "start over"
 * lives, so there is one destructive button on the screen rather than two.
 *
 * All the state is [VoiceRecorder]'s, not this widget's: a recording carries
 * on when the user leaves the note, the app or the screen, and finds this
 * widget again exactly as it was.
 */
@Composable
fun AudioRecorderWidget(
    noteId: String,
    modifier: Modifier = Modifier,
    /**
     * Start recording as soon as this appears, if Settings → Recorder says
     * so. True whenever the user asked for a recorder: a new voice note, or
     * the Add menu inside a note that already exists. Somebody who has
     * turned that setting on asked for the recording to begin, not for a
     * button to press.
     */
    autoStart: Boolean = true,
    /** Called when the user threw the recording away. */
    onDiscarded: () -> Unit = {},
) {
    val context = LocalContext.current
    val state by VoiceRecorder.state.collectAsState()
    val elapsed by VoiceRecorder.elapsedSeconds.collectAsState()
    val waveform by VoiceRecorder.waveform.collectAsState()
    val inCall by VoiceRecorder.inCall.collectAsState()
    val error by VoiceRecorder.error.collectAsState()
    var showDiscardOptions by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) VoiceRecorder.record(context, noteId) }

    fun recordOrPause() {
        when (state) {
            RecordingState.Recording -> VoiceRecorder.pause()
            RecordingState.Idle, RecordingState.Paused, RecordingState.Saved -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
                ) {
                    VoiceRecorder.record(context, noteId)
                } else {
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            }
            RecordingState.Saving -> {}
        }
    }

    // Nothing of the last recording may be left on screen: without this the
    // recorder opened showing the end of the previous recording, and its
    // button did nothing because the recorder was still in the Saved state.
    // A recording in progress is never touched: it may have been started
    // here and left running on purpose.
    LaunchedEffect(noteId) {
        VoiceRecorder.reset(context)
        if (autoStart && VoiceRecorder.state.value == RecordingState.Idle &&
            RecorderPreferences(context).startRecordingImmediately
        ) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED
            ) {
                VoiceRecorder.record(context, noteId)
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    val hasAudio = state == RecordingState.Recording || state == RecordingState.Paused

    if (showDiscardOptions) {
        AlertDialog(
            onDismissRequest = { showDiscardOptions = false },
            title = { Text("Discard this Recording?") },
            text = {
                Text(
                    "Discard throws the recording away and leaves the note without it. " +
                        "Restart throws it away and begins recording again from 00:00:00."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardOptions = false
                    VoiceRecorder.trash(context)
                    onDiscarded()
                }) { Text("Discard") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        showDiscardOptions = false
                        VoiceRecorder.restart(context)
                        // Start again directly rather than through the
                        // Record/Pause button: `state` here is the value
                        // this composition was drawn with, still saying
                        // "Recording", so that route would pause a recorder
                        // that had just been emptied.
                        VoiceRecorder.record(context, noteId)
                    }) { Text("Restart") }
                    TextButton(onClick = { showDiscardOptions = false }) { Text("Cancel") }
                }
            }
        )
    }

    error?.let { message ->
        AlertDialog(
            onDismissRequest = { VoiceRecorder.clearError() },
            title = { Text("Recording") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { VoiceRecorder.clearError() }) { Text("OK") } }
        )
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = formatElapsed(elapsed),
                style = MaterialTheme.typography.displaySmall,
                color = if (state == RecordingState.Recording) RecordRed else MaterialTheme.colorScheme.onSurface
            )

            RecordingWaveform(
                samples = waveform,
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Transport buttons keep their sides in a right-to-left layout:
            // Discard on the left, Record in the middle, Save on the right,
            // the way the buttons of a tape recorder stay where they are.
            // Mirroring them would put Save under the thumb that was
            // reaching for Discard.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Discard, and inside it the choice to start over
                    IconButton(
                        onClick = { if (hasAudio) showDiscardOptions = true else { VoiceRecorder.trash(context); onDiscarded() } },
                        enabled = state != RecordingState.Saving,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Discard",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    Surface(
                        shape = CircleShape,
                        color = RecordRed,
                        modifier = Modifier.size(72.dp)
                    ) {
                        IconButton(
                            onClick = { recordOrPause() },
                            enabled = state != RecordingState.Saving,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (state == RecordingState.Recording) Icons.Filled.Pause else Icons.Filled.Mic,
                                contentDescription = if (state == RecordingState.Recording) "Pause" else "Record",
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(24.dp))

                    IconButton(
                        onClick = { VoiceRecorder.save(context) },
                        enabled = hasAudio,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Save",
                            tint = if (hasAudio) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = statusLine(context, state, inCall),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun statusLine(
    context: android.content.Context,
    state: RecordingState,
    inCall: Boolean
): String = when (state) {
    RecordingState.Idle ->
        "Press the red button to start · ${VoiceRecorder.micName(context)} · " +
            RecorderPreferences.formatTitle(RecorderPreferences(context).recordingFormat)
    RecordingState.Recording ->
        if (inCall) "A call has the microphone, so this stretch is silent"
        else "Recording · ${VoiceRecorder.micName(context)}"
    RecordingState.Paused ->
        if (inCall) "A call is in progress; recording carries on when it ends" else "Paused"
    RecordingState.Saving -> "Saving…"
    RecordingState.Saved -> "Saved"
}

/** Live level bars, newest on the right. */
@Composable
private fun RecordingWaveform(samples: List<Float>, modifier: Modifier = Modifier) {
    val color = RecordRed
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
            drawLine(
                color,
                Offset(x, mid - half),
                Offset(x, mid + half),
                strokeWidth = slotWidth * 0.6f,
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * Where the recorder belongs: shown in this note, or reported as busy in
 * another one.
 *
 * There is one recorder in the application, so two notes cannot both have
 * one. Showing the controls in a note that is not the one being recorded
 * into would be a lie: the time and the waveform would belong to the other
 * note, and Save would file the recording there while the user was looking
 * at this one.
 *
 * @param noteId the note being looked at
 * @param recordingNoteId the note the recorder is working on, if any
 * @param recording whether the recorder is doing anything at all
 * @param asked whether the user asked for a recorder in this note (the New
 *   button, or the microphone in the note's toolbar)
 */
data class RecorderPlacement(
    /** Show the recorder controls in this note. */
    val show: Boolean,
    /** Say that a recording is already running somewhere else. */
    val busyElsewhere: Boolean,
)

fun recorderPlacement(
    noteId: String,
    recordingNoteId: String?,
    recording: Boolean,
    asked: Boolean,
): RecorderPlacement {
    val busyElsewhere = recording && recordingNoteId != null && recordingNoteId != noteId
    val show = !busyElsewhere && (asked || (recording && recordingNoteId == noteId))
    return RecorderPlacement(show = show, busyElsewhere = busyElsewhere)
}

/** hh:mm:ss, the way a recorder counts. */
fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return String.format(java.util.Locale.US, "%02d:%02d:%02d", h, m, s)
}
