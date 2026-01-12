package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.Tag
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Data class representing a tag with its computed full path and hierarchy info.
 */
data class TagWithPath(
    val tag: Tag,
    val path: String,
    val depth: Int,
    val hasChildren: Boolean
)

/**
 * ViewModel for the tag management screen.
 */
class TagManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)

    private val _noteId = MutableStateFlow<String?>(null)
    val noteId: StateFlow<String?> = _noteId.asStateFlow()

    private val _allTags = MutableStateFlow<List<TagWithPath>>(emptyList())
    val allTags: StateFlow<List<TagWithPath>> = _allTags.asStateFlow()

    private val _noteTagIds = MutableStateFlow<Set<String>>(emptySet())
    val noteTagIds: StateFlow<Set<String>> = _noteTagIds.asStateFlow()

    private val _filterText = MutableStateFlow("")
    val filterText: StateFlow<String> = _filterText.asStateFlow()

    private val _filteredTags = MutableStateFlow<List<TagWithPath>>(emptyList())
    val filteredTags: StateFlow<List<TagWithPath>> = _filteredTags.asStateFlow()

    // Set of collapsed tag IDs (parents whose children are hidden)
    private val _collapsedTagIds = MutableStateFlow<Set<String>>(emptySet())
    val collapsedTagIds: StateFlow<Set<String>> = _collapsedTagIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Event emitted when tags are changed (noteId that was modified)
    // Observers should refresh the notes list for this note
    private val _tagsChanged = MutableSharedFlow<String>()
    val tagsChanged: SharedFlow<String> = _tagsChanged.asSharedFlow()

    // Map of tag ID to its children IDs (for collapse/expand)
    private var childrenByParentId: Map<String, Set<String>> = emptyMap()

    /**
     * Load tags for a specific note.
     */
    fun loadTags(noteId: String) {
        _noteId.value = noteId
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Load all tags
                repository.getAllTags()
                    .onSuccess { tags ->
                        val tagsWithPaths = computeTagPaths(tags)
                        _allTags.value = tagsWithPaths
                        updateFilteredTags()
                    }
                    .onFailure { e ->
                        _error.value = "Failed to load tags: ${e.message}"
                    }

                // Load note's tags
                repository.getTagsForNote(noteId)
                    .onSuccess { noteTags ->
                        _noteTagIds.value = noteTags.map { it.id }.toSet()
                    }
                    .onFailure { e ->
                        _error.value = "Failed to load note tags: ${e.message}"
                    }
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Compute full paths for all tags and determine which have children.
     */
    private fun computeTagPaths(tags: List<Tag>): List<TagWithPath> {
        val tagById = tags.associateBy { it.id }

        // Build children map
        val childrenMap = mutableMapOf<String, MutableSet<String>>()
        for (tag in tags) {
            tag.parentId?.let { parentId ->
                childrenMap.getOrPut(parentId) { mutableSetOf() }.add(tag.id)
            }
        }
        childrenByParentId = childrenMap.mapValues { it.value.toSet() }

        // Compute all descendants for each tag (for collapse logic)
        val result = mutableListOf<TagWithPath>()

        for (tag in tags) {
            val pathParts = mutableListOf<String>()
            var current: Tag? = tag
            var depth = 0

            while (current != null) {
                pathParts.add(0, current.name)
                val parentId = current.parentId
                current = if (parentId != null) tagById[parentId] else null
                if (current != null) depth++
            }

            result.add(TagWithPath(
                tag = tag,
                path = pathParts.joinToString(" > "),
                depth = depth,
                hasChildren = childrenByParentId.containsKey(tag.id)
            ))
        }

        // Sort by full path for proper hierarchical order
        return result.sortedBy { it.path.lowercase() }
    }

    /**
     * Get all descendant IDs of a tag (children, grandchildren, etc.)
     */
    private fun getAllDescendantIds(tagId: String): Set<String> {
        val descendants = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(tagId)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            childrenByParentId[current]?.forEach { childId ->
                if (descendants.add(childId)) {
                    queue.add(childId)
                }
            }
        }
        return descendants
    }

    /**
     * Check if a tag is hidden due to a collapsed ancestor.
     */
    private fun isTagHiddenByCollapse(tag: TagWithPath): Boolean {
        val tagById = _allTags.value.associateBy { it.tag.id }
        var current = tag.tag.parentId

        while (current != null) {
            if (_collapsedTagIds.value.contains(current)) {
                return true
            }
            current = tagById[current]?.tag?.parentId
        }
        return false
    }

    /**
     * Toggle collapse/expand state of a tag.
     */
    fun toggleCollapse(tagId: String) {
        val current = _collapsedTagIds.value
        val newCollapsed = if (current.contains(tagId)) {
            current - tagId
        } else {
            current + tagId
        }
        _collapsedTagIds.value = newCollapsed
        updateFilteredTags()
    }

    /**
     * Check if a tag is collapsed.
     */
    fun isCollapsed(tagId: String): Boolean {
        return _collapsedTagIds.value.contains(tagId)
    }

    /**
     * Update the filter text and recompute filtered tags.
     */
    fun updateFilter(text: String) {
        _filterText.value = text
        updateFilteredTags()
    }

    /**
     * Update the filtered tags list based on current filter and collapse state.
     */
    private fun updateFilteredTags() {
        val filter = _filterText.value.trim().lowercase()
        val all = _allTags.value

        val filtered = if (filter.isEmpty()) {
            // When not filtering, respect collapse state
            all.filter { !isTagHiddenByCollapse(it) }
        } else {
            // When filtering, show all matching tags (ignore collapse)
            all.filter { tagWithPath ->
                tagWithPath.path.lowercase().contains(filter)
            }
        }
        _filteredTags.value = filtered
    }

    /**
     * Toggle a tag on/off for the current note.
     */
    fun toggleTag(tagId: String) {
        val currentNoteId = _noteId.value ?: return
        val isCurrentlySelected = _noteTagIds.value.contains(tagId)

        viewModelScope.launch {
            if (isCurrentlySelected) {
                // Remove tag from note
                repository.removeTagFromNote(currentNoteId, tagId)
                    .onSuccess { result ->
                        _noteTagIds.value = _noteTagIds.value - tagId
                        AppLogger.i(TAG, "Removed tag $tagId from note $currentNoteId (changed=${result.changed}, cacheRebuilt=${result.listCacheRebuilt})")
                        // Notify observers to refresh the notes list
                        if (result.listCacheRebuilt) {
                            _tagsChanged.emit(result.noteId)
                        }
                    }
                    .onFailure { e ->
                        _error.value = "Failed to remove tag: ${e.message}"
                        AppLogger.e(TAG, "Failed to remove tag", e)
                    }
            } else {
                // Add tag to note
                repository.addTagToNote(currentNoteId, tagId)
                    .onSuccess { result ->
                        _noteTagIds.value = _noteTagIds.value + tagId
                        AppLogger.i(TAG, "Added tag $tagId to note $currentNoteId (changed=${result.changed}, cacheRebuilt=${result.listCacheRebuilt})")
                        // Notify observers to refresh the notes list
                        if (result.listCacheRebuilt) {
                            _tagsChanged.emit(result.noteId)
                        }
                    }
                    .onFailure { e ->
                        _error.value = "Failed to add tag: ${e.message}"
                        AppLogger.e(TAG, "Failed to add tag", e)
                    }
            }
        }
    }

    /**
     * Check if a tag is selected for the current note.
     */
    fun isTagSelected(tagId: String): Boolean {
        return _noteTagIds.value.contains(tagId)
    }

    companion object {
        private const val TAG = "TagManagementViewModel"
    }
}
