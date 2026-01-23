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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.ui.components.CompactAudioPlayer
import com.dotancohen.voiceandroid.ui.components.TagTreeItem
import com.dotancohen.voiceandroid.viewmodel.FilterViewModel
import com.dotancohen.voiceandroid.viewmodel.NoteWithAudioFiles
import com.dotancohen.voiceandroid.viewmodel.NotesViewModel
import com.dotancohen.voiceandroid.viewmodel.SharedFilterViewModel
import kotlinx.coroutines.runBlocking

// Gold color for filled star
private val StarGold = Color(0xFFFFD700)

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
    val tagTree by filterViewModel.tagTree.collectAsState()
    // Use remember with keys to recalculate when expandedTagIds or tagTree change
    val visibleTags = remember(expandedTagIds, tagTree) {
        filterViewModel.getFlattenedVisibleTags()
    }

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

    // Check if is:marked is in the search text
    val hasMarkedFilter = searchText.lowercase().contains("is:marked")

    // Function to toggle is:marked filter
    fun toggleMarkedFilter() {
        if (hasMarkedFilter) {
            // Remove is:marked from search text
            searchText = searchText
                .replace(Regex("\\bis:marked\\b", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+"), " ")
                .trim()
        } else {
            // Add is:marked to search text
            searchText = "is:marked $searchText".trim()
        }
        executeSearch()
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
            // Star filter button
            IconButton(
                onClick = { toggleMarkedFilter() },
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = if (hasMarkedFilter) Icons.Filled.Star else Icons.Outlined.StarOutline,
                    contentDescription = if (hasMarkedFilter) "Remove starred filter" else "Show only starred",
                    tint = if (hasMarkedFilter) StarGold else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

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
                leadingIcon = {
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
                    // Track which audio file is currently expanded for playback
                    var expandedAudioFileId by remember { mutableStateOf<String?>(null) }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(notes) { noteWithAudio ->
                            NoteCard(
                                noteWithAudio = noteWithAudio,
                                onClick = { onNoteClick(noteWithAudio.note.id) },
                                onStarClick = { viewModel.toggleNoteMarked(noteWithAudio.note.id) },
                                expandedAudioFileId = expandedAudioFileId,
                                onAudioFileClick = { audioFileId ->
                                    expandedAudioFileId = if (expandedAudioFileId == audioFileId) {
                                        null // Collapse if already expanded
                                    } else {
                                        audioFileId // Expand this one
                                    }
                                },
                                getAudioFilePath = { audioFileId ->
                                    runBlocking { viewModel.getAudioFilePath(audioFileId) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Format duration in seconds to a human-readable string (h:mm:ss or mm:ss).
 */
private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, secs)
    } else {
        "%d:%02d".format(minutes, secs)
    }
}

@Composable
fun NoteCard(
    noteWithAudio: NoteWithAudioFiles,
    onClick: () -> Unit = {},
    onStarClick: () -> Unit = {},
    expandedAudioFileId: String? = null,
    onAudioFileClick: (String) -> Unit = {},
    getAudioFilePath: (String) -> String? = { null }
) {
    val note = noteWithAudio.note
    val audioFiles = noteWithAudio.audioFiles
    val isMarked = noteWithAudio.isMarked
    val durationSeconds = noteWithAudio.durationSeconds
    val tags = noteWithAudio.tags
    // Audio files are now expanded by default
    var isExpanded by remember { mutableStateOf(true) }
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
            // Star, date, duration, and tags row
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Star icon (clickable)
                IconButton(
                    onClick = onStarClick,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = if (isMarked) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = if (isMarked) "Unstar note" else "Star note",
                        modifier = Modifier.size(16.dp),
                        tint = if (isMarked) StarGold else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Date (bold)
                Text(
                    text = note.createdAt,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Duration (if available)
                if (durationSeconds != null && durationSeconds > 0) {
                    Text(
                        text = " | ${formatDuration(durationSeconds)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Tags (if available)
                if (tags.isNotEmpty()) {
                    Text(
                        text = " | ${tags.joinToString(", ")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

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
                            AudioFileCard(
                                audioFile = audioFile,
                                isPlayerExpanded = expandedAudioFileId == audioFile.id,
                                onTogglePlayer = { onAudioFileClick(audioFile.id) },
                                getFilePath = { getAudioFilePath(audioFile.id) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AudioFileCard(
    audioFile: AudioFile,
    isPlayerExpanded: Boolean = false,
    onTogglePlayer: () -> Unit = {},
    getFilePath: () -> String? = { null }
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small
    ) {
        Column(
            modifier = Modifier.padding(5.dp)
        ) {
            // Header row - clickable to expand/collapse player
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onTogglePlayer() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isPlayerExpanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = audioFile.filename,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                // Show expand/collapse indicator
                Icon(
                    imageVector = if (isPlayerExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = if (isPlayerExpanded) "Collapse player" else "Expand player",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Summary (if available)
            audioFile.summary?.let { summary ->
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Compact audio player (expanded)
            AnimatedVisibility(
                visible = isPlayerExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                val filePath = remember(audioFile.id) { getFilePath() }
                CompactAudioPlayer(
                    filePath = filePath,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
