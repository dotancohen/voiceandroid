package com.dotancohen.voiceandroid.audio

import android.content.Context
import com.dotancohen.voiceandroid.util.AppLogger
import com.dotancohen.voiceandroid.util.Magic
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

/**
 * Number of bars to display in the waveform visualization.
 * Change this value to adjust waveform resolution.
 */
const val WAVEFORM_BAR_COUNT = Magic.WAVEFORM_BARS

/**
 * Extracts waveform amplitude data from audio files for visualization.
 *
 * The waveform is represented as a list of normalized amplitude values (0.0 to 1.0),
 * with [WAVEFORM_BAR_COUNT] samples distributed evenly across the audio duration.
 */
class WaveformExtractor(private val context: Context) {

    private val TAG = "WaveformExtractor"


    /**
     * Extract waveform data from an audio file.
     *
     * @param filePath Path to the audio file
     * @return List of normalized amplitude values (0.0 to 1.0), or empty list on error
     */
    /**
     * The waveform if it has already been worked out, without doing any work.
     *
     * What lets a screen show the waveform of a long recording at once when it
     * was drawn before, and offer the button when it was not.
     */
    suspend fun cachedWaveform(filePath: String): List<Float>? = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) return@withContext null
            WaveformCache(File(context.filesDir, CACHE_DIRECTORY))
                .read(file, WaveformCache.fingerprint(WAVEFORM_BAR_COUNT))
        } catch (e: Throwable) {
            null
        }
    }

    suspend fun extractWaveform(filePath: String): List<Float> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext emptyList()
            }

            val cache = WaveformCache(File(context.filesDir, CACHE_DIRECTORY))
            val fingerprint = WaveformCache.fingerprint(WAVEFORM_BAR_COUNT)
            cache.read(file, fingerprint)?.let { return@withContext it }

            // One recording decoded at a time, across the whole application.
            // A phone has only a few hardware decoders — three, for some
            // kinds — and the transcription queue is using one of them to
            // convert audio. Two screens each asking for their own decoder is
            // how a waveform came back empty and was never drawn.
            val startedAt = System.currentTimeMillis()
            val bars = decoding.withPermit {
                // Another screen may have asked for the same recording while
                // this one waited for the permit.
                cache.read(file, fingerprint) ?: draw(filePath)
            }
            if (bars.isNotEmpty()) {
                cache.write(file, fingerprint, bars)
                // Logged with the size, so the thresholds for "large" can be
                // judged against what this phone really takes.
                AppLogger.d(
                    TAG,
                    "Drew the waveform of ${file.name} (${file.length() / 1024} kB) " +
                        "in ${System.currentTimeMillis() - startedAt} ms"
                )
            }
            bars
        } catch (e: Throwable) {
            // Throwable, not Exception: running out of memory here is an
            // Error, and it used to take the whole application down. A
            // recording whose waveform cannot be drawn is still a recording
            // that plays.
            AppLogger.w(TAG, "Could not read the waveform of $filePath: $e")
            emptyList()
        }
    }

    /**
     * Decode the recording and draw its bars, with one second chance.
     *
     * Opening a decoder fails when every one of the phone's few is in use —
     * the transcription queue holds one while it converts a recording — and
     * the failure is immediate rather than a wait. One retry after a moment
     * turns "no waveform at all" into a waveform a moment later.
     */
    private suspend fun draw(filePath: String): List<Float> {
        repeat(2) { attempt ->
            try {
                return extractWaveformFromFile(filePath)
            } catch (e: Throwable) {
                if (attempt == 0) {
                    AppLogger.d(TAG, "No decoder for the waveform of $filePath yet ($e); trying once more")
                    delay(400)
                } else {
                    throw e
                }
            }
        }
        return emptyList()
    }

    private fun extractWaveformFromFile(filePath: String): List<Float> {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null

        try {
            extractor.setDataSource(filePath)

            // Find the audio track
            var audioTrackIndex = -1
            var audioFormat: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    audioFormat = format
                    break
                }
            }

            if (audioTrackIndex < 0 || audioFormat == null) {
                return emptyList()
            }

            extractor.selectTrack(audioTrackIndex)

            val mime = audioFormat.getString(MediaFormat.KEY_MIME) ?: return emptyList()
            val sampleRate = audioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = audioFormat.getIntegerOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1)
            val duration = audioFormat.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)

            if (duration <= 0) {
                return emptyList()
            }

            // Create decoder
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(audioFormat, null, null, 0)
            decoder.start()

            // The bars are built as the audio is decoded, so the whole
            // recording is never in memory at once.
            val accumulator = WaveformAccumulator()
            // The header usually knows the length; when it does, the bars are
            // the right width from the first sample.
            val declaredSamples = audioFormat.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)
                .let { durationUs -> durationUs / 1_000_000.0 * sampleRate }
                .toLong()
            accumulator.expect(declaredSamples)
            val bufferInfo = MediaCodec.BufferInfo()
            var isEOS = false

            while (!isEOS) {
                // Feed input
                val inputBufferIndex = decoder.dequeueInputBuffer(10000)
                if (inputBufferIndex >= 0) {
                    val inputBuffer = decoder.getInputBuffer(inputBufferIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, 0, 0,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            isEOS = true
                        } else {
                            decoder.queueInputBuffer(
                                inputBufferIndex, 0, sampleSize,
                                extractor.sampleTime, 0
                            )
                            extractor.advance()
                        }
                    }
                }

                // Get output
                var outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 10000)
                while (outputBufferIndex >= 0) {
                    val outputBuffer = decoder.getOutputBuffer(outputBufferIndex)
                    if (outputBuffer != null && bufferInfo.size > 0) {
                        // Straight into the bars: nothing is kept but the bars
                        feedSamples(outputBuffer, bufferInfo.size, channelCount, accumulator)
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isEOS = true
                        break
                    }

                    outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            return accumulator.bars()

        } finally {
            decoder?.stop()
            decoder?.release()
            extractor.release()
        }
    }

    private companion object {
        /** Where the waveforms of this device are kept, under its files. */
        const val CACHE_DIRECTORY = "waveforms"

        /**
         * One decoder at a time, for every extractor in the application.
         *
         * Each screen makes its own extractor, so the limit cannot live in an
         * instance. A semaphore rather than a lock because the number is the
         * point: it may be raised to two if a phone turns out to cope.
         */
        val decoding = Semaphore(1)
    }

    /**
     * Read 16-bit PCM samples out of a decoded buffer and into the bars.
     *
     * The first channel stands for the rest: a waveform is a picture of the
     * loudness, and mixing the channels would cost a pass over the audio for
     * a difference nobody can see at this size.
     */
    private fun feedSamples(
        buffer: ByteBuffer,
        size: Int,
        channelCount: Int,
        accumulator: WaveformAccumulator,
    ) {
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.rewind()
        val sampleCount = size / 2 // 16-bit = 2 bytes per sample
        var i = 0
        while (i < sampleCount && buffer.remaining() >= 2) {
            accumulator.add(buffer.short)
            // Skip the other channels of this frame
            for (c in 1 until channelCount) {
                if (buffer.remaining() >= 2) buffer.short
            }
            i += channelCount
        }
    }

    /**
     * Extension function to get integer with default value.
     */
    private fun MediaFormat.getIntegerOrDefault(key: String, default: Int): Int {
        return try {
            getInteger(key)
        } catch (e: Exception) {
            default
        }
    }

    /**
     * Extension function to get long with default value.
     */
    private fun MediaFormat.getLongOrDefault(key: String, default: Long): Long {
        return try {
            getLong(key)
        } catch (e: Exception) {
            default
        }
    }
}

