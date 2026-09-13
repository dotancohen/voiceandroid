package com.dotancohen.voiceandroid.audio

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import com.dotancohen.voiceandroid.BuildConfig
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.transcription.OnDeviceTranscriber
import com.dotancohen.voiceandroid.util.AppLogger
import com.dotancohen.voiceandroid.util.Magic
import com.dotancohen.voiceandroid.util.CriticalLog
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the recorder is doing. */
enum class RecordingState { Idle, Recording, Paused, Saving, Saved }

/**
 * Whether the phone is on a call, from Android's audio mode.
 *
 * During a call the microphone belongs to the telephone, so what is recorded
 * is silence. `MODE_IN_CALL` is a telephone call and `MODE_IN_COMMUNICATION`
 * is one made by an application (WhatsApp, a video call); both take the
 * microphone away, so both count.
 */
fun isCallMode(audioMode: Int): Boolean =
    audioMode == AudioManager.MODE_IN_CALL || audioMode == AudioManager.MODE_IN_COMMUNICATION

/**
 * Whether a recording running now should pause because of a call, which is
 * the user's choice under Settings → Recorder.
 */
fun shouldPauseForCall(inCall: Boolean, duringCall: String): Boolean =
    inCall && duringCall == RecorderPreferences.CALL_PAUSE

/**
 * The one recorder in the application.
 *
 * It lives outside any screen on purpose: leaving the app, going to the home
 * screen or locking the phone must not stop a recording, which is what every
 * other recorder does. [RecordingService] keeps the process alive and shows a
 * notification while this is running; the note screen only watches these
 * flows and presses the buttons.
 *
 * A recording is always made *into* a note ([noteId]), which exists before
 * the recording starts. Saving attaches the file to that note. Because this
 * object outlives every screen, it also has to be put back to a clean state
 * before a new recording is offered ([reset]); otherwise the screen opens
 * showing the end of the last one.
 *
 * Formats: Opus (.ogg) or AAC (.m4a) through [MediaRecorder], or 16 kHz WAV
 * through [WavRecorder]. Saving creates the note and the audio file record
 * with the same call the importer uses.
 */
object VoiceRecorder {
    private const val TAG = "VoiceRecorder"
    private const val WAVEFORM_SAMPLES = Magic.RECORDER_WAVEFORM_SAMPLES

    /**
     * Where the recorder's own work runs.
     *
     * With a handler, because an uncaught throw inside a coroutine is not an
     * error message on Android: it is the end of the process. A microphone
     * that cannot be read, a telephone call at the wrong moment, a file that
     * vanished — none of those is worth losing the recording in progress and
     * everything else with it.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            AppLogger.e(TAG, "The recorder hit something it could not handle", e)
            _error.value = "The recorder stopped: ${e.message ?: e.toString()}"
        }
    )

    private val _state = MutableStateFlow(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    /** Elapsed recording time in seconds, pauses excluded. */
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    /** Recent loudness, 0..1, newest last, about ten a second. */
    private val _waveform = MutableStateFlow<List<Float>>(emptyList())
    val waveform: StateFlow<List<Float>> = _waveform.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Set when Save finished: the note that holds the recording. */
    private val _savedNoteId = MutableStateFlow<String?>(null)
    val savedNoteId: StateFlow<String?> = _savedNoteId.asStateFlow()

    /**
     * The note this recording is being made into, from the moment it starts
     * until it is saved or thrown away.
     *
     * The note screen shows the recorder only for its own note, so walking
     * to another note while recording shows that note as usual, and the
     * recording carries on with its notification.
     */
    private val _noteId = MutableStateFlow<String?>(null)
    val noteId: StateFlow<String?> = _noteId.asStateFlow()

    /** True while a telephone call is holding the microphone. */
    private val _inCall = MutableStateFlow(false)
    val inCall: StateFlow<Boolean> = _inCall.asStateFlow()

    private var recorder: MediaRecorder? = null
    private var wavRecorder: WavRecorder? = null
    private var format: String = RecorderPreferences.FORMAT_OPUS
    private var tempFile: File? = null
    private var startedAt: Long = 0
    private var ticker: Job? = null
    private var elapsedMs: Long = 0
    private var segmentStartMs: Long = 0
    /** Paused by a telephone call rather than by the user, so it resumes itself. */
    private var pausedByCall = false

    fun micName(context: Context): String {
        val prefs = RecorderPreferences(context)
        return prefs.selectedMic()?.let { prefs.friendlyName(it) } ?: "System default microphone"
    }

    fun formatTitle(context: Context): String =
        RecorderPreferences.formatTitle(RecorderPreferences(context).recordingFormat)

