package com.dotancohen.voiceandroid.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import kotlinx.coroutines.Dispatchers
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
const val WAVEFORM_BAR_COUNT = 150

/**
 * Extracts waveform amplitude data from audio files for visualization.
 *
 * The waveform is represented as a list of normalized amplitude values (0.0 to 1.0),
 * with [WAVEFORM_BAR_COUNT] samples distributed evenly across the audio duration.
 */
class WaveformExtractor(private val context: Context) {

    /**
     * Extract waveform data from an audio file.
     *
     * @param filePath Path to the audio file
     * @return List of normalized amplitude values (0.0 to 1.0), or empty list on error
     */
    suspend fun extractWaveform(filePath: String): List<Float> = withContext(Dispatchers.IO) {
        try {
            val file = File(filePath)
            if (!file.exists()) {
                return@withContext emptyList()
            }

            extractWaveformFromFile(filePath)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
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

            // Collect all PCM samples
            val allSamples = mutableListOf<Short>()
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
                        // Convert to 16-bit PCM samples
                        val samples = extractSamplesFromBuffer(outputBuffer, bufferInfo.size, channelCount)
                        allSamples.addAll(samples)
                    }
                    decoder.releaseOutputBuffer(outputBufferIndex, false)

                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        isEOS = true
                        break
                    }

                    outputBufferIndex = decoder.dequeueOutputBuffer(bufferInfo, 0)
                }
            }

            // Downsample to WAVEFORM_BAR_COUNT bars
            return downsampleToWaveform(allSamples)

        } finally {
            decoder?.stop()
            decoder?.release()
            extractor.release()
        }
    }

    /**
     * Extract 16-bit PCM samples from a decoded audio buffer.
     * Mixes stereo to mono by averaging channels.
     */
    private fun extractSamplesFromBuffer(
        buffer: ByteBuffer,
        size: Int,
        channelCount: Int
    ): List<Short> {
        val samples = mutableListOf<Short>()
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        buffer.rewind()

        val sampleCount = size / 2 // 16-bit = 2 bytes per sample

        for (i in 0 until sampleCount step channelCount) {
            if (buffer.remaining() >= 2) {
                val sample = buffer.short
                // Skip additional channels (mix to mono by taking first channel)
                for (c in 1 until channelCount) {
                    if (buffer.remaining() >= 2) {
                        buffer.short // discard
                    }
                }
                samples.add(sample)
            }
        }

        return samples
    }

    /**
     * Downsample PCM samples to [WAVEFORM_BAR_COUNT] amplitude values.
     * Each bar represents the peak amplitude in its time segment.
     */
    private fun downsampleToWaveform(samples: List<Short>): List<Float> {
        if (samples.isEmpty()) return emptyList()

        val result = FloatArray(WAVEFORM_BAR_COUNT)
        val samplesPerBar = samples.size / WAVEFORM_BAR_COUNT

        if (samplesPerBar <= 0) {
            // Fewer samples than bars - just normalize what we have
            return samples.map { abs(it.toFloat()) / Short.MAX_VALUE }
        }

        var maxAmplitude = 0f

        for (bar in 0 until WAVEFORM_BAR_COUNT) {
            val startIndex = bar * samplesPerBar
            val endIndex = minOf(startIndex + samplesPerBar, samples.size)

            // Find peak amplitude in this segment
            var peak = 0
            for (i in startIndex until endIndex) {
                peak = max(peak, abs(samples[i].toInt()))
            }

            result[bar] = peak.toFloat()
            maxAmplitude = max(maxAmplitude, result[bar])
        }

        // Normalize to 0.0 - 1.0 range
        return if (maxAmplitude > 0) {
            result.map { it / maxAmplitude }
        } else {
            result.toList()
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
