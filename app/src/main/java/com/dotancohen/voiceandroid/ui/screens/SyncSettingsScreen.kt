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
import androidx.compose.foundation.text.KeyboardActions
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
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

    // S3 config state
    val s3Enabled by viewModel.s3Enabled.collectAsState()
    val s3Bucket by viewModel.s3Bucket.collectAsState()
    val s3Region by viewModel.s3Region.collectAsState()
    val s3AccessKeyId by viewModel.s3AccessKeyId.collectAsState()
    val s3SecretAccessKey by viewModel.s3SecretAccessKey.collectAsState()
    val s3Prefix by viewModel.s3Prefix.collectAsState()
    val s3Endpoint by viewModel.s3Endpoint.collectAsState()
    val s3SaveError by viewModel.s3SaveError.collectAsState()
    val s3SaveSuccess by viewModel.s3SaveSuccess.collectAsState()

    var editedServerUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var editedServerPeerId by remember(serverPeerId) { mutableStateOf(serverPeerId) }
    var editedDeviceId by remember(deviceId) { mutableStateOf(deviceId) }
    var editedDeviceName by remember(deviceName) { mutableStateOf(deviceName) }

    // S3 form fields
    var editedS3Bucket by remember(s3Bucket) { mutableStateOf(s3Bucket) }
    var editedS3Region by remember(s3Region) { mutableStateOf(s3Region) }
    var editedS3AccessKeyId by remember(s3AccessKeyId) { mutableStateOf(s3AccessKeyId) }
    var editedS3SecretAccessKey by remember(s3SecretAccessKey) { mutableStateOf(s3SecretAccessKey) }
    var editedS3Prefix by remember(s3Prefix) { mutableStateOf(s3Prefix) }
    var editedS3Endpoint by remember(s3Endpoint) { mutableStateOf(s3Endpoint) }

    // Check for unsynced changes and update debug info when this screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.checkUnsyncedChanges()
        viewModel.updateDebugInfo()
        viewModel.loadS3Config()
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

            // Cloud Storage (S3) Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cloud Storage (S3)",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (s3Enabled) {
                            Text(
                                text = "Enabled",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "When configured, audio files are uploaded to S3 and downloaded on demand. Config syncs automatically to all connected devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = editedS3Bucket,
                        onValueChange = { editedS3Bucket = it },
                        label = { Text("Bucket") },
                        placeholder = { Text("my-voice-bucket") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editedS3Region,
                        onValueChange = { editedS3Region = it },
                        label = { Text("Region") },
                        placeholder = { Text("us-east-1") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editedS3AccessKeyId,
                        onValueChange = { editedS3AccessKeyId = it },
                        label = { Text("Access Key ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editedS3SecretAccessKey,
                        onValueChange = { editedS3SecretAccessKey = it },
                        label = { Text("Secret Access Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation()
                    )

                    OutlinedTextField(
                        value = editedS3Prefix,
                        onValueChange = { editedS3Prefix = it },
                        label = { Text("Prefix (optional)") },
                        placeholder = { Text("audio/") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = editedS3Endpoint,
                        onValueChange = { editedS3Endpoint = it },
                        label = { Text("Custom Endpoint (optional)") },
                        placeholder = { Text("https://nyc3.digitaloceanspaces.com") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri)
                    )

                    // Save / Disable buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                viewModel.saveS3Config(
                                    bucket = editedS3Bucket,
                                    region = editedS3Region,
                                    accessKeyId = editedS3AccessKeyId,
                                    secretAccessKey = editedS3SecretAccessKey,
                                    prefix = editedS3Prefix,
                                    endpoint = editedS3Endpoint
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save")
                        }

                        if (s3Enabled) {
                            OutlinedButton(
                                onClick = { viewModel.disableS3() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Text("Disable")
                            }
                        }
                    }

                    // Status messages
                    if (s3SaveSuccess) {
                        Text(
                            text = "S3 configuration saved successfully",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    s3SaveError?.let { error ->
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
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
