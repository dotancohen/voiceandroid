package com.dotancohen.voiceandroid.viewmodel

import com.dotancohen.voiceandroid.data.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The collapsible tag tree on the Filter screen, and the query the buttons
 * there build.
 */
class FilterTagTreeTest {

    private fun tag(id: String, name: String, parentId: String? = null) =
        Tag(id = id, name = name, parentId = parentId)

    private val work = tag("1", "עבודה")
    private val trips = tag("2", "נסיעות", parentId = "1")
    private val athens = tag("3", "אתונה", parentId = "2")
    private val shopping = tag("4", "קניות")

    @Test
    fun `only the tags with no parent are roots`() {
        val roots = buildTagTree(listOf(work, trips, athens, shopping))
        assertEquals(listOf("1", "4"), roots.map { it.tag.id })
        assertEquals(listOf("2"), roots[0].children.map { it.tag.id })
        assertEquals(listOf("3"), roots[0].children[0].children.map { it.tag.id })
    }

    @Test
    fun `siblings are sorted by name, ignoring case`() {
        val roots = buildTagTree(listOf(tag("1", "banana"), tag("2", "Apple"), tag("3", "apricot")))
        assertEquals(listOf("Apple", "apricot", "banana"), roots.map { it.tag.name })
    }

    @Test
    fun `a tag whose parent is not in the list is shown at the top`() {
        val roots = buildTagTree(listOf(tag("9", "יתום", parentId = "gone")))
        assertEquals(listOf("9"), roots.map { it.tag.id })
    }

    @Test
    fun `tags caught in a loop are still shown, rather than disappearing`() {
        // Two phones, both offline, each moved one of these under the other.
        val a = tag("a", "אלף", parentId = "b")
        val b = tag("b", "בית", parentId = "a")
        val roots = buildTagTree(listOf(a, b, shopping))
        val shown = mutableSetOf<String>()
        fun walk(nodes: List<TagTreeNode>) {
            for (node in nodes) {
                assertTrue("${node.tag.id} listed twice", shown.add(node.tag.id))
                walk(node.children)
            }
        }
        walk(roots)
        assertEquals(setOf("a", "b", "4"), shown)
    }

    @Test
    fun `a tag that is its own parent is shown at the top`() {
        val roots = buildTagTree(listOf(tag("s", "עצמי", parentId = "s")))
        assertEquals(listOf("s"), roots.map { it.tag.id })
        assertTrue(roots[0].children.isEmpty())
    }

    @Test
    fun `a collapsed tree shows only its roots`() {
        val roots = buildTagTree(listOf(work, trips, athens, shopping))
        val rows = flattenVisibleTags(roots, emptySet())
        assertEquals(listOf("1", "4"), rows.map { it.tag.id })
        assertEquals(listOf(0, 0), rows.map { it.depth })
    }

    @Test
    fun `expanding a tag shows its children, one level at a time`() {
        val roots = buildTagTree(listOf(work, trips, athens, shopping))
        val oneLevel = flattenVisibleTags(roots, setOf("1"))
        assertEquals(listOf("1", "2", "4"), oneLevel.map { it.tag.id })
        assertEquals(listOf(0, 1, 0), oneLevel.map { it.depth })

        val twoLevels = flattenVisibleTags(roots, setOf("1", "2"))
        assertEquals(listOf("1", "2", "3", "4"), twoLevels.map { it.tag.id })
        assertEquals(listOf(0, 1, 2, 0), twoLevels.map { it.depth })
    }

    @Test
    fun `expanding a tag whose parent is collapsed shows nothing extra`() {
        val roots = buildTagTree(listOf(work, trips, athens, shopping))
        assertEquals(listOf("1", "4"), flattenVisibleTags(roots, setOf("2")).map { it.tag.id })
    }

    @Test
    fun `a tag added to an empty query is the whole query`() {
        assertEquals("tag:עבודה", queryWithTag("", "עבודה"))
        assertEquals("tag:עבודה", queryWithTag("   ", "עבודה"))
    }

    @Test
    fun `a tag is appended to what is already searched for`() {
        assertEquals("פגישה tag:עבודה", queryWithTag("פגישה", "עבודה"))
        assertEquals("tag:קניות tag:עבודה", queryWithTag("tag:קניות", "עבודה"))
    }

    @Test
    fun `the same tag is not added twice`() {
        assertEquals("tag:עבודה", queryWithTag("tag:עבודה", "עבודה"))
        assertEquals("tag:Work meeting", queryWithTag("tag:Work meeting", "Work"))
        assertEquals("the query keeps the case the user typed", "tag:work", queryWithTag("tag:work", "Work"))
    }

    @Test
    fun `a tag whose name begins another tag's name is still added`() {
        // "tag:Workshop" in the query does not mean "tag:Work" is there.
        assertEquals("tag:Workshop tag:Work", queryWithTag("tag:Workshop", "Work"))
    }
}

/**
 * The star button over the notes list, which switches the search between all
 * the notes and the starred ones. It edits the query the user can also type
 * by hand, so it must leave the rest of that query exactly as it was.
 */
class MarkedFilterTest {

    @Test
    fun `the star is recognised in a query`() {
        assertTrue(queryHasMarkedFilter("is:marked"))
        assertTrue(queryHasMarkedFilter("פגישה is:marked"))
        assertTrue("typed in capitals, as a keyboard may offer", queryHasMarkedFilter("IS:MARKED"))
        assertTrue(queryHasMarkedFilter("tag:עבודה is:marked פגישה"))
    }

    @Test
    fun `a query without the star is not mistaken for one with it`() {
        assertFalse(queryHasMarkedFilter(""))
        assertFalse(queryHasMarkedFilter("פגישה"))
        assertFalse(queryHasMarkedFilter("marked"))
        assertFalse("a word that merely contains it", queryHasMarkedFilter("is:markedly"))
    }

    @Test
    fun `switching the star on puts it in front of what was typed`() {
        assertEquals("is:marked", queryWithMarkedFilter("", true))
        assertEquals("is:marked פגישה", queryWithMarkedFilter("פגישה", true))
        assertEquals("is:marked tag:עבודה", queryWithMarkedFilter("tag:עבודה", true))
    }

    @Test
    fun `switching the star off leaves the rest of the query alone`() {
        assertEquals("פגישה", queryWithMarkedFilter("is:marked פגישה", false))
        assertEquals("tag:עבודה פגישה", queryWithMarkedFilter("tag:עבודה is:marked פגישה", false))
        assertEquals("", queryWithMarkedFilter("is:marked", false))
    }

    @Test
    fun `switching it off does not leave a double space behind`() {
        val off = queryWithMarkedFilter("tag:עבודה is:marked פגישה", false)
        assertFalse(off, off.contains("  "))
        assertEquals(off.trim(), off)
    }

    @Test
    fun `switching it on twice does not add it twice`() {
        val once = queryWithMarkedFilter("פגישה", true)
        assertEquals(once, queryWithMarkedFilter(once, true))
    }

    @Test
    fun `the two functions agree with each other`() {
        for (query in listOf("", "פגישה", "is:marked", "is:marked פגישה", "tag:עבודה")) {
            assertTrue(queryHasMarkedFilter(queryWithMarkedFilter(query, true)))
            assertFalse(queryHasMarkedFilter(queryWithMarkedFilter(query, false)))
        }
    }

    @Test
    fun `a query that says it twice is cleared in one press`() {
        // Nothing stops the user typing it twice by hand.
        assertEquals("פגישה", queryWithMarkedFilter("is:marked is:marked פגישה", false))
    }
}
