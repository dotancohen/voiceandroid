package com.dotancohen.voiceandroid.transcription

import com.dotancohen.voiceandroid.audio.WavRecorder
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Converting a recording into what Whisper reads: 16 kHz, mono, 16-bit.
 *
 * Every recording made on the phone goes through this before it is
 * transcribed, so a fault here turns a good recording into a transcription
 * of noise, and the recording itself would sound fine when played.
 */
class AudioConversionTest {

    private fun resample(input: FloatArray, inRate: Int, outRate: Int, chunk: Int = 1024): ShortArray {
        val out = ArrayList<Short>()
        val r = AudioToWav.Resampler(inRate, outRate) { buf, n -> for (i in 0 until n) out.add(buf[i]) }
        var pos = 0
        while (pos < input.size) {
            val n = minOf(chunk, input.size - pos)
            r.push(input.copyOfRange(pos, pos + n), n)
            pos += n
        }
        r.flush()
        return out.toShortArray()
    }

    private fun tone(rate: Int, seconds: Double, hz: Double, amplitude: Float = 0.5f) =
        FloatArray((rate * seconds).toInt()) { i -> amplitude * sin(2 * PI * hz * i / rate).toFloat() }

    @Test
    fun `a recording with nothing in it converts to nothing, not to a crash`() {
        assertEquals(0, resample(FloatArray(0), 48_000, 16_000).size)
    }

    @Test
    fun `a recording shorter than the filter still produces audio`() {
        // A tap on the record button and an immediate stop.
        val out = resample(tone(48_000, 0.01, 440.0), 48_000, 16_000)
        assertTrue("got ${out.size} samples", out.size in 100..200)
    }

    @Test
    fun `upsampling a telephone-quality recording gives the right length`() {
        // An imported voicemail at 8 kHz has to be stretched, not squeezed.
        val out = resample(tone(8_000, 1.0, 300.0), 8_000, 16_000)
        assertTrue("got ${out.size} samples", abs(out.size - 16_000) < 64)
    }

    @Test
    fun `a recording from a CD-quality source keeps its length`() {
        val out = resample(tone(44_100, 1.0, 440.0), 44_100, 16_000)
        assertTrue("got ${out.size} samples", abs(out.size - 16_000) < 64)
    }

    @Test
    fun `silence converts to silence`() {
        val out = resample(FloatArray(48_000), 48_000, 16_000)
        assertTrue("silence must not become noise", out.all { it.toInt() == 0 })
    }

    @Test
    fun `a signal at full scale does not wrap around into noise`() {
        // Anything above 1.0 must be clipped, not allowed to overflow the
        // 16-bit sample, which would turn the loudest passage into a crackle.
        val loud = FloatArray(16_000) { 4f }
        val out = resample(loud, 16_000, 16_000)
        assertTrue(out.drop(64).all { it.toInt() == 32_767 })
    }

    @Test
    fun `a signal at the negative limit does not wrap either`() {
        val loud = FloatArray(16_000) { -4f }
        val out = resample(loud, 16_000, 16_000)
        assertTrue(out.drop(64).all { it.toInt() <= -32_767 })
    }

    @Test
    fun `the chunks the decoder hands over do not change the result`() {
        // MediaCodec returns buffers of whatever size it likes; the output
        // must not depend on that.
        val input = tone(48_000, 0.5, 440.0)
        val oneChunk = resample(input, 48_000, 16_000, chunk = input.size)
        val manyChunks = resample(input, 48_000, 16_000, chunk = 97)
        assertEquals(oneChunk.size, manyChunks.size)
        for (i in oneChunk.indices) {
            assertTrue(
                "sample $i: ${oneChunk[i]} against ${manyChunks[i]}",
                abs(oneChunk[i] - manyChunks[i]) <= 1
            )
        }
    }

    @Test
    fun `a recording already at 16 kHz keeps its samples exactly`() {
        val input = FloatArray(1000) { i -> (i % 100) / 200f }
        val out = resample(input, 16_000, 16_000)
        assertEquals(input.size, out.size)
        for (i in input.indices) {
            assertEquals((input[i] * 32767f).toInt().toShort().toInt().toDouble(), out[i].toDouble(), 1.0)
        }
    }

    // What counts as a file Whisper can read without conversion

    private fun wav(name: String, rate: Int = 16_000, channels: Int = 1, dataBytes: Long = 320): File {
        val f = File.createTempFile("audio", name)
        f.deleteOnExit()
        f.writeBytes(WavRecorder.wavHeader(dataBytes, rate, channels) + ByteArray(dataBytes.toInt()))
        return File(f.parentFile, "converted_${f.name.substringBeforeLast('.')}$name").also { target ->
            f.copyTo(target, overwrite = true)
            target.deleteOnExit()
        }
    }

    @Test
    fun `a recording made by this app needs no conversion`() {
        assertTrue(AudioToWav.isWhisperWav(wav(".wav")))
    }

    @Test
    fun `a recording at another sample rate must be converted`() {
        assertFalse(AudioToWav.isWhisperWav(wav(".wav", rate = 44_100)))
        assertFalse(AudioToWav.isWhisperWav(wav(".wav", rate = 8_000)))
    }

    @Test
    fun `a recording in stereo must be converted`() {
        assertFalse(AudioToWav.isWhisperWav(wav(".wav", channels = 2)))
    }

    @Test
    fun `a compressed recording must be converted, whatever its name says`() {
        val opus = File.createTempFile("audio", ".wav")
        opus.deleteOnExit()
        opus.writeBytes(ByteArray(200) { 0x4f })
        assertFalse("the header is not RIFF", AudioToWav.isWhisperWav(opus))
    }

    @Test
    fun `a file too short to hold a header is not a WAV`() {
        val stub = File.createTempFile("audio", ".wav")
        stub.deleteOnExit()
        stub.writeBytes(ByteArray(10))
        assertFalse(AudioToWav.isWhisperWav(stub))
    }

    @Test
    fun `a file of another kind is not a WAV, even when the audio inside would do`() {
        assertFalse(AudioToWav.isWhisperWav(wav(".m4a")))
    }

    @Test
    fun `a file that is not there is not a WAV`() {
        assertFalse(AudioToWav.isWhisperWav(File("/no/such/directory/recording.wav")))
    }
}
