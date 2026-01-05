package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.ui.components.TagTreeItem
import com.dotancohen.voiceandroid.viewmodel.FilterViewModel
import com.dotancohen.voiceandroid.viewmodel.SharedFilterViewModel

/**
 * Filter screen for searching notes and filtering by tags.
 *
 * Features:
 * - Search bar with clear and execute buttons
 * - Hierarchical tag tree
 * - Click tag to add to search
 * - Long-click tag to add and execute search
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilterScreen(
    sharedFilterViewModel: SharedFilterViewModel,
    onSearchExecuted: () -> Unit,
    filterViewModel: FilterViewModel = viewModel()
) {
    val searchQuery by filterViewModel.searchQuery.collectAsState()
    val tagTree by filterViewModel.tagTree.collectAsState()
    val expandedTagIds by filterViewModel.expandedTagIds.collectAsState()
    val isLoading by filterViewModel.isLoading.collectAsState()
    val error by filterViewModel.error.collectAsState()

    // Get flattened visible tags based on expanded state
    val visibleTags = filterViewModel.getFlattenedVisibleTags()

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Filter") }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Search bar row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { filterViewModel.updateSearchQuery(it) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search notes...") },
                    singleLine = true,
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { filterViewModel.clearSearch() }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    }
                )

                // Execute search button
                IconButton(
                    onClick = {
                        if (searchQuery.isNotBlank()) {
                            sharedFilterViewModel.setSearchFilter(searchQuery)
                            onSearchExecuted()
                        }
                    },
                    enabled = searchQuery.isNotBlank()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Execute search",
                        tint = if (searchQuery.isNotBlank()) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tags section
            Text(
                text = "Tags",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            when {
                isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                error != null -> {
                    Text(
                        text = "Error loading tags: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                visibleTags.isEmpty() -> {
                    Text(
                        text = "No tags available",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(
                            items = visibleTags,
                            key = { it.tag.id }
                        ) { node ->
                            TagTreeItem(
                                tag = node.tag,
                                depth = node.depth,
                                hasChildren = filterViewModel.hasChildren(node.tag.id),
                                isExpanded = expandedTagIds.contains(node.tag.id),
                                onClick = {
                                    // Add tag to search query
                                    filterViewModel.addTagToSearch(node.tag)
                                },
                                onLongClick = {
                                    // Add tag to search and execute
                                    filterViewModel.addTagToSearch(node.tag)
                                    val query = filterViewModel.searchQuery.value
                                    if (query.isNotBlank()) {
                                        sharedFilterViewModel.setSearchFilter(query)
                                        onSearchExecuted()
                                    }
                                },
                                onToggleExpand = {
                                    filterViewModel.toggleExpanded(node.tag.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
