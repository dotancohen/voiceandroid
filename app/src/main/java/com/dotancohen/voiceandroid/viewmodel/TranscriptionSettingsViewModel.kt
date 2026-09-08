package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.transcription.TranscriptionPreferences
import com.dotancohen.voiceandroid.transcription.WhisperModel
import com.dotancohen.voiceandroid.transcription.WhisperModels
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One model as shown on the Transcription settings screen. */
data class ModelItem(
    val model: WhisperModel,
    val installed: Boolean,
    val selected: Boolean,
    /** Download progress 0..1 while downloading, else null. */
    val progress: Float? = null,
)

class TranscriptionSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = TranscriptionPreferences(application)
    private val app = application

    private val _models = MutableStateFlow<List<ModelItem>>(emptyList())
    val models: StateFlow<List<ModelItem>> = _models.asStateFlow()

    private val _language = MutableStateFlow(prefs.language)
    val language: StateFlow<String> = _language.asStateFlow()

    private val _beamSize = MutableStateFlow(prefs.beamSize)
    val beamSize: StateFlow<Int> = _beamSize.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val downloads = mutableMapOf<String, Job>()
    private val progress = mutableMapOf<String, Float>()

    init { refresh() }

    fun refresh() {
        val selected = prefs.modelId
        _models.value = WhisperModels.CATALOGUE.map { m ->
            ModelItem(m, WhisperModels.isInstalled(app, m), m.id == selected, progress[m.id])
        }
    }

    fun select(model: WhisperModel) {
        prefs.modelId = model.id
        refresh()
    }

    fun setLanguage(code: String) {
        prefs.language = code
        _language.value = code
    }

    fun setBeamSize(n: Int) {
        prefs.beamSize = n
        _beamSize.value = prefs.beamSize
    }

    fun download(model: WhisperModel) {
        if (downloads[model.id]?.isActive == true) return
        progress[model.id] = 0f
        refresh()
        downloads[model.id] = viewModelScope.launch {
            try {
                WhisperModels.download(app, model) { done, total ->
                    progress[model.id] = (done.toFloat() / total).coerceIn(0f, 1f)
                    refresh()
                }
                _message.value = "${model.title} downloaded"
            } catch (e: Exception) {
                AppLogger.w(TAG, "Download of ${model.id} failed: ${e.message}")
                _message.value = "Download failed: ${e.message}"
            } finally {
                progress.remove(model.id)
                refresh()
            }
        }
    }

    fun cancelDownload(model: WhisperModel) {
        downloads.remove(model.id)?.cancel()
        progress.remove(model.id)
        refresh()
    }

    fun delete(model: WhisperModel) {
        cancelDownload(model)
        WhisperModels.delete(app, model)
        refresh()
    }

    fun clearMessage() { _message.value = null }

    companion object { private const val TAG = "TranscriptionSettingsVM" }
}
