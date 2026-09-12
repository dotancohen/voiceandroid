package com.dotancohen.voiceandroid.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Button
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.transcription.TranscriptionPreferences
import com.dotancohen.voiceandroid.transcription.WhisperModels

/**
 * "Transcribe" dialog: pick a downloaded model and the language, then Cancel,
 * Settings (download models, defaults) or Transcribe. [subtitle] names what
 * will be transcribed: one file name, or "N recordings in M notes".
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TranscribeDialog(
    subtitle: String,
    onDismiss: () -> Unit,
    onSettings: () -> Unit,
    onTranscribe: (modelId: String, language: String) -> Unit,
    /**
     * How many finished transcriptions the recording already has.
     *
     * More than none is said here, sternly, rather than in a second dialog
     * after the user has chosen: the warning belongs where the decision is
     * made, and one dialog is enough.
     */
    existingTranscriptions: Int = 0
) {
    val context = LocalContext.current
    val prefs = remember { TranscriptionPreferences(context) }
    val installed = remember { WhisperModels.installed(context) }
    var model by remember { mutableStateOf(installed.firstOrNull { it.id == prefs.modelId } ?: installed.firstOrNull()) }
    var language by remember { mutableStateOf(prefs.language) }
    // The languages the user gave a button of their own, and everything else
    // they left switched on (Settings → Transcription → Languages).
    val buttonLanguages = remember { prefs.buttonLanguages() }
    val selectable = remember { prefs.selectableLanguages() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transcribe") },
        text = {
            Column {
                if (existingTranscriptions > 0) {
                    Text(
                        text = if (existingTranscriptions == 1) {
                            "This recording has already been transcribed."
                        } else {
                            "$existingTranscriptions of these recordings have already been transcribed."
                        },
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Transcribing it again adds another one beside them. Nothing is " +
                            "replaced and nothing is lost, and the phone will work for several " +
                            "minutes. Please make sure this is what you intend.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                // One button per language the user asked to have to hand:
                // pressing it transcribes in that language straight away.
                if (buttonLanguages.isNotEmpty() && model != null) {
                    Text(
                        "Transcribe in",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        for ((code, title) in buttonLanguages) {
                            // A model that knows only one language (the
                            // Hebrew fine-tunes) cannot be asked for another.
                            val known = model?.languages.orEmpty()
                            val possible = known.isEmpty() ||
                                code in known ||
                                code == TranscriptionPreferences.LANGUAGE_AUTO
                            Button(
                                onClick = { model?.let { onTranscribe(it.id, code) } },
                                enabled = possible,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text(shortLanguageTitle(title), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                Selector(
                    label = if (buttonLanguages.isEmpty()) "Language" else "Another language",
                    value = TranscriptionPreferences.languageTitle(language),
                    options = selectable,
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

/**
 * "Hebrew (עברית)" on a button is too long: the button shows the name in the
 * language itself when there is one, which is what a speaker of it looks for.
 */
fun shortLanguageTitle(title: String): String {
    val inBrackets = title.substringAfter('(', "").substringBefore(')')
    return inBrackets.ifBlank { title }
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
