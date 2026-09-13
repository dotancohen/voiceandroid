package com.dotancohen.voiceandroid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.data.isFinished
import com.dotancohen.voiceandroid.data.isPending
import com.dotancohen.voiceandroid.util.format
import com.dotancohen.voiceandroid.transcription.WhisperModels
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import com.dotancohen.voiceandroid.ui.components.ShareItem
import com.dotancohen.voiceandroid.ui.components.ShareNoteDialog
import com.dotancohen.voiceandroid.util.NoteSharing
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.dotancohen.voiceandroid.util.Magic
import com.dotancohen.voiceandroid.util.UiPreferences
import com.dotancohen.voiceandroid.ui.theme.RecordRed
import com.dotancohen.voiceandroid.ui.theme.StarGold
import com.dotancohen.voiceandroid.BuildConfig
import com.dotancohen.voiceandroid.audio.RecorderPreferences
import com.dotancohen.voiceandroid.audio.RecordingState
import com.dotancohen.voiceandroid.audio.VoiceRecorder
import com.dotancohen.voiceandroid.ui.components.AudioPlayerWidget
import com.dotancohen.voiceandroid.ui.components.AudioRecorderWidget
import com.dotancohen.voiceandroid.ui.components.recorderPlacement
import com.dotancohen.voiceandroid.ui.components.TranscribeDialog
import com.dotancohen.voiceandroid.ui.components.TranscriptionsSection
import com.dotancohen.voiceandroid.transcription.TranscriptionStage
import com.dotancohen.voiceandroid.ui.components.stampText
import com.dotancohen.voiceandroid.viewmodel.shouldDiscardEmptyNote
import com.dotancohen.voiceandroid.viewmodel.NoteDetailViewModel

