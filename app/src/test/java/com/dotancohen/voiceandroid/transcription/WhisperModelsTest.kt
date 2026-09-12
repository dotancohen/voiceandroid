package com.dotancohen.voiceandroid.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The catalogue of Whisper models the phone can download.
 *
 * Every entry is a gigabyte or more fetched over somebody's mobile data and
 * kept on a phone, so the list has to be right: a wrong file name downloads
 * twice, a wrong size shows the wrong warning, and a duplicate id makes two
 * models the same model.
 */
class WhisperModelsTest {

    @Test
    fun `there is a catalogue, and the default is in it`() {
        assertTrue(WhisperModels.CATALOGUE.isNotEmpty())
        assertNotNull(
            "the id used when nothing is chosen must exist",
            WhisperModels.byId(WhisperModels.DEFAULT_MODEL_ID)
        )
    }

    @Test
    fun `every model has an id of its own`() {
        val ids = WhisperModels.CATALOGUE.map { it.id }
        assertEquals("two models sharing an id are one model", ids.size, ids.toSet().size)
    }

    @Test
    fun `every model has its own file name`() {
        // Two models writing the same file would overwrite each other on the
        // phone, and the second download would look instant.
        val names = WhisperModels.CATALOGUE.map { it.fileName }
        assertEquals(names.size, names.toSet().size)
    }

    @Test
    fun `the file name is the id, so a file on the phone can be traced back`() {
        for (model in WhisperModels.CATALOGUE) {
            assertEquals("${model.id}.bin", model.fileName)
        }
    }

    @Test
    fun `every model says what it is and where it comes from`() {
        for (model in WhisperModels.CATALOGUE) {
            assertTrue(model.id, model.title.isNotBlank())
            assertTrue(model.id, model.description.isNotBlank())
            assertTrue("${model.id} must be fetched over https", model.url.startsWith("https://"))
        }
    }

    @Test
    fun `every model has a believable size`() {
        for (model in WhisperModels.CATALOGUE) {
            assertTrue("${model.id} has no size", model.sizeBytes > 0)
            assertTrue(
                "${model.id} is larger than any Whisper model ever shipped",
                model.sizeBytes < 8_000_000_000L
            )
            assertTrue(
                "${model.id} is too small to be a Whisper model",
                model.sizeBytes > 50_000_000L
            )
        }
    }

    @Test
    fun `the size is shown in gigabytes, to two figures`() {
        val model = WhisperModels.byId(WhisperModels.DEFAULT_MODEL_ID)!!
        val text = model.sizeText
        assertTrue(text, text.endsWith(" GB"))
        assertTrue(text, Regex("""\d+\.\d{2} GB""").matches(text))
    }

    @Test
    fun `a model that restricts its languages names ones the app can offer`() {
        // A model that says "Hebrew only" must say it with the same code the
        // language list uses, or the Transcribe dialog cannot match them.
        val known = TranscriptionPreferences.LANGUAGE_CATALOGUE.map { it.first }.toSet()
        for (model in WhisperModels.CATALOGUE) {
            for (language in model.languages) {
                assertTrue("${model.id} names a language nobody knows: $language", language in known)
            }
        }
    }

    @Test
    fun `an unknown id is nothing, rather than the first model`() {
        assertNull(WhisperModels.byId("no-such-model"))
        assertNull(WhisperModels.byId(""))
    }

    @Test
    fun `at least one model handles every language, for the ones that are not Hebrew`() {
        assertTrue(
            "with every model restricted, most languages could never be transcribed",
            WhisperModels.CATALOGUE.any { it.languages.isEmpty() }
        )
    }
}

/**
 * Whether a model file on the phone is usable. A model is a gigabyte fetched
 * over a connection that may drop, so "the file is there" is not the same as
 * "the model is there".
 */
class WhisperModelFileTest {

    private val model = WhisperModels.byId(WhisperModels.DEFAULT_MODEL_ID)!!

    private fun fileOfSize(bytes: Long): java.io.File {
        val f = java.io.File.createTempFile("model", ".bin")
        f.deleteOnExit()
        java.io.RandomAccessFile(f, "rw").use { it.setLength(bytes) }
        return f
    }

    @Test
    fun `a file of the right size is the model`() {
        assertTrue(WhisperModels.isComplete(fileOfSize(model.sizeBytes), model))
    }

    @Test
    fun `a download that stopped early is not the model`() {
        assertFalse(WhisperModels.isComplete(fileOfSize(model.sizeBytes - 1), model))
        assertFalse(WhisperModels.isComplete(fileOfSize(0), model))
        assertFalse(WhisperModels.isComplete(fileOfSize(model.sizeBytes / 2), model))
    }

    @Test
    fun `a file longer than the model is not the model either`() {
        assertFalse(WhisperModels.isComplete(fileOfSize(model.sizeBytes + 1), model))
    }

    @Test
    fun `a file that is not there is not the model`() {
        val missing = java.io.File("/no/such/directory/large-v3-q5_0.bin")
        assertFalse(WhisperModels.isComplete(missing, model))
    }

    @Test
    fun `one model's file is not another model's`() {
        val other = WhisperModels.CATALOGUE.first { it.sizeBytes != model.sizeBytes }
        val file = fileOfSize(model.sizeBytes)
        assertTrue(WhisperModels.isComplete(file, model))
        assertFalse(WhisperModels.isComplete(file, other))
    }
}