/**
 * The [WAVEFORM_BAR_COUNT] bars drawn under a recording, from its samples.
 *
 * Each bar is the loudest sample in its share of the recording, and the bars
 * are then scaled so that the loudest of them fills the height: a quiet
 * recording is drawn as a full waveform rather than as a flat line, which is
 * what makes it possible to see where the speech is.
 *
 * For a recording already in memory. The extractor itself feeds a
 * [WaveformAccumulator] as the audio is decoded, so that it never holds the
 * whole of it at once.
 */
fun downsampleToWaveform(samples: List<Short>): List<Float> {
    if (samples.isEmpty()) return emptyList()
    val accumulator = WaveformAccumulator()
    accumulator.expect(samples.size.toLong())
    for (sample in samples) accumulator.add(sample)
    return accumulator.bars()
}

/**
 * The bars of a waveform, built as the samples arrive.
 *
 * Holds one float per bar and nothing else, however long the recording is.
 * The number of samples is not known in advance (a file's header can lie, and
 * a stream has no end until it ends), so the accumulator starts fine and
 * halves its own resolution whenever it runs out of bars: the first two bars
 * become one, the next two become one, and from then on each bar covers twice
 * as much audio. The picture is the same either way, and the memory never
 * moves.
 *
 * This replaced keeping every sample in a list. A ten-minute recording is
 * tens of millions of samples, and a `List<Short>` boxes each one into an
 * object of its own: about forty bytes a sample, hundreds of megabytes for
 * one recording. That is what crashed the application on 2026-09-10 when a
 * second Note was opened while the first was still playing.
 */
