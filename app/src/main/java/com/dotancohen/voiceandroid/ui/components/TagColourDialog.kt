package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.data.TagColours

/**
 * Choose the colour of a Tag.
 *
 * A Tag already has a colour without anybody choosing one — the hash of its
 * name — so the dialogue opens showing that, and *Use the calculated colour*
 * puts it back. The choice is stored in the synced settings, so it reaches
 * every device.
 */
@Composable
fun TagColourDialog(
    tagName: String,
    currentColour: String?,
    onChoose: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var hex by remember(tagName) {
        mutableStateOf(TagColours.colourFor(tagName, currentColour))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Colour of $tagName") },
        text = {
            Column {
                TagChip(name = tagName, colour = hex)
                Spacer(modifier = Modifier.height(12.dp))
                for (row in PALETTE.chunked(6)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        for (colour in row) {
                            Spacer(
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = Color(TagColours.argbFor(tagName, colour)),
                                        shape = MaterialTheme.shapes.small
                                    )
                                    .clickable { hex = colour }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = hex,
                    onValueChange = { hex = it.removePrefix("#").lowercase().take(6) },
                    label = { Text("RRGGBB") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onChoose(null); onDismiss() }) {
                        Text("Use the calculated colour")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onChoose(hex); onDismiss() }) { Text("Choose") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * A spread of colours that read well behind small text, dark and light both,
 * for the user who wants to pick rather than type six characters.
 */
private val PALETTE = listOf(
    "b71c1c", "e65100", "f9a825", "2e7d32", "00695c", "01579b",
    "1a237e", "4a148c", "880e4f", "3e2723", "37474f", "212121",
    "ef9a9a", "ffcc80", "fff59d", "a5d6a7", "80cbc4", "81d4fa",
    "9fa8da", "ce93d8", "f48fb1", "bcaaa4", "b0bec5", "eeeeee",
)
