package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import com.dotancohen.voiceandroid.data.AudioFile
import kotlinx.coroutines.delay

/**
 * Audio player widget with waveform visualization and playback controls.
 *
 * Features:
 * - Waveform display that doubles as a seek bar
 * - Play/pause button
 * - Skip back 3s and 10s buttons
 * - Speed control button (placeholder)
 * - Time display (MM:SS or HH:MM:SS for long files)
 * - List of audio files with selection highlighting
 */
@Composable
fun AudioPlayerWidget(
    audioFiles: List<AudioFile>,
    getFilePath: suspend (String) -> String?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Initialize managers
    val playerManager = remember { AudioPlayerManager(context) }
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
        playerManager.setAudioFiles(paths)
    }

    // Extract waveforms for all files
    LaunchedEffect(filePaths) {
        filePaths.forEachIndexed { index, path ->
            if (!waveforms.containsKey(index)) {
                val waveform = waveformExtractor.extractWaveform(path)
                waveforms = waveforms + (index to waveform)
            }
        }
    }

    // Update playback position periodically
    LaunchedEffect(playbackState.isPlaying) {
        while (playbackState.isPlaying) {
            playerManager.updatePosition()
            delay(100)
        }
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
            modifier = Modifier.padding(16.dp)
        ) {
            // Waveform display
            val currentWaveform = waveforms[playbackState.currentFileIndex] ?: emptyList()
            WaveformView(
                waveform = currentWaveform,
                progress = if (playbackState.duration > 0) {
                    playbackState.currentPosition.toFloat() / playbackState.duration
                } else 0f,
                onSeek = { fraction ->
                    playerManager.seekToFraction(fraction)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(80.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Time display
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(playbackState.currentPosition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(playbackState.duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Playback controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
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

                Spacer(modifier = Modifier.width(16.dp))

                // Play/Pause button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp)
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
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Speed control (placeholder)
                IconButton(onClick = { /* TODO: Show speed options */ }) {
                    Icon(
                        imageVector = Icons.Filled.Speed,
                        contentDescription = "Playback speed (not yet implemented)",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Empty spacer to balance layout
                Spacer(modifier = Modifier.width(48.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Audio file list
            Text(
                text = "Audio Files",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.height((audioFiles.size * 48).coerceAtMost(200).dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(audioFiles) { index, audioFile ->
                    AudioFileListItem(
                        audioFile = audioFile,
                        isSelected = index == playbackState.currentFileIndex,
                        isPlaying = index == playbackState.currentFileIndex && playbackState.isPlaying,
                        onClick = { playerManager.playFile(index) }
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
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                    onSeek(fraction)
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
    onClick: () -> Unit
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
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = audioFile.filename,
                style = MaterialTheme.typography.bodyMedium,
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

/**
 * Format milliseconds to MM:SS or HH:MM:SS for files over 60 minutes.
 */
private fun formatTime(millis: Long): String {
    if (millis <= 0) return "00:00"

    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
