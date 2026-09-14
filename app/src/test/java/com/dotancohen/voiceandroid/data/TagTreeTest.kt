package com.dotancohen.voiceandroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paths, depths and descendants of the tag hierarchy.
 *
 * The tests that matter most here are the ones with a loop in the parent
 * chain. A loop is not a fantasy: two phones that are both offline can each
 * move one tag under the other, and both moves are kept, so after a sync the
 * two tags are each other's parent. Every one of these functions used to walk
 * up the chain until it reached the root, which such a pair never does.
 */
class TagTreeTest {

    private fun tag(id: String, name: String, parentId: String? = null) =
        Tag(id = id, name = name, parentId = parentId)

    /** עבודה > נסיעות > אתונה, plus a loose tag. */
    private val work = tag("1", "עבודה")
    private val trips = tag("2", "נסיעות", parentId = "1")
    private val athens = tag("3", "אתונה", parentId = "2")
    private val shopping = tag("4", "קניות")
    private val tags = listOf(work, trips, athens, shopping)
    private val byId = tags.associateBy { it.id }

    @Test
    fun `a path names every tag from the root down`() {
        assertEquals("עבודה > נסיעות > אתונה", TagTree.pathOf(athens, byId))
        assertEquals("עבודה > נסיעות", TagTree.pathOf(trips, byId))
        assertEquals("עבודה", TagTree.pathOf(work, byId))
    }

    @Test
    fun `the depth is the number of tags above it`() {
        assertEquals(0, TagTree.depthOf(work, byId))
        assertEquals(1, TagTree.depthOf(trips, byId))
        assertEquals(2, TagTree.depthOf(athens, byId))
        assertEquals(0, TagTree.depthOf(shopping, byId))
    }

    @Test
    fun `a tag whose parent is missing is treated as a root`() {
        // The parent was deleted on another phone, or has not synced yet.
        val orphan = tag("9", "יתום", parentId = "no-such-tag")
        val map = (tags + orphan).associateBy { it.id }
        assertEquals("יתום", TagTree.pathOf(orphan, map))
        assertEquals(0, TagTree.depthOf(orphan, map))
    }

    @Test
    fun `two tags that are each other's parent do not hang the phone`() {
        val a = tag("a", "אלף", parentId = "b")
        val b = tag("b", "בית", parentId = "a")
        val map = listOf(a, b).associateBy { it.id }
        assertEquals(listOf(b), TagTree.ancestorsOf(a, map))
        assertEquals(1, TagTree.depthOf(a, map))
        assertEquals("בית > אלף", TagTree.pathOf(a, map))
        assertEquals("אלף > בית", TagTree.pathOf(b, map))
    }

    @Test
    fun `a tag that is its own parent does not hang the phone`() {
        val self = tag("s", "עצמי", parentId = "s")
        val map = mapOf("s" to self)
        assertEquals(emptyList<Tag>(), TagTree.ancestorsOf(self, map))
        assertEquals(0, TagTree.depthOf(self, map))
        assertEquals("עצמי", TagTree.pathOf(self, map))
    }

    @Test
    fun `a long loop does not hang the phone either`() {
        val loop = (0 until 50).map { tag("t$it", "tag$it", parentId = "t${(it + 1) % 50}") }
        val map = loop.associateBy { it.id }
        // 49 others above it, and then the walk meets itself.
        assertEquals(49, TagTree.depthOf(loop[0], map))
        assertTrue(TagTree.pathOf(loop[0], map).endsWith("tag0"))
    }

    @Test
    fun `a tag beneath a loop is still given a path`() {
        val a = tag("a", "אלף", parentId = "b")
        val b = tag("b", "בית", parentId = "a")
        val leaf = tag("c", "גימל", parentId = "a")
        val map = listOf(a, b, leaf).associateBy { it.id }
        assertEquals(listOf(a, b), TagTree.ancestorsOf(leaf, map))
        assertEquals("בית > אלף > גימל", TagTree.pathOf(leaf, map))
    }

