package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.data.Note
import com.dotancohen.voiceandroid.data.isFinished
import com.dotancohen.voiceandroid.data.isPending
import com.dotancohen.voiceandroid.data.modelId
import com.dotancohen.voiceandroid.data.TagColours
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.transcription.OnDeviceTranscriber
import com.dotancohen.voiceandroid.transcription.TranscriptionStage
import com.dotancohen.voiceandroid.transcription.TranscriptionPreferences
import com.dotancohen.voiceandroid.util.AppLogger
import com.dotancohen.voiceandroid.util.Magic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Data class combining a note with its audio file attachments, marked state, and display info.
 */
data class NoteWithAudioFiles(
    val note: Note,
    val audioFiles: List<AudioFile> = emptyList(),
    val isMarked: Boolean = false,
    val durationSeconds: Int? = null,
    val tags: List<String> = emptyList(),
    /**
     * For every recording that already has a finished transcription, the
     * models those transcriptions were made with. A recording transcribed by
     * a service that does not name a model maps to an empty set.
     */
    val transcribedModels: Map<String, Set<String>> = emptyMap(),
    /**
     * The opening lines of the first transcription of any recording on this
     * note, without the empty ones.
     *
     * A list rather than a single line because the user chooses how many
     * lines a row shows (Settings → Advanced), and a preview shows twice
     * that many. Only the first [TRANSCRIPTION_PREVIEW_LINES] are kept: a
     * transcription of a long recording is not worth holding in memory for
     * every row of the list.
     */
    val transcriptionLines: List<String> = emptyList(),
    /**
     * Recordings with a transcription that has been asked for and has not
     * arrived: the row shows a clock over the transcribe mark for these.
     */
    val pendingAudioIds: Set<String> = emptySet(),
    /**
     * The recording that stands for the note: the one the user marked with
     * the star, or the oldest when none is marked. Its transcription is what
     * [transcriptionLines] holds, so the row can show which recording the
     * lines under it came from.
     */
    val leadAudioFileId: String? = null,
    /**
     * The recording the user actually marked, if they marked one. Null while
     * the note falls back to its oldest recording, which is why this is kept
     * apart from [leadAudioFileId]: a star drawn as set before anybody set it
     * would leave the user with nothing to press.
     */
    val chosenAudioFileId: String? = null
) {
    /** Recordings that already have a finished transcription. */
    val transcribedAudioIds: Set<String> get() = transcribedModels.keys
}

/** How many lines of a transcription a row keeps for display. */
const val TRANSCRIPTION_PREVIEW_LINES = Magic.TRANSCRIPTION_PREVIEW_LINES

/** The opening non-empty lines of a piece of text, at most [limit] of them. */
fun openingLines(text: String?, limit: Int = TRANSCRIPTION_PREVIEW_LINES): List<String> =
    text?.lineSequence()
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?.take(limit)
        ?.toList()
        .orEmpty()

/**
 * A note's recordings, oldest first.
 *
 * That is the order they were made in, the order they are numbered in the
 * list (🔊1, 🔊2), and the order the note plays them in; the one without a
 * star is the one that stands for the note. The core returns them in this
 * order already — the sort is here so that the order the user sees is
 * decided in one place and cannot depend on a query somewhere else.
 *
 * A recording carries the moment the audio was made where that is known
 * (a file imported from elsewhere keeps its own date), and otherwise the
 * moment it arrived here.
 */
fun audioFilesOldestFirst(files: List<AudioFile>): List<AudioFile> =
    files.sortedWith(
        compareBy<AudioFile> { (it.fileCreatedAt ?: it.importedAt).at }.thenBy { it.id }
    )

/**
 * Everything a row of the notes list shows for one note.
 *
 * Written once and used by the list and by the preview a note screen shows
 * when Next or Previous is held down, so that the two cannot drift apart:
 * the preview is the row, not something that resembles it.
 */
