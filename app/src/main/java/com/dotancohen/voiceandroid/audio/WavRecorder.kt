package com.dotancohen.voiceandroid.audio

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * Records uncompressed 16 kHz mono 16-bit PCM straight into a WAV file: the
 * exact input Whisper works on, so on-device transcription needs no
 * conversion at all. About 1.9 MB per minute.
 *
 * [AudioRecord] has no pause of its own: while paused the reader thread keeps
 * draining the microphone (so nothing overflows) and simply does not write.
 */
class WavRecorder(
    private val file: File,
    private val device: AudioDeviceInfo?,
    private val sampleRate: Int = 16_000
) {
    private var record: AudioRecord? = null
    private var thread: Thread? = null
    private var out: RandomAccessFile? = null
    @Volatile private var running = false
    @Volatile var paused = false
        private set
    @Volatile private var dataBytes = 0L
    @Volatile private var peakSinceLastRead = 0

    @SuppressLint("MissingPermission")
    fun start() {
        val minBuf = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufSize = maxOf(minBuf, sampleRate) // at least half a second
        val r = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2)
        if (r.state != AudioRecord.STATE_INITIALIZED) {
            r.release()
            throw IllegalStateException("The microphone could not be opened for 16 kHz recording")
        }
        device?.let { r.preferredDevice = it }
        val f = RandomAccessFile(file, "rw")
        f.setLength(0)
        f.write(ByteArray(HEADER_SIZE)) // placeholder, patched in stop()
        out = f
        record = r
        running = true
        paused = false
        r.startRecording()
        thread = Thread({ loop(r, f, bufSize) }, "WavRecorder").also { it.start() }
    }

    private fun loop(r: AudioRecord, f: RandomAccessFile, bufSize: Int) {
        val buf = ShortArray(bufSize / 2)
        val bytes = ByteArray(buf.size * 2)
        while (running) {
            val n = r.read(buf, 0, buf.size)
            if (n <= 0) continue
            var peak = 0
            for (i in 0 until n) {
                val v = abs(buf[i].toInt())
                if (v > peak) peak = v
            }
            if (peak > peakSinceLastRead) peakSinceLastRead = peak
            if (paused) continue
            for (i in 0 until n) {
                val s = buf[i].toInt()
                bytes[i * 2] = (s and 0xff).toByte()
                bytes[i * 2 + 1] = ((s shr 8) and 0xff).toByte()
            }
            try {
                f.write(bytes, 0, n * 2)
                dataBytes += n * 2
            } catch (_: Exception) {
                running = false
            }
        }
    }

    fun pause() { paused = true }
    fun resume() { paused = false }

    /** Loudest sample (0..1) since the previous call. */
    fun takeLevel(): Float {
        val p = peakSinceLastRead
        peakSinceLastRead = 0
        return (p / 32767f).coerceIn(0f, 1f)
    }

    /** Stop and write the WAV header. Safe to call twice. */
    fun stop() {
        if (!running && record == null) return
        running = false
        thread?.join(2000)
        thread = null
        try { record?.stop() } catch (_: Exception) {}
        record?.release()
        record = null
        out?.let { f ->
            try {
                f.seek(0)
                f.write(wavHeader(dataBytes, sampleRate))
            } finally {
                f.close()
            }
        }
        out = null
    }

    companion object {
        const val HEADER_SIZE = 44

        /** Canonical 44-byte PCM WAV header for mono 16-bit audio. */
        fun wavHeader(dataBytes: Long, sampleRate: Int, channels: Int = 1): ByteArray {
            val byteRate = sampleRate * channels * 2
            val b = java.nio.ByteBuffer.allocate(HEADER_SIZE).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray(Charsets.US_ASCII))
            b.putInt((36 + dataBytes).toInt())
            b.put("WAVE".toByteArray(Charsets.US_ASCII))
            b.put("fmt ".toByteArray(Charsets.US_ASCII))
            b.putInt(16)                 // PCM chunk size
            b.putShort(1)                // PCM
            b.putShort(channels.toShort())
            b.putInt(sampleRate)
            b.putInt(byteRate)
            b.putShort((channels * 2).toShort()) // block align
            b.putShort(16)               // bits per sample
            b.put("data".toByteArray(Charsets.US_ASCII))
            b.putInt(dataBytes.toInt())
            return b.array()
        }
    }
}
