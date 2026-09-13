package com.dotancohen.voiceandroid.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Transcribe
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.filled.Add
import androidx.compose.ui.platform.LocalContext
import com.dotancohen.voiceandroid.audio.RecorderPreferences
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.input.pointer.pointerInput
import com.dotancohen.voiceandroid.BuildConfig
import com.dotancohen.voiceandroid.data.TagColours
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.ui.components.CompactAudioPlayer
import com.dotancohen.voiceandroid.ui.components.PendingTranscriptionIcon
import com.dotancohen.voiceandroid.ui.components.MultiTagDialog
import com.dotancohen.voiceandroid.ui.components.spotlight
import com.dotancohen.voiceandroid.ui.theme.RecordRed
import com.dotancohen.voiceandroid.ui.theme.StarGold
import com.dotancohen.voiceandroid.ui.components.spotlightColor
import com.dotancohen.voiceandroid.ui.components.stampText
import com.dotancohen.voiceandroid.ui.components.TagChip
import com.dotancohen.voiceandroid.ui.components.TranscribeDialog
import com.dotancohen.voiceandroid.ui.components.TagTreeItem
import com.dotancohen.voiceandroid.viewmodel.queryHasMarkedFilter
import com.dotancohen.voiceandroid.viewmodel.queryWithMarkedFilter
import com.dotancohen.voiceandroid.viewmodel.FilterViewModel
import com.dotancohen.voiceandroid.viewmodel.NoteWithAudioFiles
import com.dotancohen.voiceandroid.viewmodel.openingLines
import com.dotancohen.voiceandroid.viewmodel.NotesViewModel
import com.dotancohen.voiceandroid.ui.scaledIcon
import com.dotancohen.voiceandroid.ui.scaledIconText
import com.dotancohen.voiceandroid.util.Durations
import com.dotancohen.voiceandroid.util.UiPreferences
import com.dotancohen.voiceandroid.util.format
import com.dotancohen.voiceandroid.viewmodel.SharedFilterViewModel
import kotlinx.coroutines.flow.first

// Gold color for filled star





