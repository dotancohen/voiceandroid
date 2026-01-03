package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.data.Transcription

/**
 * Card component for displaying a transcription with state toggles.
 *
 * Shows:
 * - Transcription content
 * - State chips (verified, cleaned, polished) that can be toggled
 * - Service info and creation date
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TranscriptionCard(
    transcription: Transcription,
    onToggleState: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Transcription content
            Text(
                text = transcription.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // State toggles
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StateChip(
                    label = "Verified",
                    isSelected = transcription.isVerified,
                    onClick = { onToggleState("verified") }
                )
                StateChip(
                    label = "Cleaned",
                    isSelected = transcription.isCleaned,
                    onClick = { onToggleState("cleaned") }
                )
                StateChip(
                    label = "Polished",
                    isSelected = transcription.isPolished,
                    onClick = { onToggleState("polished") }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Metadata
            Text(
                text = "Service: ${transcription.service} | ${transcription.createdAt}",
                style = MaterialTheme.typography.bodySmall,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A filter chip for toggling transcription state.
 */
@Composable
private fun StateChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(label) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    )
}

/**
 * Section showing all transcriptions for audio files in a note.
 */
@Composable
fun TranscriptionsSection(
    transcriptions: Map<String, List<Transcription>>,
    onToggleState: (Transcription, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val allTranscriptions = transcriptions.values.flatten()

    if (allTranscriptions.isEmpty()) {
        return
    }

    Column(modifier = modifier) {
        Text(
            text = "Transcriptions",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        allTranscriptions.forEach { transcription ->
            TranscriptionCard(
                transcription = transcription,
                onToggleState = { tag -> onToggleState(transcription, tag) },
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}
