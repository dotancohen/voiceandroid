package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.data.Transcription

/**
 * One transcription: its text, then one line with the service, the date
 * (with the time only when [showTime]) and the three state toggles as icons:
 * verified (check mark in a badge), cleaned (broom), polished (sparkles).
 * A coloured icon means the flag is set.
 */
@Composable
fun TranscriptionCard(
    transcription: Transcription,
    onToggleState: (String) -> Unit,
    showTime: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)) {
            Text(
                text = transcription.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                val stamp = if (showTime) transcription.createdAt else transcriptionDate(transcription)
                Text(
                    text = "${transcription.service} | $stamp",
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                StateIcon("Verified", Icons.Filled.Verified, transcription.isVerified) { onToggleState("verified") }
                StateIcon("Cleaned", Icons.Filled.CleaningServices, transcription.isCleaned) { onToggleState("cleaned") }
                StateIcon("Polished", Icons.Filled.AutoAwesome, transcription.isPolished) { onToggleState("polished") }
            }
        }
    }
}

/** The date part of a "yyyy-MM-dd HH:mm:ss" stamp. */
fun transcriptionDate(t: Transcription): String = t.createdAt.take(10)

@Composable
private fun StateIcon(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(32.dp)) {
        Icon(
            imageVector = icon,
            contentDescription = if (selected) "$label (on)" else "$label (off)",
            modifier = Modifier.size(18.dp),
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
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
    onToggleState: (Transcription, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (transcriptions.isEmpty()) return
    val perDate = transcriptions.groupingBy { transcriptionDate(it) }.eachCount()

    Column(modifier = modifier) {
        transcriptions.forEach { transcription ->
            TranscriptionCard(
                transcription = transcription,
                onToggleState = { tag -> onToggleState(transcription, tag) },
                showTime = (perDate[transcriptionDate(transcription)] ?: 0) > 1,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
    }
}
