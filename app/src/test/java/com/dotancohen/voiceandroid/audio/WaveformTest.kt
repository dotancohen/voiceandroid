package com.dotancohen.voiceandroid.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The bars drawn under a recording. They are what the user taps to seek, so
 * a waveform that is all one height, or that is drawn from only the first
 * part of the recording, makes it impossible to find a passage in an hour of
 * audio.
 */
class WaveformTest {

    /** A recording of pure silence: every sample zero. */
    private fun silence(count: Int) = List(count) { 0.toShort() }

    @Test
    fun `a recording is drawn as the agreed number of bars`() {
        val samples = List(WAVEFORM_BAR_COUNT * 100) { (it % 1000).toShort() }
        assertEquals(WAVEFORM_BAR_COUNT, downsampleToWaveform(samples).size)
    }

    @Test
    fun `no recording is no waveform`() {
        assertEquals(emptyList<Float>(), downsampleToWaveform(emptyList()))
    }

    @Test
    fun `every bar is within the height it is drawn in`() {
        val samples = List(WAVEFORM_BAR_COUNT * 37) { (it * 613 % 32_768).toShort() }
        for (bar in downsampleToWaveform(samples)) {
            assertTrue("$bar is outside 0..1", bar in 0f..1f)
        }
    }

    @Test
    fun `the loudest moment fills the height`() {
        val samples = MutableList(WAVEFORM_BAR_COUNT * 10) { 100.toShort() }
        samples[WAVEFORM_BAR_COUNT * 10 / 2] = 3000
        val bars = downsampleToWaveform(samples)
        assertEquals(1f, bars.max(), 0.0001f)
        assertEquals("the loud moment is in the middle of the picture", 75, bars.indexOf(1f))
    }

    @Test
    fun `silence is drawn flat, not as noise`() {
        val bars = downsampleToWaveform(silence(WAVEFORM_BAR_COUNT * 10))
        assertEquals(WAVEFORM_BAR_COUNT, bars.size)
        assertTrue("silence must not be scaled up into a full waveform", bars.all { it == 0f })
    }

    @Test
    fun `a quiet recording is still drawn at full height`() {
        // Half of it at amplitude 10, half at 20: the picture must show the
        // difference, not two bars near zero.
        val half = WAVEFORM_BAR_COUNT * 5
        val samples = List(half) { 10.toShort() } + List(half) { 20.toShort() }
        val bars = downsampleToWaveform(samples)
        assertEquals(0.5f, bars.first(), 0.0001f)
        assertEquals(1f, bars.last(), 0.0001f)
    }

    @Test
    fun `the whole recording is drawn, not only its beginning`() {
        // Loud at the very end only: the last bar must be the tall one.
        val samples = MutableList(WAVEFORM_BAR_COUNT * 8) { 50.toShort() }
        for (i in samples.size - 8 until samples.size) samples[i] = 30_000
        val bars = downsampleToWaveform(samples)
        assertEquals(1f, bars.last(), 0.0001f)
        assertTrue(bars.first() < 0.01f)
    }

    @Test
    fun `a negative sample is as loud as a positive one`() {
        // Audio swings both ways; a bar taken from the signed value alone
        // would draw the negative half of every wave as silence.
        val quiet = List(WAVEFORM_BAR_COUNT * 4) { 1000.toShort() }
        val loudNegative = MutableList(WAVEFORM_BAR_COUNT * 4) { 1000.toShort() }
        for (i in 0 until 4) loudNegative[i] = (-20_000).toShort()
        val bars = downsampleToWaveform(loudNegative)
        assertEquals(1f, bars.first(), 0.0001f)
        assertEquals(1f, downsampleToWaveform(quiet).first(), 0.0001f)
    }

    @Test
    fun `a recording shorter than the number of bars is drawn with what it has`() {
        val bars = downsampleToWaveform(listOf(0, 16_383, 32_767).map { it.toShort() })
        assertEquals(3, bars.size)
        assertEquals(0f, bars[0], 0.0001f)
        assertEquals(0.5f, bars[1], 0.001f)
        assertEquals(1f, bars[2], 0.001f)
    }

    @Test
    fun `the quietest sample there is does not overflow`() {
        // Short.MIN_VALUE has no positive counterpart; negating it in an Int
        // is safe, negating it in a Short is not.
        val samples = List(WAVEFORM_BAR_COUNT * 2) { Short.MIN_VALUE }
        val bars = downsampleToWaveform(samples)
        assertTrue(bars.all { it == 1f })
    }

    @Test
    fun `two bars of the same loudness are drawn the same height`() {
        val samples = List(WAVEFORM_BAR_COUNT * 6) { 4_000.toShort() }
        val bars = downsampleToWaveform(samples)
        assertEquals(1, bars.toSet().size)
    }
}

/**
 * Building a waveform as the audio arrives, without holding the audio.
 *
 * This is the class that replaced keeping every sample in a list. A
 * ten-minute recording is tens of millions of samples and a `List<Short>`
 * boxes each one, which is how opening a second Note while the first was
 * playing ran the application out of memory and killed it (2026-09-10,
 * `OutOfMemoryError` in `WaveformExtractor`). The point of every test here is
 * that the length of the recording does not matter.
 */
class WaveformAccumulatorTest {