@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun NotesScreen(
    sharedFilterViewModel: SharedFilterViewModel,
    onNoteClick: (String) -> Unit = {},
    /** Open the Tags screen of one Note, from its row's subsection. */
    onTagNote: (String) -> Unit = {},
    onNewNote: () -> Unit = {},
    onNewRecording: () -> Unit = {},
    /** The gear in the toolbar: the only way into Settings. */
    onNavigateToSettings: () -> Unit = {},
    /** The first run's "Pair with another device" (Stage 9): the sync screen with the code reader open. */
    onPairWithAnotherDevice: () -> Unit = {},
    /** Whether the user asked for a button to switch the interface size. */
    uiSizeOffersSwitch: Boolean = false,
    /** Whether the interface is large right now. */
    uiIsLarge: Boolean = false,
    onToggleUiSize: () -> Unit = {},
    onNavigateToTranscriptionSettings: () -> Unit = {},
    viewModel: NotesViewModel = viewModel(),
    filterViewModel: FilterViewModel = viewModel()
) {
    val context = LocalContext.current
    var showNewMenu by remember { mutableStateOf(false) }
    val notes by viewModel.notes.collectAsState()

    // Hand the order on screen to the note screen, so its Next and Previous
    // move through the list the user is actually looking at.
    LaunchedEffect(notes) {
        sharedFilterViewModel.setVisibleNotes(notes.map { it.note.id })
    }
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val ambiguousTags by viewModel.ambiguousTags.collectAsState()
    val notFoundTags by viewModel.notFoundTags.collectAsState()
    val selectedNoteIds by viewModel.selectedNoteIds.collectAsState()
    val tagColours by viewModel.tagColours.collectAsState()
    val tagOptions by viewModel.tagOptions.collectAsState()
    val message by viewModel.message.collectAsState()
    val selectionMode = selectedNoteIds.isNotEmpty()
    var showTagDialog by remember { mutableStateOf(false) }
    var noteToDeleteId by remember { mutableStateOf<String?>(null) }
    // One Note's section is open at a time, and it is what the chevron opens
    var expandedNoteId by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var showMergeConfirmation by remember { mutableStateOf(false) }
    var showTranscribeDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    // Kept here, so the list is where the user left it after opening a note
    val listState = rememberLazyListState()
    // Read on every composition, so returning from Settings shows the new
    // number of lines without restarting the app.
    val contentLines = UiPreferences(context).notesListLines
    val colouredTags = UiPreferences(context).colouredTags
    // The row of the note just left is pointed out by two travelling dots
    var spotlightNoteId by remember { mutableStateOf<String?>(null) }
    val spotlight = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        val noteId = viewModel.consumeLastOpenedNote() ?: return@LaunchedEffect
        val duration = UiPreferences(context).spotlightDurationMs
        if (duration <= 0) return@LaunchedEffect
        // Wait for the reload that runs on the way back — the list is loaded
        // again whenever this screen returns to the front — and then for the
        // rows to be laid out. Animating before that spent the whole duration
        // on a list that was not on screen yet, so the dots could barely be
        // seen. The timeout is in case no reload happens at all.
        val startedAt = viewModel.loadCount.value
        withTimeoutOrNull(2000) { viewModel.loadCount.first { it > startedAt } }
        viewModel.isLoading.first { !it }
        snapshotFlow { listState.layoutInfo.totalItemsCount }.first { it > 0 }
        // One frame more, so the row is measured where it will be drawn
        withFrameNanos { }
        // Normally the row is still on screen; if it is not, bring it back
        if (listState.layoutInfo.visibleItemsInfo.none { it.key == noteId }) {
            val index = viewModel.notes.value.indexOfFirst { it.note.id == noteId }
            if (index >= 0) listState.scrollToItem(index)
        }
        spotlightNoteId = noteId
        spotlight.snapTo(0f)
        spotlight.animateTo(1f, tween(durationMillis = duration, easing = LinearEasing))
        spotlightNoteId = null
    }

    // Back leaves the selection instead of the screen
    BackHandler(enabled = selectionMode) { viewModel.clearSelection() }


    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Tags for every selected note at once
    if (showTagDialog) {
        MultiTagDialog(
            noteCount = selectedNoteIds.size,
            tags = tagOptions,
            onToggle = { tagId -> viewModel.toggleTagOnSelected(tagId) },
            // Closing the dialog ends the selection: the tags have been
            // applied, and leaving the notes ticked invited the next action
            // to be aimed at notes the user had finished with.
            onDismiss = {
                showTagDialog = false
                viewModel.clearSelection()
            },
            onCreateTag = { name -> viewModel.createTagForSelected(name) }
        )
    }

    // Deleting one Note from its row, which asks first like every delete
    noteToDeleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { noteToDeleteId = null },
            title = { Text("Delete this Note?") },
            text = { Text("It goes to the trash bin, with its Recordings, and can be recovered there.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteNote(id)
                    noteToDeleteId = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { noteToDeleteId = null }) { Text("Cancel") } }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Delete ${selectedNoteIds.size} Notes") },
            text = {
                Text(
                    "The notes go to the trash on every device at the next sync. " +
                        "You can bring them back from Settings → Trash."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirmation = false
                    viewModel.deleteSelected()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirmation = false }) { Text("Cancel") } }
        )
    }

    if (showMergeConfirmation) {
        val oldest = viewModel.selectedNotes().minByOrNull { it.note.createdAt.at }
        val firstLine = oldest?.note?.content?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
        AlertDialog(
            onDismissRequest = { showMergeConfirmation = false },
            title = { Text("Merge ${selectedNoteIds.size} Notes") },
            text = {
                Text(
                    "The oldest of them" + (firstLine?.let { ", \"$it\"," } ?: "") +
                        " keeps its text and gains the others' below it. Their recordings and tags move across, " +
                        "and the emptied notes are deleted."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMergeConfirmation = false
                    viewModel.mergeSelected()
                }) { Text("Merge") }
            },
            dismissButton = { TextButton(onClick = { showMergeConfirmation = false }) { Text("Cancel") } }
        )
    }

    if (showTranscribeDialog) {
        val recordings = viewModel.selectedAudioFiles().size
        TranscribeDialog(
            subtitle = "$recordings recording(s) in ${selectedNoteIds.size} note(s), one at a time",
            onDismiss = { showTranscribeDialog = false },
            onSettings = { showTranscribeDialog = false; onNavigateToTranscriptionSettings() },
            onTranscribe = { modelId, language ->
                showTranscribeDialog = false
                viewModel.transcribeSelected(modelId, language)
            },
            existingTranscriptions = viewModel.selectedAlreadyTranscribed()
        )
    }

    // Get active search query from shared filter state
    val activeSearchQuery by sharedFilterViewModel.activeSearchQuery.collectAsState()

    // Local search text (for typing before executing)
    var searchText by remember { mutableStateOf(activeSearchQuery ?: "") }
    /**
     * Whether the search field is out. It is not there when the list opens:
     * a list is for reading, and a keyboard over half of it is in the way.
     * Tapping the search icon brings it out, the X puts it away, and a
     * search that is still in force keeps it out on the way back.
     */
    var searchOpen by remember { mutableStateOf(!activeSearchQuery.isNullOrBlank()) }
    val searchFocus = remember { FocusRequester() }
    /**
     * Whether the user opened the field just now, as opposed to finding it
     * open because a filter is still in force.
     *
     * Only their own tap takes the keyboard. Coming back from a Note into a
     * filtered list used to put the keyboard up over the results, which read
     * as being dropped on the search screen rather than returned to the list
     * that was being read.
     */
    var searchOpenedByUser by remember { mutableStateOf(false) }
    LaunchedEffect(searchOpen, searchOpenedByUser) {
        if (searchOpen && searchOpenedByUser) runCatching { searchFocus.requestFocus() }
    }

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
    val haptics = LocalHapticFeedback.current

    // Function to execute search
    fun executeSearch() {
        if (searchText.isBlank()) {
            sharedFilterViewModel.clearSearchFilter()
        } else {
            sharedFilterViewModel.setSearchFilter(searchText)
        }
        focusManager.clearFocus()
    }

    // Back closes the search and puts the list back, rather than leaving the
    // application: the search field is a state of this screen, and a user who
    // opened it expects Back to undo that.
    BackHandler(enabled = !selectionMode && (searchOpen || !activeSearchQuery.isNullOrBlank())) {
        searchText = ""
        searchOpen = false
        searchOpenedByUser = false
        sharedFilterViewModel.clearSearchFilter()
        focusManager.clearFocus()
    }

    // Whether the list is showing only the starred notes
    val hasMarkedFilter = queryHasMarkedFilter(searchText)

    fun toggleMarkedFilter() {
        searchText = queryWithMarkedFilter(searchText, !hasMarkedFilter)
        executeSearch()
    }

    // Sync local search text when active query changes externally
    LaunchedEffect(activeSearchQuery) {
        searchText = activeSearchQuery ?: ""
        viewModel.loadNotes(activeSearchQuery)
    }

    /**
     * Keep an opened Note's section on the screen.
     *
     * A row grows downward, so a row near the bottom would open its section
     * below the edge, and a row whose top is already above the edge would be
     * pushed further out of sight. How much the list moves depends on where
     * the row is: at the top it does not move at all, at the bottom it moves
     * by the whole of what would hang off the edge, and in between it moves
     * in proportion — so the movement is never more than the situation asks
     * for. A row already cut off at the top is always brought back first.
     */
    LaunchedEffect(expandedNoteId) {
        val noteId = expandedNoteId ?: return@LaunchedEffect
        // Two frames: one for the section to be composed, one for it to be
        // measured at its full height.
        withFrameNanos { }
        withFrameNanos { }
        val info = listState.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.key == noteId } ?: return@LaunchedEffect
        val top = info.viewportStartOffset
        val bottom = info.viewportEndOffset
        val height = (bottom - top).coerceAtLeast(1)

        val hiddenAbove = (top - item.offset).coerceAtLeast(0)
        if (hiddenAbove > 0) {
            // The row's own top is off the screen: bring it back, whatever
            // else happens.
            listState.animateScrollBy(-hiddenAbove.toFloat())
            return@LaunchedEffect
        }

        val hangingBelow = (item.offset + item.size - bottom).coerceAtLeast(0)
        if (hangingBelow <= 0) return@LaunchedEffect

        // 0 at the top of the screen, 1 at the bottom: how much of the
        // overhang is worth scrolling for.
        val nearness = ((item.offset - top).toFloat() / height).coerceIn(0f, 1f)
        // Never more than the room above the row, or its top would go off.
        val room = (item.offset - top).toFloat().coerceAtLeast(0f)
        val move = (hangingBelow * nearness).coerceAtMost(room)
        if (move > 1f) listState.animateScrollBy(move)
    }

    // Coming back to this screen runs the search again, with whatever filter
    // is in force. Returning from a note that was deleted, tagged or edited
    // used to leave the list as it was, and a filtered list needed the search
    // to be pressed again before it agreed with the database.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refresh()
                // The tags behind the search icon are loaded once when this
                // view model is made. A Tag created on the tag screen — or on
                // another device, and synced — would otherwise never appear
                // in the search until the application was restarted.
                filterViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Searching takes the whole bar: the other buttons fold away so
            // that the field is one line at full width, and come back when
            // the search is closed with the X.
            AnimatedVisibility(
                visible = !searchOpen,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
            // New: tap does the default (Settings → Recorder), long-press shows both
            Box {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .combinedClickable(
                            onClick = {
                                if (RecorderPreferences(context).defaultNewAction == RecorderPreferences.ACTION_RECORDING) onNewRecording() else onNewNote()
                            },
                            onLongClick = { showNewMenu = true }
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // A red record button when that is what tapping does
                    val newIsRecording = RecorderPreferences(context).defaultNewAction == RecorderPreferences.ACTION_RECORDING
                    Icon(
                        imageVector = if (newIsRecording) Icons.Filled.FiberManualRecord else Icons.Default.Add,
                        contentDescription = if (newIsRecording) "New voice recording" else "New note",
                        tint = if (newIsRecording) RecordRed else MaterialTheme.colorScheme.primary
                    )
                }
                DropdownMenu(expanded = showNewMenu, onDismissRequest = { showNewMenu = false }) {
                    DropdownMenuItem(text = { Text("New Note") }, onClick = { showNewMenu = false; onNewNote() })
                    DropdownMenuItem(text = { Text("New Voice Recording") }, onClick = { showNewMenu = false; onNewRecording() })
                }
            }
            }

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

            AnimatedVisibility(
                visible = searchOpen,
                modifier = Modifier.weight(1f),
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(searchFocus)
                        .onFocusChanged { focusState ->
                            isSearchFocused = focusState.isFocused
                        },
                    placeholder = { Text("Search Notes…") },
                    // One line, always: a query that grows a second line
                    // pushes the list down and reads badly.
                    singleLine = true,
                    maxLines = 1,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { executeSearch() }),
                    trailingIcon = {
                        // The X leaves the search: it clears the query, drops
                        // the filter and brings the other buttons back.
                        IconButton(onClick = {
                            searchText = ""
                            sharedFilterViewModel.clearSearchFilter()
                            isSearchFocused = false
                            searchOpen = false
                            searchOpenedByUser = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Close the search"
                            )
                        }
                    }
                )
            }

            IconButton(onClick = {
                if (searchOpen) {
                    executeSearch()
                } else {
                    searchOpen = true
                    searchOpenedByUser = true
                }
            }) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = if (searchOpen) "Search" else "Search notes",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            AnimatedVisibility(
                visible = !searchOpen,
                enter = expandHorizontally() + fadeIn(),
                exit = shrinkHorizontally() + fadeOut()
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
            // The size switch, when the user asked for one. It changes every
            // screen, not only this one.
            if (uiSizeOffersSwitch) {
                IconButton(onClick = onToggleUiSize, modifier = Modifier.size(40.dp)) {
                    Icon(
                        imageVector = if (uiIsLarge) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                        contentDescription = if (uiIsLarge) "Standard size" else "Large size",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Settings lives here, at the end of the notes toolbar, and
            // nowhere else in the application.
            IconButton(onClick = onNavigateToSettings, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            }
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
        Box(modifier = Modifier.weight(1f)) {
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
                notes.isEmpty() && activeSearchQuery != null -> {
                    Text(
                        text = "No notes match the search.",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                notes.isEmpty() -> {
                    // The first run (Stage 9): two choices, and never a settings screen
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("No notes yet.", style = MaterialTheme.typography.titleMedium)
                        Text("Is Voice already running on another device? Pair with it and its notes come here. Otherwise start on your own.", style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = onPairWithAnotherDevice, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Pair with another device" }) { Text("Pair with another device") }
                        OutlinedButton(onClick = onNewNote, modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Start on my own" }) { Text("Start on my own") }
                    }
                }
                else -> {
                    // Track which audio file is currently expanded for playback

                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .dragToSelect(
                                // Not only while a selection exists: holding a
                                // note and dragging is how a run of notes is
                                // picked in one movement, from a standing start.
                                enabled = BuildConfig.DEV_FEATURES,
                                listState = listState,
                                onHold = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                },
                                onSelect = { noteId -> viewModel.selectNote(noteId) }
                            ),
                        contentPadding = PaddingValues(horizontal = 1.dp, vertical = 1.dp),
                        verticalArrangement = Arrangement.spacedBy(1.dp)
                    ) {
                        items(notes, key = { it.note.id }) { noteWithAudio ->
                            NoteCard(
                                noteWithAudio = noteWithAudio,
                                selected = noteWithAudio.note.id in selectedNoteIds,
                                selectionMode = selectionMode,
                                contentLines = contentLines,
                                spotlight = if (noteWithAudio.note.id == spotlightNoteId) spotlight.value else null,
                                onClick = {
                                    if (selectionMode) {
                                        viewModel.toggleSelection(noteWithAudio.note.id)
                                    } else {
                                        viewModel.noteOpened(noteWithAudio.note.id)
                                        onNoteClick(noteWithAudio.note.id)
                                    }
                                },
                                // In a build with drag-to-select the list owns
                                // the hold, so the row does not handle it too.
                                onLongClick = if (BuildConfig.DEV_FEATURES) {
                                    null
                                } else {
                                    { viewModel.toggleSelection(noteWithAudio.note.id) }
                                },
                                onStarClick = { viewModel.toggleNoteMarked(noteWithAudio.note.id) },
                                getAudioFilePath = { audioFileId ->
                                    viewModel.getAudioFilePath(audioFileId)
                                },
                                expanded = expandedNoteId == noteWithAudio.note.id,
                                onToggleExpanded = {
                                    expandedNoteId = if (expandedNoteId == noteWithAudio.note.id) {
                                        null
                                    } else {
                                        noteWithAudio.note.id
                                    }
                                },
                                onTagNote = { onTagNote(noteWithAudio.note.id) },
                                tagColours = if (colouredTags) tagColours else null,
                                onDeleteNote = { noteToDeleteId = noteWithAudio.note.id }
                            )
                        }
                    }
                }
            }
        }
        }

        // What to do with the selected notes
        if (selectionMode) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                tonalElevation = 3.dp,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { viewModel.clearSelection() }) {
                        Icon(Icons.Default.Close, contentDescription = "Leave selection")
                    }
                    Text(
                        text = "${selectedNoteIds.size}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.loadTagOptions(); showTagDialog = true }) {
                        Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Tag")
                    }
                    TextButton(
                        onClick = { showMergeConfirmation = true },
                        enabled = selectedNoteIds.size > 1
                    ) {
                        Icon(Icons.Default.Merge, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Merge")
                    }
                    TextButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                    TextButton(onClick = { showTranscribeDialog = true }) {
                        Icon(Icons.Default.Transcribe, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Transcribe")
                    }
                }
            }
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier.align(Alignment.BottomCenter)
    )
    }
}


