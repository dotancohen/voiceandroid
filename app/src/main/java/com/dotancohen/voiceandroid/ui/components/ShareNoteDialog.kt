package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * One thing that can be shared out of a Note.
 *
 * [depth] is what makes the list read as a tree: a Transcription sits under
 * the recording it belongs to, so it is clear what is being sent with what.
 */
data class ShareItem(
    val id: String,
    val label: String,
    /** A line of what it holds, so the user can tell two of them apart. */
    val detail: String? = null,
    val depth: Int = 0,
)

/**
 * Choose what to share out of a Note.
 *
 * Shown only when there is a choice to make: a Note that holds nothing but
 * its text, or nothing but one recording, is sent without asking, because
 * there is nothing to decide. Ticking a recording does not tick its
 * Transcriptions, and ticking a Transcription does not send the audio: each
 * is a thing of its own, and the tree is there to show which belongs to
 * which.
 */
@Composable
fun ShareNoteDialog(
    items: List<ShareItem>,
    onShare: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var chosen by remember { mutableStateOf(items.map { it.id }.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                for (item in items) {
                    val on = item.id in chosen
                    Row(
                        // Top-aligned, like every table here: the details
                        // wrap, and a centred tick box would wander.
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                chosen = if (on) chosen - item.id else chosen + item.id
                            }
                            .padding(vertical = 3.dp)
                    ) {
                        Spacer(modifier = Modifier.width((item.depth * 16).dp))
                        androidx.compose.material3.Icon(
                            imageVector = if (on) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                            contentDescription = if (on) "${item.label}, will be shared" else "${item.label}, will not be shared",
                            modifier = Modifier.size(20.dp),
                            tint = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(item.label, style = MaterialTheme.typography.bodyMedium)
                            item.detail?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onShare(chosen); onDismiss() },
                enabled = chosen.isNotEmpty()
            ) { Text("Share") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