    private fun bars(samples: Sequence<Short>, expect: Long = 0): List<Float> {
        val accumulator = WaveformAccumulator()
        if (expect > 0) accumulator.expect(expect)
        for (sample in samples) accumulator.add(sample)
        return accumulator.bars()
    }

    @Test
    fun `an hour of audio gives the same number of bars as a second`() {
        // An hour at 48 kHz: 172 800 000 samples. It runs in constant memory
        // or it does not run at all.
        val hour = bars(generateSequence(0) { it + 1 }.take(172_800_000).map { (it % 30_000).toShort() })
        assertEquals(WAVEFORM_BAR_COUNT, hour.size)
        assertTrue(hour.all { it in 0f..1f })
    }

    @Test
    fun `the bars describe the whole recording, not its beginning`() {
        // Quiet for the first nine tenths, loud at the end.
        val total = 1_000_000
        val samples = sequence {
            repeat(total * 9 / 10) { yield(100.toShort()) }
            repeat(total / 10) { yield(30_000.toShort()) }
        }
        val result = bars(samples, total.toLong())
        assertEquals(WAVEFORM_BAR_COUNT, result.size)
        assertEquals("the end is the loud part", 1f, result.last(), 0.0001f)
        assertTrue("the beginning is quiet", result.first() < 0.02f)
    }

    @Test
    fun `a recording that turns out longer than its header said is still drawn`() {
        // The header claims one second; ten arrive. Nothing may grow without
        // bound because of it.
        val accumulator = WaveformAccumulator()
        accumulator.expect(16_000)
        repeat(160_000) { accumulator.add(if (it > 150_000) 20_000 else 500) }
        val result = accumulator.bars()
        assertTrue("got ${result.size} bars", result.size in 1..WAVEFORM_BAR_COUNT)
        assertEquals("the loud tail is at the end", 1f, result.last(), 0.0001f)
    }

    @Test
    fun `a recording with no header length is drawn at full resolution`() {
        val result = bars(generateSequence { 12_000.toShort() }.take(WAVEFORM_BAR_COUNT * 40))
        assertEquals(WAVEFORM_BAR_COUNT, result.size)
        assertTrue(result.all { it == 1f })
    }

    @Test
    fun `no audio at all gives no bars`() {
        assertEquals(emptyList<Float>(), WaveformAccumulator().bars())
    }

    @Test
    fun `a recording of a few samples gives a few bars`() {
        val result = bars(sequenceOf(0, 16_383, 32_767).map { it.toShort() }, 3)
        assertEquals(3, result.size)
        assertEquals(0f, result[0], 0.0001f)
        assertEquals(0.5f, result[1], 0.001f)
        assertEquals(1f, result[2], 0.001f)
    }

    @Test
    fun `silence is flat, not scaled up into noise`() {
        val result = bars(generateSequence { 0.toShort() }.take(100_000), 100_000)
        assertTrue(result.isNotEmpty())
        assertTrue(result.all { it == 0f })
    }

    @Test
    fun `the loudest sample there is does not overflow`() {
        val result = bars(generateSequence { Short.MIN_VALUE }.take(10_000), 10_000)
        assertTrue(result.all { it == 1f })
    }

    /**
     * FILE-20: the levels kept with a recording are the slots scaled so the
     * loudest is 255, and the bars drawn from them (as the core draws them:
     * the loudest of each share, scaled to the loudest) are the bars this
     * phone draws itself, to within one step of 255.
     */
    @Test
    fun `the levels kept with a recording draw the bars this phone draws`() {
        val accumulator = WaveformAccumulator()
        val total = 1_200 * 40
        accumulator.expect(total.toLong())
        for (i in 0 until total) accumulator.add(((i * 613 % 30_000) * (if (i % 7 == 0) -1 else 1)).toShort())
        val bars = accumulator.bars()
        val levels = accumulator.levels().map { it.toInt() and 0xff }
        assertEquals(WAVEFORM_BAR_COUNT * 8, levels.size)
        assertEquals(255, levels.max())
        val loudest = levels.max().toFloat()
        val redrawn = (0 until WAVEFORM_BAR_COUNT).map { bar ->
            val from = bar * levels.size / WAVEFORM_BAR_COUNT
            val to = maxOf(from + 1, (bar + 1) * levels.size / WAVEFORM_BAR_COUNT)
            levels.subList(from, minOf(to, levels.size)).max() / loudest
        }
        for (i in bars.indices) assertEquals("bar $i", bars[i], redrawn[i], 1f / 255 + 0.0001f)
    }

    @Test
    fun `the levels of silence are zero and of no audio are empty`() {
        val silent = WaveformAccumulator()
        repeat(5_000) { silent.add(0) }
        assertTrue(silent.levels().isNotEmpty() && silent.levels().all { it == 0.toByte() })
        assertEquals(0, WaveformAccumulator().levels().size)
    }

    @Test
    fun `feeding it sample by sample agrees with the whole-list version`() {
        val samples = List(WAVEFORM_BAR_COUNT * 20) { ((it * 613) % 30_000).toShort() }
        val streamed = bars(samples.asSequence(), samples.size.toLong())
        val atOnce = downsampleToWaveform(samples)
        assertEquals(atOnce.size, streamed.size)
        for (i in atOnce.indices) {
            assertEquals("bar $i", atOnce[i], streamed[i], 0.0001f)
        }
    }
}
