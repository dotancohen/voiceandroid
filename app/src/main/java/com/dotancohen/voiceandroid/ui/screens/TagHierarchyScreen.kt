package com.dotancohen.voiceandroid.ui.screens

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.viewmodel.TagHierarchyItem
import com.dotancohen.voiceandroid.viewmodel.TagHierarchyViewModel

/**
 * Screen for managing the tag hierarchy.
 *
 * Features:
 * - Hierarchical display of tags with indentation
 * - Collapse/expand for parent tags
 * - Filter field for finding tags
 * - Add new tags (root-level or as children)
 * - Rename tags
 * - Move tags to different parents
 * - Delete tags (with confirmation)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagHierarchyScreen(
    onBack: () -> Unit,
    viewModel: TagHierarchyViewModel = viewModel()
) {
    val filteredTags by viewModel.filteredTags.collectAsState()
    val filterText by viewModel.filterText.collectAsState()
    val collapsedTagIds by viewModel.collapsedTagIds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val successMessage by viewModel.successMessage.collectAsState()
    val possibleParents by viewModel.possibleParents.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    // Dialog states
    var showAddDialog by remember { mutableStateOf(false) }
    var addDialogParentId by remember { mutableStateOf<String?>(null) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameTagItem by remember { mutableStateOf<TagHierarchyItem?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var moveTagItem by remember { mutableStateOf<TagHierarchyItem?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteTagItem by remember { mutableStateOf<TagHierarchyItem?>(null) }

    // Show success/error messages
    LaunchedEffect(successMessage) {
        successMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSuccessMessage()
        }
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Tags") },
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    addDialogParentId = null
                    showAddDialog = true
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Tag")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            // Filter field
            OutlinedTextField(
                value = filterText,
                onValueChange = { viewModel.updateFilter(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filter tags...") },
                singleLine = true,
                trailingIcon = {
                    if (filterText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateFilter("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear filter"
                            )
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status text
            when {
                isLoading -> {
                    Text(
                        text = "Loading tags...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                filteredTags.isEmpty() && filterText.isNotEmpty() -> {
                    Text(
                        text = "No tags match \"$filterText\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                filteredTags.isEmpty() -> {
                    Text(
                        text = "No tags. Tap + to create one.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tags list
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredTags) { tagItem ->
                    TagHierarchyRow(
                        tagItem = tagItem,
                        isCollapsed = collapsedTagIds.contains(tagItem.tag.id),
                        isFiltering = filterText.isNotEmpty(),
                        filterText = filterText,
                        onToggleCollapse = { viewModel.toggleCollapse(tagItem.tag.id) },
                        onAddChild = {
                            addDialogParentId = tagItem.tag.id
                            showAddDialog = true
                        },
                        onRename = {
                            renameTagItem = tagItem
                            showRenameDialog = true
                        },
                        onMove = {
                            moveTagItem = tagItem
                            viewModel.preparePossibleParents(tagItem.tag.id)
                            showMoveDialog = true
                        },
                        onDelete = {
                            deleteTagItem = tagItem
                            showDeleteDialog = true
                        }
                    )
                }
            }
        }
    }

    // Add Tag Dialog
    if (showAddDialog) {
        var newTagName by remember { mutableStateOf("") }
        val parentName = addDialogParentId?.let { parentId ->
            filteredTags.find { it.tag.id == parentId }?.tag?.name
        }

        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = {
                Text(
                    if (parentName != null) "Add Child Tag"
                    else "Add Tag"
                )
            },
            text = {
                Column {
                    if (parentName != null) {
                        Text(
                            text = "Parent: $parentName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    OutlinedTextField(
                        value = newTagName,
                        onValueChange = { newTagName = it },
                        label = { Text("Tag name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newTagName.isNotBlank()) {
                            viewModel.createTag(newTagName.trim(), addDialogParentId)
                            showAddDialog = false
                        }
                    },
                    enabled = newTagName.isNotBlank()
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Rename Dialog
    if (showRenameDialog && renameTagItem != null) {
        var newName by remember { mutableStateOf(renameTagItem!!.tag.name) }

        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Tag") },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (newName.isNotBlank() && newName != renameTagItem!!.tag.name) {
                            viewModel.renameTag(renameTagItem!!.tag.id, newName.trim())
                            showRenameDialog = false
                        }
                    },
                    enabled = newName.isNotBlank() && newName != renameTagItem!!.tag.name
                ) {
                    Text("Rename")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Move Dialog
    if (showMoveDialog && moveTagItem != null) {
        AlertDialog(
            onDismissRequest = { showMoveDialog = false },
            title = { Text("Move '${moveTagItem!!.tag.name}' to...") },
            text = {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    // Root option
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.reparentTag(moveTagItem!!.tag.id, null)
                                    showMoveDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "(No Parent - Root Level)",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                    // Other tags
                    items(possibleParents) { parent ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    viewModel.reparentTag(moveTagItem!!.tag.id, parent.tag.id)
                                    showMoveDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = parent.path,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showMoveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    if (showDeleteDialog && deleteTagItem != null) {
        val hasChildren = viewModel.hasChildren(deleteTagItem!!.tag.id)
        val children = if (hasChildren) viewModel.getChildren(deleteTagItem!!.tag.id) else emptyList()

        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Tag") },
            text = {
                Column {
                    if (hasChildren) {
                        Text(
                            text = "Cannot delete '${deleteTagItem!!.tag.name}' because it has child tags:",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        children.take(5).forEach { child ->
                            Text(
                                text = "  - ${child.name}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        if (children.size > 5) {
                            Text(
                                text = "  ... and ${children.size - 5} more",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Please move or delete the child tags first.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "Delete '${deleteTagItem!!.tag.name}'?",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (deleteTagItem!!.noteCount > 0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This tag is used by ${deleteTagItem!!.noteCount} note(s). " +
                                       "The tag will be removed from those notes.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                if (!hasChildren) {
                    TextButton(
                        onClick = {
                            viewModel.deleteTag(deleteTagItem!!.tag.id)
                            showDeleteDialog = false
                        }
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(if (hasChildren) "OK" else "Cancel")
                }
            }
        )
    }
}

/**
 * A single row in the tag hierarchy list.
 */
