package com.dotancohen.voiceandroid.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue of languages a recording can be transcribed in, and what
 * each one is offered as.
 *
 * The four languages that used to be written into the code were one user's;
 * these tests are about the list being a list, with every entry usable and
 * nothing about it decided in advance beyond a starting point.
 */
class TranscriptionLanguagesTest {

    @Test
    fun `the catalogue offers far more than the four that were hard-coded`() {
        assertTrue(
            "a handful of languages is not a catalogue",
            TranscriptionPreferences.LANGUAGE_CATALOGUE.size > 20
        )
    }

    @Test
    fun `automatic detection is one of the choices`() {
        val codes = TranscriptionPreferences.LANGUAGE_CATALOGUE.map { it.first }
        assertTrue(TranscriptionPreferences.LANGUAGE_AUTO in codes)
    }

    @Test
    fun `every language is named once, in English and in itself`() {
        val codes = TranscriptionPreferences.LANGUAGE_CATALOGUE.map { it.first }
        assertEquals("a code appears twice", codes.size, codes.toSet().size)
        for ((code, title) in TranscriptionPreferences.LANGUAGE_CATALOGUE) {
            assertTrue("$code has no name", title.isNotBlank())
        }
    }

    @Test
    fun `the languages the user named are there`() {
        // The ones this user asked for by name, and the ones they said they
        // transcribe sometimes.
        val codes = TranscriptionPreferences.LANGUAGE_CATALOGUE.map { it.first }
        for (code in listOf("he", "en", "ar", "ru", "es", "el")) {
            assertTrue("$code is missing from the catalogue", code in codes)
        }
    }

    @Test
    fun `the defaults put Hebrew, English and detection on buttons`() {
        for (code in listOf("he", "en", TranscriptionPreferences.LANGUAGE_AUTO)) {
            assertEquals(
                TranscriptionPreferences.USE_BUTTON,
                TranscriptionPreferences.DEFAULT_USES[code]
            )
        }
    }

    @Test
    fun `the languages transcribed now and then start in the list`() {
        for (code in listOf("ar", "ru", "es", "el")) {
            assertEquals(
                TranscriptionPreferences.USE_AVAILABLE,
                TranscriptionPreferences.DEFAULT_USES[code]
            )
        }
    }

    @Test
    fun `a language nobody asked for starts switched off`() {
        assertFalse("la" in TranscriptionPreferences.DEFAULT_USES)
        assertFalse("ja" in TranscriptionPreferences.DEFAULT_USES)
    }

    @Test
    fun `every default names a language that exists`() {
        val codes = TranscriptionPreferences.LANGUAGE_CATALOGUE.map { it.first }.toSet()
        for (code in TranscriptionPreferences.DEFAULT_USES.keys) {
            assertTrue("$code is a default but not in the catalogue", code in codes)
        }
    }

    @Test
    fun `every state has a name the user can read`() {
        for (use in TranscriptionPreferences.USES) {
            assertTrue(TranscriptionPreferences.useTitle(use).isNotBlank())
        }
        assertEquals(3, TranscriptionPreferences.USES.size)
    }

    @Test
    fun `a language is named by its code when the catalogue does not know it`() {
        assertEquals("xx", TranscriptionPreferences.languageTitle("xx"))
        assertEquals("English", TranscriptionPreferences.languageTitle("en"))
    }
}
