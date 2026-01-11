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
 * Data class combining a note with its audio file attachments and marked state.
 */
data class NoteWithAudioFiles(
    val note: Note,
    val audioFiles: List<AudioFile> = emptyList(),
    val isMarked: Boolean = false
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
                    // Load audio files and marked state for each note
                    val notesWithAudio = filteredNotes.map { note ->
                        val audioFiles = repository.getAudioFilesForNote(note.id)
                            .getOrNull()
                            ?.filter { it.deletedAt == null }
                            ?: emptyList()
                        // Use cached marked state if available, otherwise query
                        val isMarked = note.listDisplayCache?.let { cache ->
                            try {
                                val json = org.json.JSONObject(cache)
                                json.optBoolean("marked", false)
                            } catch (e: Exception) {
                                null
                            }
                        } ?: repository.isNoteMarked(note.id).getOrNull() ?: false
                        NoteWithAudioFiles(note, audioFiles, isMarked)
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

    /**
     * Toggle the marked (starred) state of a note.
     * Updates the local state optimistically, then persists to database.
     */
    fun toggleNoteMarked(noteId: String) {
        viewModelScope.launch {
            // Toggle in the database
            val result = repository.toggleNoteMarked(noteId)
            result.onSuccess { newMarkedState ->
                // Update local state
                _notes.value = _notes.value.map { noteWithAudio ->
                    if (noteWithAudio.note.id == noteId) {
                        noteWithAudio.copy(isMarked = newMarkedState)
                    } else {
                        noteWithAudio
                    }
                }
            }.onFailure { exception ->
                _error.value = "Failed to toggle star: ${exception.message}"
            }
        }
    }
}
