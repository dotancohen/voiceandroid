package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.data.Note
import com.dotancohen.voiceandroid.data.Transcription
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.util.AppLogger
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

    // Map of audio file ID to its transcriptions
    private val _transcriptions = MutableStateFlow<Map<String, List<Transcription>>>(emptyMap())
    val transcriptions: StateFlow<Map<String, List<Transcription>>> = _transcriptions.asStateFlow()

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

    // Delete state
    private val _isDeleting = MutableStateFlow(false)
    val isDeleting: StateFlow<Boolean> = _isDeleting.asStateFlow()

    private val _deleteSuccess = MutableStateFlow(false)
    val deleteSuccess: StateFlow<Boolean> = _deleteSuccess.asStateFlow()

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
                                val filteredFiles = files.filter { it.deletedAt == null }
                                _audioFiles.value = filteredFiles

                                // Load transcriptions for each audio file
                                loadTranscriptionsForAudioFiles(filteredFiles)
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
     * Load transcriptions for all audio files.
     */
    private suspend fun loadTranscriptionsForAudioFiles(audioFiles: List<AudioFile>) {
        val transcriptionsMap = mutableMapOf<String, List<Transcription>>()

        for (audioFile in audioFiles) {
            repository.getTranscriptionsForAudioFile(audioFile.id)
                .onSuccess { transcriptionList ->
                    transcriptionsMap[audioFile.id] = transcriptionList
                }
                .onFailure { exception ->
                    AppLogger.w(TAG, "Failed to load transcriptions for ${audioFile.id}: ${exception.message}")
                    transcriptionsMap[audioFile.id] = emptyList()
                }
        }

        _transcriptions.value = transcriptionsMap
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
     * Delete the current note (soft delete).
     * Sets deleteSuccess to true on success so the UI can navigate back.
     */
    fun deleteNote() {
        val noteId = _note.value?.id ?: return
        viewModelScope.launch {
            _isDeleting.value = true
            AppLogger.i(TAG, "Deleting note $noteId")
            repository.deleteNote(noteId)
                .onSuccess {
                    AppLogger.i(TAG, "Note deleted successfully: $noteId")
                    _deleteSuccess.value = true
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Failed to delete note $noteId", e)
                    _error.value = "Failed to delete: ${e.message}"
                }
            _isDeleting.value = false
        }
    }

    /**
     * Save the edited note content.
     */
    fun saveNote() {
        val noteId = _note.value?.id ?: return
        viewModelScope.launch {
            _isSaving.value = true
            AppLogger.i(TAG, "Saving note $noteId")
            repository.updateNote(noteId, _editedContent.value)
                .onSuccess {
                    AppLogger.i(TAG, "Note saved successfully: $noteId")
                    // Reload note to get updated modified_at
                    loadNote(noteId)
                    _isEditing.value = false
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Failed to save note $noteId", e)
                    _error.value = "Failed to save: ${e.message}"
                }
            _isSaving.value = false
        }
    }

    // =========================================================================
    // Transcription Methods
    // =========================================================================

    /**
     * Toggle a state tag for a transcription.
     * If the tag is true, it becomes false; if false, it becomes true.
     */
    fun toggleTranscriptionState(transcription: Transcription, tag: String) {
        viewModelScope.launch {
            val newState = transcription.toggleState(tag)
            AppLogger.i(TAG, "Toggling transcription ${transcription.id} state: $tag -> $newState")

            repository.updateTranscriptionState(transcription.id, newState)
                .onSuccess {
                    AppLogger.i(TAG, "Transcription state updated successfully")
                    // Update local state
                    updateLocalTranscriptionState(transcription.audioFileId, transcription.id, newState)
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Failed to update transcription state", e)
                    _error.value = "Failed to update transcription: ${e.message}"
                }
        }
    }

    /**
     * Update the local transcription state without reloading from database.
     */
    private fun updateLocalTranscriptionState(audioFileId: String, transcriptionId: String, newState: String) {
        val currentMap = _transcriptions.value.toMutableMap()
        val transcriptionList = currentMap[audioFileId]?.toMutableList() ?: return

        val index = transcriptionList.indexOfFirst { it.id == transcriptionId }
        if (index >= 0) {
            transcriptionList[index] = transcriptionList[index].copy(state = newState)
            currentMap[audioFileId] = transcriptionList
            _transcriptions.value = currentMap
        }
    }

    /**
     * Get transcriptions for a specific audio file.
     */
    fun getTranscriptionsForAudioFile(audioFileId: String): List<Transcription> {
        return _transcriptions.value[audioFileId] ?: emptyList()
    }

    companion object {
        private const val TAG = "NoteDetailViewModel"
    }
}
