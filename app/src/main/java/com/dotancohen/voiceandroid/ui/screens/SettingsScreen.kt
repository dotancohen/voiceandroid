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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BugReport
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.util.CriticalLog
import com.dotancohen.voiceandroid.viewmodel.SettingsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(),
    onNavigateToSyncSettings: () -> Unit = {},
    onNavigateToManageTags: () -> Unit = {},
    onNavigateToImportAudio: () -> Unit = {}
) {
    val audiofileDirectory by viewModel.audiofileDirectory.collectAsState()
    val defaultAudiofileDirectory by viewModel.defaultAudiofileDirectory.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val serverPeerId by viewModel.serverPeerId.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncResult by viewModel.syncResult.collectAsState()
    val syncError by viewModel.syncError.collectAsState()
    val hasUnsyncedChanges by viewModel.hasUnsyncedChanges.collectAsState()
    val logContent by viewModel.logContent.collectAsState()

    val context = LocalContext.current

    // Check for unsynced changes when screen becomes visible
    LaunchedEffect(Unit) {
        viewModel.checkUnsyncedChanges()
    }

    // State for permission dialog
    var showPermissionDialog by remember { mutableStateOf(false) }
    // State for log dialog
    var showLogDialog by remember { mutableStateOf(false) }
    // State for critical log dialog
    var showCriticalLogDialog by remember { mutableStateOf(false) }
    var criticalLogContent by remember { mutableStateOf("") }
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
            // Synchronization Section
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

                    Spacer(modifier = Modifier.height(4.dp))

                    // Sync Settings navigation
                    TextButton(
                        onClick = onNavigateToSyncSettings,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sync Settings")
                    }
                }
            }

            // Manage Tags
            OutlinedButton(
                onClick = onNavigateToManageTags,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.List,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Manage Tags")
            }

            // Import Audio Files
            OutlinedButton(
                onClick = onNavigateToImportAudio,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.AudioFile,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text("Import Audio Files")
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

            // Log Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        viewModel.loadLogContent()
                        showLogDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("View Log")
                }
                OutlinedButton(
                    onClick = {
                        criticalLogContent = CriticalLog.getLogContents()
                        showCriticalLogDialog = true
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.BugReport,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Critical Log")
                }
            }
        }
    }

    // Log Dialog
    if (showLogDialog) {
        AlertDialog(
            onDismissRequest = { showLogDialog = false },
            title = { Text("Application Log") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    Text(
                        text = "Log file: ${viewModel.getLogFilePath()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SelectionContainer {
                        Text(
                            text = logContent.ifEmpty { "Log is empty" },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showLogDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Critical Log Dialog
    if (showCriticalLogDialog) {
        AlertDialog(
            onDismissRequest = { showCriticalLogDialog = false },
            title = { Text("Critical Log") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    Text(
                        text = "Log file: ${CriticalLog.getLogFilePath()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    SelectionContainer {
                        Text(
                            text = criticalLogContent.ifEmpty { "No critical errors logged" },
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState())
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCriticalLogDialog = false }) {
                    Text("Close")
                }
            },
            dismissButton = {
                if (criticalLogContent.isNotEmpty()) {
                    TextButton(onClick = {
                        CriticalLog.clearLog()
                        criticalLogContent = ""
                    }) {
                        Text("Clear Log")
                    }
                }
            }
        )
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
