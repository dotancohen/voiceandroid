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

    // Search result metadata
    private val _ambiguousTags = MutableStateFlow<List<String>>(emptyList())
    val ambiguousTags: StateFlow<List<String>> = _ambiguousTags.asStateFlow()

    private val _notFoundTags = MutableStateFlow<List<String>>(emptyList())
    val notFoundTags: StateFlow<List<String>> = _notFoundTags.asStateFlow()

    // Current search query (null = show all notes)
    private val _currentSearchQuery = MutableStateFlow<String?>(null)
    val currentSearchQuery: StateFlow<String?> = _currentSearchQuery.asStateFlow()

    init {
        loadNotes()
    }

    /**
     * Load all notes (unfiltered).
     */
    fun loadNotes() {
        loadNotes(null)
    }

    /**
     * Load notes, optionally filtered by search query.
     * @param searchQuery The search query to filter by, or null to load all notes.
     */
    fun loadNotes(searchQuery: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _currentSearchQuery.value = searchQuery
            _ambiguousTags.value = emptyList()
            _notFoundTags.value = emptyList()

            val notesResult = if (searchQuery.isNullOrBlank()) {
                // Load all notes
                repository.getAllNotes().map { notesList ->
                    // Filter out deleted notes and sort by date (newest first)
                    notesList
                        .filter { it.deletedAt == null }
                        .sortedByDescending { it.createdAt }
                }
            } else {
                // Search notes
                repository.searchNotes(searchQuery).map { searchResult ->
                    _ambiguousTags.value = searchResult.ambiguousTags
                    _notFoundTags.value = searchResult.notFoundTags
                    // Filter out deleted notes and sort by date (newest first)
                    searchResult.notes
                        .filter { it.deletedAt == null }
                        .sortedByDescending { it.createdAt }
                }
            }

            notesResult
                .onSuccess { filteredNotes ->
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

    /**
     * Refresh notes with the current search query.
     */
    fun refresh() {
        loadNotes(_currentSearchQuery.value)
    }
}
