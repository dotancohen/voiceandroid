package com.dotancohen.voiceandroid.transcription

/**
 * The order recordings are transcribed in.
 *
 * Kept apart from [OnDeviceTranscriber] so that the ordering can be tested for
 * what it is — first in, first out, with the user able to move one recording to
 * the front — without a phone, a model or a foreground service. The transcriber
 * owns one of these and does the work; this decides only what comes next.
 *
 * Every method is synchronised on the queue itself: recordings are added from a
 * screen, taken by the service's worker, and reordered from the queue screen.
 */
class JobQueue {
    private val jobs = ArrayDeque<TranscriptionJob>()

    /** What is waiting, in the order it will be worked. */
    fun list(): List<TranscriptionJob> = synchronized(jobs) { jobs.toList() }

    fun isEmpty(): Boolean = synchronized(jobs) { jobs.isEmpty() }

    val size: Int get() = synchronized(jobs) { jobs.size }

    /**
     * Put a recording at the back of the queue.
     *
     * Refused when that recording is already waiting: asking twice for the same
     * recording is a slip, and transcribing it twice would cost the user an
     * extra wait for nothing.
     */
    fun add(job: TranscriptionJob): Boolean = synchronized(jobs) {
        if (jobs.any { it.audioFileId == job.audioFileId }) return false
        jobs.addLast(job)
        true
    }

    /** Take the next recording to work on, or null when nothing is waiting. */
    fun takeNext(): TranscriptionJob? = synchronized(jobs) { jobs.removeFirstOrNull() }

    fun clear() = synchronized(jobs) { jobs.clear() }

    fun holds(audioFileId: String): Boolean =
        synchronized(jobs) { jobs.any { it.audioFileId == audioFileId } }

    /**
     * Move a waiting recording to the front, so it is transcribed next.
     *
     * False when it is not waiting, or is already at the front. The recording
     * being worked on is not this queue's business: it has already been taken
     * out, and is not interrupted (see [OnDeviceTranscriber.doNext]).
     */
    fun doNext(audioFileId: String): Boolean = synchronized(jobs) {
        val index = jobs.indexOfFirst { it.audioFileId == audioFileId }
        if (index <= 0) return false
        val job = jobs.removeAt(index)
        jobs.addFirst(job)
        true
    }

    /** Take a waiting recording out. False when it was not waiting. */
    fun remove(audioFileId: String): Boolean = synchronized(jobs) {
        val index = jobs.indexOfFirst { it.audioFileId == audioFileId }
        if (index < 0) return false
        jobs.removeAt(index)
        true
    }

    /**
     * Where a recording will be reached: 1 is next, null when it is not waiting.
     *
     * The queue screen shows the newest first, like every other list in the
     * application, so this is what tells the user the real order.
     */
    fun positionOf(audioFileId: String): Int? = synchronized(jobs) {
        val index = jobs.indexOfFirst { it.audioFileId == audioFileId }
        if (index < 0) null else index + 1
    }

    /**
     * How much audio is in front of a recording, in seconds.
     *
     * Null when any recording in front of it has no length recorded: the wait
     * cannot be estimated from a guess, and saying nothing is the honest
     * answer. See [QueueEstimate].
     */
    fun audioSecondsAhead(audioFileId: String): Double? = synchronized(jobs) {
        var total = 0.0
        for (job in jobs) {
            if (job.audioFileId == audioFileId) return total
            val seconds = job.durationSeconds ?: return null
            total += seconds.toDouble()
        }
        return null
    }
}
