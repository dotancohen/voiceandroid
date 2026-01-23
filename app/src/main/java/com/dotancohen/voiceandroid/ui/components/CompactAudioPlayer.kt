package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay5
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.audio.AudioPlayerManager
import com.dotancohen.voiceandroid.audio.WaveformExtractor
import kotlinx.coroutines.delay

/**
 * Compact audio player for use in list items.
 *
 * Features:
 * - Compact waveform display that doubles as a seek bar
 * - Play/pause button
 * - Skip back 3s button
 * - Time display
 *
 * @param filePath Path to the audio file
 * @param modifier Modifier for the component
 * @param onPlaybackStarted Optional callback when playback starts (useful for stopping other players)
 * @param playerManager Optional shared player manager (for global playback control)
 */
@Composable
fun CompactAudioPlayer(
    filePath: String?,
    modifier: Modifier = Modifier,
    onPlaybackStarted: (() -> Unit)? = null,
    playerManager: AudioPlayerManager? = null
) {
    val context = LocalContext.current

    // Use provided player manager or create a local one
    val localPlayerManager = remember { playerManager ?: AudioPlayerManager(context) }
    val isLocalPlayer = playerManager == null
    val waveformExtractor = remember { WaveformExtractor(context) }

    // State
    val playbackState by localPlayerManager.playbackState.collectAsState()
    var waveform by remember { mutableStateOf<List<Float>>(emptyList()) }
    var isPlayerSetUp by remember { mutableStateOf(false) }

    // Set up player with the file and auto-play
    LaunchedEffect(filePath) {
        if (filePath != null) {
            localPlayerManager.setAudioFiles(listOf(filePath))
            isPlayerSetUp = true
            // Auto-play when unfolded
            onPlaybackStarted?.invoke()
            localPlayerManager.playFile(0)
        }
    }

    // Extract waveform
    LaunchedEffect(filePath) {
        if (filePath != null) {
            waveform = waveformExtractor.extractWaveform(filePath)
        }
    }

    // Update playback position periodically
    LaunchedEffect(playbackState.isPlaying) {
        while (playbackState.isPlaying) {
            localPlayerManager.updatePosition()
            delay(100)
        }
    }

    // Cleanup only if we created the player locally
    DisposableEffect(Unit) {
        onDispose {
            if (isLocalPlayer) {
                localPlayerManager.release()
            }
        }
    }

    if (filePath == null) {
        Text(
            text = "Audio file not available",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        // Waveform display
        CompactWaveformView(
            waveform = waveform,
            progress = if (playbackState.duration > 0) {
                playbackState.currentPosition.toFloat() / playbackState.duration
            } else 0f,
            onSeek = { fraction ->
                if (!isPlayerSetUp) return@CompactWaveformView
                // Start playing if not already playing
                if (playbackState.currentFileIndex < 0) {
                    onPlaybackStarted?.invoke()
                    localPlayerManager.playFile(0)
                }
                localPlayerManager.seekToFraction(fraction)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        )

        // Controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Time display
            Text(
                text = "${formatCompactTime(playbackState.currentPosition)} / ${formatCompactTime(playbackState.duration)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Right: Controls
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Skip back 3s
                IconButton(
                    onClick = { localPlayerManager.skipBack(3) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay5,
                        contentDescription = "Skip back 3 seconds",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.width(4.dp))

                // Play/Pause button
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (playbackState.currentFileIndex < 0 && isPlayerSetUp) {
                                onPlaybackStarted?.invoke()
                            }
                            localPlayerManager.togglePlayPause()
                        },
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
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Compact waveform visualization for the mini player.
 */
@Composable
private fun CompactWaveformView(
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
            val maxBarHeight = size.height * 0.85f
            val centerY = size.height / 2

            if (waveform.isEmpty()) {
                // Show placeholder bars
                for (i in 0 until 100) {
                    val x = i * (size.width / 100)
                    val amplitude = 0.1f + (i % 8) * 0.04f
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
 * Format milliseconds to MM:SS or H:MM:SS for files over 60 minutes.
 */
private fun formatCompactTime(millis: Long): String {
    if (millis <= 0) return "0:00"

    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}
