package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.data.Note
import com.dotancohen.voiceandroid.data.VoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the note detail screen.
 */
class NoteDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)

    private val _note = MutableStateFlow<Note?>(null)
    val note: StateFlow<Note?> = _note.asStateFlow()

    private val _audioFiles = MutableStateFlow<List<AudioFile>>(emptyList())
    val audioFiles: StateFlow<List<AudioFile>> = _audioFiles.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Edit state
    private val _isEditing = MutableStateFlow(false)
    val isEditing: StateFlow<Boolean> = _isEditing.asStateFlow()

    private val _editedContent = MutableStateFlow("")
    val editedContent: StateFlow<String> = _editedContent.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    /**
     * Load a note and its audio files by ID.
     */
    fun loadNote(noteId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Get all notes and find the one we need
            // (We could add a getNoteById method to the repository, but this works for now)
            repository.getAllNotes()
                .onSuccess { notesList ->
                    val foundNote = notesList.find { it.id == noteId }
                    _note.value = foundNote

                    if (foundNote != null) {
                        // Load audio files for this note
                        repository.getAudioFilesForNote(noteId)
                            .onSuccess { files ->
                                _audioFiles.value = files.filter { it.deletedAt == null }
                            }
                            .onFailure { exception ->
                                _error.value = "Failed to load audio files: ${exception.message}"
                            }
                    }
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }

            _isLoading.value = false
        }
    }

    /**
     * Get the file path for an audio file.
     */
    suspend fun getAudioFilePath(audioFileId: String): String? {
        return repository.getAudioFilePath(audioFileId).getOrNull()
    }

    /**
     * Start editing the current note.
     */
    fun startEditing() {
        _editedContent.value = _note.value?.content ?: ""
        _isEditing.value = true
    }

    /**
     * Update the edited content.
     */
    fun updateEditedContent(content: String) {
        _editedContent.value = content
    }

    /**
     * Cancel editing and discard changes.
     */
    fun cancelEditing() {
        _isEditing.value = false
        _editedContent.value = ""
    }

    /**
     * Save the edited note content.
     */
    fun saveNote() {
        val noteId = _note.value?.id ?: return
        viewModelScope.launch {
            _isSaving.value = true
            repository.updateNote(noteId, _editedContent.value)
                .onSuccess {
                    // Reload note to get updated modified_at
                    loadNote(noteId)
                    _isEditing.value = false
                }
                .onFailure { e ->
                    _error.value = "Failed to save: ${e.message}"
                }
            _isSaving.value = false
        }
    }
}
