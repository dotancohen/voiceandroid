package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.ui.components.TagTreeItem
import com.dotancohen.voiceandroid.viewmodel.FilterViewModel
import com.dotancohen.voiceandroid.viewmodel.NoteWithAudioFiles
import com.dotancohen.voiceandroid.viewmodel.NotesViewModel
import com.dotancohen.voiceandroid.viewmodel.SharedFilterViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    sharedFilterViewModel: SharedFilterViewModel,
    onNoteClick: (String) -> Unit = {},
    viewModel: NotesViewModel = viewModel(),
    filterViewModel: FilterViewModel = viewModel()
) {
    val notes by viewModel.notes.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val ambiguousTags by viewModel.ambiguousTags.collectAsState()
    val notFoundTags by viewModel.notFoundTags.collectAsState()

    // Get active search query from shared filter state
    val activeSearchQuery by sharedFilterViewModel.activeSearchQuery.collectAsState()

    // Local search text (for typing before executing)
    var searchText by remember { mutableStateOf(activeSearchQuery ?: "") }

    // Track if search bar is focused
    var isSearchFocused by remember { mutableStateOf(false) }

    // Tag tree state
    val expandedTagIds by filterViewModel.expandedTagIds.collectAsState()
    val visibleTags = filterViewModel.getFlattenedVisibleTags()

    val focusManager = LocalFocusManager.current

    // Function to execute search
    fun executeSearch() {
        if (searchText.isBlank()) {
            sharedFilterViewModel.clearSearchFilter()
        } else {
            sharedFilterViewModel.setSearchFilter(searchText)
        }
        focusManager.clearFocus()
    }

    // Sync local search text when active query changes externally
    LaunchedEffect(activeSearchQuery) {
        searchText = activeSearchQuery ?: ""
        viewModel.loadNotes(activeSearchQuery)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        isSearchFocused = focusState.isFocused
                    },
                placeholder = { Text("Search notes...") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { executeSearch() }),
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = {
                            searchText = ""
                            sharedFilterViewModel.clearSearchFilter()
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear"
                            )
                        }
                    }
                }
            )

            IconButton(onClick = { executeSearch() }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }

        // Warning messages for ambiguous or not found tags
        if (ambiguousTags.isNotEmpty() || notFoundTags.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                if (ambiguousTags.isNotEmpty()) {
                    Text(
                        text = "Ambiguous tags: ${ambiguousTags.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                if (notFoundTags.isNotEmpty()) {
                    Text(
                        text = "Tags not found: ${notFoundTags.joinToString(", ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Show tag tree when search bar is focused, otherwise show notes
        if (isSearchFocused) {
            // Tag tree
            if (visibleTags.isEmpty()) {
                Text(
                    text = "No tags available",
                    modifier = Modifier.padding(8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 4.dp)
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
                                val tagTerm = "tag:${node.tag.name}"
                                val currentQuery = searchText.trim()
                                if (!currentQuery.lowercase().contains(tagTerm.lowercase())) {
                                    searchText = if (currentQuery.isEmpty()) {
                                        tagTerm
                                    } else {
                                        "$currentQuery $tagTerm"
                                    }
                                }
                            },
                            onLongClick = {
                                // Add tag to search and execute
                                val tagTerm = "tag:${node.tag.name}"
                                val currentQuery = searchText.trim()
                                if (!currentQuery.lowercase().contains(tagTerm.lowercase())) {
                                    searchText = if (currentQuery.isEmpty()) {
                                        tagTerm
                                    } else {
                                        "$currentQuery $tagTerm"
                                    }
                                }
                                executeSearch()
                            },
                            onToggleExpand = {
                                filterViewModel.toggleExpanded(node.tag.id)
                            }
                        )
                    }
                }
            }
        } else {
            // Notes list
            when {
                isLoading -> {
                    Text(
                        text = "Loading...",
                        modifier = Modifier.padding(8.dp)
                    )
                }
                error != null -> {
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                notes.isEmpty() -> {
                    val message = if (activeSearchQuery != null) {
                        "No notes match the search."
                    } else {
                        "No notes yet. Sync with the server to get notes."
                    }
                    Text(
                        text = message,
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(notes) { noteWithAudio ->
                            NoteCard(
                                noteWithAudio = noteWithAudio,
                                onClick = { onNoteClick(noteWithAudio.note.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NoteCard(
    noteWithAudio: NoteWithAudioFiles,
    onClick: () -> Unit = {}
) {
    val note = noteWithAudio.note
    val audioFiles = noteWithAudio.audioFiles
    var isExpanded by remember { mutableStateOf(false) }
    val hasAttachments = audioFiles.isNotEmpty()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(5.dp)
        ) {
            // Date on top
            Text(
                text = note.createdAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Content preview (newlines replaced with spaces)
            Text(
                text = note.content.replace('\n', ' '),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Attachments section
            if (hasAttachments) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "${audioFiles.size} audio",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(
                        imageVector = if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        audioFiles.forEach { audioFile ->
                            AudioFileCard(audioFile = audioFile)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioFileCard(audioFile: AudioFile) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = audioFile.filename,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            audioFile.summary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
