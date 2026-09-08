package com.dotancohen.voiceandroid.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
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
 * - Playback speed (0.5× to 3×, pitch kept), remembered across players
 */
class AudioPlayerManager(context: Context) {

    private val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val prefs = PlaybackPreferences(context)

    private val _playbackState = MutableStateFlow(PlaybackState(playbackSpeed = prefs.speed))
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private var audioFiles: List<String> = emptyList()
    private var currentIndex: Int = -1

    init {
        player.playbackParameters = PlaybackParameters(prefs.speed, 1f)
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
                currentPosition = 0L,
                playbackSpeed = playbackSpeed
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
        // Until the media is prepared ExoPlayer reports C.TIME_UNSET (a huge
        // negative number) as the duration; clamping to it throws. A seek
        // before anything is loaded is simply ignored.
        val duration = player.duration
        if (duration <= 0L) return
        player.seekTo(positionMs.coerceIn(0L, duration))
        updateState { copy(currentPosition = player.currentPosition) }
    }

    /**
     * Seek to a fraction of the duration (0.0 to 1.0).
     * Used for waveform tap seeking. Does nothing before media is loaded.
     */
    fun seekToFraction(fraction: Float) {
        val duration = player.duration
        if (duration <= 0L) return
        val position = (fraction.coerceIn(0f, 1f) * duration).toLong()
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
     * Set the playback speed. ExoPlayer time-stretches the audio in its own
     * pipeline while the pitch stays the same, so the change is applied
     * mid-playback without any restart, gap or crackle. The value is kept in
     * the preferences so the next player starts at the same speed.
     */
    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(PlaybackPreferences.MIN_SPEED, PlaybackPreferences.MAX_SPEED)
        player.playbackParameters = PlaybackParameters(clamped, 1f)
        prefs.speed = clamped
        updateState { copy(playbackSpeed = clamped) }
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
