package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.transcription.TranscriptionPreferences
import com.dotancohen.voiceandroid.transcription.WhisperModel
import com.dotancohen.voiceandroid.transcription.WhisperModels

/**
 * "Transcribe" dialog for one recording: pick a downloaded model and the
 * language, then Cancel, Settings (download models, defaults) or Transcribe.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscribeDialog(
    audioFile: AudioFile,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onTranscribe: (modelId: String, language: String) -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { TranscriptionPreferences(context) }
    val installed = remember { WhisperModels.installed(context) }
    var model by remember { mutableStateOf(installed.firstOrNull { it.id == prefs.modelId } ?: installed.firstOrNull()) }
    var language by remember { mutableStateOf(prefs.language) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transcribe on this phone") },
        text = {
            Column {
                Text(audioFile.filename, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                if (installed.isEmpty()) {
                    Text("No model is downloaded yet. Open Settings to download one.", color = MaterialTheme.colorScheme.error)
                } else {
                    Selector(
                        label = "Model",
                        value = model?.title ?: "",
                        options = installed.map { it.id to it.title },
                        onSelect = { id -> model = installed.first { it.id == id } }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Selector(
                    label = "Language",
                    value = TranscriptionPreferences.languageTitle(language),
                    options = TranscriptionPreferences.LANGUAGES,
                    onSelect = { language = it }
                )
                model?.languages?.takeIf { it.isNotEmpty() && language !in it }?.let { only ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "This model only knows ${only.joinToString { TranscriptionPreferences.languageTitle(it) }}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onSettings) { Text("Settings") }
                TextButton(
                    onClick = { model?.let { onTranscribe(it.id, language) } },
                    enabled = model != null
                ) { Text("Transcribe") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Selector(label: String, value: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = open) },
            modifier = Modifier.menuAnchor().fillMaxWidth()
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for ((id, title) in options) {
                DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(id); open = false })
            }
        }
    }
}
