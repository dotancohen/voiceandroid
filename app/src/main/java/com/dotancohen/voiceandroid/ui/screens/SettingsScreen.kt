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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val serverPeerId by viewModel.serverPeerId.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncResult by viewModel.syncResult.collectAsState()
    val syncError by viewModel.syncError.collectAsState()

    var editedServerUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var editedServerPeerId by remember(serverPeerId) { mutableStateOf(serverPeerId) }
    var editedDeviceId by remember(deviceId) { mutableStateOf(deviceId) }
    var editedDeviceName by remember(deviceName) { mutableStateOf(deviceName) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") }
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

            Spacer(modifier = Modifier.height(8.dp))

            // Sync Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Synchronization",
                        style = MaterialTheme.typography.titleMedium
                    )

                    OutlinedButton(
                        onClick = { viewModel.syncNow() },
                        enabled = !isSyncing && serverUrl.isNotBlank() && serverPeerId.isNotBlank(),
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
                                Text("Sync Now")
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
        }
    }
}