@Composable
private fun TagHierarchyRow(
    tagItem: TagHierarchyItem,
    isCollapsed: Boolean,
    isFiltering: Boolean,
    filterText: String,
    onToggleCollapse: () -> Unit,
    onAddChild: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFiltering) {
            // When filtering, show full path with highlighted match
            HighlightedText(
                text = tagItem.path,
                highlight = filterText,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        } else {
            // When not filtering, show hierarchical layout with indentation
            if (tagItem.depth > 0) {
                Spacer(modifier = Modifier.width((tagItem.depth * 24).dp))
            }

            // Expand/collapse icon for tags with children
            if (tagItem.hasChildren) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = if (isCollapsed)
                            Icons.Default.KeyboardArrowRight
                        else
                            Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isCollapsed) "Expand" else "Collapse",
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(32.dp))
            }

            // Tag name
            Text(
                text = tagItem.tag.name,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }

        // Note count
        Text(
            text = "(${tagItem.noteCount})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Menu button
        IconButton(onClick = { showMenu = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "More options"
            )
        }

        // Dropdown menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Add Child") },
                onClick = {
                    showMenu = false
                    onAddChild()
                },
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    showMenu = false
                    onRename()
                },
                leadingIcon = {
                    Icon(Icons.Default.Edit, contentDescription = null)
                }
            )
            DropdownMenuItem(
                text = { Text("Move To...") },
                onClick = {
                    showMenu = false
                    onMove()
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    showMenu = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(Icons.Default.Delete, contentDescription = null)
                }
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
