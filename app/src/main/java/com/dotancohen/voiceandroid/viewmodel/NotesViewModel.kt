package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.Note
import com.dotancohen.voiceandroid.data.VoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)

    private val _notes = MutableStateFlow<List<Note>>(emptyList())
    val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        loadNotes()
    }

    fun loadNotes() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.getAllNotes()
                .onSuccess { notesList ->
                    // Filter out deleted notes and sort by date (newest first)
                    _notes.value = notesList
                        .filter { it.deletedAt == null }
                        .sortedByDescending { it.modifiedAt ?: it.createdAt }
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }

            _isLoading.value = false
        }
    }

    fun refresh() {
        loadNotes()
    }
}
