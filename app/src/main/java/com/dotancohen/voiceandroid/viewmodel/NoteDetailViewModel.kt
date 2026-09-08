package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.data.Note
import com.dotancohen.voiceandroid.data.Transcription
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.transcription.OnDeviceTranscriber
import com.dotancohen.voiceandroid.transcription.TranscriptionJob
import com.dotancohen.voiceandroid.transcription.TranscriptionStage
import uniffi.voicecore.ConflictData
import uniffi.voicecore.VersionData
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

    /** The on-device transcription running now (any note), for the progress card. */
    val onDeviceJob: StateFlow<TranscriptionJob?> = OnDeviceTranscriber.current

    private val _transcribeMessage = MutableStateFlow<String?>(null)
    val transcribeMessage: StateFlow<String?> = _transcribeMessage.asStateFlow()

    init {
        // When a job for one of this note's recordings ends, show the new transcription
        viewModelScope.launch {
            OnDeviceTranscriber.finished.collect { job ->
                if (_audioFiles.value.any { it.id == job.audioFileId }) {
                    loadTranscriptionsForAudioFiles(_audioFiles.value)
                    _transcribeMessage.value = when (job.stage) {
                        TranscriptionStage.Done -> "Transcription finished (${job.message.removePrefix("Done in ")})"
                        else -> "Transcription failed: ${job.message}"
                    }
                }
            }
        }
    }

    /** Queue one of this note's recordings for transcription on the phone. */
    fun transcribeOnDevice(audioFile: AudioFile, modelId: String? = null, language: String? = null) {
        val problem = OnDeviceTranscriber.enqueue(getApplication(), audioFile.id, audioFile.filename, language, modelId)
        _transcribeMessage.value = problem ?: "Transcription of ${audioFile.filename} queued"
        if (problem == null) {
            // Show the "Pending..." row as soon as it exists
            viewModelScope.launch {
                kotlinx.coroutines.delay(1500)
                loadTranscriptionsForAudioFiles(_audioFiles.value)
            }
        }
    }

    fun clearTranscribeMessage() { _transcribeMessage.value = null }

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

    // Download state
    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    // Download result message for user feedback
    private val _downloadMessage = MutableStateFlow<String?>(null)
    val downloadMessage: StateFlow<String?> = _downloadMessage.asStateFlow()

    // Track which audio files are available locally
    private val _audioFileAvailability = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val audioFileAvailability: StateFlow<Map<String, Boolean>> = _audioFileAvailability.asStateFlow()

    // Audio files not on this device that CAN be fetched (their device uploaded them)
    private val _downloadableCount = MutableStateFlow(0)
    val downloadableCount: StateFlow<Int> = _downloadableCount.asStateFlow()

    // Audio files not on this device that can NOT be fetched yet (not uploaded by their device)
    private val _pendingUploadCount = MutableStateFlow(0)
    val pendingUploadCount: StateFlow<Int> = _pendingUploadCount.asStateFlow()

    // Conflict state
    private val _conflictTypes = MutableStateFlow<List<String>>(emptyList())
    val conflictTypes: StateFlow<List<String>> = _conflictTypes.asStateFlow()

    // The conflicts themselves (which devices disagreed, on what)
    private val _conflicts = MutableStateFlow<List<ConflictData>>(emptyList())
    val conflicts: StateFlow<List<ConflictData>> = _conflicts.asStateFlow()

    private val _isAcceptingConflicts = MutableStateFlow(false)
    val isAcceptingConflicts: StateFlow<Boolean> = _isAcceptingConflicts.asStateFlow()

    // Version history of the note's content (oldest first), loaded on demand
    private val _history = MutableStateFlow<List<VersionData>>(emptyList())
    val history: StateFlow<List<VersionData>> = _history.asStateFlow()

    /** The two sides and the base of the note's text conflict, for side-by-side resolution. */
    data class ConflictSides(
        val conflict: ConflictData,
        val sideA: VersionData?,
        val sideB: VersionData?,
        val base: VersionData?,
        val merged: VersionData?,
    )

    private val _conflictSides = MutableStateFlow<ConflictSides?>(null)
    val conflictSides: StateFlow<ConflictSides?> = _conflictSides.asStateFlow()

    /**
     * Load a note and its audio files by ID.
     */
    fun loadNote(noteId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            _conflictTypes.value = emptyList()
            _conflicts.value = emptyList()

            // Get all notes and find the one we need
            // (We could add a getNoteById method to the repository, but this works for now)
            repository.getAllNotes()
                .onSuccess { notesList ->
                    val foundNote = notesList.find { it.id == noteId }
                    _note.value = foundNote

                    if (foundNote != null) {
                        // Load conflict types for this note
                        repository.getNoteConflictTypes(noteId)
                            .onSuccess { types ->
                                _conflictTypes.value = types
                            }
                            .onFailure { exception ->
                                AppLogger.w(TAG, "Failed to load conflict types: ${exception.message}")
                            }
                        if (_conflictTypes.value.isNotEmpty()) {
                            repository.getNoteConflicts(noteId)
                                .onSuccess { list -> _conflicts.value = list }
                                .onFailure { exception ->
                                    AppLogger.w(TAG, "Failed to load conflicts: ${exception.message}")
                                }
                        }

                        // Load audio files for this note
                        repository.getAudioFilesForNote(noteId)
                            .onSuccess { files ->
                                val filteredFiles = files.filter { it.deletedAt == null }
                                _audioFiles.value = filteredFiles

                                // Load transcriptions for each audio file
                                loadTranscriptionsForAudioFiles(filteredFiles)

                                // Check which audio files are available locally
                                checkAudioFileAvailability(filteredFiles)
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
     * Check which audio files are available locally.
     */
    private suspend fun checkAudioFileAvailability(audioFiles: List<AudioFile>) {
        AppLogger.i(TAG, "Checking availability for ${audioFiles.size} audio files")
        val availabilityMap = mutableMapOf<String, Boolean>()
        var downloadable = 0
        var pendingUpload = 0

        for (audioFile in audioFiles) {
            val exists = repository.audioFileExistsLocally(audioFile.id).getOrElse { e ->
                AppLogger.w(TAG, "Failed to check availability for ${audioFile.id}: ${e.message}")
                false
            }
            availabilityMap[audioFile.id] = exists
            if (!exists) {
                if (audioFile.isInCloud) downloadable++ else pendingUpload++
            }
            AppLogger.d(TAG, "Audio file ${audioFile.id} (${audioFile.filename}): local=$exists inCloud=${audioFile.isInCloud}")
        }

        AppLogger.i(TAG, "Audio file availability: ${availabilityMap.values.count { it }} local, $downloadable downloadable, $pendingUpload not uploaded yet")

        _audioFileAvailability.value = availabilityMap
        _downloadableCount.value = downloadable
        _pendingUploadCount.value = pendingUpload
    }

    /**
     * Check if any audio files of this note are in the cloud but not on this device.
     */
    fun hasDownloadableAudioFiles(): Boolean = _downloadableCount.value > 0

    /**
     * Download this note's missing audio files from cloud storage (on demand).
     */
    fun downloadMissingAudioFiles() {
        val noteId = _note.value?.id ?: return
        if (_isDownloading.value) return
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadMessage.value = null
            AppLogger.i(TAG, "Download requested for note ${noteId.take(8)}: ${_audioFiles.value.size} audio files, dir=${repository.getAudioFileDirectory()}")

            repository.downloadAudioFilesForNote(noteId)
                .onSuccess { result ->
                    AppLogger.i(TAG, "Download finished: ${result.describe()}")
                    if (result.errors.isNotEmpty()) {
                        AppLogger.w(TAG, "Download errors: ${result.errors.joinToString("; ")}")
                    }
                    _downloadMessage.value = "Media: ${result.describe()}"
                    // Refresh availability so the player picks up the new files
                    checkAudioFileAvailability(_audioFiles.value)
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Download failed", e)
                    _downloadMessage.value = "Download failed: ${e.message}"
                }

            _isDownloading.value = false
        }
    }

    /**
     * Clear the download message after it has been shown.
     */
    fun clearDownloadMessage() {
        _downloadMessage.value = null
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

    /**
     * Load every version of this note's content (for the history dialog).
     */
    fun loadHistory() {
        val noteId = _note.value?.id ?: return
        viewModelScope.launch {
            repository.getNoteHistory(noteId)
                .onSuccess { _history.value = it }
                .onFailure { e ->
                    AppLogger.w(TAG, "Failed to load history: ${e.message}")
                    _error.value = "Could not load history: ${e.message}"
                }
        }
    }

    /**
     * Make an earlier version the current content. A restore is an ordinary
     * edit: nothing is lost and it syncs like any other change.
     */
    fun restoreVersion(version: VersionData) {
        val noteId = _note.value?.id ?: return
        viewModelScope.launch {
            _isSaving.value = true
            repository.updateNote(noteId, version.content)
                .onSuccess {
                    AppLogger.i(TAG, "Restored note $noteId to version ${version.id}")
                    loadNote(noteId)
                    loadHistory()
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Failed to restore version", e)
                    _error.value = "Could not restore: ${e.message}"
                }
            _isSaving.value = false
        }
    }

    /**
     * Load the sides of the note's text conflict for side-by-side resolution.
     */
    fun loadConflictSides() {
        val conflict = _conflicts.value.firstOrNull { it.kind == "text" && it.entityType == "note" } ?: return
        viewModelScope.launch {
            val a = repository.getVersion(conflict.versionAId).getOrNull()
            val b = repository.getVersion(conflict.versionBId).getOrNull()
            val base = conflict.baseVersionId?.let { repository.getVersion(it).getOrNull() }
            val merged = repository.getVersion(conflict.mergeVersionId).getOrNull()
            _conflictSides.value = ConflictSides(conflict, a, b, base, merged)
        }
    }

    fun clearConflictSides() {
        _conflictSides.value = null
    }

    /**
     * Resolve the text conflict with the given result. Syncs to every peer.
     */
    fun resolveConflictWith(content: String) {
        val sides = _conflictSides.value ?: return
        val noteId = _note.value?.id ?: return
        viewModelScope.launch {
            _isSaving.value = true
            repository.resolveConflictWithContent(sides.conflict.id, content)
                .onSuccess {
                    AppLogger.i(TAG, "Resolved conflict ${sides.conflict.id} on note $noteId")
                    _conflictSides.value = null
                    loadNote(noteId)
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Failed to resolve conflict", e)
                    _error.value = "Could not resolve: ${e.message}"
                }
            _isSaving.value = false
        }
    }

    /**
     * Accept the merged values of every conflict on this note as they stand.
     * To fix the text instead, the user edits and saves: a save resolves too.
     */
    fun acceptConflicts() {
        val noteId = _note.value?.id ?: return
        if (_isEditing.value) {
            _error.value = "Save or cancel your edit first"
            return
        }
        viewModelScope.launch {
            _isAcceptingConflicts.value = true
            repository.acceptNoteConflicts(noteId)
                .onSuccess { n ->
                    AppLogger.i(TAG, "Accepted $n conflict(s) on note $noteId")
                    loadNote(noteId)
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Failed to accept conflicts on note $noteId", e)
                    _error.value = "Could not accept the merge: ${e.message}"
                }
            _isAcceptingConflicts.value = false
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