    /**
     * Record into [noteId], or resume after a pause.
     *
     * `Saved` is a starting state as well as `Idle`: the screen may still be
     * showing the recorder when the user asks for another recording, and the
     * button must not sit there doing nothing.
     */
    fun record(context: Context, noteId: String) {
        when (_state.value) {
            RecordingState.Idle, RecordingState.Saved -> start(context, noteId)
            RecordingState.Paused -> resume(context)
            else -> {}
        }
    }

    private fun start(context: Context, noteId: String) {
        // Whatever was playing stops now. A recording made while a player was
        // running caught the other recording's sound at its start, which is a
        // ruined recording and cannot be undone.
        try { AudioPlayerManager.shared(context.applicationContext).pause() } catch (_: Exception) {}

        val app = context.applicationContext
        try {
            val prefs = RecorderPreferences(app)
            format = prefs.recordingFormat
            val file = File.createTempFile(
                "recording-",
                "." + RecorderPreferences.formatExtension(format),
                app.cacheDir
            )
            if (format == RecorderPreferences.FORMAT_WAV16) {
                val wav = WavRecorder(file, prefs.selectedMic())
                wav.start()
                wavRecorder = wav
            } else {
                val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(app) else @Suppress("DEPRECATION") MediaRecorder()
                r.setAudioSource(MediaRecorder.AudioSource.MIC)
                if (format == RecorderPreferences.FORMAT_OPUS || format == RecorderPreferences.FORMAT_OPUS_SPEECH) {
                    r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    r.setAudioEncodingBitRate(if (format == RecorderPreferences.FORMAT_OPUS_SPEECH) 32_000 else 128_000)
                    r.setAudioSamplingRate(48_000)
                } else {
                    r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                    r.setAudioEncodingBitRate(96_000)
                    r.setAudioSamplingRate(44_100)
                }
                r.setAudioChannels(1)
                r.setOutputFile(file.absolutePath)
                prefs.selectedMic()?.let { r.setPreferredDevice(it) }
                r.prepare()
                r.start()
                recorder = r
            }
            tempFile = file
            startedAt = System.currentTimeMillis()
            elapsedMs = 0
            segmentStartMs = System.currentTimeMillis()
            pausedByCall = false
            _waveform.value = emptyList()
            _elapsedSeconds.value = 0
            _savedNoteId.value = null
            _noteId.value = noteId
            _state.value = RecordingState.Recording
            startTicker(app)
            // The notification is what lets the recording outlive the screen.
            // If Android refuses it (the app was not in the foreground when
            // this was asked for), the recording still runs while the app is.
            try {
                RecordingService.start(app)
            } catch (e: Exception) {
                AppLogger.w(TAG, "No foreground service, so recording stops if the app leaves: ${e.message}")
            }
            AppLogger.i(TAG, "Recording started into ${file.name} ($format) with ${micName(app)}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Could not start recording", e)
            _error.value = "Could not start recording: ${e.message}"
            cleanup(app)
        }
    }

    fun pause() {
        if (_state.value != RecordingState.Recording) return
        try {
            recorder?.pause()
            wavRecorder?.pause()
            elapsedMs += System.currentTimeMillis() - segmentStartMs
            _state.value = RecordingState.Paused
            ticker?.cancel()
        } catch (e: Exception) {
            _error.value = "Could not pause: ${e.message}"
        }
    }

    private fun resume(context: Context) {
        if (recorder == null && wavRecorder == null) return
        try {
            recorder?.resume()
            wavRecorder?.resume()
            segmentStartMs = System.currentTimeMillis()
            pausedByCall = false
            _state.value = RecordingState.Recording
            startTicker(context.applicationContext)
        } catch (e: Exception) {
            _error.value = "Could not resume: ${e.message}"
        }
    }