/**
 * Select every note the finger passes over, once a selection has begun.
 *
 * Tagging or deleting a run of notes otherwise means a long press and then a
 * tap on each one. The gesture only exists while notes are already selected,
 * so it cannot be started by accident, and it only ever adds to the
 * selection: a finger that wanders must not un-tick what the user already
 * chose.
 *
 * Still being tried out, so it is in the debug build only
 * ([BuildConfig.DEV_FEATURES]).
 */
private fun Modifier.dragToSelect(
    enabled: Boolean,
    listState: LazyListState,
    /** Felt the moment the hold registers, as a long press on a row used to. */
    onHold: () -> Unit,
    onSelect: (String) -> Unit,
): Modifier = if (!enabled) this else this.pointerInput(Unit) {
    var lastKey: Any? = null

    fun selectAt(y: Float) {
        // The list's offsets are measured from the top of its viewport, which
        // is where the pointer's y is measured from too.
        val item = listState.layoutInfo.visibleItemsInfo.firstOrNull { info ->
            y >= info.offset && y <= info.offset + info.size
        } ?: return
        if (item.key != lastKey) {
            lastKey = item.key
            (item.key as? String)?.let(onSelect)
        }
    }

    awaitEachGesture {
        // Watched from the Initial pass, before the rows and before the
        // list's own scrolling. A gesture detector on the list itself never
        // saw the long press at all: the row under the finger has a long-press
        // handler of its own and consumed it first, which is why dragging did
        // nothing however long the finger was held.
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        val slop = viewConfiguration.touchSlop

        // A long press is a press that stays put. If the finger lifts or
        // wanders before the timeout, this is a tap or a scroll and is left
        // alone.
        val held = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: return@withTimeoutOrNull false
                if (!change.pressed) return@withTimeoutOrNull false
                if ((change.position - down.position).getDistance() > slop) return@withTimeoutOrNull false
            }
            @Suppress("UNREACHABLE_CODE") false
        }
        // Null means the timeout struck with the finger still down and still
        // in place: the press became a hold.
        if (held != null) return@awaitEachGesture

        lastKey = null
        onHold()
        selectAt(down.position.y)

        // From here every move is ours. Consuming in the Initial pass is what
        // stops the list scrolling under the finger while it selects.
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) {
                change.consume()
                break
            }
            selectAt(change.position.y)
            change.consume()
        }
    }
}

