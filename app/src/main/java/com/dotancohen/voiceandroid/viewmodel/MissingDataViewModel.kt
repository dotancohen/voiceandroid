package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.MissingData
import com.dotancohen.voiceandroid.data.RepositoryMissingDataStore
import com.dotancohen.voiceandroid.data.VoiceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings → Calculate missing data.
 *
 * Two steps, deliberately: first say what is missing, then calculate it. The
 * survey opens no audio, so it is quick even on a phone holding thousands of
 * recordings, and the user decides whether the work is worth doing before any
 * of it starts.
 */
class MissingDataViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)
    private val store = RepositoryMissingDataStore(repository)

    private val _survey = MutableStateFlow<MissingData.Survey?>(null)
    val survey: StateFlow<MissingData.Survey?> = _survey.asStateFlow()

    private val _report = MutableStateFlow<MissingData.Report?>(null)
    val report: StateFlow<MissingData.Report?> = _report.asStateFlow()

    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()

    /** The last thing done, so the screen can show that something is happening. */
    private val _currently = MutableStateFlow("")
    val currently: StateFlow<String> = _currently.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        look()
    }

    /** Count what is missing, changing nothing. */
    fun look() {
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            _error.value = null
            _currently.value = "Counting..."
            try {
                _survey.value = withContext(Dispatchers.IO) { MissingData.survey(store) }
            } catch (e: Throwable) {
                _error.value = e.message ?: "Could not read the database"
            } finally {
                _currently.value = ""
                _isWorking.value = false
            }
        }
    }

    /** Calculate what can be calculated, then count again. */
    fun calculate() {
        if (_isWorking.value) return
        viewModelScope.launch {
            _isWorking.value = true
            _error.value = null
            _report.value = null
            try {
                val report = withContext(Dispatchers.IO) {
                    MissingData.calculate(store) { line -> _currently.value = line }
                }
                _report.value = report
                _survey.value = withContext(Dispatchers.IO) { MissingData.survey(store) }
            } catch (e: Throwable) {
                _error.value = e.message ?: "Could not calculate the missing data"
            } finally {
                _currently.value = ""
                _isWorking.value = false
            }
        }
    }

    /** Put the report away, so the screen shows only what is still missing. */
    fun forgetReport() {
        _report.value = null
    }
}
