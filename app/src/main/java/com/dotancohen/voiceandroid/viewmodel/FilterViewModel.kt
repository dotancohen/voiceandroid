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

/**
 * The tags as a tree of root nodes, each sorted by name.
 *
 * A tag whose parent is not in the list is shown at the top level rather than
 * being dropped, and so is a tag caught in a loop — two phones that were both
 * offline can each move one tag under the other, and after they sync neither
 * of the two has a root above it. Such a tag is cut loose from its parent
 * here, so that it can still be seen and moved somewhere sensible.
 */
fun buildTagTree(tags: List<Tag>): List<TagTreeNode> {
    val nodeMap = tags.associate { tag -> tag.id to TagTreeNode(tag = tag, depth = 0) }
    val rootNodes = mutableListOf<TagTreeNode>()

    for (tag in tags) {
        val node = nodeMap[tag.id] ?: continue
        val parentNode = tag.parentId?.let { nodeMap[it] }
        if (parentNode != null && parentNode !== node) parentNode.children.add(node)
        else rootNodes.add(node)
    }

    // Anything no root can reach is in a loop; the first such tag becomes a
    // root of its own, which is enough to reach the rest of the loop.
    val reachable = mutableSetOf<String>()
    fun mark(node: TagTreeNode) {
        if (reachable.add(node.tag.id)) node.children.forEach(::mark)
    }
    rootNodes.forEach(::mark)
    for (tag in tags) {
        if (tag.id in reachable) continue
        val node = nodeMap[tag.id] ?: continue
        tag.parentId?.let { nodeMap[it]?.children?.remove(node) }
        rootNodes.add(node)
        mark(node)
    }

    fun sortNodes(nodes: MutableList<TagTreeNode>) {
        nodes.sortBy { it.tag.name.lowercase() }
        nodes.forEach { sortNodes(it.children) }
    }
    sortNodes(rootNodes)

    return rootNodes
}

/** The rows the tree shows: a node's children only while the node is expanded. */
fun flattenVisibleTags(roots: List<TagTreeNode>, expandedIds: Set<String>): List<TagTreeNode> {
    val result = mutableListOf<TagTreeNode>()
    fun addNodes(nodes: List<TagTreeNode>, depth: Int) {
        for (node in nodes) {
            result.add(node.copy(depth = depth))
            if (node.tag.id in expandedIds && node.children.isNotEmpty()) {
                addNodes(node.children, depth + 1)
            }
        }
    }
    addNodes(roots, 0)
    return result
}

/**
 * The query with `tag:Name` added, or unchanged when that exact term is
 * already there.
 *
 * The term has to match whole: a query that filters on `tag:Workshop` does
 * not already filter on `tag:Work`.
 */
fun queryWithTag(query: String, tagName: String): String {
    val term = "tag:$tagName"
    val present = query.split(" ", "\t", "\n")
        .any { it.equals(term, ignoreCase = true) }
    if (present) return query.trim()
    val trimmed = query.trim()
    return if (trimmed.isEmpty()) term else "$trimmed $term"
}

/** The term the search understands as "only the starred notes". */
const val MARKED_TERM = "is:marked"

private val MARKED_PATTERN = Regex("""\bis:marked\b""", RegexOption.IGNORE_CASE)

/** Whether this query already asks for the starred notes only. */
fun queryHasMarkedFilter(query: String): Boolean = MARKED_PATTERN.containsMatchIn(query)

/**
 * The query with the star filter switched on or off, leaving the rest of
 * what the user typed alone.
 */
fun queryWithMarkedFilter(query: String, wanted: Boolean): String {
    val without = MARKED_PATTERN.replace(query, "").replace(Regex("""\s+"""), " ").trim()
    return if (wanted) "$MARKED_TERM $without".trim() else without
}

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
                    // Without _system and every tag under it, however deep
                    val filteredTags = com.dotancohen.voiceandroid.data.TagTree.withoutSystemTags(tags)

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
     * Get a flattened list of visible tags based on expanded state.
     * Each item includes its depth for indentation.
     */
    fun getFlattenedVisibleTags(): List<TagTreeNode> =
        flattenVisibleTags(_tagTree.value, _expandedTagIds.value)

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
        _searchQuery.value = queryWithTag(_searchQuery.value, tag.name)
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
