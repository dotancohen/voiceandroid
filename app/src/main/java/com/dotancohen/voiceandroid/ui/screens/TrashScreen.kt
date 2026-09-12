package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import com.dotancohen.voiceandroid.ui.components.stampText
import uniffi.voicecore.Stamp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.data.Note
import com.dotancohen.voiceandroid.util.UiPreferences
import com.dotancohen.voiceandroid.util.format
import com.dotancohen.voiceandroid.viewmodel.TrashViewModel

/**
 * The trash bin: notes that were deleted, with what can still be done about
 * them.
 *
 * Deleting a note has always been a soft delete, so a deleted note is not
 * gone. Recover puts it back in the list. Delete for good is the one thing
 * in the application that really destroys something, so it asks first and
 * says what it will take with it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    /** Open a deleted note, which is a note like any other. */
    onOpenNote: (String) -> Unit = {},
    viewModel: TrashViewModel = viewModel()
) {
    val contentLines = UiPreferences(LocalContext.current).notesListLines
    val notes by viewModel.notes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmPurge by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(Unit) { viewModel.load() }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    confirmPurge?.let { note ->
        AlertDialog(
            onDismissRequest = { confirmPurge = null },
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
                    viewModel.purge(note)
                    confirmPurge = null
                }) {
                    Text("Delete for good", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurge = null }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (notes.isEmpty()) "Trash" else "Trash (${notes.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(8.dp)) {
            when {
                isLoading && notes.isEmpty() -> Text("Loading…")
                notes.isEmpty() -> Text(
                    "The trash is empty. A note you delete waits here until you " +
                        "recover it or remove it for good.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(notes, key = { it.note.id }) { row ->
                        Column {
                            // The row the notes list draws, built by the same
                            // code: a note in the trash is still a note, and
                            // tapping it opens it.
                            NoteCard(
                                noteWithAudio = row,
                                contentLines = contentLines,
                                onClick = { onOpenNote(row.note.id) },
                                // The chevron opens the player, so a recording
                                // can be listened to before it is removed for
                                // good. The card keeps the open state itself.
                                getAudioFilePath = { audioId -> viewModel.audioFilePath(audioId) },
                                modifier = Modifier.fillMaxWidth()
                            )
                            DeletedNoteActions(
                                deletedAt = row.note.deletedAt,
                                onRecover = { viewModel.recover(row.note) },
                                onPurge = { confirmPurge = row.note }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * The line under a deleted note: when it went, and the two things that can
 * still be done with it.
 *
 * The same line appears at the top of the note itself when a deleted note is
 * opened, so the two places cannot say different things.
 */
@Composable
fun DeletedNoteActions(
    deletedAt: Stamp?,
    onRecover: () -> Unit,
    onPurge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = deletedAt?.let {
                stampText(it, baseSize = MaterialTheme.typography.labelMedium.fontSize, prefix = "Deleted ")
            } ?: AnnotatedString("Deleted"),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onPurge) {
            Text("Delete permanently", color = MaterialTheme.colorScheme.error)
        }
        TextButton(onClick = onRecover) { Text("Recover") }
    }
}
