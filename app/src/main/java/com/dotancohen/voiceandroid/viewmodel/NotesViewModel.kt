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
 * Data class combining a note with its audio file attachments.
 */
data class NoteWithAudioFiles(
    val note: Note,
    val audioFiles: List<AudioFile> = emptyList()
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)

    private val _notes = MutableStateFlow<List<NoteWithAudioFiles>>(emptyList())
    val notes: StateFlow<List<NoteWithAudioFiles>> = _notes.asStateFlow()

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
                    val filteredNotes = notesList
                        .filter { it.deletedAt == null }
                        .sortedByDescending { it.modifiedAt ?: it.createdAt }

                    // Load audio files for each note
                    val notesWithAudio = filteredNotes.map { note ->
                        val audioFiles = repository.getAudioFilesForNote(note.id)
                            .getOrNull()
                            ?.filter { it.deletedAt == null }
                            ?: emptyList()
                        NoteWithAudioFiles(note, audioFiles)
                    }

                    _notes.value = notesWithAudio
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }

            _isLoading.value = false
        }
    }

    /**
     * Get the file path for an audio file (if it exists on disk).
     */
    suspend fun getAudioFilePath(audioFileId: String): String? {
        return repository.getAudioFilePath(audioFileId).getOrNull()
    }

    fun refresh() {
        loadNotes()
    }
}
