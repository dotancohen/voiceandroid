package com.dotancohen.voiceandroid.transcription

import org.json.JSONObject

/**
 * What one transcription cost, read back from its `service_response`.
 *
 * The same numbers the transcription's own details dialog shows, as numbers
 * rather than as sentences, because the queue does arithmetic with them: how
 * fast this phone transcribes decides how long the recordings still waiting
 * are going to take.
 *
 * Every field is nullable. A transcription made by an older version of the
 * application, or by a cloud service, recorded less than this one does.
 */
data class TranscriptionWork(
    /** How long the recording is. */
    val audioSeconds: Double? = null,
    /** Clock time for the whole job, conversion and model loading included. */
    val clockSeconds: Double? = null,
    /** Processor time, which is larger than the clock time when cores work at once. */
    val cpuSeconds: Double? = null,
    /** Roughly how many cores were kept busy. */
    val coresBusy: Double? = null,
    /** The largest the native heap reached: what decides whether a model fits. */
    val peakMemoryBytes: Long? = null,
    /** Seconds of audio transcribed per second of clock time. */
    val speedVsRealtime: Double? = null,
    val model: String? = null,
) {
    /**
     * Seconds of work per second of audio: the number that predicts a wait.
     *
     * Null unless both halves are known and the recording had some length.
     */
    val secondsPerAudioSecond: Double?
        get() {
            val audio = audioSeconds ?: return null
            val clock = clockSeconds ?: return null
            if (audio <= 0 || clock <= 0) return null
            return clock / audio
        }

    companion object {
        /** Read what was recorded, or an empty reading when nothing was. */
        fun of(serviceResponse: String?): TranscriptionWork {
            val json = try {
                JSONObject(serviceResponse ?: return TranscriptionWork())
            } catch (e: Exception) {
                return TranscriptionWork()
            }
            val performance = json.optJSONObject("performance")

            fun number(from: JSONObject?, key: String): Double? {
                val value = from?.optDouble(key, Double.NaN) ?: return null
                return if (value.isNaN()) null else value
            }

            return TranscriptionWork(
                audioSeconds = number(json, "duration_seconds"),
                // The whole job where it was recorded, the transcribing call
                // otherwise: what the user waits for is the whole job.
                clockSeconds = number(performance, "total_seconds")
                    ?: number(performance, "elapsed_seconds")
                    ?: number(json, "elapsed_time"),
                cpuSeconds = number(performance, "cpu_seconds"),
                coresBusy = number(performance, "cpu_cores_busy"),
                peakMemoryBytes = performance?.optLong("peak_native_heap_bytes", 0L)?.takeIf { it > 0 },
                speedVsRealtime = number(performance, "speed_vs_realtime"),
                model = performance?.optString("model")?.takeIf { it.isNotEmpty() }
                    ?: json.optString("model").takeIf { it.isNotEmpty() },
            )
        }
    }
}

/**
 * How long the recordings still waiting are going to take.
 *
 * This is the question the queue exists to answer: a user who needs one
 * particular transcription wants to know whether it is five minutes away or an
 * hour, and whether moving it to the front is worth doing. The estimate comes
 * from what this phone actually did, not from a specification: Whisper on a
 * phone depends on the model, the cores, and how hot the phone already is.
 */
object QueueEstimate {

    /**
     * Seconds of work per second of audio, from finished transcriptions.
     *
     * Readings for the same model are used where there are any, because a
     * larger model is several times slower; otherwise every reading is used.
     * The median is taken rather than the mean: one transcription that ran
     * while the phone was busy with something else should not move the
     * estimate for everything behind it.
     *
     * Null when nothing has finished yet on this phone, which is honest — the
     * first transcription after installing has nothing to go on.
     */
    fun rateFrom(finished: List<TranscriptionWork>, model: String? = null): Double? {
        val forModel = finished.filter { model != null && it.model == model }
        val pool = (if (forModel.isNotEmpty()) forModel else finished)
            .mapNotNull { it.secondsPerAudioSecond }
            .sorted()
        if (pool.isEmpty()) return null
        return pool[pool.size / 2]
    }

    /**
     * How long until a recording is done, in seconds.
     *
     * [audioSecondsAhead] is every recording in front of it in the queue, and
     * [ownAudioSeconds] is its own length; the recording being worked on now
     * counts for what is left of it. Null when the rate is not known or a
     * length is missing, because a made-up number here is worse than none: the
     * user would plan around it.
     */
    fun waitSeconds(
        audioSecondsAhead: Double?,
        ownAudioSeconds: Double?,
        rate: Double?,
    ): Double? {
        if (rate == null || rate <= 0) return null
        val ahead = audioSecondsAhead ?: return null
        val own = ownAudioSeconds ?: return null
        if (ahead < 0 || own < 0) return null
        return (ahead + own) * rate
    }

    /** A wait in words: "about 4 minutes", "about 1 hour 10 minutes". */
    fun inWords(seconds: Double?): String? {
        if (seconds == null || seconds <= 0) return null
        val whole = Math.round(seconds)
        return when {
            whole < 90 -> "about a minute"
            whole < 3600 -> "about ${Math.round(whole / 60.0)} minutes"
            else -> {
                val hours = whole / 3600
                val minutes = Math.round((whole % 3600) / 60.0)
                if (minutes == 0L) "about $hours hour${if (hours == 1L) "" else "s"}"
                else "about $hours hour${if (hours == 1L) "" else "s"} $minutes minutes"
            }
        }
    }
}
