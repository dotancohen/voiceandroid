package com.dotancohen.voiceandroid.data

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * What another application's share menu hands Voice: text becomes a note, an
 * audio file a note holding it, anything else is not taken. Runs on the JVM with
 * Robolectric, which supplies real Intent and Uri objects.
 */
@RunWith(RobolectricTestRunner::class)
class SharedContentTest {

    private fun share(type: String?, text: String? = null, subject: String? = null, stream: Uri? = null) =
        Intent(Intent.ACTION_SEND).apply {
            type?.let { setType(it) }
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
            stream?.let { putExtra(Intent.EXTRA_STREAM, it) }
        }

    @Test
    fun sharedTextBecomesTheNoteText() {
        assertEquals(SharedContent.Text("לקנות חלב ולחם מחר בבוקר"), SharedContent.from(share("text/plain", text = "  לקנות חלב ולחם מחר בבוקר \n")))
    }

    @Test
    fun aSubjectGoesFirstOnALineOfItsOwn() {
        assertEquals(
            SharedContent.Text("מתכון לעוגת גבינה\n\nhttps://example.com/עוגה"),
            SharedContent.from(share("text/plain", text = "https://example.com/עוגה", subject = "מתכון לעוגת גבינה"))
        )
    }

    @Test
    fun aSubjectTheTextAlreadyStartsWithIsNotRepeated() {
        assertEquals("פגישת צוות ביום שני\nבשעה עשר", SharedContent.noteText("פגישת צוות ביום שני\nבשעה עשר", "פגישת צוות ביום שני"))
    }

    @Test
    fun aSubjectAloneIsTheNoteAndNothingAtAllIsNotANote() {
        assertEquals("רק כותרת", SharedContent.noteText("   ", "רק כותרת"))
        assertNull(SharedContent.noteText(null, null))
        assertNull(SharedContent.from(share("text/plain", text = "  ")))
    }

    @Test
    fun aSharedAudioFileIsTakenWithItsUriAndType() {
        val uri = Uri.parse("content://com.whatsapp.provider.media/item/הקלטה%20קולית.opus")
        assertEquals(SharedContent.Audio(uri, "audio/ogg"), SharedContent.from(share("audio/ogg", stream = uri)))
    }

    @Test
    fun anythingElseIsNotTaken() {
        val uri = Uri.parse("content://media/external/images/media/42")
        assertNull("an image is not taken yet", SharedContent.from(share("image/jpeg", stream = uri)))
        assertNull("a text file is a stream, not text", SharedContent.from(share("text/plain", stream = uri)))
        assertNull("no type", SharedContent.from(share(null, text = "טקסט")))
        assertNull("not a share", SharedContent.from(Intent(Intent.ACTION_VIEW).apply { putExtra(Intent.EXTRA_TEXT, "טקסט") }))
        assertNull(SharedContent.from(null))
    }

    @Test
    fun aSharedFileKeepsItsNameAndGainsTheExtensionOfItsTypeOnlyWhenItHasNone() {
        val extensionOf = { mime: String -> mapOf("audio/ogg" to "ogg", "audio/mpeg" to "mp3")[mime] }
        assertEquals("הקלטה מהשיעור.m4a", SharedContent.fileName("הקלטה מהשיעור.m4a", "audio/ogg", extensionOf))
        assertEquals("הודעה קולית.ogg", SharedContent.fileName("הודעה קולית", "audio/ogg", extensionOf))
        assertEquals("shared.mp3", SharedContent.fileName(null, "audio/mpeg", extensionOf))
        assertEquals("shared", SharedContent.fileName("  ", "audio/x-unknown", extensionOf))
    }
}