class WaveformAccumulator(private val barCount: Int = WAVEFORM_BAR_COUNT) {

    /**
     * Slots kept while the audio streams in — more than the bars drawn, so
     * that the halving never costs visible detail and the final bars are
     * always the full count. Eight per bar is 1200 floats: five kilobytes,
     * whatever the length of the recording.
     */
    private val slots = FloatArray(barCount * SLOTS_PER_BAR)
    private var filled = 0
    private var samplesPerSlot = 1L
    private var inSlot = 0L
    private var peak = 0f
    private var any = false

    /**
     * Say roughly how many samples are coming, where that is known, so the
     * slots are the right width from the start rather than after a few
     * halvings.
     */
    fun expect(totalSamples: Long) {
        if (totalSamples > 0 && filled == 0 && inSlot == 0L) {
            samplesPerSlot = (totalSamples / slots.size).coerceAtLeast(1)
        }
    }

    fun add(sample: Short) {
        any = true
        val value = abs(sample.toInt()).toFloat()
        if (value > peak) peak = value
        inSlot++
        if (inSlot >= samplesPerSlot) commit()
    }

    /**
     * The bars, scaled so the loudest fills the height.
     *
     * Exactly [barCount] of them for any recording longer than that many
     * samples; fewer for a recording of a handful of samples, which has no
     * more detail to give. Empty when no audio arrived at all.
     */
    fun bars(): List<Float> {
        if (!any) return emptyList()
        if (inSlot > 0) commit()
        if (filled == 0) return emptyList()

        val loudest = (0 until filled).maxOf { slots[it] }
        fun scaled(value: Float) = if (loudest > 0f) value / loudest else 0f

        if (filled <= barCount) return (0 until filled).map { scaled(slots[it]) }

        // Group the slots into exactly barCount bars, each the loudest of its
        // own share. The boundaries are worked out from the index so that no
        // slot is counted twice and none is left out.
        return (0 until barCount).map { bar ->
            val from = (bar.toLong() * filled / barCount).toInt()
            val to = (((bar + 1).toLong() * filled / barCount).toInt()).coerceAtLeast(from + 1)
            var loudestHere = 0f
            for (i in from until to.coerceAtMost(filled)) {
                if (slots[i] > loudestHere) loudestHere = slots[i]
            }
            scaled(loudestHere)
        }
    }

    private fun commit() {
        if (filled == slots.size) halve()
        slots[filled++] = peak
        peak = 0f
        inSlot = 0
    }

    /** Two slots become one, and every slot from now on covers twice as much. */
    private fun halve() {
        var write = 0
        var read = 0
        while (read < filled) {
            val a = slots[read]
            val b = if (read + 1 < filled) slots[read + 1] else 0f
            slots[write++] = max(a, b)
            read += 2
        }
        for (i in write until slots.size) slots[i] = 0f
        filled = write
        samplesPerSlot *= 2
    }

    private companion object {
        const val SLOTS_PER_BAR = 8
    }
}
