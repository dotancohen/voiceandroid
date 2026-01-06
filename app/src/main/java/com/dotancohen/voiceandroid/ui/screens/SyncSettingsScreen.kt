package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onBack: () -> Unit
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val serverPeerId by viewModel.serverPeerId.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncResult by viewModel.syncResult.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val hasUnsyncedChanges by viewModel.hasUnsyncedChanges.collectAsState()

    var editedServerUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var editedServerPeerId by remember(serverPeerId) { mutableStateOf(serverPeerId) }
    var editedDeviceId by remember(deviceId) { mutableStateOf(deviceId) }
    var editedDeviceName by remember(deviceName) { mutableStateOf(deviceName) }

    // Check for unsynced changes and update debug info when this screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.checkUnsyncedChanges()
        viewModel.updateDebugInfo()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Sync Settings") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Sync Server Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync Server",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = editedServerUrl,
                        onValueChange = { editedServerUrl = it },
                        label = { Text("Server URL") },
                        placeholder = { Text("https://example.com:8384") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )

                    OutlinedTextField(
                        value = editedServerPeerId,
                        onValueChange = { editedServerPeerId = it },
                        label = { Text("Server Peer ID") },
                        placeholder = { Text("32 hex characters") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            // Device Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Device",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedTextField(
                        value = editedDeviceName,
                        onValueChange = { editedDeviceName = it },
                        label = { Text("Device Name") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editedDeviceId,
                        onValueChange = { editedDeviceId = it },
                        label = { Text("Device ID") },
                        placeholder = { Text("32 hex characters (or generate new)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    TextButton(
                        onClick = { editedDeviceId = viewModel.generateNewDeviceId() }
                    ) {
                        Text("Generate New Device ID")
                    }
                }
            }

            // Save Button
            Button(
                onClick = {
                    viewModel.saveSettings(
                        serverUrl = editedServerUrl,
                        serverPeerId = editedServerPeerId,
                        deviceId = editedDeviceId,
                        deviceName = editedDeviceName
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            // Sync Actions Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync Actions",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Sync Now button
                    OutlinedButton(
                        onClick = { viewModel.syncNow() },
                        enabled = !isSyncing && serverUrl.isNotBlank() && serverPeerId.isNotBlank(),
                        colors = if (hasUnsyncedChanges) {
                            ButtonDefaults.outlinedButtonColors(
                                containerColor = Color(0xFFFFEB3B),
                                contentColor = Color.Black
                            )
                        } else {
                            ButtonDefaults.outlinedButtonColors()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSyncing) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.height(20.dp)
                                )
                                Text("Syncing...")
                            }
                        } else {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null
                                )
                                Text(if (hasUnsyncedChanges) "Sync Now (changes pending)" else "Sync Now")
                            }
                        }
                    }

                    // Sync result
                    syncResult?.let { result ->
                        if (result.success) {
                            Text(
                                text = "Sync successful! Received: ${result.notesReceived}, Sent: ${result.notesSent}",
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Sync failed: ${result.errorMessage ?: "Unknown error"}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    // Sync error
                    syncError?.let { error ->
                        Text(
                            text = "Error: $error",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Advanced Sync Options
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Advanced Sync Options",
                        style = MaterialTheme.typography.titleMedium
                    )

                    // Full Re-sync explanation
                    Text(
                        text = "Full Re-sync",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Fetches all data from the server in a single request. Use this if some data like attachments or transcriptions are missing. Does not affect local data.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { viewModel.fullResync() },
                        enabled = !isSyncing && serverUrl.isNotBlank() && serverPeerId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Full Re-sync")
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Reset Sync explanation
                    Text(
                        text = "Reset Sync Timestamps",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Clears the 'last synced' timestamps, causing the next regular sync to exchange all data with peers. Use this if instances are out of sync and regular sync isn't picking up all changes. Server configuration is preserved.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { viewModel.resetSyncTimestamps() },
                        enabled = !isSyncing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset Sync Timestamps")
                    }
                }
            }

            // Debug Info Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Debug Info",
                        style = MaterialTheme.typography.titleMedium
                    )

                    debugInfo?.let { info ->
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } ?: Text(
                        text = "No debug info available",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    TextButton(
                        onClick = { viewModel.updateDebugInfo() }
                    ) {
                        Text("Refresh Debug Info")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
