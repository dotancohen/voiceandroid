package com.dotancohen.voiceandroid.viewmodel

import com.dotancohen.voiceandroid.data.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tag list as the tag screens show it: full paths, sorted, with the tags
 * that have children marked so they can be collapsed.
 */
class TagPathsTest {

    private fun tag(id: String, name: String, parentId: String? = null) =
        Tag(id = id, name = name, parentId = parentId)

    @Test
    fun `every tag is listed once, by its full path`() {
        val tags = listOf(
            tag("1", "עבודה"),
            tag("2", "נסיעות", parentId = "1"),
            tag("3", "קניות"),
        )
        val paths = tagsWithPaths(tags)
        assertEquals(3, paths.size)
        assertEquals(setOf("עבודה", "עבודה > נסיעות", "קניות"), paths.map { it.path }.toSet())
    }

    @Test
    fun `a child follows its parent, because the sort is by path`() {
        val tags = listOf(
            tag("2", "Alpha", parentId = "1"),
            tag("3", "Beta"),
            tag("1", "Alpha root"),
        )
        assertEquals(
            listOf("Alpha root", "Alpha root > Alpha", "Beta"),
            tagsWithPaths(tags).map { it.path }
        )
    }

    @Test
    fun `the sort ignores case, so Work and work sit together`() {
        val tags = listOf(tag("1", "banana"), tag("2", "Apple"), tag("3", "apricot"))
        assertEquals(listOf("Apple", "apricot", "banana"), tagsWithPaths(tags).map { it.path })
    }

    @Test
    fun `a tag with children is marked as having them`() {
        val tags = listOf(tag("1", "עבודה"), tag("2", "נסיעות", parentId = "1"))
        val byName = tagsWithPaths(tags).associateBy { it.tag.name }
        assertTrue(byName.getValue("עבודה").hasChildren)
        assertFalse(byName.getValue("נסיעות").hasChildren)
    }

    @Test
    fun `depth is carried through for the indentation`() {
        val tags = listOf(
            tag("1", "a"),
            tag("2", "b", parentId = "1"),
            tag("3", "c", parentId = "2"),
        )
        assertEquals(listOf(0, 1, 2), tagsWithPaths(tags).map { it.depth })
    }

    @Test
    fun `a hierarchy with a loop is still listed, rather than hanging`() {
        val tags = listOf(tag("a", "one", parentId = "b"), tag("b", "two", parentId = "a"))
        val paths = tagsWithPaths(tags)
        assertEquals(2, paths.size)
        assertTrue(paths.all { it.hasChildren })
    }

    @Test
    fun `an empty list of tags gives an empty list of paths`() {
        assertEquals(emptyList<Any>(), tagsWithPaths(emptyList()))
    }
}
