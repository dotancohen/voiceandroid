package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.Note
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The trash bin: the notes that were deleted and are still recoverable.
 *
 * Deleting a note has always been a soft delete, so nothing has been lost.
 * From here a note goes back to the list, or out of the database for good on
 * every device.
 */
class TrashViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)

    /**
     * The deleted notes as the notes list would draw them, so that the trash
     * shows the rows the user already knows, built by the same code.
     */
    private val _notes = MutableStateFlow<List<NoteWithAudioFiles>>(emptyList())
    val notes: StateFlow<List<NoteWithAudioFiles>> = _notes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _isLoading.value = true
            repository.getDeletedNotes()
                .onSuccess { deleted -> _notes.value = deleted.map { loadNoteRow(repository, it) } }
                .onFailure { e ->
                    AppLogger.e(TAG, "Could not read the trash", e)
                    _message.value = "Could not read the trash: ${e.message}"
                }
            _isLoading.value = false
        }
    }

    /** Put a note back in the list. */
    fun recover(note: Note) {
        viewModelScope.launch {
            repository.undeleteNote(note.id)
                .onSuccess { recovered ->
                    _message.value = if (recovered) "Note recovered" else "That note is not in the trash"
                    load()
                }
                .onFailure { e -> _message.value = "Could not recover the note: ${e.message}" }
        }
    }

    /**
     * Remove a note for good, with the recordings that hung on it alone.
     *
     * This is the one thing in the application that really destroys
     * something, here and on every device this one syncs with.
     */
    fun purge(note: Note) {
        viewModelScope.launch {
            repository.purgeNote(note.id)
                .onSuccess { files ->
                    _message.value = if (files > 0) {
                        "Note removed for good, with $files recording file(s)"
                    } else {
                        "Note removed for good"
                    }
                    load()
                }
                .onFailure { e -> _message.value = "Could not remove the note: ${e.message}" }
        }
    }

    /** Where a deleted note's recording is, for the player inside its row. */
    suspend fun audioFilePath(audioFileId: String): String? =
        repository.getAudioFilePath(audioFileId).getOrNull()

    fun clearMessage() {
        _message.value = null
    }

    companion object {
        private const val TAG = "TrashViewModel"
    }
}