/**
 * Note detail screen showing the full note content and audio player.
 *
 * Supports editing the note content with save/cancel controls.
 *
 * @param noteId The ID of the note to display
 * @param onBack Callback when the user presses the back button
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NoteDetailScreen(
    noteId: String,
    onBack: () -> Unit,
    onNavigateToTags: () -> Unit = {},
    onNavigateToTranscriptionSettings: () -> Unit = {},
    /**
     * The note was created in order to record into it, so the recorder is
     * open when the screen appears (and starts by itself if Settings →
     * Recorder says so).
     */
    startRecording: Boolean = false,
    /** The notes as the list is showing them, for Previous and Next. */
    visibleNoteIds: List<String> = emptyList(),
    /** Open another note in place of this one. */
    onOpenNote: (String) -> Unit = {},
    viewModel: NoteDetailViewModel = viewModel()
) {
    val note by viewModel.note.collectAsState()
    val audioFiles by viewModel.audioFiles.collectAsState()
    val transcriptions by viewModel.transcriptions.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val isEditing by viewModel.isEditing.collectAsState()
    val editedContent by viewModel.editedContent.collectAsState()
    val isSaving by viewModel.isSaving.collectAsState()
    val isDeleting by viewModel.isDeleting.collectAsState()
    val deleteSuccess by viewModel.deleteSuccess.collectAsState()
    val conflictTypes by viewModel.conflictTypes.collectAsState()
    val conflicts by viewModel.conflicts.collectAsState()
    val isAcceptingConflicts by viewModel.isAcceptingConflicts.collectAsState()
    val history by viewModel.history.collectAsState()
    val conflictSides by viewModel.conflictSides.collectAsState()
    var showHistory by remember { mutableStateOf(false) }
    var resolveText by remember { mutableStateOf("") }
    val isDownloading by viewModel.isDownloading.collectAsState()
    val audioFileAvailability by viewModel.audioFileAvailability.collectAsState()
    val downloadableCount by viewModel.downloadableCount.collectAsState()
    val pendingUploadCount by viewModel.pendingUploadCount.collectAsState()
    val downloadMessage by viewModel.downloadMessage.collectAsState()
    val onDeviceJob by viewModel.onDeviceJob.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val isMarked by viewModel.isMarked.collectAsState()
    val primaryAudioFileId by viewModel.primaryAudioFileId.collectAsState()
    val primaryTranscriptionIds by viewModel.primaryTranscriptionIds.collectAsState()
    val neighbours by viewModel.neighbours.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val transcribeMessage by viewModel.transcribeMessage.collectAsState()
    /** The recording whose transcriptions are shown: the one the player is on */
    var selectedAudioIndex by remember { mutableStateOf(0) }
    var transcribeTarget by remember { mutableStateOf<AudioFile?>(null) }
    var removeLocalTarget by remember { mutableStateOf<AudioFile?>(null) }
    var showTimes by remember { mutableStateOf(false) }
    var shareRequested by remember { mutableStateOf(false) }
    var showNoteMenu by remember { mutableStateOf(false) }
    var shareItems by remember { mutableStateOf<List<ShareItem>>(emptyList()) }
    /** The "Add to this Note" menu in the toolbar. */
    var showAddMenu by remember { mutableStateOf(false) }
    val recorderState by VoiceRecorder.state.collectAsState()
    val recorderNoteId by VoiceRecorder.noteId.collectAsState()
    /**
     * Open the recorder now because this note was created for a recording.
     *
     * Asked of the view model, which answers yes once: the screen is built
     * again on the way back from the tag screen, and the route still says
     * `record=true`, so anything remembered here alone would start another
     * recording every time the user came back.
     */
    val recordOnArrival = remember { viewModel.consumeStartRecording(startRecording) }
    /** The recorder was asked for in this note, by the New button or the microphone. */
    var recorderAsked by remember { mutableStateOf(recordOnArrival) }
    // Whether the recorder belongs in this note, or is busy in another one.
    // A recording carries on when the user walks to another note, so it must
    // not appear there (see recorderPlacement for why).
    val placement = recorderPlacement(
        noteId = noteId,
        recordingNoteId = recorderNoteId,
        recording = recorderState != RecordingState.Idle,
        asked = recorderAsked,
    )
    val recorderBusyElsewhere = placement.busyElsewhere
    val showRecorder = placement.show

    /**
     * Leave the note, taking it with us if it only ever existed to hold a
     * recording that was never made.
     *
     * The note is created before the recording starts, so backing out of a
     * recording that was never started would otherwise leave an empty note
     * in the list. A recording in progress keeps its note, of course: it is
     * still being made, and it carries on while the user is elsewhere.
     */
    fun leaveNote() {
        val busy = recorderNoteId == noteId && recorderState != RecordingState.Idle
        if (shouldDiscardEmptyNote(startRecording, busy, note, audioFiles)) {
            viewModel.deleteNote()
        } else {
            onBack()
        }
    }

    /**
     * Add something to this note. One kind so far; images and video will be
     * further branches here.
     */
    fun addAttachment(kind: String) {
        when (kind) {
            RecorderPreferences.ATTACHMENT_RECORDING -> recorderAsked = true
        }
    }

    BackHandler(enabled = !isEditing) { leaveNote() }

    /**
     * A recording was attached to this note: put the recorder away and show
     * the note again with its new recording in the player.
     *
     * The screen watches for this rather than the recorder widget, because
     * the widget can leave the screen the moment the recording is saved and
     * would then never hear that its own save finished.
     */
    val savedRecordingNoteId by VoiceRecorder.savedNoteId.collectAsState()
    LaunchedEffect(savedRecordingNoteId) {
        if (savedRecordingNoteId == noteId) {
            VoiceRecorder.clearSavedNote()
            recorderAsked = false
            viewModel.loadNote(noteId)
        }
    }

    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }

    // Confirmation dialog state
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    /** Removing a note that is already in the trash, for good. */
    var showPurgeConfirmation by remember { mutableStateOf(false) }

    // Show snackbar when download message changes
    LaunchedEffect(downloadMessage) {
        downloadMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearDownloadMessage()
        }
    }

    // Show snackbar for on-device transcription events
    LaunchedEffect(transcribeMessage) {
        transcribeMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearTranscribeMessage()
        }
    }

    // Load the note when the noteId changes
    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
    }

    // Where this note sits in the list, for Previous and Next
    LaunchedEffect(noteId, visibleNoteIds) {
        viewModel.loadNeighbours(noteId, visibleNoteIds)
    }

    // Navigate back when note is deleted
    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            onBack()
        }
    }

    // Sharing. One thing to send goes straight out; several open the tree so
    // the user can say which of them travel together.
    val shareContext = LocalContext.current
    val shareScope = rememberCoroutineScope()

    fun sendShare(chosen: Set<String>) {
        shareScope.launch {
            val files = NoteSharing.audioFilesFor(chosen, audioFiles).mapNotNull { audioFile ->
                viewModel.getAudioFilePath(audioFile.id)?.let { java.io.File(it) }
            }
            NoteSharing.share(
                context = shareContext,
                text = NoteSharing.textFor(chosen, note, audioFiles, transcriptions),
                files = files,
                subject = note?.content?.lineSequence()?.firstOrNull { it.isNotBlank() }
            )?.let { problem -> viewModel.reportError(problem) }
        }
    }

    LaunchedEffect(shareRequested) {
        if (!shareRequested) return@LaunchedEffect
        shareRequested = false
        val items = NoteSharing.itemsFor(note, audioFiles, transcriptions) { audioFileId ->
            audioFileAvailability[audioFileId] != false
        }
        when {
            items.isEmpty() -> viewModel.reportError("This Note holds nothing to share yet")
            items.size == 1 -> sendShare(setOf(items.first().id))
            else -> shareItems = items
        }
    }

    if (shareItems.isNotEmpty()) {
        ShareNoteDialog(
            items = shareItems,
            onShare = { chosen -> sendShare(chosen) },
            onDismiss = { shareItems = emptyList() }
        )
    }

    // Version history dialog: every version of the content, restore any of them
    if (showHistory) {
        AlertDialog(
            onDismissRequest = { showHistory = false },
            title = { Text("History (${history.size} versions)") },
            text = {
                if (history.isEmpty()) {
                    Text("Loading…")
                } else {
                    val current = note?.content
                    LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                        items(history.reversed()) { version ->
                            val kind = when {
                                version.mergeParentId != null -> "merge"
                                version.parentId == null -> "original"
                                else -> "edit"
                            }
                            val isCurrent = version.content == current
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                Text(
                                    text = "${version.createdAt.format(LocalContext.current)} · ${version.deviceLabel} · $kind" +
                                        (version.conflictKind?.let { " · $it conflict" } ?: "") +
                                        (if (isCurrent) " · current" else ""),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = version.content.lineSequence().firstOrNull()?.take(80) ?: "",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (!isCurrent) {
                                    TextButton(
                                        onClick = { viewModel.restoreVersion(version) },
                                        enabled = !isSaving && !isEditing
                                    ) { Text("Restore this version") }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showHistory = false }) { Text("Close") }
            }
        )
    }

    // Side-by-side resolution of the note's text conflict
    conflictSides?.let { sides ->
        LaunchedEffect(sides.conflict.id) {
            resolveText = sides.merged?.content ?: note?.content ?: ""
        }
        AlertDialog(
            onDismissRequest = { viewModel.clearConflictSides() },
            title = { Text("Resolve: ${sides.conflict.deviceA} vs ${sides.conflict.deviceB}") },
            text = {
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(max = 480.dp)) {
                    Text("${sides.conflict.deviceA} (A)", style = MaterialTheme.typography.labelMedium)
                    Text(sides.sideA?.content ?: "", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${sides.conflict.deviceB} (B)", style = MaterialTheme.typography.labelMedium)
                    Text(sides.sideB?.content ?: "", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Common ancestor", style = MaterialTheme.typography.labelMedium)
                    Text(sides.base?.content ?: "", style = MaterialTheme.typography.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        TextButton(onClick = { resolveText = sides.sideA?.content ?: "" }) { Text("Use A") }
                        TextButton(onClick = { resolveText = sides.sideB?.content ?: "" }) { Text("Use B") }
                        TextButton(onClick = { resolveText = sides.merged?.content ?: "" }) { Text("Merged") }
                    }
                    OutlinedTextField(
                        value = resolveText,
                        onValueChange = { resolveText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Result") },
                        minLines = 4
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.resolveConflictWith(resolveText) },
                    enabled = !isSaving
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.clearConflictSides() }) { Text("Cancel") }
            }
        )
    }

    // Transcribe dialog for one recording
    transcribeTarget?.let { target ->
        TranscribeDialog(
            subtitle = target.filename,
            onDismiss = { transcribeTarget = null },
            onSettings = { transcribeTarget = null; onNavigateToTranscriptionSettings() },
            onTranscribe = { modelId, language ->
                transcribeTarget = null
                viewModel.transcribeOnDevice(target, modelId, language)
            },
            existingTranscriptions = transcriptions[target.id].orEmpty().count { it.isFinished }
        )
    }

    // Held down Previous or Next: that note's row from the notes list, the
    // width of the screen and with twice as many lines of text as the list
    // shows. It is the list's own row, so it behaves like one: tapping it
    // opens the note. Tapping anywhere else puts it away.
    preview?.let { row ->
        Dialog(
            onDismissRequest = { viewModel.clearPreview() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // Scrollable: opening the section adds a player and a waveform, and
            // on a long note that is taller than the screen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
            NoteCard(
                noteWithAudio = row,
                contentLines = UiPreferences(LocalContext.current).notesListLines * 2,
                onClick = {
                    val id = row.note.id
                    viewModel.clearPreview()
                    onOpenNote(id)
                },
                // So the chevron opens the preview's own player, as it does in
                // the list: the card keeps the open state itself here.
                getAudioFilePath = { audioId -> viewModel.getAudioFilePath(audioId) },
                modifier = Modifier.fillMaxWidth()
            )
            }
        }
    }

    // Created and modified times (long-press on the title)
    if (showTimes && note != null) {
        AlertDialog(
            onDismissRequest = { showTimes = false },
            title = { Text("Times") },
            text = {
                val context = LocalContext.current
                Text(
                    "Created: ${note!!.createdAt.format(context)}\n" +
                        "Modified: ${note!!.modifiedAt?.format(context) ?: "never"}"
                )
            },
            confirmButton = { TextButton(onClick = { showTimes = false }) { Text("Close") } }
        )
    }

    // Removing a note in the trash for good, from the note itself
    if (showPurgeConfirmation) {
        AlertDialog(
            onDismissRequest = { showPurgeConfirmation = false },
            title = { Text("Remove this Note for good?") },
            text = {
                Text(
                    "The note, its history and the recordings that belong only to it are " +
                        "removed from this phone and from every device it syncs with. " +
                        "This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showPurgeConfirmation = false
                    viewModel.purgeNote()
                }) {
                    Text("Delete for good", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showPurgeConfirmation = false }) { Text("Cancel") }
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Note") },
            text = {
                Text(
                    "The note goes to the trash, with its recordings. " +
                        "You can bring it back from Settings → Trash."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirmation = false
                        viewModel.deleteNote()
                    }
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                // A note screen is mostly note, so the bar is as short as it
                // can be while still being tappable, and it does not add the
                // status bar's height a second time.
                modifier = Modifier.height(48.dp),
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Previous and next, then the date: the two steps
                        // belong together, beside the arrow that leaves the
                        // note, and the date reads as the note's own title
                        // rather than as a label between two buttons.
                        //
                        // Up and down rather than left and right, because
                        // that is how the list runs, and because arrows that
                        // mean "left" have to be mirrored in Hebrew while
                        // "up" means up everywhere. Hold one to see the note
                        // it would open.
                        if (!isEditing) {
                            NoteStepButton(
                                icon = Icons.Filled.KeyboardArrowUp,
                                label = "Previous note",
                                targetNoteId = neighbours.previous,
                                onOpen = onOpenNote,
                                onPreview = { viewModel.loadPreview(it) }
                            )
                            NoteStepButton(
                                icon = Icons.Filled.KeyboardArrowDown,
                                label = "Next note",
                                targetNoteId = neighbours.next,
                                onOpen = onOpenNote,
                                onPreview = { viewModel.loadPreview(it) }
                            )
                        }
                        // The last change as the title; long-press for created
                        // and modified. It is meant to be one line: if the
                        // format is too long for the width, it breaks once,
                        // before the time of day, rather than being cut.
                        val shownStamp = note?.modifiedAt ?: note?.createdAt
                        Text(
                            text = when {
                                isEditing -> AnnotatedString("Edit Note")
                                shownStamp != null -> stampText(
                                    shownStamp,
                                    baseSize = MaterialTheme.typography.titleMedium.fontSize
                                )
                                else -> AnnotatedString("Note")
                            },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            softWrap = true,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .weight(1f)
                                .combinedClickable(onClick = {}, onLongClick = { showTimes = true })
                        )
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (isEditing) {
                                viewModel.cancelEditing()
                            } else {
                                leaveNote()
                            }
                        },
                        modifier = Modifier.size(Magic.TOOLBAR_BUTTON_DP.dp)
                    ) {
                        Icon(
                            imageVector = if (isEditing) Icons.Filled.Close else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (isEditing) "Cancel" else "Back"
                        )
                    }
                },
                actions = {
                    if (isEditing) {
                        IconButton(
                            onClick = { viewModel.saveNote() },
                            enabled = !isSaving,
                            modifier = Modifier.size(Magic.TOOLBAR_BUTTON_DP.dp)
                        ) {
                            if (isSaving) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = "Save"
                                )
                            }
                        }
                    } else if (note != null) {
                        // One menu holds everything that is not stepping
                        // between Notes: adding to this Note, sharing it, its
                        // history, and deleting it. Four buttons on the bar
                        // left no room for the date they sat beside.
                        val context = LocalContext.current
                        Box {
                            IconButton(
                                onClick = { showNoteMenu = true },
                                modifier = Modifier.size(Magic.TOOLBAR_BUTTON_DP.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.MoreVert,
                                    contentDescription = "More"
                                )
                            }
                            DropdownMenu(
                                expanded = showNoteMenu,
                                onDismissRequest = { showNoteMenu = false }
                            ) {
                                if (!showRecorder) {
                                    for (kind in RecorderPreferences.ATTACHMENT_KINDS) {
                                        DropdownMenuItem(
                                            text = { Text(RecorderPreferences.attachmentKindTitle(kind)) },
                                            leadingIcon = {
                                                Icon(
                                                    imageVector = Icons.Filled.Mic,
                                                    contentDescription = null,
                                                    tint = RecordRed
                                                )
                                            },
                                            onClick = {
                                                showNoteMenu = false
                                                addAttachment(kind)
                                            }
                                        )
                                    }
                                }
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) },
                                    onClick = {
                                        showNoteMenu = false
                                        shareRequested = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("History") },
                                    leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                                    onClick = {
                                        showNoteMenu = false
                                        viewModel.loadHistory()
                                        showHistory = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete Note", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Filled.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    },
                                    enabled = !isDeleting,
                                    onClick = {
                                        showNoteMenu = false
                                        showDeleteConfirmation = true
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            isLoading -> {
                Text(
                    text = "Loading...",
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)
                )
            }
            error != null -> {
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp)
                )
            }
            note == null -> {
                Text(
                    text = "Note not found",
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState())
                        // Room at the sides, none above: the first thing
                        // under the bar sits against it.
                        .padding(horizontal = 12.dp)
                ) {
                    // A note in the trash says so before anything else, with
                    // the same two things the trash offers.
                    if (note?.deletedAt != null) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            DeletedNoteActions(
                                deletedAt = note?.deletedAt,
                                onRecover = { viewModel.recoverNote() },
                                onPurge = { showPurgeConfirmation = true }
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Conflict banner: what disagreed, on which devices, and how to resolve
                    if (conflictTypes.isNotEmpty()) {
                        val typesStr = conflictTypes.joinToString(", ")
                        val devices = conflicts
                            .flatMap { listOf(it.deviceA, it.deviceB) }
                            .distinct()
                            .joinToString(" and ")
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = if (devices.isNotEmpty())
                                        "CONFLICT ($typesStr): changed on $devices. Both versions are kept."
                                    else
                                        "CONFLICT ($typesStr): both versions are kept.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Text(
                                    text = "Edit and save to resolve, or accept the merge as it is.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Row {
                                    TextButton(
                                        onClick = { viewModel.acceptConflicts() },
                                        enabled = !isEditing && !isAcceptingConflicts
                                    ) {
                                        Text(if (isAcceptingConflicts) "Accepting…" else "Accept merge")
                                    }
                                    if (conflicts.any { it.kind == "text" && it.entityType == "note" }) {
                                        TextButton(
                                            onClick = { viewModel.loadConflictSides() },
                                            enabled = !isEditing && !isAcceptingConflicts
                                        ) { Text("Resolve…") }
                                    }
                                }
                            }
                        }
                    }

                    // Star, tags button and the note's tags, on one line of
                    // their own: the star is the same one the notes list
                    // shows, and the tags say what the note is filed under
                    // without having to open the tag screen.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.toggleMarked() },
                            modifier = Modifier.size(Magic.NOTE_LINE_BUTTON_DP.dp)
                        ) {
                            Icon(
                                imageVector = if (isMarked) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                contentDescription = if (isMarked) "Remove the star" else "Star this Note",
                                modifier = Modifier.size(20.dp),
                                tint = if (isMarked) StarGold else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // The label is the icon: what it opens is plain from
                        // the tags listed beside it, and the words cost a
                        // third of the line.
                        IconButton(
                            onClick = onNavigateToTags,
                            modifier = Modifier.size(Magic.NOTE_LINE_BUTTON_DP.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Label,
                                contentDescription = "Manage tags",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = if (tags.isEmpty()) "no tags" else tags.joinToString(", "),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (tags.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.primary,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 4.dp).weight(1f)
                        )
                    }

                    // Note content - editable or read-only, always in a visible box
                    if (isEditing) {
                        val focusRequester = remember { FocusRequester() }
                        OutlinedTextField(
                            value = editedContent,
                            onValueChange = { viewModel.updateEditedContent(it) },
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            enabled = !isSaving,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            minLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                        // Editing began with a tap on the text, so the
                        // keyboard should be there without a second tap.
                        LaunchedEffect(Unit) { focusRequester.requestFocus() }
                    } else {
                        // Tapping the text is how editing starts; there is no
                        // separate pencil in the toolbar to look for.
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.startEditing() },
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = note!!.content.ifBlank { "Tap to write" },
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (note!!.content.isBlank()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            )
                        }
                    }

                    // Asked to record here while the recorder is busy in
                    // another note: say so instead of showing controls that
                    // would act on the other note.
                    if (recorderAsked && recorderBusyElsewhere) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "A recording is already in progress in another note.",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "Save or discard that one first; this phone records one note at a time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                TextButton(onClick = { recorderAsked = false }) { Text("OK") }
                            }
                        }
                    }

                    // Recording into this note, in the same place as the player
                    if (showRecorder) {
                        Spacer(modifier = Modifier.height(10.dp))
                        AudioRecorderWidget(
                            noteId = noteId,
                            // Whether the recorder was opened by the New
                            // button or by the Add menu, the setting decides
                            // whether it starts by itself.
                            autoStart = true,
                            modifier = Modifier.fillMaxWidth(),
                            onDiscarded = {
                                recorderAsked = false
                                // A note made only to hold this recording has
                                // nothing left in it, so it goes with the
                                // recording rather than being left behind
                                // empty in the list. A note the user made
                                // themselves is theirs, empty or not, and is
                                // never removed here.
                                val loaded = note
                                if (startRecording && loaded != null &&
                                    loaded.content.isBlank() && audioFiles.isEmpty()
                                ) {
                                    viewModel.deleteNote()
                                }
                            }
                        )
                    }

                    // Audio player widget (if there are audio files)
                    if (audioFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(10.dp))

                        // Check if any audio files are missing locally
                        val hasLocalFiles = audioFileAvailability.values.any { it }
                        val hasMissingFiles = audioFileAvailability.values.any { !it }
                        val missingText = buildString {
                            if (downloadableCount > 0) {
                                append("Media missing: $downloadableCount file(s) not on this device.")
                            }
                            if (pendingUploadCount > 0) {
                                if (isNotEmpty()) append(" ")
                                append("$pendingUploadCount file(s) not uploaded by their device yet.")
                            }
                        }

                        if (hasMissingFiles && !hasLocalFiles) {
                            // No local files at all - show download button instead of player
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudDownload,
                                        contentDescription = null,
                                        modifier = Modifier.size(48.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = missingText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    if (downloadableCount > 0) Button(
                                        onClick = { viewModel.downloadMissingAudioFiles() },
                                        enabled = !isDownloading,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        if (isDownloading) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Spacer(modifier = Modifier.size(8.dp))
                                            Text("Downloading...")
                                        } else {
                                            Icon(
                                                imageVector = Icons.Filled.CloudDownload,
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.size(8.dp))
                                            Text("Download")
                                        }
                                    }
                                }
                            }
                        } else {
                            // Some or all files are available locally - show player
                            // Where a recording's copies are, and removing this phone's copy (FILE-22)
                            val locationLines by viewModel.locationLines.collectAsState()
                            locationLines?.let { lines ->
                                AlertDialog(
                                    onDismissRequest = { viewModel.dismissLocations() },
                                    title = { Text("Where the copies are") },
                                    text = { Text(lines.joinToString("\n")) },
                                    confirmButton = { TextButton(onClick = { viewModel.dismissLocations() }) { Text("Close") } }
                                )
                            }
                            removeLocalTarget?.let { target ->
                                AlertDialog(
                                    onDismissRequest = { removeLocalTarget = null },
                                    title = { Text("Remove from this phone") },
                                    text = { Text("Remove ${target.filename} from this phone? The recording stays, and it can be fetched again from wherever else it is kept.") },
                                    confirmButton = {
                                        TextButton(onClick = {
                                            removeLocalTarget = null
                                            viewModel.removeLocalCopy(target)
                                        }) { Text("Remove") }
                                    },
                                    dismissButton = { TextButton(onClick = { removeLocalTarget = null }) { Text("Cancel") } }
                                )
                            }
                            AudioPlayerWidget(
                                audioFiles = audioFiles,
                                getFilePath = { audioId ->
                                    viewModel.getAudioFilePath(audioId)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                onTranscribe = { audioFile -> transcribeTarget = audioFile },
                                onCurrentFileChanged = { index -> selectedAudioIndex = index },
                                pendingTranscriptionIds = transcriptions
                                    .filterValues { rows -> rows.any { it.isPending } }
                                    .keys,
                                primaryAudioFileId = primaryAudioFileId,
                                autoPlay = UiPreferences(LocalContext.current).autoplayOnOpen,
                                onSetPrimary = { audioFile -> viewModel.setPrimaryAudioFile(audioFile) },
                                onShowLocations = { audioFile -> viewModel.showLocations(audioFile) },
                                onRemoveLocal = { audioFile -> removeLocalTarget = audioFile },
                                noteId = noteId,
                                noteLine = note?.content?.lineSequence()?.firstOrNull()?.take(60)
                            )

                            // Progress of a transcription running for one of these recordings
                            onDeviceJob?.takeIf { job -> audioFiles.any { it.id == job.audioFileId } && job.stage < TranscriptionStage.Done }?.let { job ->
                                Text(job.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
                            }

                            // If some files are still missing, say so and offer a download
                            if (hasMissingFiles) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = missingText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (downloadableCount > 0) TextButton(
                                    onClick = { viewModel.downloadMissingAudioFiles() },
                                    enabled = !isDownloading
                                ) {
                                    if (isDownloading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(modifier = Modifier.size(4.dp))
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.CloudDownload,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.size(4.dp))
                                    }
                                    Text("Download missing media")
                                }
                            }
                        }
                    }

                    // Transcriptions of the recording the player is on
                    val shownAudio = audioFiles.getOrNull(selectedAudioIndex) ?: audioFiles.firstOrNull()
                    val shownTranscriptions = shownAudio?.let { transcriptions[it.id] }.orEmpty()
                    if (shownTranscriptions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))

                        TranscriptionsSection(
                            transcriptions = shownTranscriptions,
                            onToggleFlag = { transcription, tag ->
                                viewModel.toggleTranscriptionFlag(transcription, tag)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            primaryTranscriptionId = shownAudio?.let { primaryTranscriptionIds[it.id] },
                            onSetPrimary = { transcription -> viewModel.setPrimaryTranscription(transcription) },
                            // Editing a transcription by hand is under trial:
                            // debug builds only.
                            onEditContent = if (BuildConfig.DEV_FEATURES) {
                                { transcription, text ->
                                    viewModel.editTranscriptionContent(transcription, text)
                                }
                            } else null
                        )
                    }
                }
            }
        }
    }
}

/**
 * One step through the notes list: a tap opens that note, a long press shows
 * its row from the list first.
 *
 * Greyed out at the ends of the list, where there is no note to step to. A
 * plain [IconButton] cannot tell a long press from a tap, so this is a box
 * with both.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteStepButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    targetNoteId: String?,
    onOpen: (String) -> Unit,
    onPreview: (String) -> Unit,
) {
    val enabled = targetNoteId != null
    Box(
        modifier = Modifier
            .size(Magic.TOOLBAR_BUTTON_DP.dp)
            .combinedClickable(
                enabled = enabled,
                onClick = { targetNoteId?.let(onOpen) },
                onLongClick = { targetNoteId?.let(onPreview) }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (enabled) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        )
    }
}
