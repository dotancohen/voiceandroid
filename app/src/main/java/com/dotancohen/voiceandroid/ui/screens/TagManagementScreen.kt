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

    // Load tags when noteId changes
    LaunchedEffect(noteId) {
        viewModel.loadTags(noteId)
    }

    Column(modifier = Modifier.fillMaxSize()) {
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

        Column(
            modifier = Modifier
                .fillMaxSize()
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
                error != null -> {
                    Text(
                        text = error!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
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
                        text = "No tags available",
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
            .clickable { onToggle() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isFiltering) {
            // When filtering, show checkbox then full path with highlighted match
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            HighlightedText(
                text = tagWithPath.path,
                highlight = filterText,
                style = MaterialTheme.typography.bodyLarge
            )
        } else {
            // When not filtering, show hierarchical layout with indentation
            // Indentation based on depth
            if (tagWithPath.depth > 0) {
                Spacer(modifier = Modifier.width((tagWithPath.depth * 24).dp))
            }

            // Expand/collapse icon for tags with children
            if (tagWithPath.hasChildren) {
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
                // Placeholder for alignment
                Spacer(modifier = Modifier.width(32.dp))
            }

            Checkbox(
                checked = isSelected,
                onCheckedChange = { onToggle() }
            )

            Spacer(modifier = Modifier.width(4.dp))

            Text(
                text = tagWithPath.tag.name,
                style = MaterialTheme.typography.bodyLarge
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