suspend fun loadNoteRow(repository: VoiceRepository, note: Note): NoteWithAudioFiles {
    val audioFiles = audioFilesOldestFirst(
        repository.getAudioFilesForNote(note.id)
            .getOrNull()
            ?.filter { it.deletedAt == null }
            ?: emptyList()
    )

    // The recording that stands for the note: the one the user marked, or
    // the first one, which is the oldest.
    val primaryAudioFileId = repository.getPrimaryAttachment(note.id).getOrNull()?.let { attachmentId ->
        repository.getAttachmentsForNote(note.id).getOrNull()
            ?.firstOrNull { it.id == attachmentId }
            ?.attachmentId
    }
    val chosenAudioFile = audioFiles.firstOrNull { it.id == primaryAudioFileId }
    val leadAudioFile = chosenAudioFile ?: audioFiles.firstOrNull()

    // The display cache holds what the row needs; only a note whose cache is
    // missing or unreadable costs a query.
    var isMarked = false
    var durationSeconds: Int? = null
    var tags: List<String> = emptyList()

    note.listDisplayCache?.let { cache ->
        try {
            val json = org.json.JSONObject(cache)
            isMarked = json.optBoolean("marked", false)
            durationSeconds = if (json.has("duration_seconds")) {
                json.optInt("duration_seconds", 0).takeIf { it > 0 }
            } else null
            val tagsArray = json.optJSONArray("tags")
            if (tagsArray != null) {
                tags = (0 until tagsArray.length()).mapNotNull { i ->
                    tagsArray.optString(i).takeIf { it.isNotEmpty() }
                }
            }
        } catch (e: Exception) {
            isMarked = repository.isNoteMarked(note.id).getOrNull() ?: false
        }
    } ?: run {
        isMarked = repository.isNoteMarked(note.id).getOrNull() ?: false
    }

    // Which recordings already have a transcription, and with which model:
    // the first gives the mark on the attachment button, the second lets a
    // bulk transcription skip work that has been done. Only notes with
    // recordings cost a query here.
    val transcribedModels = mutableMapOf<String, Set<String>>()
    val pendingAudioIds = mutableSetOf<String>()
    var transcriptionLines: List<String> = emptyList()
    for (audioFile in audioFiles) {
        val rows = repository.getTranscriptionsForAudioFile(audioFile.id)
            .getOrNull()
            ?.filter { it.deletedAt == null }
            .orEmpty()
        val done = rows.filter { it.isFinished }
        // Waiting, rather than finished or failed: the row still says
        // "Pending...", which is what the phone writes before it starts.
        if (rows.any { it.isPending }) {
            pendingAudioIds.add(audioFile.id)
        }
        if (done.isNotEmpty()) {
            transcribedModels[audioFile.id] = done.mapNotNull { it.modelId }.toSet()
            // The line under the note is the transcription of the recording
            // that stands for it, not of whichever recording came back first.
            if (audioFile.id == leadAudioFile?.id) {
                val primaryTranscriptionId = repository.getPrimaryTranscription(audioFile.id).getOrNull()
                val chosen = done.firstOrNull { it.id == primaryTranscriptionId } ?: done.first()
                transcriptionLines = openingLines(chosen.content)
            }
        }
    }

    return NoteWithAudioFiles(
        note, audioFiles, isMarked, durationSeconds, tags, transcribedModels,
        transcriptionLines, pendingAudioIds, leadAudioFile?.id, chosenAudioFile?.id
    )
}

/** One tag as offered in the tag dialog of a multi-note selection. */
data class TagSelection(
    val tag: TagWithPath,
    /** How many of the selected notes carry this tag. */
    val noteCount: Int
)

class NotesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)

    private val _notes = MutableStateFlow<List<NoteWithAudioFiles>>(emptyList())
    val notes: StateFlow<List<NoteWithAudioFiles>> = _notes.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /**
     * How many times the list has finished loading.
     *
     * The spotlight waits for the reload that runs on the way back from a
     * Note: without it the animation ran against the old list and was over
     * by the time the rows appeared, which is why it could barely be seen.
     */
    private val _loadCount = MutableStateFlow(0)
    val loadCount: StateFlow<Int> = _loadCount.asStateFlow()

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

    // Notes picked by long-pressing in the list, and what the bulk actions say
    private val _selectedNoteIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedNoteIds: StateFlow<Set<String>> = _selectedNoteIds.asStateFlow()

    /** Tags offered in the tag dialog, with how many selected notes have each. */
    private val _tagOptions = MutableStateFlow<List<TagSelection>>(emptyList())
    val tagOptions: StateFlow<List<TagSelection>> = _tagOptions.asStateFlow()

    /** One-off text for the snackbar (queued, deleted, failed). */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        loadNotes()
        // A transcription that finished adds a mark to its note's button
        viewModelScope.launch {
            OnDeviceTranscriber.finished.collect { refresh() }
        }
        // And one that was asked for puts a clock on it, before any row
        // exists in the database: a recording sent to be transcribed from
        // this screen showed nothing at all until its turn came.
        viewModelScope.launch {
            OnDeviceTranscriber.queued.collect { queued ->
                val waiting = queued.map { it.audioFileId }.toSet() +
                    setOfNotNull(OnDeviceTranscriber.current.value?.audioFileId)
                _notes.value = _notes.value.map { row ->
                    val mine = row.audioFiles.map { it.id }.filter { it in waiting }.toSet()
                    if (mine.isEmpty()) row else row.copy(pendingAudioIds = row.pendingAudioIds + mine)
                }
            }
        }
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
    /**
     * Recordings queued for transcription on this phone but not yet started.
     *
     * The database row that says "Pending..." is written when the work
     * begins, so a recording sent from the list's Transcribe button has
     * nothing to show until its turn comes. The queue itself is asked as
     * well, and the two are added together.
     */
    private fun queuedAudioFileIds(): Set<String> {
        val queued = OnDeviceTranscriber.queued.value.map { it.audioFileId }
        val current = OnDeviceTranscriber.current.value
            ?.takeIf { it.stage < TranscriptionStage.Done }
            ?.audioFileId
        return (queued + listOfNotNull(current)).toSet()
    }

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
                        .sortedByDescending { it.createdAt.at }
                }
            } else {
                // Search notes
                repository.searchNotes(searchQuery).map { searchResult ->
                    _ambiguousTags.value = searchResult.ambiguousTags
                    _notFoundTags.value = searchResult.notFoundTags
                    // Filter out deleted notes and sort by date (newest first)
                    searchResult.notes
                        .filter { it.deletedAt == null }
                        .sortedByDescending { it.createdAt.at }
                }
            }

            notesResult
                .onSuccess { filteredNotes ->
                    // Load audio files and display info for each note
                    val waiting = queuedAudioFileIds()
                    val notesWithAudio = filteredNotes.map { note ->
                        val row = loadNoteRow(repository, note)
                        val alsoWaiting = row.audioFiles.map { it.id }.filter { it in waiting }
                        if (alsoWaiting.isEmpty()) row
                        else row.copy(pendingAudioIds = row.pendingAudioIds + alsoWaiting)
                    }

                    _notes.value = notesWithAudio
                    loadTagColours()
                    _loadCount.value = _loadCount.value + 1
                    // Notes that disappeared (deleted, or filtered out) leave the selection
                    val present = notesWithAudio.map { it.note.id }.toSet()
                    if (_selectedNoteIds.value.any { it !in present }) {
                        _selectedNoteIds.value = _selectedNoteIds.value.filter { it in present }.toSet()
                    }
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
     * Make one of a note's recordings the one that stands for it, or take the
     * mark off the one that has it.
     *
     * The same choice as the star inside the note, offered here so that the
     * recording whose transcription the row shows can be changed without
     * opening the note.
     */
    fun setPrimaryAudioFile(noteId: String, audioFileId: String) {
        viewModelScope.launch {
            val attachments = repository.getAttachmentsForNote(noteId).getOrNull().orEmpty()
            val attachment = attachments.firstOrNull { it.attachmentId == audioFileId }
            if (attachment == null) {
                _error.value = "That recording is not attached to this note"
                return@launch
            }
            val current = repository.getPrimaryAttachment(noteId).getOrNull()
            // Pressing the star of the one that already has it puts the note
            // back to "the first recording", which is a choice too.
            val target = if (current == attachment.id) null else attachment.id
            repository.setPrimaryAttachment(noteId, target)
                .onSuccess { refresh() }
                .onFailure { e -> _error.value = "Could not set the main recording: ${e.message}" }
        }
    }

    /**
     * The colours chosen for Tags, by Tag name.
     *
     * A Tag with no chosen colour is absent from the map: the colour
     * calculated from its name is used, and nothing is stored for it. Loaded
     * with the list, so a colour chosen on another device appears after a
     * sync like everything else.
     */
    private val _tagColours = MutableStateFlow<Map<String, String>>(emptyMap())
    val tagColours: StateFlow<Map<String, String>> = _tagColours.asStateFlow()

    private suspend fun loadTagColours() {
        val chosen = mutableMapOf<String, String>()
        for (tag in repository.getAllTags().getOrNull().orEmpty()) {
            repository.getSetting(TagColours.settingKey(tag.name)).getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { chosen[tag.name] = it }
        }
        _tagColours.value = chosen
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

    // =========================================================================
    // Selecting several notes (long-press in the list) and acting on them
    // =========================================================================

    fun clearMessage() { _message.value = null }

    /**
     * The note the user opened last, so that the list can point out its row
     * when they come back. Reading it clears it.
     */
    private var lastOpenedNoteId: String? = null

    fun noteOpened(noteId: String) { lastOpenedNoteId = noteId }

    fun consumeLastOpenedNote(): String? {
        val id = lastOpenedNoteId
        lastOpenedNoteId = null
        return id
    }

    fun toggleSelection(noteId: String) {
        val current = _selectedNoteIds.value
        _selectedNoteIds.value = if (noteId in current) current - noteId else current + noteId
    }

    /**
     * Add a note to the selection, whether or not it is in it already.
     *
     * Separate from [toggleSelection] because a finger dragged down the list
     * passes over a row more than once, and a toggle would tick and untick
     * it as it went.
     */
    fun selectNote(noteId: String) {
        _selectedNoteIds.value = _selectedNoteIds.value + noteId
    }

    fun clearSelection() {
        _selectedNoteIds.value = emptySet()
    }

    fun selectedNotes(): List<NoteWithAudioFiles> =
        _notes.value.filter { it.note.id in _selectedNoteIds.value }

    /** Every recording attached to the selected notes. */
    fun selectedAudioFiles(): List<AudioFile> = selectedNotes().flatMap { it.audioFiles }

    /** How many of the selected recordings already have a transcription. */
    fun selectedAlreadyTranscribed(): Int =
        selectedNotes().sumOf { row -> row.audioFiles.count { it.id in row.transcribedAudioIds } }

    /** Delete every selected note (a soft delete, like the button in a note). */
    /** Delete one Note from its row in the list. It goes to the trash bin. */
    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            repository.deleteNote(noteId)
                .onSuccess {
                    _message.value = "Note deleted"
                    refresh()
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Could not delete note ${noteId.take(8)}", e)
                    _error.value = "Could not delete the Note: ${e.message}"
                }
        }
    }

    fun deleteSelected() {
        val ids = _selectedNoteIds.value.toList()
        if (ids.isEmpty()) return
        viewModelScope.launch {
            var deleted = 0
            var failed = 0
            for (id in ids) {
                repository.deleteNote(id)
                    .onSuccess { if (it) deleted++ else failed++ }
                    .onFailure { e ->
                        failed++
                        AppLogger.e(TAG, "Could not delete note ${id.take(8)}", e)
                    }
            }
            _message.value = if (failed == 0) "$deleted note(s) deleted" else "$deleted deleted, $failed failed"
            clearSelection()
            refresh()
        }
    }

    /**
     * Queue the recordings of the selected notes for transcription. They run
     * one at a time; [OnDeviceTranscriber] holds the queue. A recording that
     * already has a transcription from the chosen model is skipped: asking
     * the same model twice is only possible from inside a note, where it is
     * one deliberate tap on one recording.
     */
    fun transcribeSelected(modelId: String?, language: String?) {
        val model = modelId ?: TranscriptionPreferences(getApplication<Application>()).modelId
        val candidates = selectedNotes().flatMap { note ->
            note.audioFiles.map { audioFile -> audioFile to note.transcribedModels[audioFile.id].orEmpty() }
        }
        if (candidates.isEmpty()) {
            _message.value = "The selected notes have no recordings"
            return
        }
        val todo = candidates.filter { (_, models) -> model !in models }
        val skipped = candidates.size - todo.size
        var queued = 0
        var problem: String? = null
        for ((audioFile, _) in todo) {
            val error = OnDeviceTranscriber.enqueue(
                getApplication(),
                audioFile.id,
                audioFile.filename,
                language,
                model,
                audioFile.durationSeconds,
            )
            if (error == null) queued++ else problem = error
        }
        val skippedText = if (skipped > 0) ", $skipped skipped (already done with this model)" else ""
        _message.value = when {
            queued == 0 && skipped > 0 -> "Nothing queued: all $skipped recording(s) already have a transcription from this model. Open a note to transcribe one again."
            queued == 0 -> problem ?: "Nothing to transcribe"
            problem == null -> "$queued recording(s) queued, one at a time$skippedText"
            else -> "$queued queued$skippedText; $problem"
        }
        clearSelection()
    }

    /**
     * Merge the selected notes into the oldest of them: its text keeps what it
     * had and gains the others' below, the recordings and tags move across,
     * and the emptied notes are deleted.
     */
    fun mergeSelected() {
        val chosen = selectedNotes().sortedBy { it.note.createdAt.at }
        if (chosen.size < 2) {
            _message.value = "Select at least two notes to merge"
            return
        }
        viewModelScope.launch {
            var survivor = chosen.first().note.id
            var merged = 0
            var failed = 0
            // Why the first failure happened, so that a merge which cannot
            // work says so on the screen. Reporting only a count is what let
            // a merge that failed every single time look like a merge that
            // did nothing.
            var why: String? = null
            for (other in chosen.drop(1)) {
                repository.mergeNotes(survivor, other.note.id)
                    .onSuccess { id ->
                        survivor = id
                        merged++
                    }
                    .onFailure { e ->
                        failed++
                        if (why == null) why = e.message ?: e.toString()
                        AppLogger.e(TAG, "Could not merge note ${other.note.id.take(8)}", e)
                    }
            }
            _message.value = if (failed == 0) {
                "${merged + 1} notes merged into one"
            } else {
                "$merged merged, $failed could not be: ${why ?: "no reason given"}"
            }
            clearSelection()
            refresh()
        }
    }

    /** Load the tag list for the dialog, with a count of selected notes per tag. */
    fun loadTagOptions() {
        val noteIds = _selectedNoteIds.value.toList()
        viewModelScope.launch {
            val tags = repository.getAllTags().getOrNull().orEmpty()
                .filter { !it.name.startsWith("_") }
            val counts = mutableMapOf<String, Int>()
            for (noteId in noteIds) {
                for (tag in repository.getTagsForNote(noteId).getOrNull().orEmpty()) {
                    counts[tag.id] = (counts[tag.id] ?: 0) + 1
                }
            }
            _tagOptions.value = tagsWithPaths(tags).map { TagSelection(it, counts[it.tag.id] ?: 0) }
        }
    }

    /**
     * Tapping a tag in the dialog: if every selected note already has it the
     * tag is removed from all of them, otherwise it is added to those missing it.
     */
    /**
     * Create a Tag and put it on every selected Note.
     *
     * The Tag list of the dialogue is loaded again afterwards, so the new Tag
     * appears there ticked rather than the user having to look for it.
     */
    fun createTagForSelected(name: String) {
        val noteIds = _selectedNoteIds.value.toList()
        viewModelScope.launch {
            repository.createTag(name, null)
                .onSuccess { tagId ->
                    for (noteId in noteIds) {
                        repository.addTagToNote(noteId, tagId).onFailure { e ->
                            _error.value = "Could not put the Tag on a Note: ${e.message}"
                        }
                    }
                    loadTagOptions()
                    refresh()
                }
                .onFailure { e -> _error.value = "Could not create the Tag: ${e.message}" }
        }
    }

    fun toggleTagOnSelected(tagId: String) {
        val noteIds = _selectedNoteIds.value.toList()
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            val hasTag = mutableListOf<String>()
            for (noteId in noteIds) {
                if (repository.getTagsForNote(noteId).getOrNull().orEmpty().any { it.id == tagId }) {
                    hasTag.add(noteId)
                }
            }
            val removing = hasTag.size == noteIds.size
            for (noteId in noteIds) {
                val result = if (removing) {
                    repository.removeTagFromNote(noteId, tagId)
                } else {
                    if (noteId in hasTag) continue else repository.addTagToNote(noteId, tagId)
                }
                result.onFailure { e ->
                    _error.value = "Failed to change tags: ${e.message}"
                    AppLogger.e(TAG, "Tagging note ${noteId.take(8)} failed", e)
                }
            }
            loadTagOptions()
            refresh()
        }
    }

    companion object { private const val TAG = "NotesViewModel" }
}
