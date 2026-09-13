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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.viewmodel.SettingsViewModel
import androidx.compose.material3.Switch

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
    val isUploading by viewModel.isUploading.collectAsState()
    val joinMessage by viewModel.joinMessage.collectAsState()
    val checkRows by viewModel.checkRows.collectAsState()
    val listening by viewModel.listening.collectAsState()
    val listenUrls by viewModel.listenUrls.collectAsState()
    val certificateFingerprint by viewModel.certificateFingerprint.collectAsState()
    val accountId by viewModel.accountId.collectAsState()
    val uploadMessage by viewModel.uploadMessage.collectAsState()
    val syncResult by viewModel.syncResult.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val notDuplicatedLine by viewModel.notDuplicatedLine.collectAsState()
    val peerSummaries by viewModel.peerSummaries.collectAsState()
    val maxSyncFileSizeMb by viewModel.maxSyncFileSizeMb.collectAsState()

    var editedServerUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var editedServerPeerId by remember(serverPeerId) { mutableStateOf(serverPeerId) }
    var editedDeviceId by remember(deviceId) { mutableStateOf(deviceId) }
    var editedDeviceName by remember(deviceName) { mutableStateOf(deviceName) }
    var editedMaxFileSizeMb by remember(maxSyncFileSizeMb) { mutableStateOf(maxSyncFileSizeMb.toString()) }

    // Check for unsynced changes and update debug info when this screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.refreshProof()
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
            // Proof (Stage 10): what is on this phone only, and when each peer was last reached.
            // This line, here, is the only place it is said: no notification, no badge.
            Text(
                text = notDuplicatedLine ?: "Counting what is on this device only…",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.semantics { contentDescription = notDuplicatedLine ?: "Counting what is on this device only" }
            )
            peerSummaries.forEach { peer ->
                val reached = peer.lastReachedAt?.let { java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(it * 1000)) } ?: "never"
                Text(
                    text = "${peer.peerName.ifEmpty { peer.peerId.take(8) }}: last reached $reached" + (if (peer.lastOperation.isEmpty()) "" else ", last operation ${peer.lastOperation}"),
                    style = MaterialTheme.typography.bodySmall
                )
            }

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

            // Sync Limits Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Sync Limits",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Files larger than this limit will not be synced. They will be tagged with '_system/_nonsynced/_too-big' and remain local only.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = editedMaxFileSizeMb,
                        onValueChange = { newValue ->
                            // Only allow numeric input
                            if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                                editedMaxFileSizeMb = newValue
                            }
                        },
                        label = { Text("Max File Size (MB)") },
                        placeholder = { Text("100") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("Default: 100 MB. Set to 0 for unlimited.") }
                    )

                    Button(
                        onClick = {
                            val sizeMb = editedMaxFileSizeMb.toUIntOrNull() ?: 100u
                            viewModel.saveMaxSyncFileSizeMb(sizeMb)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save File Size Limit")
                    }
                }
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

                    // Sync button
                    OutlinedButton(
                        onClick = { viewModel.sync() },
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
                                Text("Sync")
                            }
                        }
                    }

                    // This device: the listener switch, the address a peer would type, the certificate
                    LaunchedEffect(Unit) { viewModel.loadThisDevice() }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Listen for peers", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (listening) "Other devices can reach this phone" else "Off; nothing can reach this phone",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(checked = listening, onCheckedChange = { viewModel.setListening(it) })
                    }
                    Text("Account $accountId", style = MaterialTheme.typography.bodySmall)
                    Text("Address ${listenUrls.joinToString(", ").ifEmpty { "unknown (not on a network?)" }}", style = MaterialTheme.typography.bodySmall)
                    Text("Certificate $certificateFingerprint", style = MaterialTheme.typography.bodySmall)

                    // Exchange: sync, then send and fetch recordings; the one button the manual leads with
                    OutlinedButton(
                        onClick = { viewModel.exchange() },
                        enabled = !isSyncing && serverUrl.isNotBlank() && serverPeerId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isSyncing) "Working..." else "Exchange (notes and recordings)")
                    }

                    // A setup text shown by another device: a code to join
                    // its account, or a server's grant text to host this one
                    var setupText by remember { mutableStateOf("") }
                    OutlinedTextField(
                        value = setupText,
                        onValueChange = { setupText = it },
                        label = { Text("Setup text from another device") },
                        placeholder = { Text("voice://pair?...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { viewModel.pairWith(setupText) },
                        enabled = setupText.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use setup text")
                    }
                    joinMessage?.let { message ->
                        Text(text = message, color = MaterialTheme.colorScheme.primary)
                    }

                    // Upload button: recordings to the bucket, never part of a sync
                    OutlinedButton(
                        onClick = { viewModel.upload() },
                        enabled = !isUploading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isUploading) "Uploading..." else "Upload")
                    }
                    uploadMessage?.let { message ->
                        Text(text = message, color = MaterialTheme.colorScheme.primary)
                    }

                    // Sync result
                    syncResult?.let { result ->
                        if (result.success) {
                            Text(
                                text = "Done. Received ${result.notesReceived} changes, sent ${result.notesSent}" +
                                    (if (result.filesSent > 0 || result.filesFetched > 0) ", sent ${result.filesSent} and fetched ${result.filesFetched} recordings (${result.bytesMoved / (1024 * 1024)} MB)" else ""),
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text(
                                text = "Sync failed: ${result.errorMessage ?: "Unknown error"}",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        result.warnings.forEach { warning ->
                            Text(
                                text = "Warning: $warning",
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        if (result.requestId.isNotEmpty()) {
                            Text(text = "Request ${result.requestId}", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    // The connection check: one line per thing that can be wrong, each with its code
                    OutlinedButton(
                        onClick = { viewModel.checkConnection() },
                        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Check the connection to the peer" }
                    ) {
                        Text("Check connection")
                    }
                    checkRows?.forEach { row ->
                        Text(
                            text = (if (row.passed) "✓ " else "✗ ") + row.name + ": " + row.detail + (if (row.code.isEmpty()) "" else " (" + row.code + ")"),
                            color = if (row.passed) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
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
