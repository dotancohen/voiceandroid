package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp

/**
 * Create a Tag.
 *
 * The one place a Tag is made, wherever the user is standing: the tag
 * hierarchy screen, the tag screen of a Note, and the Tags dialogue of a
 * selection of Notes all open this. Whatever is added here later — a colour,
 * a description — appears in all three at once, which is why it is not
 * written out three times.
 *
 * The name field takes the keyboard as the dialogue opens: the field is the
 * whole purpose of the dialogue.
 */
@Composable
fun CreateTagDialog(
    /** Shown so the user can see where the new Tag will sit, when it has a parent. */
    parentName: String? = null,
    /** What the Tag will be used for here, e.g. "It will be put on this Note." */
    note: String? = null,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (parentName != null) "Create Tag under $parentName" else "Create Tag") },
        text = {
            Column {
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Tag name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(autoFocus())
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (name.isNotBlank()) {
                        onCreate(name.trim())
                        onDismiss()
                    }
                },
                enabled = name.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
