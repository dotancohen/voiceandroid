package com.dotancohen.voiceandroid.transcription

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.dotancohen.voiceandroid.audio.WavRecorder
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Turns any audio file the phone can decode (Opus, AAC, MP3, FLAC, WAV, ...)
 * into the 16 kHz mono 16-bit WAV that Whisper consumes, streaming so that an
 * hour-long recording never sits in memory at once. The phone has no ffmpeg;
 * this is its replacement. Uses the hardware/software decoders through
 * MediaCodec and a windowed-sinc resampler (a proper low-pass, not linear
 * interpolation, so the high frequencies do not fold back as noise).
 */
object AudioToWav {
    const val TARGET_RATE = 16_000

    /** Convert [input] to [output]; returns [input] itself if it already is the right WAV. */
    fun convert(input: File, output: File): File {
        if (isWhisperWav(input)) return input
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        val out = RandomAccessFile(output, "rw")
        try {
            extractor.setDataSource(input.absolutePath)
            var track = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; format = f; break }
            }
            if (track < 0 || format == null) throw IllegalArgumentException("No audio track in ${input.name}")
            extractor.selectTrack(track)
            val mime = format.getString(MediaFormat.KEY_MIME)!!
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(format, null, null, 0)
            decoder.start()

            out.setLength(0)
            out.write(ByteArray(WavRecorder.HEADER_SIZE))
            var dataBytes = 0L
            var resampler: Resampler? = null
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var rate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var floatPcm = false
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val sink = { pcm: ShortArray, n: Int ->
                val bytes = ByteArray(n * 2)
                for (i in 0 until n) {
                    val s = pcm[i].toInt()
                    bytes[i * 2] = (s and 0xff).toByte()
                    bytes[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
                }
                out.write(bytes)
                dataBytes += n * 2
            }
            while (!outputDone) {
                if (!inputDone) {
                    val idx = decoder.dequeueInputBuffer(10_000)
                    if (idx >= 0) {
                        val buf = decoder.getInputBuffer(idx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            decoder.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val idx = decoder.dequeueOutputBuffer(info, 10_000)
                when {
                    idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val f = decoder.outputFormat
                        channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                        resampler = Resampler(rate, TARGET_RATE, sink)
                    }
                    idx >= 0 -> {
                        val buf = decoder.getOutputBuffer(idx)!!
                        buf.position(info.offset)
                        buf.limit(info.offset + info.size)
                        val r = resampler ?: Resampler(rate, TARGET_RATE, sink).also { resampler = it }
                        if (floatPcm) {
                            val fb = buf.order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
                            val frames = fb.remaining() / channels
                            val mono = FloatArray(frames)
                            for (i in 0 until frames) {
                                var acc = 0f
                                for (c in 0 until channels) acc += fb.get(i * channels + c)
                                mono[i] = acc / channels
                            }
                            r.push(mono, frames)
                        } else {
                            val sb = buf.order(java.nio.ByteOrder.nativeOrder()).asShortBuffer()
                            val frames = sb.remaining() / channels
                            val mono = FloatArray(frames)
                            for (i in 0 until frames) {
                                var acc = 0
                                for (c in 0 until channels) acc += sb.get(i * channels + c)
                                mono[i] = acc / (channels * 32768f)
                            }
                            r.push(mono, frames)
                        }
                        decoder.releaseOutputBuffer(idx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                }
            }
            resampler?.flush()
            out.seek(0)
            out.write(WavRecorder.wavHeader(dataBytes, TARGET_RATE))
            return output
        } finally {
            out.close()
            try { decoder?.stop() } catch (_: Exception) {}
            decoder?.release()
            extractor.release()
        }
    }

    /** True for a PCM WAV that is already 16 kHz, mono, 16-bit. */
    fun isWhisperWav(file: File): Boolean {
        if (!file.name.lowercase().endsWith(".wav") || file.length() < WavRecorder.HEADER_SIZE) return false
        RandomAccessFile(file, "r").use { f ->
            val h = ByteArray(WavRecorder.HEADER_SIZE)
            f.readFully(h)
            val b = java.nio.ByteBuffer.wrap(h).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            if (String(h, 0, 4, Charsets.US_ASCII) != "RIFF" || String(h, 8, 4, Charsets.US_ASCII) != "WAVE") return false
            if (String(h, 12, 4, Charsets.US_ASCII) != "fmt ") return false
            val format = b.getShort(20).toInt()
            val channels = b.getShort(22).toInt()
            val rate = b.getInt(24)
            val bits = b.getShort(34).toInt()
            return format == 1 && channels == 1 && rate == TARGET_RATE && bits == 16
        }
    }

    /**
     * Streaming windowed-sinc resampler for mono float audio. Output samples
     * are handed to [sink] as 16-bit PCM in blocks.
     */
    class Resampler(private val inRate: Int, private val outRate: Int, private val sink: (ShortArray, Int) -> Unit) {
        private val taps = 32                      // each side
        private val phases = 256                   // sub-sample positions in the table
        private val step = inRate.toDouble() / outRate
        private val cutoff = minOf(1.0, outRate.toDouble() / inRate) * 0.95
        private val table: FloatArray = FloatArray(phases * (2 * taps)).also { t ->
            for (p in 0 until phases) {
                val frac = p.toDouble() / phases
                for (k in 0 until 2 * taps) {
                    val x = (k - taps + 1) - frac      // distance from the sample to the output point
                    val sinc = if (abs(x) < 1e-9) cutoff else sin(PI * cutoff * x) / (PI * x)
                    val w = 0.5 * (1 + cos(PI * x / taps)) // Hann window over ±taps
                    t[p * 2 * taps + k] = (sinc * w).toFloat()
                }
            }
        }
        // Starts with `taps` silent samples so the filter has history at the beginning
        private var pending = FloatArray(taps * 2)
        private var pendingLen = taps
        private var base = -taps.toLong() // absolute index of pending[0]
        private var outPos = 0.0     // absolute input position of the next output sample
        private val outBuf = ShortArray(8192)
        private var outLen = 0

        fun push(samples: FloatArray, n: Int) {
            if (pendingLen + n > pending.size) pending = pending.copyOf(maxOf(pending.size * 2, pendingLen + n))
            System.arraycopy(samples, 0, pending, pendingLen, n)
            pendingLen += n
            produce()
        }

        fun flush() {
            push(FloatArray(taps), taps)
            produce()
            if (outLen > 0) { sink(outBuf, outLen); outLen = 0 }
        }

        private fun produce() {
            val same = inRate == outRate
            while (true) {
                val center = outPos - base            // index within pending
                val ic = kotlin.math.floor(center).toInt()
                if (ic + taps >= pendingLen) break
                var v: Float
                if (same) {
                    v = pending[ic]
                } else {
                    val frac = center - ic
                    val p = (frac * phases).roundToInt().coerceIn(0, phases - 1)
                    val row = p * 2 * taps
                    var acc = 0f
                    val start = ic - taps + 1
                    for (k in 0 until 2 * taps) acc += pending[start + k] * table[row + k]
                    v = acc
                }
                outBuf[outLen++] = (v.coerceIn(-1f, 1f) * 32767f).roundToInt().toShort()
                if (outLen == outBuf.size) { sink(outBuf, outLen); outLen = 0 }
                outPos += step
            }
            // Drop what every future output has passed
            val keepFrom = (kotlin.math.floor(outPos - base).toInt() - taps).coerceAtLeast(0)
            if (keepFrom > 0) {
                System.arraycopy(pending, keepFrom, pending, 0, pendingLen - keepFrom)
                pendingLen -= keepFrom
                base += keepFrom
            }
        }
    }
}
