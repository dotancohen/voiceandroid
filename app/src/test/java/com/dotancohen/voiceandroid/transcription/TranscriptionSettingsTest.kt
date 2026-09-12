package com.dotancohen.voiceandroid.transcription

import com.dotancohen.voiceandroid.testing.FakePreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transcription settings held on the phone: the model, the language and
 * how hard the model works, and which languages are offered where.
 *
 * The language states decide what the Transcribe dialog looks like, so a
 * wrong answer here is a button that is missing or a language that cannot be
 * chosen at all.
 */
class TranscriptionSettingsTest {

    private fun settings(vararg values: Pair<String, Any>) =
        TranscriptionPreferences(FakePreferences(values.toMap()))

    // Defaults

    @Test
    fun `the defaults are a model that exists and a language that is offered`() {
        val prefs = settings()
        assertEquals(WhisperModels.DEFAULT_MODEL_ID, prefs.modelId)
        assertTrue(
            "the default model must be in the catalogue",
            WhisperModels.byId(prefs.modelId) != null
        )
        assertEquals("he", prefs.language)
        assertEquals(
            "the default language must be one the user is offered",
            TranscriptionPreferences.USE_BUTTON,
            prefs.languageUse(prefs.language)
        )
    }

    @Test
    fun `beam search is the default, because accuracy matters more than speed here`() {
        assertEquals(5, settings().beamSize)
    }

    // Values that cannot be right

    @Test
    fun `the beam width is held between one and eight`() {
        val prefs = settings()
        prefs.beamSize = 0
        assertEquals("a beam of zero decodes nothing", 1, prefs.beamSize)
        prefs.beamSize = -4
        assertEquals(1, prefs.beamSize)
        prefs.beamSize = 100
        assertEquals("a beam of a hundred would never finish", 8, prefs.beamSize)
    }

    @Test
    fun `a stored beam width from another version is brought into range`() {
        assertEquals(8, settings("transcription_beam_size" to 64).beamSize)
        assertEquals(1, settings("transcription_beam_size" to 0).beamSize)
    }

    @Test
    fun `a language state nobody has heard of is treated as not used`() {
        val prefs = settings("transcription_language_use_ja" to "sometimes")
        assertEquals(TranscriptionPreferences.USE_OFF, prefs.languageUse("ja"))
        prefs.setLanguageUse("ja", "sometimes")
        assertEquals(TranscriptionPreferences.USE_OFF, prefs.languageUse("ja"))
    }

    // The three states

    @Test
    fun `a language starts where the defaults put it, and moves when told`() {
        val prefs = settings()
        assertEquals(TranscriptionPreferences.USE_BUTTON, prefs.languageUse("en"))
        assertEquals(TranscriptionPreferences.USE_AVAILABLE, prefs.languageUse("ar"))
        assertEquals(TranscriptionPreferences.USE_OFF, prefs.languageUse("ja"))

        prefs.setLanguageUse("ja", TranscriptionPreferences.USE_BUTTON)
        assertEquals(TranscriptionPreferences.USE_BUTTON, prefs.languageUse("ja"))
        prefs.setLanguageUse("en", TranscriptionPreferences.USE_OFF)
        assertEquals(TranscriptionPreferences.USE_OFF, prefs.languageUse("en"))
    }

    @Test
    fun `the buttons are the languages the user put on buttons, in catalogue order`() {
        val prefs = settings()
        val buttons = prefs.buttonLanguages().map { it.first }

        assertEquals(listOf(TranscriptionPreferences.LANGUAGE_AUTO, "he", "en"), buttons)
    }

    @Test
    fun `switching a language off takes its button away`() {
        val prefs = settings()
        prefs.setLanguageUse("en", TranscriptionPreferences.USE_OFF)

        assertFalse("en" in prefs.buttonLanguages().map { it.first })
    }

    @Test
    fun `the list holds everything not switched off`() {
        val prefs = settings()
        val listed = prefs.selectableLanguages().map { it.first }

        assertTrue("a button language is also in the list", "he" in listed)
        assertTrue("ar" in listed)
        assertFalse("ja" in listed)
    }

    @Test
    fun `the language in use is always in the list, even after being switched off`() {
        // Otherwise the box would show a language it does not contain, and
        // the user could not see what is about to be used.
        val prefs = settings()
        prefs.language = "ar"
        prefs.setLanguageUse("ar", TranscriptionPreferences.USE_OFF)

        assertTrue("ar" in prefs.selectableLanguages().map { it.first })
    }

    @Test
    fun `turning everything off still leaves the language in use choosable`() {
        val prefs = settings()
        for ((code, _) in TranscriptionPreferences.LANGUAGE_CATALOGUE) {
            prefs.setLanguageUse(code, TranscriptionPreferences.USE_OFF)
        }

        assertTrue(prefs.buttonLanguages().isEmpty())
        assertEquals(listOf(prefs.language), prefs.selectableLanguages().map { it.first })
    }

    @Test
    fun `a language put on a button is offered in both places`() {
        val prefs = settings()
        prefs.setLanguageUse("el", TranscriptionPreferences.USE_BUTTON)

        assertTrue("el" in prefs.buttonLanguages().map { it.first })
        assertTrue("el" in prefs.selectableLanguages().map { it.first })
    }

    @Test
    fun `the model and language chosen are remembered`() {
        val prefs = settings()
        prefs.modelId = "medium-q5_0"
        prefs.language = "ru"
        assertEquals("medium-q5_0", prefs.modelId)
        assertEquals("ru", prefs.language)
    }
}