/**
 * The recordings mark at the left of a Note's row: the symbol of the medium,
 * how many recordings the Note holds, and a small mark when any of them has a
 * transcription. Laid out left to right even in a right-to-left interface, so
 * the symbol always precedes its number (bidirectional text would otherwise
 * put the digit first).
 *
 * Tapping it opens the Note's section, the same one the chevron opens: there
 * is one section per Note, and the recordings are played inside it.
 */
@Composable
private fun AttachmentChip(
    /** How many recordings the Note holds. */
    count: Int,
    open: Boolean,
    transcribed: Boolean,
    /** A transcription was asked for and has not arrived yet. */
    pending: Boolean = false,
    onClick: () -> Unit
) {
    val content = if (open) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        modifier = Modifier
            .padding(start = 2.dp)
            // Said in words as well as drawn: a screen reader would otherwise
            // read this as "speaker 2", and a test has nothing to press.
            .semantics {
                contentDescription = if (count == 1) "1 recording" else "$count recordings"
            }
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.extraSmall,
        color = if (open) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        // The speaker, the number of recordings and the transcription mark all
        // follow the icon-size setting together, so that the chip stays one piece.
        val labelStyle = MaterialTheme.typography.labelSmall.copy(
            fontSize = scaledIconText(MaterialTheme.typography.labelSmall.fontSize),
            lineHeight = scaledIconText(MaterialTheme.typography.labelSmall.lineHeight)
        )
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = AUDIO_SYMBOL, style = labelStyle, color = content)
                Text(text = "$count", style = labelStyle, color = content)
                if (pending) {
                    // Asked for and not arrived: the mark with a clock over it
                    PendingTranscriptionIcon(
                        modifier = Modifier.padding(start = 2.dp),
                        size = scaledIcon(11.dp),
                        tint = content,
                        background = if (open) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant
                    )
                } else if (transcribed) {
                    Icon(
                        imageVector = Icons.Default.Transcribe,
                        contentDescription = "transcribed",
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .size(scaledIcon(11.dp)),
                        tint = content
                    )
                }
            }
        }
    }
}