    private fun startTicker(app: Context) {
        ticker?.cancel()
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        // Read as we go, so changing the setting mid-recording takes effect
        val prefs = RecorderPreferences(app)
        ticker = scope.launch {
            while (_state.value == RecordingState.Recording) {
                val level = wavRecorder?.takeLevel()
                    ?: (try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 } / 32767f).coerceIn(0f, 1f)
                _waveform.value = (_waveform.value + level).takeLast(WAVEFORM_SAMPLES)
                _elapsedSeconds.value = (elapsedMs + (System.currentTimeMillis() - segmentStartMs)) / 1000

                // Android hands the microphone to the telephone during a call,
                // so what we record is silence. The user decides whether to
                // keep that silence or to stop until the call is over.
                val callNow = isCallMode(audio.mode)
                if (callNow != _inCall.value) {
                    _inCall.value = callNow
                    AppLogger.i(TAG, if (callNow) "A call started while recording" else "The call ended")
                }
                if (shouldPauseForCall(callNow, prefs.duringCall)) {
                    pausedByCall = true
                    pause()
                }
                delay(100)
            }
            // Waiting for a call to end so the recording can carry on
            while (pausedByCall && _state.value == RecordingState.Paused) {
                val callNow = isCallMode(audio.mode)
                _inCall.value = callNow
                if (!callNow) {
                    AppLogger.i(TAG, "Call over, recording again")
                    resume(app)
                    break
                }
                delay(500)
            }
        }
    }

    /** Throw away what was recorded and be ready to record again. */
    fun restart(context: Context) {
        val note = _noteId.value
        cleanup(context.applicationContext)
        _waveform.value = emptyList()
        _elapsedSeconds.value = 0
        _state.value = RecordingState.Idle
        // Still recording into the same note: only the audio is thrown away.
        _noteId.value = note
    }

    /** Throw the recording away. */
    fun trash(context: Context) {
        cleanup(context.applicationContext)
        _waveform.value = emptyList()
        _elapsedSeconds.value = 0
        _savedNoteId.value = null
        _noteId.value = null
        _state.value = RecordingState.Idle
    }

    /**
     * Put the recorder back to a clean slate, unless it is busy.
     *
     * A recorder that outlives every screen also outlives the recording it
     * just made: without this, opening the recorder again showed the elapsed
     * time and the waveform of the last recording, and the record button did
     * nothing because the state was still `Saved`. Anything in progress is
     * left alone, since the whole point of this object is that a recording
     * survives the screen that started it.
     */
    fun reset(context: Context) {
        if (_state.value == RecordingState.Recording ||
            _state.value == RecordingState.Paused ||
            _state.value == RecordingState.Saving
        ) return
        trash(context)
        _error.value = null
    }

    /**
     * Stop, attach the recording to the note it was made in, move the file
     * into place.
     *
     * The note already exists (the recorder is inside it), so nothing new is
     * created here beyond the audio file record itself.
     */
    fun save(context: Context) {
        val app = context.applicationContext
        val file = tempFile
        val note = _noteId.value
        if ((recorder == null && wavRecorder == null) || file == null || _state.value == RecordingState.Idle) {
            _error.value = "Nothing recorded yet"
            return
        }
        if (note == null) {
            _error.value = "This recording has no note to go into"
            return
        }
        _state.value = RecordingState.Saving
        ticker?.cancel()
        scope.launch {
            try {
                if (elapsedMs == 0L && segmentStartMs > 0) elapsedMs = System.currentTimeMillis() - segmentStartMs
                recorder?.let { r ->
                    try { r.stop() } catch (e: Exception) { AppLogger.w(TAG, "stop(): ${e.message}") }
                    r.release()
                }
                recorder = null
                wavRecorder?.stop()
                wavRecorder = null
                val repository = VoiceRepository.getInstance(app)
                repository.initialize().getOrThrow()
                val duration = maxOf(1L, _elapsedSeconds.value)
                val ext = RecorderPreferences.formatExtension(format)
                val filename = "Recording " + SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date(startedAt)) + "." + ext
                val audioFileId = repository.importAudioFileIntoNote(note, filename, startedAt / 1000, duration).getOrThrow()
                repository.copyAudioFileToStorage(app, Uri.fromFile(file), audioFileId).getOrThrow()
                file.delete()
                tempFile = null
                AppLogger.i(TAG, "Saved recording $filename into note ${note.take(8)} (${duration}s)")
                // Straight into the transcription queue, when that is asked
                // for. The recording is already saved by this point, so a
                // queue that refuses it (no model downloaded, no room for a
                // foreground service) costs the recording nothing.
                if (BuildConfig.DEV_FEATURES && RecorderPreferences(app).transcribeWhenSaved) {
                    val refused = OnDeviceTranscriber.enqueue(
                        app,
                        audioFileId,
                        filename,
                        durationSeconds = duration,
                    )
                    if (refused != null) AppLogger.i(TAG, "Not transcribed yet: $refused")
                }
                _savedNoteId.value = note
                _state.value = RecordingState.Saved
                // Leave nothing of this recording behind: the next one starts
                // at 00:00:00 with an empty waveform.
                _elapsedSeconds.value = 0
                _waveform.value = emptyList()
                _noteId.value = null
            } catch (e: Exception) {
                AppLogger.e(TAG, "Could not save recording", e)
                CriticalLog.logImportFailure("recording", e.message ?: "unknown")
                _error.value = "Could not save the recording: ${e.message}"
                _state.value = RecordingState.Paused
            } finally {
                try { RecordingService.stop(app) } catch (_: Exception) {}
            }
        }
    }

    fun clearError() { _error.value = null }

    /** Read once by the screen after it has opened the saved note. */
    fun clearSavedNote() { _savedNoteId.value = null }

    private fun cleanup(app: Context) {
        ticker?.cancel()
        pausedByCall = false
        _inCall.value = false
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { wavRecorder?.stop() } catch (_: Exception) {}
        wavRecorder = null
        tempFile?.delete()
        tempFile = null
        elapsedMs = 0
        try { RecordingService.stop(app) } catch (_: Exception) {}
    }
}
