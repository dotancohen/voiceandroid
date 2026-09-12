package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.data.Transcription
import com.dotancohen.voiceandroid.data.TranscriptionFlags
import com.dotancohen.voiceandroid.data.detectedLanguages
import com.dotancohen.voiceandroid.data.modelId
import com.dotancohen.voiceandroid.data.performance
import com.dotancohen.voiceandroid.data.requestedLanguage
import com.dotancohen.voiceandroid.transcription.TranscriptionPreferences
import com.dotancohen.voiceandroid.transcription.WhisperModels
import com.dotancohen.voiceandroid.util.format

/**
 * What is known about one transcription, and what can be said about it.
 *
 * Where it came from — when it was made, by which service, with which model
 * and in which language — and then the five flags, each a line the user can
 * press to turn on or off. Any number of them can be true at once, which is
 * why they are flags and not one state. They are words in the
 * transcription's own record and travel with it, so the same five lines
 * appear in the desktop application; the wording is shared with the user
 * manual of both.
 */
@Composable
fun TranscriptionDetailsDialog(
    transcription: Transcription,
    onToggleFlag: (String) -> Unit,
    onDismiss: () -> Unit,
    /** This is the transcription that stands for its recording. */
    isPrimary: Boolean = false,
    /** Make this the one that stands for its recording, or unmake it. */
    onSetPrimary: (() -> Unit)? = null,
    /**
     * Save the transcription's text as the user has rewritten it. Null where
     * editing is not offered — it is under trial, so only a debug build
     * passes this in.
     */
    onEditContent: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    var editing by remember(transcription.id) { mutableStateOf(false) }
    var draft by remember(transcription.id) { mutableStateOf(transcription.content) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transcription") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Correcting the words themselves, where that is offered.
                // The flags are not touched: whether a corrected
                // transcription is still "original" is the user's judgement,
                // made a few lines below this.
                if (onEditContent != null) {
                    if (editing) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("The Transcription's text") },
                            minLines = 4,
                        )
                        Row {
                            TextButton(onClick = {
                                onEditContent(draft)
                                editing = false
                            }) { Text("Save text") }
                            TextButton(onClick = {
                                draft = transcription.content
                                editing = false
                            }) { Text("Cancel") }
                        }
                    } else {
                        TextButton(
                            onClick = { draft = transcription.content; editing = true },
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp)
                        ) { Text("Edit the text") }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                DetailRow("Made", transcription.createdAt.format(context))
                DetailRow("Service", transcription.service)
                DetailRow("Model", modelLabel(transcription.modelId))
                DetailRow("Language", languageLabel(transcription))

                if (onSetPrimary != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSetPrimary)
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (isPrimary) Icons.Filled.Star else Icons.Outlined.StarOutline,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (isPrimary) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("Main Transcription", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Shown first here, and under the note in the list",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // What the phone was doing while it worked. Written by the
                // transcriber with every transcription made on this phone, so
                // that the cost of a model on a real recording can be read
                // afterwards rather than guessed at. A transcription made
                // elsewhere, or before these were recorded, simply has none.
                val performance = transcription.performance
                if (performance.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "How it ran",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    for ((label, value) in performance) {
                        DetailRow(label, value)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                for (flag in TranscriptionFlags.ALL) {
                    val on = transcription.hasFlag(flag.name)
                    // Top-aligned, like every table in the application: the
                    // descriptions are of different lengths, and a centred
                    // row puts each name at a different height.
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggleFlag(flag.name) }
                            .padding(vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = if (on) "${flag.title}, on" else "${flag.title}, off",
                            modifier = Modifier.size(20.dp),
                            tint = if (on) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(flag.title, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                flag.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodySmall)
    }
}

/** The model's own name where it is known, and its bare id where it is not. */
private fun modelLabel(modelId: String?): String {
    if (modelId.isNullOrBlank()) return "not recorded"
    return WhisperModels.byId(modelId)?.title ?: modelId
}

/**
 * The language asked for, or — when the service was left to work it out —
 * what it decided, so that a transcription in the wrong language explains
 * itself.
 */
private fun languageLabel(transcription: Transcription): String {
    val asked = transcription.requestedLanguage
    val detected = transcription.detectedLanguages
        .map { TranscriptionPreferences.languageTitle(it) }
    return when {
        asked != null -> TranscriptionPreferences.languageTitle(asked)
        detected.isNotEmpty() -> "Detected automatically: ${detected.joinToString(", ")}"
        else -> "Detected automatically"
    }
}
