package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.filled.Add
import com.dotancohen.voiceandroid.ui.components.CreateTagDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.viewmodel.TagManagementViewModel
import com.dotancohen.voiceandroid.viewmodel.TagWithPath

/**
 * Screen for managing tags on a note.
 *
 * Features:
 * - Hierarchical display of tags with indentation
 * - Collapse/expand for parent tags
 * - Filter field that filters on each keypress
 * - Full path shown when filtering (e.g., "Geography > Europe > France > Paris")
 * - Checkboxes to add/remove tags from the note
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagManagementScreen(
    noteId: String,
    onBack: () -> Unit,
    viewModel: TagManagementViewModel = viewModel()
) {
    val filteredTags by viewModel.filteredTags.collectAsState()
    val noteTagIds by viewModel.noteTagIds.collectAsState()
    val filterText by viewModel.filterText.collectAsState()
    val collapsedTagIds by viewModel.collapsedTagIds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    var showCreateTag by remember { mutableStateOf(false) }

    // Load tags when noteId changes
    LaunchedEffect(noteId) {
        viewModel.loadTags(noteId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Tags") },
            actions = {
                // The same dialogue as the tag hierarchy screen: a Tag made
                // here is put on this Note at once.
                IconButton(onClick = { showCreateTag = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create Tag")
                }
            },
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
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            // The filter is not focused when the screen opens. This screen is
            // for tapping tags, and a keyboard over it hides most of them —
            // the user had to dismiss it before they could tag anything.
            OutlinedTextField(
                value = filterText,
                onValueChange = { viewModel.updateFilter(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Filter Tags…") },
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

            Spacer(modifier = Modifier.height(4.dp))

            if (showCreateTag) {
                CreateTagDialog(
                    note = "It will be put on this Note.",
                    onCreate = { name -> viewModel.createTagForNote(name) },
                    onDismiss = { showCreateTag = false }
                )
            }

            // Status text
            when {
                isLoading -> {
                    Text(
                        text = "Loading Tags…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                error != null -> {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                filteredTags.isEmpty() && filterText.isNotEmpty() -> {
                    Text(
                        text = "No Tags match \"$filterText\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                filteredTags.isEmpty() -> {
                    Text(
                        text = "No Tags yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Tags list
            LazyColumn(
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredTags) { tagWithPath ->
                    TagItem(
                        tagWithPath = tagWithPath,
                        isSelected = noteTagIds.contains(tagWithPath.tag.id),
                        isCollapsed = collapsedTagIds.contains(tagWithPath.tag.id),
                        isFiltering = filterText.isNotEmpty(),
                        filterText = filterText,
                        onToggle = { viewModel.toggleTag(tagWithPath.tag.id) },
                        onToggleCollapse = { viewModel.toggleCollapse(tagWithPath.tag.id) }
                    )
                }
            }
        }
    }
}

/**
 * A single tag item with checkbox and optional expand/collapse.
 */
@Composable
private fun TagItem(
    tagWithPath: TagWithPath,
    isSelected: Boolean,
    isCollapsed: Boolean,
    isFiltering: Boolean,
    filterText: String,
    onToggle: () -> Unit,
    onToggleCollapse: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFiltering) {
            // When filtering, show checkbox then full path with highlighted match
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            HighlightedText(
                text = tagWithPath.path,
                highlight = filterText,
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            // When not filtering, show hierarchical layout with indentation
            // Indentation based on depth
            if (tagWithPath.depth > 0) {
                Spacer(modifier = Modifier.width((tagWithPath.depth * 16).dp))
            }

            // Expand/collapse icon for tags with children
            if (tagWithPath.hasChildren) {
                IconButton(
                    onClick = onToggleCollapse,
                    modifier = Modifier.size(24.dp)
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
                // Placeholder for alignment
                Spacer(modifier = Modifier.width(24.dp))
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() },
                modifier = Modifier.size(28.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = tagWithPath.tag.name,
                style = MaterialTheme.typography.bodyMedium
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
    style: androidx.compose.ui.text.TextStyle
) {
    if (highlight.isEmpty()) {
        Text(text = text, style = style)
        return
    }

    val annotatedString = buildAnnotatedString {
        var currentIndex = 0
        val lowerText = text.lowercase()
        val lowerHighlight = highlight.lowercase()

        while (currentIndex < text.length) {
            val matchIndex = lowerText.indexOf(lowerHighlight, currentIndex)
            if (matchIndex == -1) {
                // No more matches, append the rest
                append(text.substring(currentIndex))
                break
            }

            // Append text before the match
            if (matchIndex > currentIndex) {
                append(text.substring(currentIndex, matchIndex))
            }

            // Append the highlighted match
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

    Text(text = annotatedString, style = style)
}
