package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.DisableSelection
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.data.Transcription
import com.dotancohen.voiceandroid.viewmodel.transcriptionsInDisplayOrder
import com.dotancohen.voiceandroid.data.isFinished
import com.dotancohen.voiceandroid.data.isPending
import com.dotancohen.voiceandroid.ui.scaledIcon
import com.dotancohen.voiceandroid.ui.theme.StarGold

/**
 * One transcription: its text, and under it two buttons — copy the whole of
 * it, and open what is known about it.
 *
 * Everything else that used to sit on that line — the service, the date, and
 * the state toggles — is in the dialog the second button opens. A
 * transcription is read for its words; the line under them was three icons
 * whose meaning had to be remembered, and a date the note already gives.
 */
@Composable
fun TranscriptionCard(
    transcription: Transcription,
    onToggleFlag: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** This is the transcription that stands for its recording. */
    isPrimary: Boolean = false,
    /** Make this the transcription that stands for its recording. */
    onSetPrimary: (() -> Unit)? = null,
    /** Save the transcription's text as the user rewrote it; null where editing is not offered. */
    onEditContent: ((String) -> Unit)? = null
) {
    var showDetails by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)) {
            // Selectable, so a word or a paragraph can be taken out of a
            // transcription by hand; the copy button beside the date takes
            // the whole of it.
            SelectionContainer {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (transcription.isPending) {
                        // Still being worked on: the transcribe mark with a
                        // clock running over it.
                        DisableSelection {
                            PendingTranscriptionIcon(
                                modifier = Modifier.padding(end = 6.dp),
                                size = scaledIcon(18.dp)
                            )
                        }
                    }
                    Text(
                        text = transcription.content,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Copy the whole transcription, for pasting somewhere else
                val clipboard = LocalClipboardManager.current
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(transcription.content)) },
                    modifier = Modifier.size(scaledIcon(32.dp))
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Copy this Transcription",
                        modifier = Modifier.size(scaledIcon(18.dp)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Where it came from, and what can be said about it
                IconButton(
                    onClick = { showDetails = true },
                    modifier = Modifier.size(scaledIcon(32.dp))
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "About this Transcription",
                        modifier = Modifier.size(scaledIcon(18.dp)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showDetails) {
        TranscriptionDetailsDialog(
            transcription = transcription,
            onToggleFlag = onToggleFlag,
            onDismiss = { showDetails = false },
            isPrimary = isPrimary,
            onSetPrimary = onSetPrimary?.let { cb -> { cb(); showDetails = false } },
            onEditContent = onEditContent
        )
    }
}

/**
 * The transcriptions of one audio file (the one selected in the player).
 * The time of day is shown only when a date has more than one transcription.
 */
@Composable
fun TranscriptionsSection(
    transcriptions: List<Transcription>,
    onToggleFlag: (Transcription, String) -> Unit,
    modifier: Modifier = Modifier,
    /** The transcription that stands for this recording, if one was chosen. */
    primaryTranscriptionId: String? = null,
    onSetPrimary: ((Transcription) -> Unit)? = null,
    /** Save a transcription's text as the user rewrote it; null where editing is not offered. */
    onEditContent: ((Transcription, String) -> Unit)? = null
) {
    if (transcriptions.isEmpty()) return

    // The one that stands for the recording comes first, so the reader sees
    // it without scrolling; the rest keep their own order.
    val ordered = transcriptionsInDisplayOrder(transcriptions, primaryTranscriptionId)
    Column(modifier = modifier) {
        ordered.forEach { transcription ->
            TranscriptionCard(
                transcription = transcription,
                onToggleFlag = { tag -> onToggleFlag(transcription, tag) },
                modifier = Modifier.padding(bottom = 6.dp),
                isPrimary = transcription.id == primaryTranscriptionId,
                onSetPrimary = onSetPrimary?.let { cb -> { cb(transcription) } },
                onEditContent = onEditContent?.let { cb -> { text: String -> cb(transcription, text) } }
            )
        }
    }
}
