package com.dotancohen.voiceandroid.audio

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * Streams the loudness heard by one microphone, 0.0 (silence) to 1.0, about
 * ten times a second, for the "which microphone hears me best" test in the
 * recorder settings. Uses [AudioRecord] so a specific device can be chosen.
 */
object MicLevelMeter {
    private const val SAMPLE_RATE = 16_000

    @SuppressLint("MissingPermission") // The screen asks for RECORD_AUDIO before starting
    fun levels(mic: AudioDeviceInfo?): Flow<Float> = flow {
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufferSize = maxOf(minBuffer, SAMPLE_RATE / 10 * 2)
        val record = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
        if (mic != null) record.preferredDevice = mic
        val samples = ShortArray(SAMPLE_RATE / 10)
        try {
            record.startRecording()
            while (true) {
                val n = record.read(samples, 0, samples.size)
                if (n <= 0) break
                var sum = 0.0
                for (i in 0 until n) sum += samples[i].toDouble() * samples[i].toDouble()
                val rms = sqrt(sum / n)
                // Map -60 dBFS..0 dBFS onto 0..1
                val db = if (rms < 1.0) -60.0 else 20 * log10(rms / 32768.0)
                emit(((db + 60.0) / 60.0).coerceIn(0.0, 1.0).toFloat())
            }
        } finally {
            try { record.stop() } catch (_: Exception) {}
            record.release()
        }
    }.flowOn(Dispatchers.IO)
}
