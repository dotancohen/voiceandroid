package com.dotancohen.voiceandroid.viewmodel

import com.dotancohen.voiceandroid.util.IssuesText
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

/** The notes either side of the one on screen, and where it sits in the list. */
data class NoteNeighbours(
    val previous: String? = null,
    val next: String? = null,
    /** 1-based position of this note in the list, 0 when it is not in it. */
    val position: Int = 0,
    val total: Int = 0,
)

/**
 * Which notes sit either side of [noteId] in [ids].
 *
 * Kept apart from the loading so the awkward places can be tested: the two
 * ends of the list, a list of one, and a note that is not in the list at all
 * (deleted while it was open, or filtered out by a search).
 */
fun neighboursIn(ids: List<String>, noteId: String): NoteNeighbours {
    val index = ids.indexOf(noteId)
    if (index < 0) return NoteNeighbours()
    return NoteNeighbours(
        previous = ids.getOrNull(index - 1),
        next = ids.getOrNull(index + 1),
        position = index + 1,
        total = ids.size,
    )
}

/**
 * Whether backing out of a note should take the note with it.
 *
 * A note is written to the database before its recording starts, so opening
 * the recorder and then changing one's mind would otherwise leave an empty
 * note in the list. Four things must all hold: the note was opened in order
 * to record, no recording is running in it, the note has arrived (a note that
 * is still loading reads as empty, and a slow note must not be mistaken for
 * an unwritten one), and it holds neither text nor a recording.
 */
fun shouldDiscardEmptyNote(
    openedToRecord: Boolean,
    recorderBusyOnThisNote: Boolean,
    note: Note?,
    audioFiles: List<AudioFile>,
): Boolean =
    openedToRecord &&
        !recorderBusyOnThisNote &&
        note != null &&
        note.content.isBlank() &&
        audioFiles.isEmpty()

/**
 * The transcriptions of one recording in the order they are shown: the one
 * the user chose first, the rest after it in the order they arrived.
 */
fun transcriptionsInDisplayOrder(
    transcriptions: List<Transcription>,
    primaryTranscriptionId: String?,
): List<Transcription> =
    transcriptions.sortedBy { if (it.id == primaryTranscriptionId) 0 else 1 }

/**
 * Which recording to open a note on: the one the user chose, or the first —
 * which is the oldest — when there is no choice or the choice is not here.
 */
