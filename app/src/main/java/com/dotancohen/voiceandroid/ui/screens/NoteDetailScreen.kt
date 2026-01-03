package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.ui.components.AudioPlayerWidget
import com.dotancohen.voiceandroid.ui.components.TranscriptionsSection
import com.dotancohen.voiceandroid.viewmodel.NoteDetailViewModel

/**
 * Note detail screen showing the full note content and audio player.
 *
 * Supports editing the note content with save/cancel controls.
 *
 * @param noteId The ID of the note to display
 * @param onBack Callback when the user presses the back button
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteDetailScreen(
    noteId: String,
    onBack: () -> Unit,
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

    // Load the note when the noteId changes
    LaunchedEffect(noteId) {
        viewModel.loadNote(noteId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (isEditing) "Edit Note" else "Note") },
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
                    IconButton(onClick = { viewModel.startEditing() }) {
                        Icon(
                            imageVector = Icons.Filled.Edit,
                            contentDescription = "Edit"
                        )
                    }
                }
            }
        )

        when {
            isLoading -> {
                Text(
                    text = "Loading...",
                    modifier = Modifier.padding(16.dp)
                )
            }
            error != null -> {
                Text(
                    text = "Error: $error",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp)
                )
            }
            note == null -> {
                Text(
                    text = "Note not found",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // Note content - editable or read-only
                    if (isEditing) {
                        OutlinedTextField(
                            value = editedContent,
                            onValueChange = { viewModel.updateEditedContent(it) },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSaving,
                            textStyle = MaterialTheme.typography.bodyLarge,
                            minLines = 5
                        )
                    } else {
                        Text(
                            text = note!!.content,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Date info
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Modified: ${note!!.modifiedAt ?: note!!.createdAt}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Audio player widget (if there are audio files)
                    if (audioFiles.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))

                        AudioPlayerWidget(
                            audioFiles = audioFiles,
                            getFilePath = { audioId ->
                                viewModel.getAudioFilePath(audioId)
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Transcriptions section (if there are any)
                    if (transcriptions.values.any { it.isNotEmpty() }) {
                        Spacer(modifier = Modifier.height(24.dp))

                        TranscriptionsSection(
                            transcriptions = transcriptions,
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
