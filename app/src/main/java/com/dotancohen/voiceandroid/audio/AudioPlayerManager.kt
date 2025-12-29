package com.dotancohen.voiceandroid.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Playback state for the audio player.
 */
data class PlaybackState(
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val currentFileIndex: Int = -1,
    val playbackSpeed: Float = 1.0f
)

/**
 * Manages audio playback using ExoPlayer.
 *
 * Supports:
 * - Playing a list of audio files
 * - Auto-advancement to next file
 * - Seeking via position or waveform tap
 * - Skip back 3s and 10s
 * - Playback speed control (placeholder for now)
 */
class AudioPlayerManager(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var audioFiles: List<String> = emptyList()
    private var currentIndex: Int = -1

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState { copy(isPlaying = isPlaying) }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> {
                        // Auto-play next file
                        if (currentIndex < audioFiles.size - 1) {
                            playFile(currentIndex + 1)
                        } else {
                            updateState { copy(isPlaying = false) }
                        }
                    }
                    Player.STATE_READY -> {
                        updateState { copy(duration = player.duration.coerceAtLeast(0L)) }
                    }
                }
            }
        })
    }

    /**
     * Set the list of audio files to play.
     *
     * @param files List of file paths
     */
    fun setAudioFiles(files: List<String>) {
        audioFiles = files.filter { File(it).exists() }
        currentIndex = -1
        updateState {
            PlaybackState(
                currentFileIndex = -1,
                duration = 0L,
                currentPosition = 0L
            )
        }
    }

    /**
     * Play a specific file by index.
     *
     * @param index Index in the audio files list
     */
    @OptIn(UnstableApi::class)
    fun playFile(index: Int) {
        if (index < 0 || index >= audioFiles.size) return

        currentIndex = index
        val filePath = audioFiles[index]

        player.stop()
        player.clearMediaItems()
        player.setMediaItem(MediaItem.fromUri("file://$filePath"))
        player.prepare()
        player.play()

        updateState {
            copy(
                currentFileIndex = index,
                currentPosition = 0L,
                isPlaying = true
            )
        }
    }

    /**
     * Toggle play/pause.
     */
    fun togglePlayPause() {
        if (currentIndex < 0 && audioFiles.isNotEmpty()) {
            // Start playing first file
            playFile(0)
        } else if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }

    /**
     * Seek to a specific position in milliseconds.
     */
    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0L, player.duration))
        updateState { copy(currentPosition = player.currentPosition) }
    }

    /**
     * Seek to a fraction of the duration (0.0 to 1.0).
     * Used for waveform tap seeking.
     */
    fun seekToFraction(fraction: Float) {
        val position = (fraction * player.duration).toLong()
        seekTo(position)
    }

    /**
     * Skip back by the specified number of seconds.
     */
    fun skipBack(seconds: Int) {
        val newPosition = (player.currentPosition - seconds * 1000).coerceAtLeast(0L)
        seekTo(newPosition)
    }

    /**
     * Get current playback position. Call periodically to update UI.
     */
    fun updatePosition() {
        if (player.isPlaying || player.currentPosition > 0) {
            updateState { copy(currentPosition = player.currentPosition) }
        }
    }

    /**
     * Set playback speed (placeholder - not functional yet).
     */
    fun setPlaybackSpeed(speed: Float) {
        // TODO: Implement playback speed control
        // player.setPlaybackSpeed(speed)
        updateState { copy(playbackSpeed = speed) }
    }

    /**
     * Release player resources. Call when done.
     */
    fun release() {
        player.release()
    }

    private inline fun updateState(update: PlaybackState.() -> PlaybackState) {
        _playbackState.value = _playbackState.value.update()
    }
}
