package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Replay5
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.filled.Transcribe
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextAlign
import com.dotancohen.voiceandroid.audio.LargeRecording
import java.io.File
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.audio.AudioPlayerManager
import com.dotancohen.voiceandroid.audio.PlaybackState
import com.dotancohen.voiceandroid.audio.WaveformExtractor
import com.dotancohen.voiceandroid.ui.scaledIcon
import com.dotancohen.voiceandroid.util.Durations
import com.dotancohen.voiceandroid.viewmodel.indexOfPrimaryAudioFile
import com.dotancohen.voiceandroid.ui.theme.StarGold
import com.dotancohen.voiceandroid.data.AudioFile
import kotlinx.coroutines.delay

/**
 * Audio player widget with waveform visualization and playback controls.
 *
 * Features:
 * - Waveform display that doubles as a seek bar
 * - Play/pause button
 * - Skip back 3s and 10s buttons
 * - Playback speed slider and presets under the waveform
 * - Time display (MM:SS or HH:MM:SS for long files)
 * - List of audio files with selection highlighting and a transcribe icon each
 */
@Composable
fun AudioPlayerWidget(
    audioFiles: List<AudioFile>,
    getFilePath: suspend (String) -> String?,
    modifier: Modifier = Modifier,
    /** Shown as a transcribe icon at the left of every file; null hides it. */
    onTranscribe: ((AudioFile) -> Unit)? = null,
    /** Index of the file the player is on (0 before anything was played). */
    onCurrentFileChanged: ((Int) -> Unit)? = null,
    /** Recordings whose transcription was asked for and has not arrived. */
    pendingTranscriptionIds: Set<String> = emptySet(),
    /**
     * Which recording stands for the note: played first, and played by
     * itself when the note is opened and the setting says so. Null means the
     * first one in the list.
     */
    primaryAudioFileId: String? = null,
    /** Start playing that recording as soon as the note is open. */
    autoPlay: Boolean = false,
    /** Mark one of the recordings as the one that stands for the note. */
    onSetPrimary: ((AudioFile) -> Unit)? = null,
    /**
     * Which note these recordings are in, and its first line — for the
     * notification drawer, which says what is playing and opens that note
     * when it is tapped.
     */
    noteId: String? = null,
    noteLine: String? = null,
) {
    val context = LocalContext.current

    // The one player of the application: a recording started in the notes
    // list is still playing when this screen opens, and must not be cut off.
    val playerManager = remember { AudioPlayerManager.shared(context) }
    val waveformExtractor = remember { WaveformExtractor(context) }

    // State
    val playbackState by playerManager.playbackState.collectAsState()
    var waveforms by remember { mutableStateOf<Map<Int, List<Float>>>(emptyMap()) }
    var filePaths by remember { mutableStateOf<List<String>>(emptyList()) }

    // Load file paths and set up player
    LaunchedEffect(audioFiles) {
        val paths = audioFiles.mapNotNull { audioFile ->
            getFilePath(audioFile.id)
        }
        filePaths = paths
        // Say what each recording is called before any of them plays, so the
        // notification drawer names it rather than its id.
        audioFiles.forEachIndexed { index, audioFile ->
            paths.getOrNull(index)?.let { path ->
                playerManager.describe(path, audioFile.filename, noteId, audioFile.id, noteLine)
            }
        }
        // If one of these recordings is already playing — started in the
        // notes list — it carries on from where it is. Nothing else here
        // interrupts it, autoplay included: the user is already listening.
        val carriedOver = playerManager.adoptAudioFiles(paths)
        if (!carriedOver && autoPlay && paths.isNotEmpty()) {
            // Straight to the recording that stands for the note, which is
            // the first one unless the user chose another.
            val index = indexOfPrimaryAudioFile(audioFiles, primaryAudioFileId)
            playerManager.playFile(index)
        }
    }

    /** Recordings whose waveform would cost real work, by their index here. */
    var askBeforeDrawing by remember(filePaths) { mutableStateOf(setOf<Int>()) }
    var drawingNow by remember(filePaths) { mutableStateOf<Int?>(null) }

    // The waveforms: drawn at once for ordinary recordings, shown from the
    // cache when they were drawn before, and offered as a button for a long
    // recording, where decoding it is minutes of work for a picture in which
    // each bar is several minutes of audio.
    LaunchedEffect(filePaths) {
        filePaths.forEachIndexed { index, path ->
            if (waveforms.containsKey(index)) return@forEachIndexed
            waveformExtractor.cachedWaveform(path)?.let {
                waveforms = waveforms + (index to it)
                return@forEachIndexed
            }
            // The duration is not known for every recording until its player
            // has prepared it, so the size decides here; the compact player in
            // the list, which has a prepared player, uses both.
            if (LargeRecording.isLarge(null, File(path).length())) {
                askBeforeDrawing = askBeforeDrawing + index
            } else {
                waveforms = waveforms + (index to waveformExtractor.extractWaveform(path))
            }
        }
    }

    suspend fun drawWaveform(index: Int) {
        val path = filePaths.getOrNull(index) ?: return
        drawingNow = index
        askBeforeDrawing = askBeforeDrawing - index
        waveforms = waveforms + (index to waveformExtractor.extractWaveform(path))
        drawingNow = null
    }

    // Update playback position periodically
    LaunchedEffect(playbackState.isPlaying) {
        while (playbackState.isPlaying) {
            playerManager.updatePosition()
            delay(100)
        }
    }

    // Tell the screen which file is current, so it can show that file's transcriptions
    LaunchedEffect(playbackState.currentFileIndex) {
        onCurrentFileChanged?.invoke(playbackState.currentFileIndex.coerceAtLeast(0))
    }

    // Cleanup
    DisposableEffect(Unit) {
        onDispose {
            playerManager.release()
        }
    }

    if (audioFiles.isEmpty()) {
        return
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            // Waveform display
            val shownIndex = playbackState.currentFileIndex.coerceAtLeast(0)
            val currentWaveform = waveforms[shownIndex] ?: emptyList()
            if (shownIndex in askBeforeDrawing) {
                val scope = rememberCoroutineScope()
                TextButton(
                    onClick = { scope.launch { drawWaveform(shownIndex) } },
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Text(
                        text = LargeRecording.GENERATE_WAVEFORM_PROMPT,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center
                    )
                }
            } else if (drawingNow == shownIndex) {
                Box(
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Drawing the waveform…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else WaveformView(
                waveform = currentWaveform,
                progress = if (playbackState.duration > 0) {
                    playbackState.currentPosition.toFloat() / playbackState.duration
                } else 0f,
                onSeek = { fraction ->
                    playerManager.seekToFraction(fraction)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            )

            // Playback speed, right under the waveform
            PlaybackSpeedControl(
                speed = playbackState.playbackSpeed,
                onSpeedChange = { playerManager.setPlaybackSpeed(it) }
            )

            // Position, controls and length on one row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(playbackState.currentPosition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                // Skip back 10s
                IconButton(onClick = { playerManager.skipBack(10) }) {
                    Icon(
                        imageVector = Icons.Filled.Replay10,
                        contentDescription = "Skip back 10 seconds",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                // Skip back 3s (using Replay5 icon as closest available)
                IconButton(onClick = { playerManager.skipBack(3) }) {
                    Icon(
                        imageVector = Icons.Filled.Replay5,
                        contentDescription = "Skip back 3 seconds",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Play/Pause button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(
                        onClick = { playerManager.togglePlayPause() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) {
                                Icons.Filled.Pause
                            } else {
                                Icons.Filled.PlayArrow
                            },
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))
                // Same width as the two skip buttons, so play stays centred
                Spacer(modifier = Modifier.width(96.dp))
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = formatTime(playbackState.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // The files: transcribe icon, play state, name.
            //
            // A plain Column, not a list with a height of its own: a control
            // must never scroll inside itself. Every recording of the note is
            // drawn, and the screen it sits on does the scrolling, so nothing
            // is hidden behind an edge the user cannot see.
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                audioFiles.forEachIndexed { index, audioFile ->
                    AudioFileListItem(
                        audioFile = audioFile,
                        isSelected = index == playbackState.currentFileIndex || (playbackState.currentFileIndex < 0 && index == 0),
                        isPlaying = index == playbackState.currentFileIndex && playbackState.isPlaying,
                        onClick = { playerManager.playFile(index) },
                        onTranscribe = onTranscribe?.let { cb -> { cb(audioFile) } },
                        transcriptionPending = audioFile.id in pendingTranscriptionIds,
                        isPrimary = audioFile.id == primaryAudioFileId,
                        onSetPrimary = onSetPrimary?.let { cb -> { cb(audioFile) } }
                    )
                }
            }
        }
    }
}

/**
 * Waveform visualization that also serves as a seek bar.
 */
@Composable
fun WaveformView(
    waveform: List<Float>,
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val playedColor = primaryColor
    val unplayedColor = surfaceVariantColor

    Box(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surface,
                shape = MaterialTheme.shapes.small
            )
            // Tap to seek, and drag to scrub: the position follows the
            // finger while it moves, so a passage can be found by ear
            // without lifting and trying again.
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        onSeek((offset.x / size.width).coerceIn(0f, 1f))
                    }
                ) { change, _ ->
                    change.consume()
                    onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val barCount = waveform.size.coerceAtLeast(1)
            val barWidth = size.width / barCount
            val barGap = 1.dp.toPx()
            val actualBarWidth = (barWidth - barGap).coerceAtLeast(1f)
            val maxBarHeight = size.height * 0.9f
            val centerY = size.height / 2

            if (waveform.isEmpty()) {
                // Show placeholder bars
                for (i in 0 until 150) {
                    val x = i * barWidth
                    val amplitude = 0.1f + (i % 10) * 0.05f
                    val barHeight = amplitude * maxBarHeight

                    drawRect(
                        color = surfaceVariantColor,
                        topLeft = Offset(x, centerY - barHeight / 2),
                        size = Size(actualBarWidth, barHeight)
                    )
                }
            } else {
                waveform.forEachIndexed { index, amplitude ->
                    val x = index * barWidth
                    val barHeight = (amplitude * maxBarHeight).coerceAtLeast(2f)
                    val barProgress = index.toFloat() / barCount

                    val color = if (barProgress <= progress) playedColor else unplayedColor

                    drawRect(
                        color = color,
                        topLeft = Offset(x, centerY - barHeight / 2),
                        size = Size(actualBarWidth, barHeight)
                    )
                }
            }

            // Draw playhead
            if (progress > 0 && waveform.isNotEmpty()) {
                val playheadX = progress * size.width
                drawLine(
                    color = primaryColor,
                    start = Offset(playheadX, 0f),
                    end = Offset(playheadX, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
    }
}

/**
 * List item for an audio file.
 */
@Composable
fun AudioFileListItem(
    audioFile: AudioFile,
    isSelected: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onTranscribe: (() -> Unit)? = null,
    /** A transcription of this recording was asked for and has not arrived. */
    transcriptionPending: Boolean = false,
    /** This is the recording that stands for the note. */
    isPrimary: Boolean = false,
    /** Make this the recording that stands for the note. */
    onSetPrimary: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small
    ) {
        Row(
            modifier = Modifier.padding(start = 2.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onTranscribe != null) {
                // The mark, and the button around it, both follow the
                // icon-size setting: a larger mark in a button of the old
                // size would be cut off by it.
                val markSize = scaledIcon(18.dp)
                IconButton(onClick = onTranscribe, modifier = Modifier.size(scaledIcon(32.dp))) {
                    if (transcriptionPending) {
                        // Already being worked on: the mark with a clock over
                        // it. The button still works, since asking for a
                        // second transcription is allowed.
                        PendingTranscriptionIcon(
                            size = markSize,
                            tint = MaterialTheme.colorScheme.primary,
                            background = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surface
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Transcribe,
                            contentDescription = "Transcribe ${audioFile.filename}",
                            modifier = Modifier.size(markSize),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            // The star marks the recording that stands for the note: the one
            // played when the note is opened, and the one whose
            // transcription the notes list shows.
            if (onSetPrimary != null) {
                IconButton(onClick = onSetPrimary, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (isPrimary) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (isPrimary) {
                            "The main Recording of this Note"
                        } else {
                            "Make this the main Recording"
                        },
                        modifier = Modifier.size(16.dp),
                        tint = if (isPrimary) StarGold else MaterialTheme.colorScheme.outline
                    )
                }
            }
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = audioFile.filename,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isSelected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

/** Format milliseconds to MM:SS, or HH:MM:SS for files over an hour. */
private fun formatTime(millis: Long): String = Durations.ofMillis(millis)
