package com.dotancohen.voiceandroid.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.viewmodel.SettingsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel()
) {
    val serverUrl by viewModel.serverUrl.collectAsState()
    val serverPeerId by viewModel.serverPeerId.collectAsState()
    val deviceId by viewModel.deviceId.collectAsState()
    val deviceName by viewModel.deviceName.collectAsState()
    val audiofileDirectory by viewModel.audiofileDirectory.collectAsState()
    val defaultAudiofileDirectory by viewModel.defaultAudiofileDirectory.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncResult by viewModel.syncResult.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val debugInfo by viewModel.debugInfo.collectAsState()
    val hasUnsyncedChanges by viewModel.hasUnsyncedChanges.collectAsState()

    var editedServerUrl by remember(serverUrl) { mutableStateOf(serverUrl) }
    var editedServerPeerId by remember(serverPeerId) { mutableStateOf(serverPeerId) }
    var editedDeviceId by remember(deviceId) { mutableStateOf(deviceId) }
    var editedDeviceName by remember(deviceName) { mutableStateOf(deviceName) }

    val context = LocalContext.current

    // State for permission dialog
    var showPermissionDialog by remember { mutableStateOf(false) }
    // Pending audio path is stored in ViewModel to survive activity recreation
    val pendingAudioPath by viewModel.pendingAudioPath.collectAsState()

    // Check if we have storage permission
    fun hasStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // Lower versions use scoped storage or legacy permissions
        }
    }

    // Track permission status for UI
    val hasAllFilesPermission = hasStoragePermission()

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistent permission
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)

            // Convert content URI to file path
            val path = getPathFromUri(context, uri)
            if (path != null) {
                // Check if path is outside app's sandbox and permission is needed
                val appExternalDir = context.getExternalFilesDir(null)?.absolutePath ?: ""
                val isOutsideAppSandbox = !path.startsWith(appExternalDir)

                if (isOutsideAppSandbox && !hasStoragePermission()) {
                    viewModel.setPendingAudioPath(path)
                    showPermissionDialog = true
                } else {
                    viewModel.saveAudiofileDirectory(path)
                }
            }
        }
    }

    // Apply pending path after user returns from settings (if permission granted)
    // This uses LaunchedEffect to handle the side effect properly
    LaunchedEffect(pendingAudioPath, hasAllFilesPermission) {
        if (pendingAudioPath != null && hasAllFilesPermission) {
            viewModel.tryApplyPendingAudioPath()
        }
    }

    // Check for unsynced changes and update debug info when this screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.checkUnsyncedChanges()
        viewModel.updateDebugInfo()
    }

    // Permission dialog
    if (showPermissionDialog) {
        AlertDialog(
            onDismissRequest = { showPermissionDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Storage Permission Required") },
            text = {
                Text(
                    "To save audio files outside the app's storage, you need to grant " +
                    "\"All files access\" permission. This allows Voice to write to any folder on your device.\n\n" +
                    "Tap \"Open Settings\" and enable the permission for Voice."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
                    // Open app's permission settings
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPermissionDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

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
            // Sync Section (at top for easy access)
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

                    // Full Re-sync button
                    TextButton(
                        onClick = { viewModel.fullResync() },
                        enabled = !isSyncing && serverUrl.isNotBlank() && serverPeerId.isNotBlank()
                    ) {
                        Text("Full Re-sync (fetch all)")
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

                    // Debug info
                    debugInfo?.let { info ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = info,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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

            // Audio Files Storage
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Audio Files Storage",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = "Audio files from sync will be stored in this directory.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Current directory display
                    Text(
                        text = audiofileDirectory,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { folderPickerLauncher.launch(null) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("Choose Folder")
                        }

                        TextButton(
                            onClick = { viewModel.resetAudiofileDirectory() }
                        ) {
                            Text("Reset to Default")
                        }
                    }

                    // Permission status (only show on Android 11+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = if (hasAllFilesPermission) "✓ All files access granted"
                                       else "⚠ All files access not granted",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hasAllFilesPermission) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.error
                            )
                            if (!hasAllFilesPermission) {
                                TextButton(
                                    onClick = {
                                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                        }
                                        context.startActivity(intent)
                                    }
                                ) {
                                    Text("Grant", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }

                    if (audiofileDirectory != defaultAudiofileDirectory) {
                        Text(
                            text = "Default: $defaultAudiofileDirectory",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

        }
    }
}

/**
 * Convert a content:// URI from the folder picker to a filesystem path.
 * Works for primary storage and SD cards on most devices.
 */
private fun getPathFromUri(context: android.content.Context, uri: Uri): String? {
    // For tree URIs from OpenDocumentTree
    val docId = try {
        DocumentsContract.getTreeDocumentId(uri)
    } catch (e: Exception) {
        return null
    }

    // Handle primary storage (e.g., "primary:Download/VoiceAudio")
    if (docId.startsWith("primary:")) {
        val relativePath = docId.substringAfter("primary:")
        val basePath = android.os.Environment.getExternalStorageDirectory().absolutePath
        return "$basePath/$relativePath"
    }

    // Handle SD card or other volumes (e.g., "1234-5678:folder")
    if (docId.contains(":")) {
        val volumeId = docId.substringBefore(":")
        val relativePath = docId.substringAfter(":")

        // Try to find the volume mount point
        val storageManager = context.getSystemService(android.content.Context.STORAGE_SERVICE)
                as android.os.storage.StorageManager
        try {
            val storageVolumes = storageManager.storageVolumes
            for (volume in storageVolumes) {
                val volumeDirectory = volume.directory
                if (volumeDirectory != null) {
                    val uuid = volume.uuid
                    if (uuid != null && uuid.equals(volumeId, ignoreCase = true)) {
                        return "${volumeDirectory.absolutePath}/$relativePath"
                    }
                    // Check if this is the primary volume
                    if (volume.isPrimary && volumeId == "primary") {
                        return "${volumeDirectory.absolutePath}/$relativePath"
                    }
                }
            }
        } catch (e: Exception) {
            // Fall through to return null
        }
    }

    return null
}
