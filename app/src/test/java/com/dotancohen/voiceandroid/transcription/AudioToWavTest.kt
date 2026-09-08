package com.dotancohen.voiceandroid.transcription

import com.dotancohen.voiceandroid.audio.WavRecorder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/** The resampler and the WAV header, the pieces of the ffmpeg replacement that run without Android. */
class AudioToWavTest {

    private fun resample(input: FloatArray, inRate: Int, outRate: Int): ShortArray {
        val out = ArrayList<Short>()
        val r = AudioToWav.Resampler(inRate, outRate) { buf, n -> for (i in 0 until n) out.add(buf[i]) }
        // feed in uneven chunks, like a decoder does
        var pos = 0
        val sizes = intArrayOf(1000, 37, 4096, 512)
        var k = 0
        while (pos < input.size) {
            val n = minOf(sizes[k++ % sizes.size], input.size - pos)
            r.push(input.copyOfRange(pos, pos + n), n)
            pos += n
        }
        r.flush()
        return out.toShortArray()
    }

    @Test
    fun downsamplingKeepsAToneAndItsLength() {
        val inRate = 48_000
        val seconds = 2
        val tone = FloatArray(inRate * seconds) { i -> 0.5f * sin(2 * PI * 440.0 * i / inRate).toFloat() }
        val out = resample(tone, inRate, 16_000)
        // length within a few samples of 2 s at 16 kHz
        assertTrue("got ${out.size} samples", abs(out.size - 32_000) < 64)
        // the tone is still there at half amplitude: compare against the ideal 16 kHz tone (skipping the filter edges)
        var err = 0.0
        var count = 0
        for (i in 200 until out.size - 200) {
            val ideal = 0.5 * sin(2 * PI * 440.0 * i / 16_000.0)
            err += abs(out[i] / 32767.0 - ideal)
            count++
        }
        assertTrue("mean error ${err / count}", err / count < 0.02)
    }

    @Test
    fun highFrequenciesAboveTheNewNyquistAreRemoved() {
        val inRate = 48_000
        // 12 kHz tone: above 8 kHz, must not alias into the 16 kHz output
        val tone = FloatArray(inRate) { i -> 0.9f * sin(2 * PI * 12_000.0 * i / inRate).toFloat() }
        val out = resample(tone, inRate, 16_000)
        var peak = 0
        for (i in 200 until out.size - 200) peak = maxOf(peak, abs(out[i].toInt()))
        assertTrue("residual peak $peak", peak < 32767 * 0.05)
    }

    @Test
    fun sameRateIsPassedThrough() {
        val input = FloatArray(1000) { i -> ((i % 100) - 50) / 100f }
        val out = resample(input, 16_000, 16_000)
        assertEquals(1000, out.size)
        for (i in 0 until 1000) assertEquals((input[i] * 32767).toDouble(), out[i].toDouble(), 1.0)
    }

    @Test
    fun wavHeaderIsCanonical() {
        val h = WavRecorder.wavHeader(dataBytes = 32_000, sampleRate = 16_000)
        assertEquals(44, h.size)
        assertEquals("RIFF", String(h, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(h, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(h, 12, 4, Charsets.US_ASCII))
        assertEquals("data", String(h, 36, 4, Charsets.US_ASCII))
        val b = ByteBuffer.wrap(h).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + 32_000, b.getInt(4))
        assertEquals(1, b.getShort(20).toInt())      // PCM
        assertEquals(1, b.getShort(22).toInt())      // mono
        assertEquals(16_000, b.getInt(24))
        assertEquals(32_000, b.getInt(28))           // byte rate
        assertEquals(2, b.getShort(32).toInt())      // block align
        assertEquals(16, b.getShort(34).toInt())
        assertEquals(32_000, b.getInt(40))
    }

    @Test
    fun whisperWavDetectionReadsTheHeader() {
        val dir = createTempDir()
        val good = java.io.File(dir, "a.wav")
        good.writeBytes(WavRecorder.wavHeader(3200, 16_000) + ByteArray(3200))
        assertTrue(AudioToWav.isWhisperWav(good))
        val stereo = java.io.File(dir, "b.wav")
        stereo.writeBytes(WavRecorder.wavHeader(3200, 16_000, channels = 2) + ByteArray(3200))
        assertTrue(!AudioToWav.isWhisperWav(stereo))
        val other = java.io.File(dir, "c.wav")
        other.writeBytes(WavRecorder.wavHeader(3200, 48_000) + ByteArray(3200))
        assertTrue(!AudioToWav.isWhisperWav(other))
        assertTrue(!AudioToWav.isWhisperWav(java.io.File(dir, "d.ogg").also { it.writeBytes(ByteArray(100)) }))
    }
}
