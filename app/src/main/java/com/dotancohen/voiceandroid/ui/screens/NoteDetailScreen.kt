package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.ui.components.AudioPlayerWidget
import com.dotancohen.voiceandroid.ui.components.TranscribeDialog
import com.dotancohen.voiceandroid.ui.components.TranscriptionsSection
import com.dotancohen.voiceandroid.transcription.TranscriptionStage
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
    val transcribeMessage by viewModel.transcribeMessage.collectAsState()
    /** The recording whose transcriptions are shown: the one the player is on */
    var selectedAudioIndex by remember { mutableStateOf(0) }
    var transcribeTarget by remember { mutableStateOf<AudioFile?>(null) }
    var showTimes by remember { mutableStateOf(false) }

    // Snackbar state
    val snackbarHostState = remember { SnackbarHostState() }

    // Confirmation dialog state
    var showDeleteConfirmation by remember { mutableStateOf(false) }

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

    // Navigate back when note is deleted
    LaunchedEffect(deleteSuccess) {
        if (deleteSuccess) {
            onBack()
        }
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
                                    text = "${version.createdAt} · ${version.deviceLabel} · $kind" +
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
            audioFile = target,
            onDismiss = { transcribeTarget = null },
            onSettings = { transcribeTarget = null; onNavigateToTranscriptionSettings() },
            onTranscribe = { modelId, language ->
                transcribeTarget = null
                viewModel.transcribeOnDevice(target, modelId, language)
            }
        )
    }

    // Created and modified times (long-press on the title)
    if (showTimes && note != null) {
        AlertDialog(
            onDismissRequest = { showTimes = false },
            title = { Text("Times") },
            text = { Text("Created: ${note!!.createdAt}\nModified: ${note!!.modifiedAt ?: "never"}") },
            confirmButton = { TextButton(onClick = { showTimes = false }) { Text("Close") } }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete Note") },
            text = { Text("Are you sure you want to delete this note? This action cannot be undone.") },
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
                title = {
                    // The last change as the title; long-press for created and modified
                    Text(
                        text = if (isEditing) "Edit Note" else (note?.modifiedAt ?: note?.createdAt ?: "Note"),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { showTimes = true })
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isEditing) {
                            viewModel.cancelEditing()
                        } else {
                            onBack()
                        }
                    }) {
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
                            enabled = !isSaving
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
                        // History button
                        IconButton(onClick = { viewModel.loadHistory(); showHistory = true }) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = "History"
                            )
                        }
                        // Tags button
                        IconButton(onClick = onNavigateToTags) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Label,
                                contentDescription = "Tags"
                            )
                        }
                        // Delete button
                        IconButton(
                            onClick = { showDeleteConfirmation = true },
                            enabled = !isDeleting
                        ) {
                            if (isDeleting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Delete"
                                )
                            }
                        }
                        // Edit button
                        IconButton(onClick = { viewModel.startEditing() }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Edit"
                            )
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
                        .padding(16.dp)
                ) {
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

                    // Note content - editable or read-only, always in a visible box
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedContent,
                            onValueChange = { viewModel.updateEditedContent(it) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            minLines = 5,
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedBorderColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    } else {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = note!!.content,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp)
                            )
                        }
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
                            AudioPlayerWidget(
                                audioFiles = audioFiles,
                                getFilePath = { audioId ->
                                    viewModel.getAudioFilePath(audioId)
                                },
                                modifier = Modifier.fillMaxWidth(),
                                onTranscribe = { audioFile -> transcribeTarget = audioFile },
                                onCurrentFileChanged = { index -> selectedAudioIndex = index }
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
                            onToggleState = { transcription, tag ->
                                viewModel.toggleTranscriptionState(transcription, tag)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
    }
}
