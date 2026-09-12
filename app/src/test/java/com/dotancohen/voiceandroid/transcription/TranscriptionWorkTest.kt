package com.dotancohen.voiceandroid.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a transcription cost, and how long the ones still waiting will take.
 *
 * This is the arithmetic behind the question the queue exists to answer — "how
 * long until the one I need?" — so it has to be right, and it has to refuse to
 * answer when it cannot: a made-up estimate is worse than none, because the user
 * plans around it.
 */
class TranscriptionWorkTest {

    private fun response(
        audio: Double? = 120.0,
        total: Double? = 300.0,
        elapsed: Double? = null,
        cpu: Double? = 1100.0,
        cores: Double? = 3.7,
        memory: Long? = 1_400_000_000L,
        speed: Double? = 0.4,
        model: String? = "small-q5_1",
    ): String {
        val performance = buildString {
            append("{")
            val parts = mutableListOf<String>()
            total?.let { parts += "\"total_seconds\":$it" }
            elapsed?.let { parts += "\"elapsed_seconds\":$it" }
            cpu?.let { parts += "\"cpu_seconds\":$it" }
            cores?.let { parts += "\"cpu_cores_busy\":$it" }
            memory?.let { parts += "\"peak_native_heap_bytes\":$it" }
            speed?.let { parts += "\"speed_vs_realtime\":$it" }
            model?.let { parts += "\"model\":\"$it\"" }
            append(parts.joinToString(","))
            append("}")
        }
        val outer = mutableListOf<String>()
        audio?.let { outer += "\"duration_seconds\":$it" }
        outer += "\"performance\":$performance"
        return "{" + outer.joinToString(",") + "}"
    }

    // ------------------------------------------------------- reading it back

    @Test
    fun `every measure is read from the service response`() {
        val work = TranscriptionWork.of(response())
        assertEquals(120.0, work.audioSeconds!!, 0.001)
        assertEquals(300.0, work.clockSeconds!!, 0.001)
        assertEquals(1100.0, work.cpuSeconds!!, 0.001)
        assertEquals(3.7, work.coresBusy!!, 0.001)
        assertEquals(1_400_000_000L, work.peakMemoryBytes)
        assertEquals(0.4, work.speedVsRealtime!!, 0.001)
        assertEquals("small-q5_1", work.model)
    }

    @Test
    fun `the whole job is the clock time, not the transcribing call`() {
        // The user waits for the conversion and the model loading as well.
        val work = TranscriptionWork.of(response(total = 300.0, elapsed = 240.0))
        assertEquals(300.0, work.clockSeconds!!, 0.001)
    }

    @Test
    fun `an older row that recorded only the elapsed time is still read`() {
        val work = TranscriptionWork.of("""{"duration_seconds":60,"elapsed_time":90.5}""")
        assertEquals(90.5, work.clockSeconds!!, 0.001)
        assertEquals(60.0, work.audioSeconds!!, 0.001)
    }

    @Test
    fun `nothing recorded reads as nothing known`() {
        for (nothing in listOf(null, "", "not json at all", "{}")) {
            val work = TranscriptionWork.of(nothing)
            assertNull("$nothing gave a clock time", work.clockSeconds)
            assertNull("$nothing gave a length", work.audioSeconds)
            assertNull("$nothing gave a rate", work.secondsPerAudioSecond)
        }
    }

    @Test
    fun `the rate is work per second of audio`() {
        val work = TranscriptionWork.of(response(audio = 120.0, total = 300.0))
        assertEquals(2.5, work.secondsPerAudioSecond!!, 0.001)
    }

    @Test
    fun `a recording of no length gives no rate`() {
        assertNull(TranscriptionWork.of(response(audio = 0.0)).secondsPerAudioSecond)
    }

    // ----------------------------------------------------------- the rate

    @Test
    fun `the rate is the median of what this phone did`() {
        val finished = listOf(
            TranscriptionWork(audioSeconds = 100.0, clockSeconds = 100.0),  // 1×
            TranscriptionWork(audioSeconds = 100.0, clockSeconds = 200.0),  // 2×
            TranscriptionWork(audioSeconds = 100.0, clockSeconds = 900.0),  // 9×, the phone was busy
        )
        assertEquals(2.0, QueueEstimate.rateFrom(finished)!!, 0.001)
    }

    @Test
    fun `readings for the model being used are preferred`() {
        val finished = listOf(
            TranscriptionWork(audioSeconds = 100.0, clockSeconds = 100.0, model = "tiny"),
            TranscriptionWork(audioSeconds = 100.0, clockSeconds = 600.0, model = "large-v3"),
        )
        assertEquals(6.0, QueueEstimate.rateFrom(finished, "large-v3")!!, 0.001)
        assertEquals(1.0, QueueEstimate.rateFrom(finished, "tiny")!!, 0.001)
    }

    @Test
    fun `a model never used here falls back to every reading`() {
        val finished = listOf(
            TranscriptionWork(audioSeconds = 100.0, clockSeconds = 200.0, model = "tiny"),
        )
        assertEquals(2.0, QueueEstimate.rateFrom(finished, "medium")!!, 0.001)
    }

    @Test
    fun `nothing finished yet gives no rate`() {
        assertNull(QueueEstimate.rateFrom(emptyList()))
        assertNull(QueueEstimate.rateFrom(listOf(TranscriptionWork())))
    }

    // ---------------------------------------------------------- the wait

    @Test
    fun `the wait counts everything in front and the recording itself`() {
        // Eight minutes of audio ahead, two minutes of its own, at 2.5× each
        val wait = QueueEstimate.waitSeconds(480.0, 120.0, 2.5)
        assertEquals(1500.0, wait!!, 0.001)
    }

    @Test
    fun `no rate, no length, no estimate`() {
        assertNull("without a rate", QueueEstimate.waitSeconds(480.0, 120.0, null))
        assertNull("without its own length", QueueEstimate.waitSeconds(480.0, null, 2.5))
        assertNull("without what is ahead", QueueEstimate.waitSeconds(null, 120.0, 2.5))
        assertNull("a rate of zero says nothing", QueueEstimate.waitSeconds(480.0, 120.0, 0.0))
    }

    @Test
    fun `first in the queue waits only for itself`() {
        assertEquals(300.0, QueueEstimate.waitSeconds(0.0, 120.0, 2.5)!!, 0.001)
    }

    @Test
    fun `a wait is said in units a person uses`() {
        assertEquals("about a minute", QueueEstimate.inWords(45.0))
        assertEquals("about a minute", QueueEstimate.inWords(89.0))
        assertEquals("about 2 minutes", QueueEstimate.inWords(100.0))
        assertEquals("about 25 minutes", QueueEstimate.inWords(1500.0))
        assertEquals("about 1 hour", QueueEstimate.inWords(3600.0))
        assertEquals("about 1 hour 10 minutes", QueueEstimate.inWords(4200.0))
        assertEquals("about 2 hours 30 minutes", QueueEstimate.inWords(9000.0))
        assertNull(QueueEstimate.inWords(null))
        assertNull(QueueEstimate.inWords(0.0))
    }
}
