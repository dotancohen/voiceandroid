package com.dotancohen.voiceandroid.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.dotancohen.voiceandroid.data.AudioFile
import com.dotancohen.voiceandroid.data.Note
import com.dotancohen.voiceandroid.ui.screens.NoteCard
import com.dotancohen.voiceandroid.viewmodel.NoteWithAudioFiles
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import uniffi.voicecore.Stamp

/**
 * The chevron on a note's row opens that note's section.
 *
 * This is the test for a defect that shipped: `NoteCard` took the open/closed
 * state from its caller, and only the notes list passed it. Everywhere else that
 * draws the same row — the preview from a held-down Previous or Next, the
 * transcription queue's preview, the trash — the chevron called an empty
 * function, so it drew a control that could not do anything. Three screens were
 * wrong and nothing failed.
 *
 * The rule the card now keeps:
 *
 * - Given no state and no handler, the card holds the state itself, so the
 *   chevron always opens and closes the section.
 * - Given both, the caller decides — which is what the notes list needs, because
 *   only one row may be open at a time and opening one scrolls to it.
 *
 * Runs on the JVM: Robolectric supplies the Android framework and
 * `createComposeRule` hosts the row and presses its chevron, so no phone is
 * needed. See DEVELOPMENT.md, "Testing a screen".
 */
@RunWith(RobolectricTestRunner::class)
class NoteCardChevronTest {

    @get:Rule
    val compose = createComposeRule()

    private val stamp = Stamp(at = 1_757_419_500, offset = 10800, zone = "Asia/Jerusalem")

    private val recording = AudioFile(
        id = "audio-1",
        importedAt = stamp,
        filename = "פגישה-בוקר.opus",
        durationSeconds = 95,
        deviceId = "phone",
    )

    private val row = NoteWithAudioFiles(
        note = Note(id = "note-1", content = "פגישה עם הצוות", createdAt = stamp),
        audioFiles = listOf(recording),
    )

    /** The filename is drawn inside the section, so it says whether it is open. */
    private val insideTheSection = "פגישה-בוקר.opus"

    @Test
    fun `a card left to itself opens when its chevron is pressed`() {
        compose.setContent {
            NoteCard(noteWithAudio = row, getAudioFilePath = { null })
        }

        compose.onNodeWithText(insideTheSection).assertDoesNotExist()

        compose.onNodeWithContentDescription("Open this Note").performClick()

        compose.onNodeWithText(insideTheSection).assertIsDisplayed()
    }

    @Test
    fun `pressing the chevron again closes the section`() {
        compose.setContent {
            NoteCard(noteWithAudio = row, getAudioFilePath = { null })
        }

        compose.onNodeWithContentDescription("Open this Note").performClick()
        compose.onNodeWithText(insideTheSection).assertIsDisplayed()

        compose.onNodeWithContentDescription("Close this Note").performClick()

        compose.onNodeWithText(insideTheSection).assertDoesNotExist()
    }

    @Test
    fun `the recordings mark opens the same one section`() {
        // One section, opened by the chevron or by the recordings mark, so there
        // is never a second panel to close.
        compose.setContent {
            NoteCard(noteWithAudio = row, getAudioFilePath = { null })
        }

        compose.onNodeWithContentDescription("1 recording").performClick()

        compose.onNodeWithText(insideTheSection).assertIsDisplayed()
    }

    @Test
    fun `where the caller holds the state the caller decides`() {
        // What the notes list needs: the press is reported, and nothing opens
        // until the list says so. A card that opened itself here would fight the
        // list, which allows only one open row.
        var presses = 0
        compose.setContent {
            NoteCard(
                noteWithAudio = row,
                getAudioFilePath = { null },
                expanded = false,
                onToggleExpanded = { presses++ },
            )
        }

        compose.onNodeWithContentDescription("Open this Note").performClick()

        assertEquals(1, presses)
        compose.onNodeWithText(insideTheSection).assertDoesNotExist()
    }

    @Test
    fun `a caller that says open draws it open`() {
        compose.setContent {
            NoteCard(
                noteWithAudio = row,
                getAudioFilePath = { null },
                expanded = true,
                onToggleExpanded = {},
            )
        }

        compose.onNodeWithText(insideTheSection).assertIsDisplayed()
        compose.onNodeWithContentDescription("Close this Note").assertIsDisplayed()
    }

    @Test
    fun `the caller can open and close it`() {
        var open by mutableStateOf(false)
        compose.setContent {
            NoteCard(
                noteWithAudio = row,
                getAudioFilePath = { null },
                expanded = open,
                onToggleExpanded = { open = !open },
            )
        }

        compose.onNodeWithContentDescription("Open this Note").performClick()
        compose.onNodeWithText(insideTheSection).assertIsDisplayed()

        compose.onNodeWithContentDescription("Close this Note").performClick()
        compose.onNodeWithText(insideTheSection).assertDoesNotExist()
    }

    @Test
    fun `a note with no recordings still opens`() {
        // The section also holds the Tags and Delete buttons, so it is worth
        // opening even with nothing to play.
        val withoutRecordings = row.copy(audioFiles = emptyList())
        var tagged = false
        compose.setContent {
            NoteCard(
                noteWithAudio = withoutRecordings,
                getAudioFilePath = { null },
                onTagNote = { tagged = true },
            )
        }

        compose.onNodeWithContentDescription("Open this Note").performClick()
        compose.onNodeWithText("Tags").performClick()

        assertEquals(true, tagged)
    }

    @Test
    fun `each card keeps its own state`() {
        // Two rows drawn together: opening one must not open the other.
        val second = row.copy(
            note = Note(id = "note-2", content = "שיחה עם הלקוח", createdAt = stamp),
            audioFiles = listOf(recording.copy(id = "audio-2", filename = "שיחה.opus")),
        )
        compose.setContent {
            androidx.compose.foundation.layout.Column {
                NoteCard(noteWithAudio = row, getAudioFilePath = { null })
                NoteCard(noteWithAudio = second, getAudioFilePath = { null })
            }
        }

        compose.onAllNodesWithContentDescription("Open this Note")[0].performClick()

        compose.onNodeWithText(insideTheSection).assertIsDisplayed()
        compose.onNodeWithText("שיחה.opus").assertDoesNotExist()
    }
}
