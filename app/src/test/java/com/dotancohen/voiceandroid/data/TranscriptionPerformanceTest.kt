package com.dotancohen.voiceandroid.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.voicecore.Stamp

/**
 * The numbers a transcription keeps about how it ran.
 *
 * They are read out of the service response, which is written by the phone
 * that did the work — an older transcription, or one made by another
 * service, has none, and the dialog must then simply show nothing rather
 * than a row of zeroes or a crash.
 */
class TranscriptionPerformanceTest {

    private fun transcription(response: String?) = Transcription(
        id = "t1",
        audioFileId = "a1",
        content = "שלום עולם",
        service = "local_whisper",
        serviceResponse = response,
        state = TranscriptionFlags.DEFAULT_FLAGS,
        deviceId = "phone",
        createdAt = Stamp(at = 1_757_419_500, offset = 10800, zone = "Asia/Jerusalem"),
    )

    /** What the transcriber writes after a two-minute recording. */
    private fun fullResponse(): String = JSONObject().apply {
        put("elapsed_time", 95.5)
        put("duration_seconds", 120.0)
        put("segment_count", 14)
        put("confidence", 0.87)
        put("model", "large-v3-q5_0")
        put("device", "android")
        put("performance", JSONObject().apply {
            put("elapsed_seconds", 95.5)
            put("cpu_seconds", 305.6)
            put("cpu_cores_busy", 3.2)
            put("speed_vs_realtime", 1.256)
            put("peak_native_heap_bytes", 1_450_000_000L)
            put("native_heap_growth_bytes", 1_200_000_000L)
            put("peak_java_heap_bytes", 48_000_000L)
            put("java_heap_limit_bytes", 512_000_000L)
            put("cpu_cores", 8)
            put("total_seconds", 101.2)
            put("model_bytes", 1_081_140_203L)
            put("beam_size", 5)
            put("audio_bytes", 1_920_000L)
            put("converted_wav_bytes", 3_840_044L)
            put("device_model", "Google Pixel 8")
            put("android_version", "15")
            put("android_sdk", 35)
        })
    }.toString()

    @Test
    fun `a transcription made on this phone reports what it cost`() {
        val rows = transcription(fullResponse()).performance.toMap()
        assertEquals("2 min 0 s", rows["Recording"])
        assertEquals("1 min 36 s", rows["Transcribing"])
        assertEquals("1.26× real time", rows["Speed"])
        assertEquals("5 min 6 s", rows["CPU time"])
        assertEquals("3.20 of 8", rows["Cores busy"])
    }

    @Test
    fun `memory is reported in units a person reads`() {
        val rows = transcription(fullResponse()).performance.toMap()
        assertEquals("1.45 GB", rows["Memory, peak"])
        assertEquals("1.20 GB", rows["Memory for this"])
        assertEquals("48 MB of 512 MB", rows["App heap, peak"])
        assertEquals("1.08 GB", rows["Model file"])
    }

    @Test
    fun `the phone and the settings that produced these numbers are named`() {
        val rows = transcription(fullResponse()).performance.toMap()
        assertEquals("Google Pixel 8, Android 15 (API 35)", rows["Phone"])
        assertEquals("5", rows["Beam size"])
        assertEquals("14", rows["Segments"])
        assertEquals("0.87", rows["Confidence"])
    }

    @Test
    fun `the rows are in a sensible order, the recording first`() {
        val labels = transcription(fullResponse()).performance.map { it.first }
        assertEquals("Recording", labels.first())
        assertTrue(labels.indexOf("Transcribing") < labels.indexOf("CPU time"))
        assertEquals(labels.size, labels.toSet().size)
    }

    @Test
    fun `a transcription from before these were recorded still shows what it has`() {
        // An old row: elapsed time and nothing else.
        val old = JSONObject().put("elapsed_time", 42.0).put("duration_seconds", 60.0).toString()
        val rows = transcription(old).performance.toMap()
        assertEquals("1 min 0 s", rows["Recording"])
        assertEquals("42.0 s", rows["Transcribing"])
        assertTrue("nothing is invented", rows["CPU time"] == null)
        assertTrue(rows["Memory, peak"] == null)
    }

    @Test
    fun `a transcription made elsewhere reports nothing rather than nonsense`() {
        assertEquals(emptyList<Pair<String, String>>(), transcription(null).performance)
        assertEquals(emptyList<Pair<String, String>>(), transcription("").performance)
    }

    @Test
    fun `a response that is not JSON does not take the dialog down`() {
        assertEquals(emptyList<Pair<String, String>>(), transcription("not json at all {[").performance)
    }

    @Test
    fun `a response with an empty performance object shows the rest`() {
        val response = JSONObject()
            .put("duration_seconds", 30.0)
            .put("performance", JSONObject())
            .toString()
        val rows = transcription(response).performance.toMap()
        assertEquals("30.0 s", rows["Recording"])
        assertTrue(rows["Cores busy"] == null)
    }

    @Test
    fun `a very short transcription is not shown as zero seconds`() {
        val response = JSONObject()
            .put("duration_seconds", 2.0)
            .put("performance", JSONObject().put("elapsed_seconds", 0.4))
            .toString()
        assertEquals("0.40 s", transcription(response).performance.toMap()["Transcribing"])
    }
}
