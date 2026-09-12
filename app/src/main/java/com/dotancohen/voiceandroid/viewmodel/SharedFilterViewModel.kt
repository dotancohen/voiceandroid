package com.dotancohen.voiceandroid.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared ViewModel for filter state, scoped to the activity.
 * This allows the filter state to be shared between FilterScreen and NotesScreen.
 */
class SharedFilterViewModel : ViewModel() {

    private val _activeSearchQuery = MutableStateFlow<String?>(null)
    val activeSearchQuery: StateFlow<String?> = _activeSearchQuery.asStateFlow()

    /**
     * Check if there is an active search filter.
     */
    val hasActiveFilter: Boolean
        get() = !_activeSearchQuery.value.isNullOrBlank()

    /**
     * Set the active search filter.
     * This will be used by FilterScreen when executing a search.
     */
    fun setSearchFilter(query: String) {
        _activeSearchQuery.value = query.trim().ifBlank { null }
    }

    /**
     * Clear the active search filter.
     * This will show all notes (unfiltered).
     */
    fun clearSearchFilter() {
        _activeSearchQuery.value = null
    }

    /**
     * The notes as the list is showing them, in that order.
     *
     * A note screen moves to the next and previous note through this, so
     * "next" means the next one in the list the user was looking at, search
     * and star filter included, rather than the next one in the database.
     */
    private val _visibleNoteIds = MutableStateFlow<List<String>>(emptyList())
    val visibleNoteIds: StateFlow<List<String>> = _visibleNoteIds.asStateFlow()

    fun setVisibleNotes(ids: List<String>) {
        if (_visibleNoteIds.value != ids) _visibleNoteIds.value = ids
    }
}
