package com.dotancohen.voiceandroid.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Which recording is being listened to, for the notification drawer.
 *
 * The file on disk is named after its id, so its name says nothing; the title
 * is what the recording was called when it arrived, and the note is where the
 * user reads along with it. Both are supplied by whichever screen handed the
 * player its files ([AudioPlayerManager.describe]).
 */
data class NowPlaying(
    val title: String,
    val noteId: String? = null,
    val audioFileId: String? = null,
    /** The first line of the note, to say which note this is. */
    val noteLine: String? = null,
)

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
 *
 * There is normally **one** of these, [AudioPlayerManager.shared], because
 * there is one pair of ears: a recording started in the notes list goes on
 * playing when the note is opened, and opening the tag screen does not stop
 * it. A screen that made its own player would take the sound with it when it
 * was left.
 */
class AudioPlayerManager(context: Context) {

    private val appContext = context.applicationContext
    private val player: ExoPlayer = buildPlayer(context)
    private val prefs = PlaybackPreferences(context)

    private val _playbackState = MutableStateFlow(PlaybackState(playbackSpeed = prefs.speed))
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    /** What is playing, for the notification drawer; null when nothing is. */
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** What each file is called and where it belongs, by its path. */
    private val described = mutableMapOf<String, NowPlaying>()

    private var audioFiles: List<String> = emptyList()
    private var currentIndex: Int = -1

    /**
     * Say what a file is called and which note it belongs to.
     *
     * Called by the screen that hands the player its files, because only that
     * screen knows: the file on disk is named after its id. Without it the
     * notification can only say "Playing a recording".
     */
    fun describe(path: String, title: String, noteId: String? = null, audioFileId: String? = null, noteLine: String? = null) {
        described[path] = NowPlaying(title, noteId, audioFileId, noteLine)
        if (path == currentFile) publishNowPlaying()
    }

    /**
     * Put up, refresh or take down the notification of what is playing.
     *
     * The notification is a foreground service, which is what stops Android
     * from killing the process in the middle of a recording that is being
     * listened to with the screen off.
     */
    private fun publishNowPlaying() {
        val path = currentFile
        if (path == null) {
            _nowPlaying.value = null
            PlaybackService.stop(appContext)
            return
        }
        _nowPlaying.value = described[path]
            ?: NowPlaying(title = File(path).nameWithoutExtension)
        PlaybackService.start(appContext)
    }

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
                            // The last recording has played out: nothing is
                            // playing, so the notification comes down.
                            updateState { copy(isPlaying = false) }
                            currentIndex = -1
                            publishNowPlaying()
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
        _nowPlaying.value = null
        updateState {
            PlaybackState(
                currentFileIndex = -1,
                duration = 0L,
                currentPosition = 0L,
                playbackSpeed = playbackSpeed
            )
        }
    }

    /** The file playing now, or null. */
    val currentFile: String? get() = audioFiles.getOrNull(currentIndex)

    /**
     * Hand this player a new list of files without interrupting it.
     *
     * If the file playing now is one of them it keeps playing, exactly where
     * it is, and only its place in the list is updated. That is what lets a
     * recording started in the notes list carry on when the note is opened.
     * Otherwise the list is replaced as usual.
     *
     * Returns true when playback was carried over.
     */
    fun adoptAudioFiles(files: List<String>): Boolean {
        val playing = currentFile
        val existing = files.filter { File(it).exists() }
        val index = existing.indexOf(playing)
        if (playing == null || index < 0) {
            setAudioFiles(files)
            return false
        }
        audioFiles = existing
        currentIndex = index
        updateState {
            copy(
                currentFileIndex = index,
                currentPosition = player.currentPosition,
                duration = player.duration.coerceAtLeast(0L),
                isPlaying = player.isPlaying
            )
        }
        publishNowPlaying()
        return true
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
        publishNowPlaying()
    }

    /**
     * Stop playing, wherever it had reached.
     *
     * Used when something else must have the sound: a recording about to
     * start, above all, since a player left running is recorded into it.
     */
    fun pause() {
        if (player.isPlaying) player.pause()
        updateState { copy(isPlaying = false) }
        // The notification stays: a paused recording is resumed from it.
    }

    /**
     * Stop playing and let the recording go.
     *
     * What the Stop button on the notification does, and it is a different
     * thing from [pause]: the position is given up, the notification comes
     * down, and the foreground service ends. Playing again starts the
     * recording from its beginning.
     */
    fun stopPlayback() {
        player.stop()
        player.seekTo(0)
        currentIndex = -1
        updateState { copy(isPlaying = false, currentPosition = 0L, currentFileIndex = -1) }
        publishNowPlaying()
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
            // A recording that has played to its end is at its end: pressing
            // Play there means "again", not "carry on from nowhere".
            if (player.playbackState == Player.STATE_ENDED) {
                player.seekTo(0)
            }
            player.play()
            publishNowPlaying()
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
     *
     * The shared player is never released: it belongs to the application, not
     * to a screen, and releasing it would stop a recording that is still
     * being listened to.
     */
    fun release() {
        if (this === shared) return
        player.release()
    }

    companion object {
        @Volatile private var instance: AudioPlayerManager? = null

        /**
         * The one player of the application.
         *
         * Made on first use and kept for the life of the process, so that
         * playback survives moving between screens. One player also means one
         * sound: starting a recording in a note stops whatever the list was
         * playing, rather than the two talking over each other.
         */
        fun shared(context: Context): AudioPlayerManager =
            instance ?: synchronized(this) {
                instance ?: AudioPlayerManager(context.applicationContext).also { instance = it }
            }

        private val shared: AudioPlayerManager? get() = instance
    }

    private inline fun updateState(update: PlaybackState.() -> PlaybackState) {
        _playbackState.value = _playbackState.value.update()
    }
}

/**
 * The player, able to play every common audio format: the phone's own
 * decoders first, and FFmpeg's for what the phone has no decoder for
 * (EXTENSION_RENDERER_MODE_ON), such as MP2 or AC-3 on many phones.
 */
@OptIn(UnstableApi::class)
private fun buildPlayer(context: Context): ExoPlayer =
    ExoPlayer.Builder(
        context,
        DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON),
    ).build()
