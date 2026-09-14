package com.dotancohen.voiceandroid.data

/**
 * The arithmetic of the tag hierarchy: paths, depths, descendants, and what a
 * collapsed parent hides. Four screens showed the same walk up the parent
 * chain; they now share this one, which is also the only place that has to
 * cope with a chain that does not end.
 *
 * A parent chain is a chain only as long as every device agrees. Two phones
 * that are offline can each move a tag under the other's tag — "Work" under
 * "Trips" here, "Trips" under "Work" there — and after they sync, the two
 * tags are each other's parent. Walking up such a chain never reaches the
 * root, so every function here counts the tags it has already seen and stops.
 * The screen then shows an odd path rather than freezing the phone.
 */
object TagTree {

    /** Child ids by parent id, for the tags given. */
    fun childrenByParent(tags: List<Tag>): Map<String, Set<String>> {
        val children = mutableMapOf<String, MutableSet<String>>()
        for (tag in tags) {
            tag.parentId?.let { children.getOrPut(it) { mutableSetOf() }.add(tag.id) }
        }
        return children.mapValues { it.value.toSet() }
    }

    /**
     * The ancestors of a tag, nearest parent first, stopping at the root — or
     * at the first tag that repeats, when the chain loops.
     *
     * A parent id naming a tag that is not in the list ends the walk too: a
     * tag whose parent has been deleted, or has not synced yet, is shown at
     * the depth it can be shown at rather than being hidden.
     */
    fun ancestorsOf(tag: Tag, tagById: Map<String, Tag>): List<Tag> {
        val seen = mutableSetOf(tag.id)
        val ancestors = mutableListOf<Tag>()
        var current = tag.parentId?.let { tagById[it] }
        while (current != null && seen.add(current.id)) {
            ancestors.add(current)
            current = current.parentId?.let { tagById[it] }
        }
        return ancestors
    }

    /** The full path of a tag, e.g. "Work > Trips > Athens". */
    fun pathOf(tag: Tag, tagById: Map<String, Tag>): String =
        (ancestorsOf(tag, tagById).reversed() + tag).joinToString(PATH_SEPARATOR) { it.name }

    /** How many tags stand between this tag and the root. */
    fun depthOf(tag: Tag, tagById: Map<String, Tag>): Int = ancestorsOf(tag, tagById).size

    /**
     * Every tag below this one — children, their children, and so on. The tag
     * itself is not included, even when it is part of a loop that reaches it.
     */
    fun descendantIds(tagId: String, childrenByParent: Map<String, Set<String>>): Set<String> {
        val descendants = mutableSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(tagId)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            childrenByParent[current]?.forEach { childId ->
                if (childId != tagId && descendants.add(childId)) queue.add(childId)
            }
        }
        return descendants
    }

    /**
     * The tags a tag may be moved under: everything except itself and its own
     * descendants, since either would cut the tag off from the root.
     */
    fun possibleParentIds(tagId: String, tags: List<Tag>): Set<String> {
        val forbidden = descendantIds(tagId, childrenByParent(tags)) + tagId
        return tags.map { it.id }.filterNot { it in forbidden }.toSet()
    }

    /** True when one of the tag's ancestors is collapsed, so the tag is not shown. */
    fun isHiddenByCollapse(tag: Tag, tagById: Map<String, Tag>, collapsedIds: Set<String>): Boolean =
        ancestorsOf(tag, tagById).any { it.id in collapsedIds }

    /** The id of the hidden `_system` tag, the same on every device (the core's `SYSTEM_TAG_UUID`). */
    const val SYSTEM_TAG_ID = "a1b2c3d4000050008000000000000001"

    /**
     * The hidden tags the core makes on every device, by their fixed ids:
     * `_system`, `_system/_marked`, `_system/_nonsynced` and
     * `_system/_nonsynced/_too-big` (the core's `*_TAG_UUID`).
     */
    val BUILT_IN_HIDDEN_IDS: Set<String> = setOf(
        SYSTEM_TAG_ID,
        "a1b2c3d4000050008000000000000002",
        "a1b2c3d4000050008000000000000003",
        "a1b2c3d4000050008000000000000004",
    )

    /**
     * The tags a person sees: without `_system` and every tag under it, however
     * deep, and without the core's built-in hidden tags even when their parents
     * are not in the list (a note's own tags). A tag of the user's whose name
     * starts with "_" is shown like any other.
     */
    fun withoutSystemTags(tags: List<Tag>): List<Tag> {
        val hidden = descendantIds(SYSTEM_TAG_ID, childrenByParent(tags)) + BUILT_IN_HIDDEN_IDS
        return tags.filterNot { it.id in hidden }
    }

    const val PATH_SEPARATOR = " > "
}
