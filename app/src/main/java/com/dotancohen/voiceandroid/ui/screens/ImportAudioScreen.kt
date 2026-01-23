package com.dotancohen.voiceandroid.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.viewmodel.ImportAudioViewModel
import com.dotancohen.voiceandroid.viewmodel.ImportState
import com.dotancohen.voiceandroid.viewmodel.SelectableTagItem

/**
 * Screen for importing audio files from a folder.
 *
 * Features:
 * - Folder picker for selecting source folder
 * - Hierarchical tag selection with checkboxes
 * - Progress display during import
 * - Error reporting via Critical Log
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportAudioScreen(
    onBack: () -> Unit,
    onImportComplete: () -> Unit = {},
    viewModel: ImportAudioViewModel = viewModel()
) {
    val context = LocalContext.current

    val selectedFolderUri by viewModel.selectedFolderUri.collectAsState()
    val selectedFolderName by viewModel.selectedFolderName.collectAsState()
    val audioFileCount by viewModel.audioFileCount.collectAsState()
    val filteredTags by viewModel.filteredTags.collectAsState()
    val filterText by viewModel.filterText.collectAsState()
    val collapsedTagIds by viewModel.collapsedTagIds.collectAsState()
    val selectedTagIds by viewModel.selectedTagIds.collectAsState()
    val importState by viewModel.importState.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var showPermissionDialog by remember { mutableStateOf(false) }

    // Check storage permission
    val hasStoragePermission = viewModel.hasStoragePermission()

    // Folder picker launcher
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            // Take persistent permission
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            viewModel.setSelectedFolder(uri)
        }
    }

    // Show error messages
    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Handle import completion
    LaunchedEffect(importState) {
        if (importState is ImportState.Complete) {
            val state = importState as ImportState.Complete
            if (state.failedCount == 0) {
                snackbarHostState.showSnackbar("Imported ${state.successCount} files successfully")
            } else {
                snackbarHostState.showSnackbar(
                    "Imported ${state.successCount} files. ${state.failedCount} failed (see Critical Log)"
                )
            }
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
                    "To import audio files from external storage, you need to grant " +
                    "\"All files access\" permission.\n\n" +
                    "Tap \"Open Settings\" and enable the permission for Voice."
                )
            },
            confirmButton = {
                Button(onClick = {
                    showPermissionDialog = false
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Import Audio Files") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Source Folder Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Source Folder",
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(
                        text = selectedFolderName ?: "No folder selected",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (selectedFolderName != null)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (audioFileCount > 0) {
                        Text(
                            text = "$audioFileCount audio file(s) found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (!hasStoragePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                showPermissionDialog = true
                            } else {
                                folderPickerLauncher.launch(null)
                            }
                        },
                        enabled = importState !is ImportState.InProgress
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Choose Folder")
                    }

                    // Permission status (only show on Android 11+)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !hasStoragePermission) {
                        Text(
                            text = "All files access not granted - may be needed for some folders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            // Tags Selection Card
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
                            text = "Tags to Apply",
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (selectedTagIds.isNotEmpty()) {
                            Text(
                                text = "${selectedTagIds.size} selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Text(
                        text = "Selected tags will be applied to all imported notes",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Filter field
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { viewModel.setFilterText(it) },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Filter tags...") },
                        singleLine = true,
                        trailingIcon = {
                            if (filterText.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setFilterText("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear filter"
                                    )
                                }
                            }
                        }
                    )

                    // Tags list (fixed height to prevent infinite scroll issues)
                    if (filteredTags.isEmpty()) {
                        Text(
                            text = if (filterText.isNotEmpty()) "No tags match \"$filterText\""
                                   else if (isLoading) "Loading tags..."
                                   else "No tags available",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                        ) {
                            items(filteredTags) { tagItem ->
                                SelectableTagRow(
                                    tagItem = tagItem,
                                    isCollapsed = collapsedTagIds.contains(tagItem.tag.id),
                                    isFiltering = filterText.isNotEmpty(),
                                    filterText = filterText,
                                    onToggleSelection = { viewModel.toggleTag(tagItem.tag.id) },
                                    onToggleCollapse = { viewModel.toggleCollapse(tagItem.tag.id) }
                                )
                            }
                        }
                    }
                }
            }

            // Import Button
            Button(
                onClick = { viewModel.startImport() },
                modifier = Modifier.fillMaxWidth(),
                enabled = selectedFolderUri != null &&
                          audioFileCount > 0 &&
                          importState !is ImportState.InProgress
            ) {
                Text(
                    text = when {
                        importState is ImportState.InProgress -> "Importing..."
                        audioFileCount == 0 -> "Select a folder with audio files"
                        else -> "Import $audioFileCount Audio File(s)"
                    }
                )
            }

            // Progress section
            when (val state = importState) {
                is ImportState.InProgress -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Progress: ${state.current}/${state.total} - ${state.currentFile}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        LinearProgressIndicator(
                            progress = { if (state.total > 0) state.current.toFloat() / state.total else 0f },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                is ImportState.Complete -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (state.failedCount == 0)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Import Complete",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Successfully imported: ${state.successCount}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            if (state.failedCount > 0) {
                                Text(
                                    text = "Failed: ${state.failedCount}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = "Check the Critical Log in Settings for details",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.resetState() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Import More")
                                }
                                Button(
                                    onClick = onImportComplete,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Done")
                                }
                            }
                        }
                    }
                }
                is ImportState.Error -> {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Import Error",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            OutlinedButton(
                                onClick = { viewModel.resetState() }
                            ) {
                                Text("Try Again")
                            }
                        }
                    }
                }
                ImportState.Idle -> {
                    // No progress to show
                }
            }
        }
    }
}

/**
 * A row for selecting a tag with checkbox.
 */
@Composable
private fun SelectableTagRow(
    tagItem: SelectableTagItem,
    isCollapsed: Boolean,
    isFiltering: Boolean,
    filterText: String,
    onToggleSelection: () -> Unit,
    onToggleCollapse: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleSelection() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Checkbox
        Checkbox(
            checked = tagItem.isSelected,
            onCheckedChange = { onToggleSelection() }
        )

        if (isFiltering) {
            // When filtering, show full path with highlighted match
            HighlightedText(
                text = tagItem.path,
                highlight = filterText,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        } else {
            // When not filtering, show hierarchical layout with indentation
            if (tagItem.depth > 0) {
                Spacer(modifier = Modifier.width((tagItem.depth * 20).dp))
            }

            // Expand/collapse icon for tags with children
            if (tagItem.hasChildren) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (isCollapsed)
                            Icons.Default.KeyboardArrowRight
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isCollapsed) "Expand" else "Collapse",
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(28.dp))
            }

            // Tag name
            Text(
                text = tagItem.tag.name,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * Text with highlighted substring.
 */
@Composable
private fun HighlightedText(
    text: String,
    highlight: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier
) {
    if (highlight.isEmpty()) {
        Text(text = text, style = style, modifier = modifier)
        return
    }

    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerHighlight = highlight.lowercase()

        while (currentIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerHighlight, currentIndex)
            if (matchIndex == -1) {
                append(text.substring(currentIndex))
                break
            }

            if (matchIndex > currentIndex) {
                append(text.substring(currentIndex, matchIndex))
            }

            withStyle(
                style = SpanStyle(
                    fontWeight = FontWeight.Bold,
                    background = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                append(text.substring(matchIndex, matchIndex + highlight.length))
            }

            currentIndex = matchIndex + highlight.length
        }
    }

    Text(text = annotatedString, style = style, modifier = modifier)
}
