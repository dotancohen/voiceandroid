package com.dotancohen.voiceandroid.audio

import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The waveforms kept on this device.
 *
 * A waveform costs seconds of decoding and one of the phone's few hardware
 * decoders, so it is worked out once and kept. The rules that matter are the
 * ones about *not* using an entry: drawn with other settings, drawn from a
 * recording that has since changed, or half written. Using a stale entry
 * would show the user the wrong picture of their own recording, which is
 * worse than drawing it again.
 */
class WaveformCacheTest {

    private lateinit var directory: File
    private lateinit var cache: WaveformCache
    private lateinit var recording: File

    private val bars = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
    private val fingerprint = WaveformCache.fingerprint(150)

    @Before
    fun setUp() {
        directory = File.createTempFile("waveforms", "").let {
            it.delete()
            it.mkdirs()
            it
        }
        cache = WaveformCache(directory)
        recording = File(directory.parentFile, "הקלטה-${System.nanoTime()}.opus")
        recording.writeBytes(ByteArray(2048) { 7 })
    }

    @After
    fun tearDown() {
        directory.deleteRecursively()
        recording.delete()
    }

    @Test
    fun `a waveform that was never drawn is not in the cache`() {
        assertNull(cache.read(recording, fingerprint))
    }

    @Test
    fun `a waveform kept comes back the same`() {
        cache.write(recording, fingerprint, bars)
        val back = cache.read(recording, fingerprint)
        assertNotNull(back)
        assertEquals(bars.size, back!!.size)
        for (i in bars.indices) assertEquals(bars[i], back[i], 0.001f)
    }

    @Test
    fun `a waveform drawn with other settings is not used`() {
        cache.write(recording, fingerprint, bars)
        assertNull(
            "a different bar count is a different picture",
            cache.read(recording, WaveformCache.fingerprint(300))
        )
        assertNull(
            "so is a different appearance",
            cache.read(recording, WaveformCache.fingerprint(150, "colour=blue"))
        )
    }

    @Test
    fun `the settings fingerprint names the settings it stands for`() {
        assertEquals("bars=150;", WaveformCache.fingerprint(150))
        assertEquals("bars=150;height=tall", WaveformCache.fingerprint(150, "height=tall"))
        assertTrue(WaveformCache.fingerprint(150) != WaveformCache.fingerprint(151))
    }

    @Test
    fun `a waveform drawn from a recording that has changed is not used`() {
        cache.write(recording, fingerprint, bars)
        assertNotNull(cache.read(recording, fingerprint))

        // The file was replaced: same name, different content.
        recording.writeBytes(ByteArray(4096) { 9 })
        assertNull(cache.read(recording, fingerprint))
    }

    @Test
    fun `a waveform drawn from a recording touched at another time is not used`() {
        cache.write(recording, fingerprint, bars)
        recording.setLastModified(recording.lastModified() - 60_000)
        assertNull(cache.read(recording, fingerprint))
    }

    @Test
    fun `two recordings keep their own waveforms`() {
        val other = File(recording.parentFile, "שיחה-${System.nanoTime()}.opus")
        other.writeBytes(ByteArray(1024) { 3 })
        try {
            cache.write(recording, fingerprint, bars)
            cache.write(other, fingerprint, listOf(1f, 1f, 1f))
            assertEquals(5, cache.read(recording, fingerprint)!!.size)
            assertEquals(3, cache.read(other, fingerprint)!!.size)
        } finally {
            other.delete()
        }
    }

    @Test
    fun `a half-written entry is forgotten rather than trusted`() {
        cache.write(recording, fingerprint, bars)
        val entry = directory.listFiles()!!.first { it.name.endsWith(".waveform") }
        entry.writeText("{\"version\": 1, \"bars\": [0.5, 0.")

        assertNull(cache.read(recording, fingerprint))
        assertTrue("and the broken entry is removed", !entry.exists())
    }

    @Test
    fun `an empty waveform is not kept, so it is tried again`() {
        cache.write(recording, fingerprint, emptyList())
        assertEquals(0, cache.count())
        assertNull(cache.read(recording, fingerprint))
    }

    @Test
    fun `clearing forgets everything`() {
        cache.write(recording, fingerprint, bars)
        assertEquals(1, cache.count())
        assertTrue(cache.sizeBytes() > 0)

        cache.clear()

        assertEquals(0, cache.count())
        assertNull(cache.read(recording, fingerprint))
    }

    @Test
    fun `a cache whose directory does not exist yet works`() {
        val fresh = WaveformCache(File(directory, "not-made-yet"))
        assertNull(fresh.read(recording, fingerprint))
        assertEquals(0, fresh.count())
        assertEquals(0L, fresh.sizeBytes())

        fresh.write(recording, fingerprint, bars)
        assertNotNull(fresh.read(recording, fingerprint))
    }

    @Test
    fun `an entry is small enough to keep for every recording`() {
        cache.write(recording, fingerprint, List(150) { it / 150f })
        assertTrue("an entry was ${cache.sizeBytes()} bytes", cache.sizeBytes() < 2048)
    }

    @Test
    fun `writing twice replaces the entry rather than adding another`() {
        cache.write(recording, fingerprint, bars)
        cache.write(recording, fingerprint, List(150) { 1f })
        assertEquals(1, cache.count())
        assertEquals(150, cache.read(recording, fingerprint)!!.size)
    }
}
