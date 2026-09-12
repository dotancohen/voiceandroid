package com.dotancohen.voiceandroid.transcription

import com.dotancohen.voiceandroid.ui.components.shortLanguageTitle
import com.dotancohen.voiceandroid.ui.screens.jobLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The transcription queue as the user reads it: what each waiting or running
 * job says about itself, and the language names on the buttons.
 *
 * A transcription of an hour of Hebrew takes many minutes on a phone, so the
 * one line each job shows is the only sign that the phone is working rather
 * than stuck.
 */
class TranscriptionQueueTest {

    private fun job(
        stage: TranscriptionStage,
        message: String = "",
        modelId: String = "ggml-large-v3-turbo-q5_0",
        language: String = "he",
    ) = TranscriptionJob(
        audioFileId = "a1",
        filename = "הקלטה.opus",
        modelId = modelId,
        language = language,
        beamSize = 5,
        stage = stage,
        message = message,
    )

    @Test
    fun `a waiting job says what it is waiting to do`() {
        val line = jobLine(job(TranscriptionStage.Queued))
        assertTrue(line, line.startsWith("Waiting"))
        assertTrue("the model must be named", line.contains("ggml-large-v3-turbo-q5_0"))
        assertTrue("the language must be named", line.contains("he"))
    }

    @Test
    fun `a running job says which stage it has reached`() {
        for (stage in listOf(
            TranscriptionStage.Converting,
            TranscriptionStage.LoadingModel,
            TranscriptionStage.Transcribing,
        )) {
            val line = jobLine(job(stage, message = "Converting the audio"))
            assertTrue(line, line.startsWith("Converting the audio"))
            assertTrue(line.contains("ggml-large-v3-turbo-q5_0"))
        }
    }

    @Test
    fun `a running job with nothing to say still says it is working`() {
        val line = jobLine(job(TranscriptionStage.Transcribing))
        assertTrue(line, line.startsWith("Working"))
    }

    @Test
    fun `a finished job says so, with what came of it`() {
        assertEquals("Finished · 1 min 20 s", jobLine(job(TranscriptionStage.Done, "1 min 20 s")))
    }

    @Test
    fun `a failed job shows the reason, which is what the user needs`() {
        assertEquals(
            "Failed · The model file is missing",
            jobLine(job(TranscriptionStage.Failed, "The model file is missing"))
        )
    }

    @Test
    fun `a stopped job says only that, without a stale message`() {
        assertEquals("Stopped", jobLine(job(TranscriptionStage.Stopped, "Transcribing 40%")))
    }

    @Test
    fun `every stage produces a line, so no job is ever blank`() {
        for (stage in TranscriptionStage.entries) {
            assertTrue("$stage produced nothing", jobLine(job(stage)).isNotBlank())
        }
    }

    @Test
    fun `the stages before Done are the ones the progress bar is shown for`() {
        // The screen tests `stage < Done`, so the order of this enum is not
        // decoration: an end state sorted before Done would show a finished
        // job with a progress bar running for ever.
        for (working in listOf(
            TranscriptionStage.Queued,
            TranscriptionStage.Converting,
            TranscriptionStage.LoadingModel,
            TranscriptionStage.Transcribing,
        )) {
            assertTrue("$working must sort before Done", working < TranscriptionStage.Done)
        }
        for (ended in listOf(
            TranscriptionStage.Done,
            TranscriptionStage.Failed,
            TranscriptionStage.Stopped,
        )) {
            assertTrue("$ended must not sort before Done", ended >= TranscriptionStage.Done)
        }
    }

    @Test
    fun `a language button shows the name in that language`() {
        assertEquals("עברית", shortLanguageTitle("Hebrew (עברית)"))
        assertEquals("English", shortLanguageTitle("English (English)"))
        assertEquals("العربية", shortLanguageTitle("Arabic (العربية)"))
    }

    @Test
    fun `a language with no name of its own keeps the one it has`() {
        assertEquals("Detect automatically", shortLanguageTitle("Detect automatically"))
        assertEquals("Klingon", shortLanguageTitle("Klingon"))
    }

    @Test
    fun `an empty bracket is not shown as an empty button`() {
        assertEquals("Hebrew ()", shortLanguageTitle("Hebrew ()"))
    }

    @Test
    fun `every language in the catalogue gives a button label`() {
        for ((code, title) in TranscriptionPreferences.LANGUAGE_CATALOGUE) {
            assertTrue("$code has a blank button", shortLanguageTitle(title).isNotBlank())
        }
    }

    @Test
    fun `the elapsed time is counted in minutes once it passes one`() {
        assertEquals("0 s", OnDeviceTranscriber.formatElapsed(0.0))
        assertEquals("7 s", OnDeviceTranscriber.formatElapsed(7.4))
        assertEquals("59 s", OnDeviceTranscriber.formatElapsed(59.9))
        assertEquals("1 min 0 s", OnDeviceTranscriber.formatElapsed(60.0))
        assertEquals("1 min 20 s", OnDeviceTranscriber.formatElapsed(80.2))
        assertEquals("41 min 40 s", OnDeviceTranscriber.formatElapsed(2500.0))
    }
}
