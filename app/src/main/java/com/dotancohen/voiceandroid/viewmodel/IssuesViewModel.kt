package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.util.AppLogger
import com.dotancohen.voiceandroid.util.IssuesText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What needs the user's attention (ISSUE-1), read when the screen opens or is
 * refreshed. It only lists; nothing is pushed at the user.
 */
class IssuesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)

    private val _sections = MutableStateFlow<List<Pair<String, List<String>>>>(emptyList())
    val sections: StateFlow<List<Pair<String, List<String>>>> = _sections.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            val names = repository.deviceNames().getOrNull().orEmpty()
            val here = repository.getThisDeviceId().getOrNull().orEmpty()
            repository.issues()
                .onSuccess { issues -> _sections.value = IssuesText.sections(issues, names, here) }
                .onFailure { e ->
                    AppLogger.e(TAG, "Could not read the issues", e)
                    _message.value = "Could not read the issues: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    fun clearMessage() {
        _message.value = null
    }

    private companion object {
        const val TAG = "IssuesViewModel"
    }
}
