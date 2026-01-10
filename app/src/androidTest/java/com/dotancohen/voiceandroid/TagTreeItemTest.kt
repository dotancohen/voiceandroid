package com.dotancohen.voiceandroid

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.dotancohen.voiceandroid.data.Tag
import com.dotancohen.voiceandroid.ui.components.TagTreeItem
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented UI tests for TagTreeItem expand/collapse functionality.
 *
 * These tests run on an Android device/emulator and verify that the
 * expand/collapse arrows in the tag tree work correctly.
 *
 * To run: ./gradlew connectedAndroidTest --tests "*.TagTreeItemTest"
 */
@RunWith(AndroidJUnit4::class)
class TagTreeItemTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private val testTag = Tag(
        id = "test-tag-001",
        name = "ParentTag",
        parentId = null,
        createdAt = "2025-01-01 12:00:00",
        modifiedAt = null
    )

    /**
     * Test that clicking the expand arrow triggers the onToggleExpand callback.
     *
     * This test verifies the fix for the bug where the expand/collapse arrows
     * didn't work because clicks were being captured by the parent Surface's
     * combinedClickable modifier instead of the IconButton.
     */
    @Test
    fun testExpandArrowClick_triggersOnToggleExpand() {
        var toggleExpandClicked = false
        var onClickClicked = false

        composeTestRule.setContent {
            TagTreeItem(
                tag = testTag,
                depth = 0,
                hasChildren = true,
                isExpanded = false,
                onClick = { onClickClicked = true },
                onLongClick = { },
                onToggleExpand = { toggleExpandClicked = true }
            )
        }

        // Click on the expand arrow (has content description "Expand")
        composeTestRule.onNodeWithContentDescription("Expand").performClick()

        // Verify that onToggleExpand was called, NOT onClick
        assertTrue("onToggleExpand should be called when clicking expand arrow", toggleExpandClicked)
        assertFalse("onClick should NOT be called when clicking expand arrow", onClickClicked)
    }

    /**
     * Test that clicking the collapse arrow (when expanded) triggers onToggleExpand.
     */
    @Test
    fun testCollapseArrowClick_triggersOnToggleExpand() {
        var toggleExpandClicked = false
        var onClickClicked = false

        composeTestRule.setContent {
            TagTreeItem(
                tag = testTag,
                depth = 0,
                hasChildren = true,
                isExpanded = true,  // Already expanded
                onClick = { onClickClicked = true },
                onLongClick = { },
                onToggleExpand = { toggleExpandClicked = true }
            )
        }

        // Click on the collapse arrow (has content description "Collapse")
        composeTestRule.onNodeWithContentDescription("Collapse").performClick()

        // Verify that onToggleExpand was called, NOT onClick
        assertTrue("onToggleExpand should be called when clicking collapse arrow", toggleExpandClicked)
        assertFalse("onClick should NOT be called when clicking collapse arrow", onClickClicked)
    }

    /**
     * Test that clicking the tag name triggers onClick, not onToggleExpand.
     */
    @Test
    fun testTagNameClick_triggersOnClick() {
        var toggleExpandClicked = false
        var onClickClicked = false

        composeTestRule.setContent {
            TagTreeItem(
                tag = testTag,
                depth = 0,
                hasChildren = true,
                isExpanded = false,
                onClick = { onClickClicked = true },
                onLongClick = { },
                onToggleExpand = { toggleExpandClicked = true }
            )
        }

        // Click on the tag name
        composeTestRule.onNodeWithText("ParentTag").performClick()

        // Verify that onClick was called, NOT onToggleExpand
        assertTrue("onClick should be called when clicking tag name", onClickClicked)
        assertFalse("onToggleExpand should NOT be called when clicking tag name", toggleExpandClicked)
    }

    /**
     * Test that the expand/collapse arrow icon changes based on isExpanded state.
     */
    @Test
    fun testExpandCollapseIconChanges() {
        var isExpanded by mutableStateOf(false)

        composeTestRule.setContent {
            TagTreeItem(
                tag = testTag,
                depth = 0,
                hasChildren = true,
                isExpanded = isExpanded,
                onClick = { },
                onLongClick = { },
                onToggleExpand = { isExpanded = !isExpanded }
            )
        }

        // Initially shows "Expand" arrow
        composeTestRule.onNodeWithContentDescription("Expand").assertIsDisplayed()

        // Click to expand
        composeTestRule.onNodeWithContentDescription("Expand").performClick()

        // Now shows "Collapse" arrow
        composeTestRule.onNodeWithContentDescription("Collapse").assertIsDisplayed()

        // Click to collapse
        composeTestRule.onNodeWithContentDescription("Collapse").performClick()

        // Back to "Expand" arrow
        composeTestRule.onNodeWithContentDescription("Expand").assertIsDisplayed()
    }

    /**
     * Test that tags without children don't show expand/collapse arrows.
     */
    @Test
    fun testNoExpandArrowForLeafTags() {
        composeTestRule.setContent {
            TagTreeItem(
                tag = testTag,
                depth = 0,
                hasChildren = false,  // No children
                isExpanded = false,
                onClick = { },
                onLongClick = { },
                onToggleExpand = { }
            )
        }

        // Should not find expand or collapse buttons
        composeTestRule.onNodeWithContentDescription("Expand").assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Collapse").assertDoesNotExist()
    }

    /**
     * Test nested tags with different depths render correctly.
     */
    @Test
    fun testNestedTagDepths() {
        val childTag = Tag(
            id = "test-tag-002",
            name = "ChildTag",
            parentId = "test-tag-001",
            createdAt = "2025-01-01 12:00:00",
            modifiedAt = null
        )

        composeTestRule.setContent {
            TagTreeItem(
                tag = childTag,
                depth = 2,  // Nested depth
                hasChildren = false,
                isExpanded = false,
                onClick = { },
                onLongClick = { },
                onToggleExpand = { }
            )
        }

        // Tag should be displayed (verification that depth doesn't break rendering)
        composeTestRule.onNodeWithText("ChildTag").assertIsDisplayed()
    }
}
