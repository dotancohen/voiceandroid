package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import android.media.AudioDeviceInfo
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.audio.MicLevelMeter
import com.dotancohen.voiceandroid.audio.RecorderPreferences
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** One microphone as shown in the recorder settings. */
data class MicItem(
    val key: String,
    val device: AudioDeviceInfo,
    val friendlyName: String,
    val technicalName: String,
    val selected: Boolean,
)

class RecorderSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = RecorderPreferences(application)

    private val _mics = MutableStateFlow<List<MicItem>>(emptyList())
    val mics: StateFlow<List<MicItem>> = _mics.asStateFlow()

    private val _defaultNewAction = MutableStateFlow(prefs.defaultNewAction)
    val defaultNewAction: StateFlow<String> = _defaultNewAction.asStateFlow()

    private val _recordingFormat = MutableStateFlow(prefs.recordingFormat)
    val recordingFormat: StateFlow<String> = _recordingFormat.asStateFlow()

    private val _startImmediately = MutableStateFlow(prefs.startRecordingImmediately)
    val startImmediately: StateFlow<Boolean> = _startImmediately.asStateFlow()

    /** Debug builds only: queue every recording for transcription as it is saved. */
    private val _transcribeWhenSaved = MutableStateFlow(prefs.transcribeWhenSaved)
    val transcribeWhenSaved: StateFlow<Boolean> = _transcribeWhenSaved.asStateFlow()

    private val _duringCall = MutableStateFlow(prefs.duringCall)
    val duringCall: StateFlow<String> = _duringCall.asStateFlow()

    /** Key of the microphone whose level is being shown, or null. */
    private val _testingMicKey = MutableStateFlow<String?>(null)
    val testingMicKey: StateFlow<String?> = _testingMicKey.asStateFlow()

    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()

    /** Loudest level seen since the test started: tells the user where the microphone hears best. */
    private val _peak = MutableStateFlow(0f)
    val peak: StateFlow<Float> = _peak.asStateFlow()

    private var meterJob: Job? = null

    init { refresh() }

    fun refresh() {
        val selected = prefs.selectedMicKey
        _mics.value = prefs.microphones().map { d ->
            val key = RecorderPreferences.micKey(d)
            MicItem(key, d, prefs.friendlyName(d), RecorderPreferences.defaultName(d) + " (id ${d.id})", key == selected)
        }
    }

    fun setDefaultNewAction(action: String) {
        prefs.defaultNewAction = action
        _defaultNewAction.value = action
    }

    fun setRecordingFormat(format: String) {
        prefs.recordingFormat = format
        _recordingFormat.value = prefs.recordingFormat
    }

    fun setStartImmediately(start: Boolean) {
        prefs.startRecordingImmediately = start
        _startImmediately.value = start
    }

    fun setTranscribeWhenSaved(transcribe: Boolean) {
        prefs.transcribeWhenSaved = transcribe
        _transcribeWhenSaved.value = transcribe
    }

    fun setDuringCall(behaviour: String) {
        prefs.duringCall = behaviour
        _duringCall.value = prefs.duringCall
    }

    fun selectMic(key: String?) {
        prefs.selectedMicKey = key
        refresh()
    }

    fun rename(item: MicItem, name: String) {
        prefs.setFriendlyName(item.device, name)
        refresh()
    }

    /** Start (or switch) the live level for one microphone; null stops. */
    fun test(item: MicItem?) {
        meterJob?.cancel()
        meterJob = null
        _level.value = 0f
        _peak.value = 0f
        _testingMicKey.value = item?.key
        if (item == null) return
        meterJob = viewModelScope.launch {
            try {
                MicLevelMeter.levels(item.device).collect { v ->
                    _level.value = v
                    if (v > _peak.value) _peak.value = v
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Level meter stopped: ${e.message}")
                _testingMicKey.value = null
            }
        }
    }

    override fun onCleared() {
        meterJob?.cancel()
        super.onCleared()
    }

    companion object { private const val TAG = "RecorderSettingsVM" }
}
