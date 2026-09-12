package com.dotancohen.voiceandroid.audio

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The 44 bytes written at the front of every WAV recording. Whisper, the
 * desktop player and the Python importer all read this header; one wrong
 * field and a recording plays at the wrong speed, sounds like noise, or is
 * rejected outright — and the audio itself would be fine, so the fault would
 * look like a lost recording.
 */
class WavHeaderTest {

    private fun header(dataBytes: Long = 32_000, sampleRate: Int = 16_000, channels: Int = 1) =
        ByteBuffer.wrap(WavRecorder.wavHeader(dataBytes, sampleRate, channels))
            .order(ByteOrder.LITTLE_ENDIAN)

    private fun ascii(b: ByteBuffer, at: Int, length: Int = 4): String {
        val bytes = ByteArray(length)
        b.position(at)
        b.get(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    @Test
    fun `the header is exactly the 44 bytes the recorder reserves`() {
        assertEquals(44, WavRecorder.HEADER_SIZE)
        assertEquals(44, WavRecorder.wavHeader(0, 16_000).size)
    }

    @Test
    fun `the four chunk names are in their canonical places`() {
        val b = header()
        assertEquals("RIFF", ascii(b, 0))
        assertEquals("WAVE", ascii(b, 8))
        assertEquals("fmt ", ascii(b, 12))
        assertEquals("data", ascii(b, 36))
    }

    @Test
    fun `the RIFF size covers everything after those first eight bytes`() {
        val b = header(dataBytes = 32_000)
        assertEquals(36 + 32_000, b.getInt(4))
    }

    @Test
    fun `the data size is the audio alone`() {
        val b = header(dataBytes = 32_000)
        assertEquals(32_000, b.getInt(40))
    }

    @Test
    fun `the format chunk says uncompressed PCM, sixteen bits`() {
        val b = header()
        assertEquals("the fmt chunk of PCM is 16 bytes long", 16, b.getInt(16))
        assertEquals("format 1 is PCM", 1, b.getShort(20).toInt())
        assertEquals(16, b.getShort(34).toInt())
    }

    @Test
    fun `mono at 16 kHz, which is what Whisper wants`() {
        val b = header(sampleRate = 16_000, channels = 1)
        assertEquals(1, b.getShort(22).toInt())
        assertEquals(16_000, b.getInt(24))
        assertEquals("byte rate = 16000 samples x 1 channel x 2 bytes", 32_000, b.getInt(28))
        assertEquals("block align = 1 channel x 2 bytes", 2, b.getShort(32).toInt())
    }

    @Test
    fun `stereo doubles the byte rate and the block alignment`() {
        val b = header(sampleRate = 44_100, channels = 2)
        assertEquals(2, b.getShort(22).toInt())
        assertEquals(44_100, b.getInt(24))
        assertEquals(44_100 * 2 * 2, b.getInt(28))
        assertEquals(4, b.getShort(32).toInt())
    }

    @Test
    fun `every number is little-endian, as RIFF requires`() {
        // 16000 = 0x00003E80, so the low byte comes first.
        val raw = WavRecorder.wavHeader(0, 16_000)
        assertEquals(0x80.toByte(), raw[24])
        assertEquals(0x3E.toByte(), raw[25])
        assertEquals(0x00.toByte(), raw[26])
        assertEquals(0x00.toByte(), raw[27])
    }

    @Test
    fun `a recording of nothing still produces a valid, empty header`() {
        val b = header(dataBytes = 0)
        assertEquals("RIFF", ascii(b, 0))
        assertEquals(36, b.getInt(4))
        assertEquals(0, b.getInt(40))
    }

    @Test
    fun `an hour of audio is written correctly, not wrapped around`() {
        // 16 kHz x 2 bytes x 3600 s = 115.2 MB, well inside a signed int.
        val oneHour = 16_000L * 2 * 3600
        val b = header(dataBytes = oneHour)
        assertEquals(oneHour.toInt(), b.getInt(40))
        assertEquals((36 + oneHour).toInt(), b.getInt(4))
        assertTrue("an hour must not read as negative", b.getInt(40) > 0)
    }

    @Test
    fun `the byte rate matches the duration the header implies`() {
        // Five minutes of 16 kHz mono: data size divided by byte rate must be
        // the number of seconds recorded, or players show the wrong length.
        val fiveMinutes = 16_000L * 2 * 300
        val b = header(dataBytes = fiveMinutes)
        assertEquals(300, b.getInt(40) / b.getInt(28))
    }
}
