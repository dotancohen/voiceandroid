package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.dotancohen.voiceandroid.audio.PlaybackPreferences
import com.dotancohen.voiceandroid.ui.components.PlaybackSpeedControl
import com.dotancohen.voiceandroid.util.UiPreferences

/**
 * Settings → Playback: how recordings are played.
 *
 * Its own screen rather than a corner of Advanced settings. Playing a
 * recording is what this application is for, so the settings for it belong
 * where they can be found — Advanced settings is where a user goes to change
 * something unusual, not to decide how the application behaves every day.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val ui = remember { UiPreferences(context) }
    val playback = remember { PlaybackPreferences(context) }
    var automatic by remember { mutableStateOf(ui.autoplayOnOpen) }
    var speed by remember { mutableStateOf(playback.speed) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playback") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Automatic playback", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Switch(
                    checked = automatic,
                    onCheckedChange = {
                        automatic = it
                        ui.autoplayOnOpen = it
                    }
                )
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(
                        "Play the main recording when a Note is opened",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "The Note's main recording starts by itself. Off by default: a Note " +
                            "opened in company should not begin talking on its own. A recording " +
                            "already playing carries on either way.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text("Speed", style = MaterialTheme.typography.titleMedium)
            Text(
                "The speed every player starts at. It can also be changed under the waveform " +
                    "while listening, and what is chosen there is remembered here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            PlaybackSpeedControl(
                speed = speed,
                onSpeedChange = {
                    speed = it
                    playback.speed = it
                }
            )
        }
    }
}