fun indexOfPrimaryAudioFile(audioFiles: List<AudioFile>, primaryAudioFileId: String?): Int =
    audioFiles.indexOfFirst { it.id == primaryAudioFileId }.coerceAtLeast(0)

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

    /** The sentence of a copy removed from this phone, naming the place that holds it (FILE-26). */
    private val _copyMessage = MutableStateFlow<String?>(null)
    val copyMessage: StateFlow<String?> = _copyMessage.asStateFlow()

    fun clearCopyMessage() {
        _copyMessage.value = null
    }

    /**
     * The notes to either side of this one, for the Previous and Next
     * buttons: their ids, or null at the ends of the list.
     */
    private val _neighbours = MutableStateFlow(NoteNeighbours())
    val neighbours: StateFlow<NoteNeighbours> = _neighbours.asStateFlow()

    /**
     * Whether the recorder should open by itself, asked once and answered
     * once.
     *
     * The screen is built again every time the user comes back from the tag
     * screen or the tag dialog, and the route still says "this note was
     * opened in order to record". Without somewhere to remember that the
     * recorder has already been offered, coming back from managing the tags
     * started a second recording in the note.
     */
    private var startRecordingOffered = false

    fun consumeStartRecording(requested: Boolean): Boolean {
        if (!requested || startRecordingOffered) return false
        startRecordingOffered = true
        return true
    }

    /** The note's tags, without the system ones, in the order the core gives. */
    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    /** Whether the note carries the star (the `_marked` system tag). */
    private val _isMarked = MutableStateFlow(false)
    val isMarked: StateFlow<Boolean> = _isMarked.asStateFlow()

    /**
     * The attachment that stands for this note, if the user chose one: the
     * recording played when the note is opened, and the one whose
     * transcription the notes list shows.
     */
    private val _primaryAttachmentId = MutableStateFlow<String?>(null)
    val primaryAttachmentId: StateFlow<String?> = _primaryAttachmentId.asStateFlow()

    /** The same, given as the audio file's id rather than the attachment's. */
    private val _primaryAudioFileId = MutableStateFlow<String?>(null)
    val primaryAudioFileId: StateFlow<String?> = _primaryAudioFileId.asStateFlow()

    /**
     * The transcription that stands for each recording, where one was
     * chosen: audio file id to transcription id.
     */
    private val _primaryTranscriptionIds = MutableStateFlow<Map<String, String>>(emptyMap())
    val primaryTranscriptionIds: StateFlow<Map<String, String>> = _primaryTranscriptionIds.asStateFlow()

    /** The row shown while Previous or Next is held down, or null. */
    private val _preview = MutableStateFlow<NoteWithAudioFiles?>(null)
    val preview: StateFlow<NoteWithAudioFiles?> = _preview.asStateFlow()

    /**
     * Work out which notes sit either side of this one.
     *
     * [order] is the list the user was looking at, filters and search
     * included. A note that is not in it (opened from a link, or from the
     * automation tool) falls back to every note, newest first, which is the
     * order the list uses when nothing is filtered.
     */
    fun loadNeighbours(noteId: String, order: List<String>) {
        viewModelScope.launch {
            var ids = order
            if (noteId !in ids) {
                ids = repository.getAllNotes().getOrNull()
                    ?.filter { it.deletedAt == null }
                    ?.sortedByDescending { it.createdAt.at }
                    ?.map { it.id }
                    .orEmpty()
            }
            _neighbours.value = neighboursIn(ids, noteId)
        }
    }

    /**
     * Load the row of a neighbouring note, to show while its button is held.
     *
     * It is the same row the notes list draws, built by the same code, so a
     * preview cannot say something the list would not.
     */
    fun loadPreview(noteId: String) {
        viewModelScope.launch {
            val note = repository.getAllNotes().getOrNull()?.firstOrNull { it.id == noteId }
            _preview.value = note?.let { loadNoteRow(repository, it) }
        }
    }

    fun clearPreview() {
        _preview.value = null
    }

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

    /**
     * Put the star on this note, or take it off: the same `_marked` tag the
     * notes list toggles.
     */
    fun toggleMarked() {
        val noteId = _note.value?.id ?: return
        viewModelScope.launch {
            repository.toggleNoteMarked(noteId)
                .onSuccess { marked -> _isMarked.value = marked }
                .onFailure { e -> _error.value = "Could not change the star: ${e.message}" }
        }
    }

    /** Take this note out of the trash, from the note itself. */
    fun recoverNote() {
        val noteId = _note.value?.id ?: return
        viewModelScope.launch {
            repository.undeleteNote(noteId)
                .onSuccess { loadNote(noteId) }
                .onFailure { e -> _error.value = "Could not recover the note: ${e.message}" }
        }
    }

    /**
     * Remove this note for good, with the recordings that hang on it alone.
     * Only a note that is already in the trash can be removed this way.
     */
    fun purgeNote() {
        val noteId = _note.value?.id ?: return
        viewModelScope.launch {
            repository.purgeNote(noteId)
                .onSuccess { _deleteSuccess.value = true }
                .onFailure { e -> _error.value = "Could not remove the note: ${e.message}" }
        }
    }

    /**
     * Make one of a recording's transcriptions the one that stands for it,
     * or take the mark off the one that has it.
     */
    fun setPrimaryTranscription(transcription: Transcription) {
        viewModelScope.launch {
            val audioFileId = transcription.audioFileId
            val current = _primaryTranscriptionIds.value[audioFileId]
            val target = if (current == transcription.id) null else transcription.id
            repository.setPrimaryTranscription(audioFileId, target)
                .onSuccess { loadPrimaryTranscriptions(_audioFiles.value) }
                .onFailure { e -> _error.value = "Could not set the main transcription: ${e.message}" }
        }
    }

    private suspend fun loadPrimaryTranscriptions(audioFiles: List<AudioFile>) {
        val chosen = mutableMapOf<String, String>()
        for (audioFile in audioFiles) {
            repository.getPrimaryTranscription(audioFile.id).getOrNull()?.let { id ->
                chosen[audioFile.id] = id
            }
        }
        _primaryTranscriptionIds.value = chosen
    }

    /**
     * Make one of this note's recordings the one that stands for it, or take
     * the mark off the one that has it.
     */
    /** Where the copies of the recording being asked about are, as lines; null when no one asked. */
    private val _locationLines = MutableStateFlow<List<String>?>(null)
    val locationLines: StateFlow<List<String>?> = _locationLines.asStateFlow()

    /** Where a recording's copies are (FILE-22): this phone's folder is compared first. */
    fun showLocations(audioFile: AudioFile) {
        viewModelScope.launch {
            repository.checkFilesHere()
            val names = repository.deviceNames().getOrNull().orEmpty()
            val here = repository.getThisDeviceId().getOrNull().orEmpty()
            val madeHereButMissing = repository.madeHereButMissing(audioFile.id).getOrNull()
            repository.fileLocations(audioFile.id)
                .onSuccess { locations ->
                    _locationLines.value = IssuesText.locationLines(locations, names, here, madeHereButMissing) { millis ->
                        java.text.DateFormat.getDateTimeInstance().format(java.util.Date(millis))
                    }
                }
                .onFailure { e -> _error.value = "Could not read where the copies are: ${e.message}" }
        }
    }

    fun dismissLocations() {
        _locationLines.value = null
    }

    /** Remove this phone's copy of a recording once another place confirms it holds the file (FILE-26). */
    fun removeLocalCopy(audioFile: AudioFile) {
        val noteId = _note.value?.id ?: return
        viewModelScope.launch {
            repository.removeLocalCopy(audioFile.id)
                .onSuccess { sentence ->
                    _copyMessage.value = sentence
                    loadNote(noteId)
                }
                .onFailure { e -> _error.value = "Not removed: ${e.message}" }
        }
    }

    fun setPrimaryAudioFile(audioFile: AudioFile) {
        val noteId = _note.value?.id ?: return
        viewModelScope.launch {
            val attachments = repository.getAttachmentsForNote(noteId).getOrNull().orEmpty()
            val attachment = attachments.firstOrNull { it.attachmentId == audioFile.id }
            if (attachment == null) {
                _error.value = "That recording is not attached to this note"
                return@launch
            }
            // Pressing the star of the one that already has it puts the note
            // back to "the first recording", which is a choice too.
            val target = if (_primaryAttachmentId.value == attachment.id) null else attachment.id
            repository.setPrimaryAttachment(noteId, target)
                .onSuccess { loadNote(noteId) }
                .onFailure { e -> _error.value = "Could not set the main recording: ${e.message}" }
        }
    }

    /** Queue one of this note's recordings for transcription on the phone. */
    fun transcribeOnDevice(audioFile: AudioFile, modelId: String? = null, language: String? = null) {
        val problem = OnDeviceTranscriber.enqueue(
            getApplication(),
            audioFile.id,
            audioFile.filename,
            language,
            modelId,
            audioFile.durationSeconds,
        )
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

    /** Say something went wrong, from a screen that found it out for itself. */
    fun reportError(message: String) {
        _error.value = message
    }

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
                    // A note in the trash is not in the list, and it can be
                    // opened from there, so it is looked for in both places.
                    val foundNote = notesList.find { it.id == noteId }
                        ?: repository.getDeletedNotes().getOrNull()?.find { it.id == noteId }
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

                        // Which recording stands for the note, if one was chosen
                        _primaryAttachmentId.value = repository.getPrimaryAttachment(noteId).getOrNull()
                        _primaryAudioFileId.value = _primaryAttachmentId.value?.let { attachmentId ->
                            repository.getAttachmentsForNote(noteId).getOrNull()
                                ?.firstOrNull { it.id == attachmentId }
                                ?.attachmentId
                        }

                        // The note's tags and its star, for the row under the
                        // text: the same two things the notes list shows.
                        repository.getTagsForNote(noteId)
                            .onSuccess { tags ->
                                _tags.value = com.dotancohen.voiceandroid.data.TagTree.withoutSystemTags(tags)
                                    .map { it.name }
                            }
                            .onFailure { e -> AppLogger.w(TAG, "Failed to load tags: ${e.message}") }
                        _isMarked.value = repository.isNoteMarked(noteId).getOrNull() ?: false

                        // Load audio files for this note
                        repository.getAudioFilesForNote(noteId)
                            .onSuccess { files ->
                                // Oldest first, the same order as the list
                                val filteredFiles = audioFilesOldestFirst(files.filter { it.deletedAt == null })
                                _audioFiles.value = filteredFiles

                                // Load transcriptions for each audio file
                                loadTranscriptionsForAudioFiles(filteredFiles)
                                loadPrimaryTranscriptions(filteredFiles)

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
     * Resolve the text conflict with the given result. Syncs to every device.
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
    fun toggleTranscriptionFlag(transcription: Transcription, tag: String) {
        viewModelScope.launch {
            val newState = transcription.toggleFlag(tag)
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
     * Replace the text of a transcription with the user's own.
     *
     * The flags are left exactly as they are: whether a corrected
     * transcription is still "original" is the user's judgement, made in the
     * same dialogue, and this must not answer it for them.
     *
     * Still being tried out, so only the debug build offers a way in.
     */
    fun editTranscriptionContent(transcription: Transcription, content: String) {
        viewModelScope.launch {
            repository.updateTranscription(transcription.id, content)
                .onSuccess {
                    AppLogger.i(TAG, "Transcription ${transcription.id} edited by hand")
                    updateLocalTranscriptionContent(transcription.audioFileId, transcription.id, content)
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Failed to edit transcription", e)
                    _error.value = "Could not save the transcription: ${e.message}"
                }
        }
    }

    private fun updateLocalTranscriptionContent(audioFileId: String, transcriptionId: String, content: String) {
        val currentMap = _transcriptions.value.toMutableMap()
        val list = currentMap[audioFileId]?.toMutableList() ?: return
        val index = list.indexOfFirst { it.id == transcriptionId }
        if (index >= 0) {
            list[index] = list[index].copy(content = content)
            currentMap[audioFileId] = list
            _transcriptions.value = currentMap
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
