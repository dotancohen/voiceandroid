package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.Tag
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Data class representing a tag with hierarchy information for display.
 */
data class TagHierarchyItem(
    val tag: Tag,
    val path: String,
    val depth: Int,
    val hasChildren: Boolean,
    val noteCount: Int
)

/**
 * ViewModel for the tag hierarchy management screen.
 * Handles creating, renaming, reparenting, and deleting tags.
 */
class TagHierarchyViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)

    private val _allTags = MutableStateFlow<List<TagHierarchyItem>>(emptyList())
    val allTags: StateFlow<List<TagHierarchyItem>> = _allTags.asStateFlow()

    private val _filterText = MutableStateFlow("")
    val filterText: StateFlow<String> = _filterText.asStateFlow()

    private val _filteredTags = MutableStateFlow<List<TagHierarchyItem>>(emptyList())
    val filteredTags: StateFlow<List<TagHierarchyItem>> = _filteredTags.asStateFlow()

    private val _collapsedTagIds = MutableStateFlow<Set<String>>(emptySet())
    val collapsedTagIds: StateFlow<Set<String>> = _collapsedTagIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    // For reparent dialog - list of possible parents
    private val _possibleParents = MutableStateFlow<List<TagHierarchyItem>>(emptyList())
    val possibleParents: StateFlow<List<TagHierarchyItem>> = _possibleParents.asStateFlow()

    // Map of tag ID to its children IDs
    private var childrenByParentId: Map<String, Set<String>> = emptyMap()

    // Raw tags for internal operations
    private var rawTags: List<Tag> = emptyList()

    init {
        loadTags()
    }

    /**
     * Load all tags from the database.
     */
    fun loadTags() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.getAllTags()
                .onSuccess { tags ->
                    rawTags = tags
                    val tagsWithPaths = computeTagHierarchy(tags)
                    _allTags.value = tagsWithPaths
                    updateFilteredTags()
                }
                .onFailure { e ->
                    _error.value = "Failed to load tags: ${e.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Compute hierarchy information for all tags.
     */
    private suspend fun computeTagHierarchy(tags: List<Tag>): List<TagHierarchyItem> {
        val tagById = tags.associateBy { it.id }

        // Build children map
        val childrenMap = mutableMapOf<String, MutableSet<String>>()
        for (tag in tags) {
            tag.parentId?.let { parentId ->
                childrenMap.getOrPut(parentId) { mutableSetOf() }.add(tag.id)
            }
        }
        childrenByParentId = childrenMap.mapValues { it.value.toSet() }

        // Get note counts for each tag
        val noteCounts = mutableMapOf<String, Int>()
        for (tag in tags) {
            repository.filterNotesByTags(listOf(tag.id))
                .onSuccess { notes -> noteCounts[tag.id] = notes.size }
                .onFailure { noteCounts[tag.id] = 0 }
        }

        val result = mutableListOf<TagHierarchyItem>()

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

            result.add(TagHierarchyItem(
                tag = tag,
                path = pathParts.joinToString(" > "),
                depth = depth,
                hasChildren = childrenByParentId.containsKey(tag.id),
                noteCount = noteCounts[tag.id] ?: 0
            ))
        }

        return result.sortedBy { it.path.lowercase() }
    }

    /**
     * Update filter text and recompute filtered tags.
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
            all.filter { !isTagHiddenByCollapse(it) }
        } else {
            all.filter { tagItem ->
                tagItem.path.lowercase().contains(filter)
            }
        }
        _filteredTags.value = filtered
    }

    /**
     * Check if a tag is hidden due to a collapsed ancestor.
     */
    private fun isTagHiddenByCollapse(tagItem: TagHierarchyItem): Boolean {
        val tagById = _allTags.value.associateBy { it.tag.id }
        var current = tagItem.tag.parentId

        while (current != null) {
            if (_collapsedTagIds.value.contains(current)) {
                return true
            }
            current = tagById[current]?.tag?.parentId
        }
        return false
    }

    /**
     * Toggle collapse state of a tag.
     */
    fun toggleCollapse(tagId: String) {
        val current = _collapsedTagIds.value
        _collapsedTagIds.value = if (current.contains(tagId)) {
            current - tagId
        } else {
            current + tagId
        }
        updateFilteredTags()
    }

    /**
     * Create a new tag.
     */
    fun createTag(name: String, parentId: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.createTag(name, parentId)
                .onSuccess { tagId ->
                    AppLogger.i(TAG, "Created tag $tagId: $name (parent: $parentId)")
                    _successMessage.value = "Tag '$name' created"
                    loadTags()
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Failed to create tag", e)
                    _error.value = "Failed to create tag: ${e.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Rename a tag.
     */
    fun renameTag(tagId: String, newName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.renameTag(tagId, newName)
                .onSuccess { success ->
                    if (success) {
                        AppLogger.i(TAG, "Renamed tag $tagId to: $newName")
                        _successMessage.value = "Tag renamed to '$newName'"
                        loadTags()
                    } else {
                        _error.value = "Tag not found"
                    }
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Failed to rename tag", e)
                    _error.value = "Failed to rename tag: ${e.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Reparent a tag (move to different parent).
     */
    fun reparentTag(tagId: String, newParentId: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.reparentTag(tagId, newParentId)
                .onSuccess { success ->
                    if (success) {
                        val parentName = if (newParentId != null) {
                            rawTags.find { it.id == newParentId }?.name ?: "unknown"
                        } else {
                            "root"
                        }
                        AppLogger.i(TAG, "Moved tag $tagId to parent: $parentName")
                        _successMessage.value = "Tag moved to $parentName"
                        loadTags()
                    } else {
                        _error.value = "Tag not found"
                    }
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Failed to reparent tag", e)
                    _error.value = "Failed to move tag: ${e.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Delete a tag.
     */
    fun deleteTag(tagId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            repository.deleteTag(tagId)
                .onSuccess { success ->
                    if (success) {
                        AppLogger.i(TAG, "Deleted tag $tagId")
                        _successMessage.value = "Tag deleted"
                        loadTags()
                    } else {
                        _error.value = "Tag not found"
                    }
                }
                .onFailure { e ->
                    AppLogger.e(TAG, "Failed to delete tag", e)
                    _error.value = "Failed to delete tag: ${e.message}"
                }

            _isLoading.value = false
        }
    }

    /**
     * Check if a tag has children.
     */
    fun hasChildren(tagId: String): Boolean {
        return childrenByParentId.containsKey(tagId)
    }

    /**
     * Get children of a tag.
     */
    fun getChildren(tagId: String): List<Tag> {
        val childIds = childrenByParentId[tagId] ?: return emptyList()
        return rawTags.filter { it.id in childIds }
    }

    /**
     * Prepare list of possible parents for reparenting (excluding self and descendants).
     */
    fun preparePossibleParents(tagId: String) {
        val descendants = getAllDescendantIds(tagId)
        descendants.add(tagId)

        _possibleParents.value = _allTags.value.filter { it.tag.id !in descendants }
    }

    /**
     * Get all descendant IDs of a tag.
     */
    private fun getAllDescendantIds(tagId: String): MutableSet<String> {
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
     * Clear success message after it's been shown.
     */
    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    /**
     * Clear error message after it's been shown.
     */
    fun clearError() {
        _error.value = null
    }

    companion object {
        private const val TAG = "TagHierarchyViewModel"
    }
}