    @Test
    fun `children are collected by parent`() {
        val children = TagTree.childrenByParent(tags)
        assertEquals(setOf("2"), children["1"])
        assertEquals(setOf("3"), children["2"])
        assertFalse("a leaf has no entry at all", children.containsKey("3"))
        assertFalse(children.containsKey("4"))
    }

    @Test
    fun `descendants reach every generation below`() {
        val children = TagTree.childrenByParent(tags)
        assertEquals(setOf("2", "3"), TagTree.descendantIds("1", children))
        assertEquals(setOf("3"), TagTree.descendantIds("2", children))
        assertEquals(emptySet<String>(), TagTree.descendantIds("3", children))
        assertEquals(emptySet<String>(), TagTree.descendantIds("no-such-tag", children))
    }

    @Test
    fun `descendants of a looping tag terminate, and exclude the tag itself`() {
        val a = tag("a", "אלף", parentId = "b")
        val b = tag("b", "בית", parentId = "a")
        val children = TagTree.childrenByParent(listOf(a, b))
        assertEquals(setOf("b"), TagTree.descendantIds("a", children))
        assertEquals(setOf("a"), TagTree.descendantIds("b", children))
    }

    @Test
    fun `a tag may not be moved under itself or under its own descendants`() {
        val possible = TagTree.possibleParentIds("1", tags)
        assertEquals("only the unrelated tag is left", setOf("4"), possible)
        assertEquals(setOf("1", "4"), TagTree.possibleParentIds("2", tags))
        assertEquals(setOf("1", "2", "4"), TagTree.possibleParentIds("3", tags))
    }

    @Test
    fun `a collapsed parent hides everything under it`() {
        val collapsed = setOf("1")
        assertFalse("the collapsed tag itself stays visible", TagTree.isHiddenByCollapse(work, byId, collapsed))
        assertTrue(TagTree.isHiddenByCollapse(trips, byId, collapsed))
        assertTrue("a grandchild is hidden too", TagTree.isHiddenByCollapse(athens, byId, collapsed))
        assertFalse(TagTree.isHiddenByCollapse(shopping, byId, collapsed))
    }

    @Test
    fun `collapsing nothing hides nothing`() {
        for (t in tags) {
            assertFalse(TagTree.isHiddenByCollapse(t, byId, emptySet()))
        }
    }

    @Test
    fun `the collapse check terminates on a loop`() {
        val a = tag("a", "אלף", parentId = "b")
        val b = tag("b", "בית", parentId = "a")
        val map = listOf(a, b).associateBy { it.id }
        assertTrue(TagTree.isHiddenByCollapse(a, map, setOf("b")))
        assertFalse(TagTree.isHiddenByCollapse(a, map, setOf("c")))
    }

    @Test
    fun `an empty hierarchy is not a special case`() {
        assertEquals(emptyMap<String, Set<String>>(), TagTree.childrenByParent(emptyList()))
        assertEquals(emptySet<String>(), TagTree.possibleParentIds("1", emptyList()))
    }

    /** The four hidden tags the core makes on every device, as the phone reads them. */
    private val system = tag(TagTree.SYSTEM_TAG_ID, "_system")
    private val marked = tag("a1b2c3d4000050008000000000000002", "_marked", parentId = TagTree.SYSTEM_TAG_ID)
    private val nonsynced = tag("a1b2c3d4000050008000000000000003", "_nonsynced", parentId = TagTree.SYSTEM_TAG_ID)
    private val tooBig = tag("a1b2c3d4000050008000000000000004", "_too-big", parentId = nonsynced.id)

    @Test
    fun `the hidden tags are left out however deep, and a tag of the user's is shown whatever its name`() {
        val underTooBig = tag("5", "עוד פנימה", parentId = tooBig.id)
        val mine = tag("6", "_שלי")
        val all = listOf(system, marked, work, nonsynced, tooBig, underTooBig, trips, mine)
        assertEquals(listOf(work, trips, mine), TagTree.withoutSystemTags(all))
    }

    @Test
    fun `a note's own tags lose the hidden ones even without their parents in the list`() {
        assertEquals(listOf(athens), TagTree.withoutSystemTags(listOf(marked, athens, tooBig)))
    }
}