/** Speaker symbol for a recording. Video attachments get their own symbol. */
private const val AUDIO_SYMBOL = "\uD83D\uDD0A"

/** Format duration in seconds to a human-readable string (h:mm:ss or mm:ss). */
private fun formatDuration(seconds: Int): String = Durations.ofSeconds(seconds)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    noteWithAudio: NoteWithAudioFiles,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    /**
     * How many lines of the note's text, and of its transcription, the row
     * shows. One each by default; the user sets it in Settings → Advanced,
     * and a preview of a note doubles it.
     */
    contentLines: Int = 1,
    /** 0 to 1 while this row is being pointed out, null the rest of the time. */
    spotlight: Float? = null,
    onClick: () -> Unit = {},
    /**
     * Held down. Null where the list itself owns the gesture, so that a hold
     * is not handled twice — one handler selecting the row and the other
     * toggling it straight back off.
     */
    onLongClick: (() -> Unit)? = null,
    onStarClick: () -> Unit = {},
    /**
     * Where a recording's file is. Suspending, and read inside the section
     * that needs it: it is a question for the database, and the database is
     * not something to ask on the thread that draws the screen.
     */
    getAudioFilePath: suspend (String) -> String? = { null },
    /**
     * Whether this row's subsection is open.
     *
     * Null means the card keeps that for itself, which is what every caller
     * wants except the notes list: the list owns it because only one row may be
     * open at a time and opening one scrolls to it. A card given neither this
     * nor [onToggleExpanded] still opens and closes when its chevron is
     * pressed — a chevron that does nothing is a defect, and the trash, the
     * note preview and the transcription queue all draw this card.
     */
    expanded: Boolean? = null,
    /** Open or close this row's subsection. Null leaves it to the card. */
    onToggleExpanded: (() -> Unit)? = null,
    /** Put Tags on this Note. Null hides the button, as in the trash bin. */
    onTagNote: (() -> Unit)? = null,
    /** Delete this Note. Null hides the button. */
    onDeleteNote: (() -> Unit)? = null,
    /**
     * The colour chosen for a Tag, by name; a Tag not in the map is drawn in
     * the colour calculated from its name. Null draws every Tag as plain
     * text, which is Settings → Advanced → Draw Tags in their own colours.
     */
    tagColours: Map<String, String>? = emptyMap()
) {
    val note = noteWithAudio.note
    val audioFiles = noteWithAudio.audioFiles

    // Where the caller does not hold the open/closed state, the card holds it,
    // so the chevron always does what it looks like it does. Keyed by the note,
    // so a reused row does not open because its predecessor was open.
    var openedHere by remember(note.id) { mutableStateOf(false) }
    val isOpen = expanded ?: openedHere
    val toggleOpen: () -> Unit = onToggleExpanded ?: { openedHere = !openedHere }
    val isMarked = noteWithAudio.isMarked
    val durationSeconds = noteWithAudio.durationSeconds
    val tags = noteWithAudio.tags

    // The card keeps its usual colour unless it is selected; the dots of the
    // spotlight are mixed against whichever colour that is.
    val cardColors = if (selected) {
        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    } else {
        CardDefaults.cardColors()
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .spotlight(spotlight, spotlightColor(cardColors.containerColor)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = cardColors
    ) {
        Column(
            modifier = Modifier.padding(5.dp)
        ) {
            // Star, date, duration, and tags row
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // The chevron opens this Note's section, and is the only
                // thing that does. It comes first in the row, before the
                // star, because it is what the row is unfolded by.
                IconButton(
                    onClick = toggleOpen,
                    modifier = Modifier.size(scaledIcon(22.dp))
                ) {
                    Icon(
                        imageVector = if (isOpen) Icons.Filled.KeyboardArrowUp
                        else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (isOpen) "Close this Note" else "Open this Note",
                        modifier = Modifier.size(scaledIcon(18.dp)),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // While notes are being selected the star becomes the tick box
                IconButton(
                    onClick = if (selectionMode) onClick else onStarClick,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        imageVector = when {
                            selectionMode && selected -> Icons.Filled.CheckCircle
                            selectionMode -> Icons.Outlined.Circle
                            isMarked -> Icons.Filled.Star
                            else -> Icons.Outlined.StarOutline
                        },
                        contentDescription = when {
                            selectionMode && selected -> "Selected"
                            selectionMode -> "Not selected"
                            isMarked -> "Unstar this Note"
                            else -> "Star this Note"
                        },
                        modifier = Modifier.size(16.dp),
                        tint = when {
                            selectionMode -> MaterialTheme.colorScheme.primary
                            isMarked -> StarGold
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
                // One mark for the whole Note, with how many recordings it
                // holds. Three recordings used to mean three buttons, which
                // pushed the date off a narrow screen and said nothing that
                // the number does not.
                if (audioFiles.isNotEmpty()) {
                    AttachmentChip(
                        count = audioFiles.size,
                        open = isOpen,
                        transcribed = audioFiles.any { it.id in noteWithAudio.transcribedAudioIds },
                        pending = audioFiles.any { it.id in noteWithAudio.pendingAudioIds },
                        onClick = toggleOpen
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                // Date (bold), as the clock read where the note was made
                Text(
                    text = stampText(
                        note.createdAt,
                        baseSize = MaterialTheme.typography.labelSmall.fontSize
                    ),
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
                // Tags, each in its own colour, so a Note is recognised by
                // the colours on it before its words are read.
                if (tags.isNotEmpty()) {
                    Text(
                        text = " | ",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (tagColours == null) {
                        Text(
                            text = tags.joinToString(", "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            for (tag in tags) {
                                TagChip(name = tag, colour = tagColours[tag])
                            }
                        }
                    }
                }
            }

            // The opening lines of the note, then of its first transcription.
            // Whichever is missing leaves no empty row behind, so a note with
            // no text and a recording does not waste a blank line.
            val lines = contentLines.coerceAtLeast(1)
            val noteText = openingLines(note.content, lines).joinToString("\n")
            if (noteText.isNotEmpty()) {
                Text(
                    text = noteText,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val transcriptionText = noteWithAudio.transcriptionLines.take(lines).joinToString("\n")
            if (transcriptionText.isNotEmpty()) {
                Text(
                    text = transcriptionText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = lines,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // The Note's section: everything that can be done with it, and
            // its recordings played. One section, opened by the chevron or by
            // the recordings mark, so there is never a second panel to close.
            AnimatedVisibility(
                visible = isOpen,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    if (audioFiles.isNotEmpty()) {
                        // Which recording the player is on. The one that
                        // stands for the Note to begin with, and its number
                        // is pressed to move to another.
                        var playing by remember(noteWithAudio.note.id) {
                            mutableStateOf(
                                noteWithAudio.chosenAudioFileId ?: audioFiles.first().id
                            )
                        }
                        val current = audioFiles.firstOrNull { it.id == playing } ?: audioFiles.first()

                        if (audioFiles.size > 1) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                audioFiles.forEachIndexed { index, audioFile ->
                                    val open = audioFile.id == current.id
                                    Surface(
                                        modifier = Modifier.clickable { playing = audioFile.id },
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = if (open) MaterialTheme.colorScheme.primaryContainer
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (open) MaterialTheme.colorScheme.onPrimaryContainer
                                            else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Text(
                            text = current.filename,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        var filePath by remember(current.id) { mutableStateOf<String?>(null) }
                        LaunchedEffect(current.id) { filePath = getAudioFilePath(current.id) }
                        CompactAudioPlayer(
                            filePath = filePath,
                            modifier = Modifier.padding(top = 2.dp),
                            title = current.filename,
                            noteId = note.id,
                            noteLine = note.content.lineSequence().firstOrNull()?.take(60),
                            audioFileId = current.id,
                        )
                    }

                    // The buttons come last, under the waveform: the player is
                    // what the section is opened for, and a Delete button
                    // above it would be the first thing under the finger.
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onTagNote != null) {
                            TextButton(onClick = onTagNote) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Label,
                                    contentDescription = null,
                                    modifier = Modifier.size(scaledIcon(16.dp))
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Tags", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                        if (onDeleteNote != null) {
                            TextButton(onClick = onDeleteNote) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = null,
                                    modifier = Modifier.size(scaledIcon(16.dp)),
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "Delete",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

