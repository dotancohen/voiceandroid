package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.viewmodel.TagSelection

/**
 * Tags for several notes at once. A box is filled when every selected note
 * has the tag, half-filled when only some do. Tapping an unfilled or
 * half-filled box adds the tag to the notes that lack it; tapping a filled
 * box removes it from all of them.
 */
@Composable
fun MultiTagDialog(
    noteCount: Int,
    tags: List<TagSelection>,
    onToggle: (String) -> Unit,
    onDismiss: () -> Unit,
    /** Create a Tag and put it on every selected Note. */
    onCreateTag: ((String) -> Unit)? = null
) {
    var showCreateTag by remember { mutableStateOf(false) }
    if (showCreateTag && onCreateTag != null) {
        CreateTagDialog(
            note = "It will be put on the $noteCount selected Notes.",
            onCreate = onCreateTag,
            onDismiss = { showCreateTag = false }
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tags for $noteCount Notes") },
        text = {
            if (tags.isEmpty()) {
                Text("No Tags yet. Create one with the button below.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                    items(tags, key = { it.tag.tag.id }) { option ->
                        val state = when (option.noteCount) {
                            0 -> ToggleableState.Off
                            noteCount -> ToggleableState.On
                            else -> ToggleableState.Indeterminate
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onToggle(option.tag.tag.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Tight: a phone should show a dozen tags at once,
                            // not four with air between them.
                            TriStateCheckbox(
                                state = state,
                                onClick = { onToggle(option.tag.tag.id) },
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(option.tag.tag.name, style = MaterialTheme.typography.bodyMedium)
                                if (option.tag.depth > 0) {
                                    Text(
                                        text = option.tag.path,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = {
            if (onCreateTag != null) {
                TextButton(onClick = { showCreateTag = true }) { Text("Create Tag") }
            }
        }
    )
}
