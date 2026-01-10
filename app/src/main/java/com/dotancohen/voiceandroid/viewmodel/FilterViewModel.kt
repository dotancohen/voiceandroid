package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.Tag
import com.dotancohen.voiceandroid.data.VoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Represents a tag in a hierarchical tree structure.
 */
data class TagTreeNode(
    val tag: Tag,
    val children: MutableList<TagTreeNode> = mutableListOf(),
    val depth: Int = 0
)

class FilterViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)

    // All tags as a flat list
    private val _allTags = MutableStateFlow<List<Tag>>(emptyList())

    // Tags organized as a tree (root nodes only)
    private val _tagTree = MutableStateFlow<List<TagTreeNode>>(emptyList())
    val tagTree: StateFlow<List<TagTreeNode>> = _tagTree.asStateFlow()

    // Current search query being built
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Set of expanded tag IDs for the tree UI
    private val _expandedTagIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedTagIds: StateFlow<Set<String>> = _expandedTagIds.asStateFlow()

    // System tag ID for filtering
    private var systemTagId: String? = null

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

            // Get the system tag ID for filtering
            systemTagId = repository.getSystemTagIdHex().getOrNull()

            repository.getAllTags()
                .onSuccess { tags ->
                    // Filter out _system tag and its children
                    val filteredTags = if (systemTagId != null) {
                        tags.filter { tag ->
                            tag.id != systemTagId && tag.parentId != systemTagId
                        }
                    } else {
                        tags
                    }

                    _allTags.value = filteredTags
                    _tagTree.value = buildTagTree(filteredTags)
                    // Expand all root nodes by default
                    _expandedTagIds.value = _tagTree.value.map { it.tag.id }.toSet()
                }
                .onFailure { exception ->
                    _error.value = exception.message
                }

            _isLoading.value = false
        }
    }

    /**
     * Build a hierarchical tree from a flat list of tags.
     */
    private fun buildTagTree(tags: List<Tag>): List<TagTreeNode> {
        // Create a map of tag ID to TagTreeNode
        val nodeMap = tags.associate { tag ->
            tag.id to TagTreeNode(tag = tag, depth = 0)
        }.toMutableMap()

        // Build parent-child relationships
        val rootNodes = mutableListOf<TagTreeNode>()

        for (tag in tags) {
            val node = nodeMap[tag.id] ?: continue

            if (tag.parentId == null) {
                // Root node
                rootNodes.add(node)
            } else {
                // Child node - add to parent's children
                val parentNode = nodeMap[tag.parentId]
                if (parentNode != null) {
                    parentNode.children.add(node)
                } else {
                    // Parent not found, treat as root
                    rootNodes.add(node)
                }
            }
        }

        // Calculate depths and sort
        fun setDepths(nodes: List<TagTreeNode>, depth: Int) {
            for (node in nodes) {
                // Create a new node with the correct depth (TagTreeNode is a data class)
                val updatedNode = node.copy(depth = depth)
                // Since we're modifying in place, we need to update children first
                setDepths(node.children, depth + 1)
            }
        }
        setDepths(rootNodes, 0)

        // Sort alphabetically
        fun sortNodes(nodes: MutableList<TagTreeNode>) {
            nodes.sortBy { it.tag.name.lowercase() }
            nodes.forEach { sortNodes(it.children) }
        }
        sortNodes(rootNodes)

        return rootNodes
    }

    /**
     * Get a flattened list of visible tags based on expanded state.
     * Each item includes its depth for indentation.
     */
    fun getFlattenedVisibleTags(): List<TagTreeNode> {
        val result = mutableListOf<TagTreeNode>()
        val expanded = _expandedTagIds.value

        fun addNodes(nodes: List<TagTreeNode>, depth: Int) {
            for (node in nodes) {
                val nodeWithDepth = node.copy(depth = depth)
                result.add(nodeWithDepth)
                if (expanded.contains(node.tag.id) && node.children.isNotEmpty()) {
                    addNodes(node.children, depth + 1)
                }
            }
        }

        addNodes(_tagTree.value, 0)
        return result
    }

    /**
     * Toggle the expanded state of a tag.
     */
    fun toggleExpanded(tagId: String) {
        val current = _expandedTagIds.value.toMutableSet()
        if (current.contains(tagId)) {
            current.remove(tagId)
        } else {
            current.add(tagId)
        }
        _expandedTagIds.value = current
    }

    /**
     * Check if a tag has children.
     */
    fun hasChildren(tagId: String): Boolean {
        fun findNode(nodes: List<TagTreeNode>): TagTreeNode? {
            for (node in nodes) {
                if (node.tag.id == tagId) return node
                val found = findNode(node.children)
                if (found != null) return found
            }
            return null
        }
        return findNode(_tagTree.value)?.children?.isNotEmpty() == true
    }

    /**
     * Add a tag to the search query.
     * Adds "tag:TagName" to the current query.
     */
    fun addTagToSearch(tag: Tag) {
        val tagTerm = "tag:${tag.name}"
        val currentQuery = _searchQuery.value.trim()

        // Check if tag is already in query (case-insensitive)
        if (currentQuery.lowercase().contains(tagTerm.lowercase())) {
            return // Tag already added
        }

        _searchQuery.value = if (currentQuery.isEmpty()) {
            tagTerm
        } else {
            "$currentQuery $tagTerm"
        }
    }

    /**
     * Update the search query directly.
     */
    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    /**
     * Clear the search query.
     */
    fun clearSearch() {
        _searchQuery.value = ""
    }

    /**
     * Refresh tags from the database.
     */
    fun refresh() {
        loadTags()
    }
}
