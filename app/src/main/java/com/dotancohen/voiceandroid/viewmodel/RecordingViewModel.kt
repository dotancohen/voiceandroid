package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.audio.RecorderPreferences
import com.dotancohen.voiceandroid.audio.WavRecorder
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.util.AppLogger
import com.dotancohen.voiceandroid.util.CriticalLog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the recording screen is doing. */
enum class RecordingState { Idle, Recording, Paused, Saving, Saved }

/**
 * Records one voice message into the cache directory in the format chosen
 * under Settings → Recorder: Opus (.ogg) or AAC (.m4a) with [MediaRecorder],
 * or 16 kHz WAV with [WavRecorder]. Save creates the note and the audio file record
 * (the same call the importer uses, so the note looks like an imported
 * recording) and moves the file into the app's audio directory.
 */
class RecordingViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = VoiceRepository.getInstance(application)
    private val prefs = RecorderPreferences(application)

    private val _state = MutableStateFlow(RecordingState.Idle)
    val state: StateFlow<RecordingState> = _state.asStateFlow()

    /** Elapsed recording time in seconds (pauses excluded). */
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    /** Recent loudness samples, 0..1, newest last (about ten per second). */
    private val _waveform = MutableStateFlow<List<Float>>(emptyList())
    val waveform: StateFlow<List<Float>> = _waveform.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /** Set when Save finished: the id of the note that holds the recording. */
    private val _savedNoteId = MutableStateFlow<String?>(null)
    val savedNoteId: StateFlow<String?> = _savedNoteId.asStateFlow()

    val micName: String get() = prefs.selectedMic()?.let { prefs.friendlyName(it) } ?: "System default microphone"

    private var recorder: MediaRecorder? = null
    private var wavRecorder: WavRecorder? = null
    private var format: String = prefs.recordingFormat
    private var tempFile: File? = null
    private var startedAt: Long = 0
    private var ticker: Job? = null
    private var elapsedMs: Long = 0
    private var segmentStartMs: Long = 0

    /** Record, or resume after a pause. */
    fun record() {
        when (_state.value) {
            RecordingState.Idle -> start()
            RecordingState.Paused -> resume()
            else -> {}
        }
    }

    /** Title of the format the next recording will use (for the screen). */
    val formatTitle: String get() = RecorderPreferences.formatTitle(prefs.recordingFormat)

    private fun start() {
        try {
            format = prefs.recordingFormat
            val file = File.createTempFile("recording-", "." + RecorderPreferences.formatExtension(format), getApplication<Application>().cacheDir)
            if (format == RecorderPreferences.FORMAT_WAV16) {
                val w = WavRecorder(file, prefs.selectedMic())
                w.start()
                wavRecorder = w
            } else {
                val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(getApplication()) else @Suppress("DEPRECATION") MediaRecorder()
                r.setAudioSource(MediaRecorder.AudioSource.MIC)
                if (format == RecorderPreferences.FORMAT_OPUS) {
                    r.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    r.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    r.setAudioEncodingBitRate(128_000)
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
            _waveform.value = emptyList()
            _elapsedSeconds.value = 0
            _state.value = RecordingState.Recording
            startTicker()
            AppLogger.i(TAG, "Recording started into ${file.name} ($format) with ${micName}")
        } catch (e: Exception) {
            AppLogger.e(TAG, "Could not start recording", e)
            _error.value = "Could not start recording: ${e.message}"
            cleanup()
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

    private fun resume() {
        if (recorder == null && wavRecorder == null) return
        try {
            recorder?.resume()
            wavRecorder?.resume()
            segmentStartMs = System.currentTimeMillis()
            _state.value = RecordingState.Recording
            startTicker()
        } catch (e: Exception) {
            _error.value = "Could not resume: ${e.message}"
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = viewModelScope.launch {
            while (_state.value == RecordingState.Recording) {
                val level = wavRecorder?.takeLevel()
                    ?: (try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 } / 32767f).coerceIn(0f, 1f)
                _waveform.value = (_waveform.value + level).takeLast(WAVEFORM_SAMPLES)
                _elapsedSeconds.value = (elapsedMs + (System.currentTimeMillis() - segmentStartMs)) / 1000
                delay(100)
            }
        }
    }

    /** Throw away what was recorded and go back to the idle state, ready to record again. */
    fun restart() {
        cleanup()
        _waveform.value = emptyList()
        _elapsedSeconds.value = 0
        _state.value = RecordingState.Idle
    }

    /** Throw away the recording; the caller leaves the screen. */
    fun trash() {
        cleanup()
        _state.value = RecordingState.Idle
    }

    /** Stop, create the note with the recording attached, move the file into place. */
    fun save() {
        val file = tempFile
        if ((recorder == null && wavRecorder == null) || file == null || _state.value == RecordingState.Idle) {
            _error.value = "Nothing recorded yet"
            return
        }
        _state.value = RecordingState.Saving
        ticker?.cancel()
        viewModelScope.launch {
            try {
                if (elapsedMs == 0L && segmentStartMs > 0) elapsedMs = System.currentTimeMillis() - segmentStartMs
                recorder?.let { r ->
                    try { r.stop() } catch (e: Exception) { AppLogger.w(TAG, "stop(): ${e.message}") }
                    r.release()
                }
                recorder = null
                wavRecorder?.stop()
                wavRecorder = null
                val duration = maxOf(1L, _elapsedSeconds.value)
                val ext = RecorderPreferences.formatExtension(format)
                val filename = "Recording " + SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US).format(Date(startedAt)) + "." + ext
                val result = repository.importAudioFile(filename, startedAt / 1000, duration).getOrThrow()
                repository.copyAudioFileToStorage(getApplication(), Uri.fromFile(file), result.audioFileId, ext).getOrThrow()
                file.delete()
                tempFile = null
                AppLogger.i(TAG, "Saved recording $filename as note ${result.noteId.take(8)} (${duration}s)")
                _savedNoteId.value = result.noteId
                _state.value = RecordingState.Saved
            } catch (e: Exception) {
                AppLogger.e(TAG, "Could not save recording", e)
                CriticalLog.logImportFailure("recording", e.message ?: "unknown")
                _error.value = "Could not save the recording: ${e.message}"
                _state.value = RecordingState.Paused
            }
        }
    }

    fun clearError() { _error.value = null }

    private fun cleanup() {
        ticker?.cancel()
        try { recorder?.stop() } catch (_: Exception) {}
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
        try { wavRecorder?.stop() } catch (_: Exception) {}
        wavRecorder = null
        tempFile?.delete()
        tempFile = null
        elapsedMs = 0
    }

    override fun onCleared() {
        cleanup()
        super.onCleared()
    }

    companion object {
        private const val TAG = "RecordingVM"
        private const val WAVEFORM_SAMPLES = 120
    }
}
