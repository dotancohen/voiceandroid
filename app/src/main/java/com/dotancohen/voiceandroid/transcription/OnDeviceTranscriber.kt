package com.dotancohen.voiceandroid.transcription

import android.content.Context
import android.os.Build
import android.os.Debug
import android.os.Process
import android.content.Intent
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.audio.WavRecorder
import com.dotancohen.voiceandroid.util.Durations
import com.dotancohen.voiceandroid.util.Magic
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uniffi.voice_transcription.TranscriptionClient
import uniffi.voice_transcription.TranscriptionConfig
import uniffi.voice_transcription.TranscriptionException
import uniffi.voice_transcription.clearTranscriptionCancel
import uniffi.voice_transcription.createLocalWhisperClient
import uniffi.voice_transcription.requestTranscriptionCancel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a queued or running on-device transcription is doing. */
enum class TranscriptionStage { Queued, Converting, LoadingModel, Transcribing, Done, Failed, Stopped }

data class TranscriptionJob(
    val audioFileId: String,
    val filename: String,
    val modelId: String,
    val language: String,
    val beamSize: Int,
    val stage: TranscriptionStage = TranscriptionStage.Queued,
    val message: String = "",
    val transcriptionId: String? = null,
    val startedAt: Long = System.currentTimeMillis(),
    /**
     * How long the recording is, where it is known.
     *
     * The queue needs it to say how long the waiting will take: a recording's
     * length is what the work is proportional to. Null where the caller did
     * not know, in which case no estimate is offered for it rather than a
     * guess being made.
     */
    val durationSeconds: Long? = null,
    /** Which note holds the recording, filled in by the queue screen. */
    val noteId: String? = null,
)

/**
 * Runs Whisper on the phone. Jobs are queued here and executed strictly one
 * at a time by [TranscriptionService] (a foreground service, so the OS does
 * not kill a twenty-minute transcription when the screen turns off); the
 * model needs about a gigabyte of memory and every core, so a second job
 * would only slow the first one down. Queueing a whole selection of notes is
 * therefore safe. Each job creates a
 * "Pending..." transcription record first and then stores the result, exactly
 * the way the desktop's transcription service does, so devices see the same
 * thing whichever device transcribed.
 *
 * The loaded model is kept in memory between jobs and released when the
 * service stops.
 */
object OnDeviceTranscriber {
    private const val TAG = "OnDeviceTranscriber"
    const val SERVICE_NAME = "local_whisper"
    /** What a transcription cut short by the app closing is left saying. */
    const val INTERRUPTED_PREFIX = "Error: the app was closed"
    /** What a transcription says while it is still being worked on. */
    const val PENDING_PREFIX = "Pending..."

    /** What is waiting, and in what order: see [JobQueue]. */
    private val queue = JobQueue()
    private val _current = MutableStateFlow<TranscriptionJob?>(null)
    /** The job running now (or the last one that finished, until the next starts). */
    val current: StateFlow<TranscriptionJob?> = _current.asStateFlow()
    private val _queued = MutableStateFlow<List<TranscriptionJob>>(emptyList())
    val queued: StateFlow<List<TranscriptionJob>> = _queued.asStateFlow()
    private val _finished = MutableSharedFlow<TranscriptionJob>(extraBufferCapacity = 16)
    /** Emits every job when it ends, so a note screen can reload its transcriptions. */
    val finished: SharedFlow<TranscriptionJob> = _finished.asSharedFlow()

    private var loadedModelPath: String? = null
    private var client: TranscriptionClient? = null

    /**
     * Queue one audio file and make sure the service is running. Returns an
     * error text when nothing can be done (no model downloaded, file missing).
     */
    /**
     * What the user is told when a recording is too long for this phone.
     *
     * Names the limit and where the work can be done instead, because the
     * recording is not the problem: the phone is.
     */
    fun tooLongForThisPhone(durationSeconds: Long): String {
        val minutes = Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE / 60
        val length = Durations.ofSeconds(durationSeconds.toInt())
        return "This recording is $length long. A phone transcribes up to $minutes minutes; " +
            "transcribe it on the desktop after the next sync."
    }

    fun enqueue(
        context: Context,
        audioFileId: String,
        filename: String,
        languageOverride: String? = null,
        modelOverride: String? = null,
        /**
         * How long the recording is, where the caller knows. A recording past
         * [Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE] is refused here; one whose
         * length is not known is refused by the job once the audio has been
         * converted and its length is certain.
         */
        durationSeconds: Long? = null,
    ): String? {
        if (durationSeconds != null && durationSeconds > Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE) {
            return tooLongForThisPhone(durationSeconds)
        }
        // A stop asked for earlier must not kill the job the user is asking
        // for now.
        clearTranscriptionCancel()
        val prefs = TranscriptionPreferences(context)
        val model = WhisperModels.byId(modelOverride ?: prefs.modelId) ?: WhisperModels.byId(WhisperModels.DEFAULT_MODEL_ID)!!
        if (!WhisperModels.isInstalled(context, model)) {
            return "Download the model \"${model.title}\" under Settings → Transcription first"
        }
        val running = _current.value?.let {
            it.audioFileId == audioFileId && it.stage < TranscriptionStage.Done
        } == true
        if (running) return "This recording is already being transcribed"
        val added = queue.add(
            TranscriptionJob(
                audioFileId, filename, model.id,
                languageOverride ?: prefs.language, prefs.beamSize,
                durationSeconds = durationSeconds,
            )
        )
        if (!added) return "This recording is already being transcribed"
        _queued.value = queue.list()
        val intent = Intent(context, TranscriptionService::class.java)
        return try {
            context.startForegroundService(intent)
            null
        } catch (e: Exception) {
            // Android refuses to start a foreground service for an app that
            // is not on screen (from the ADB receiver with the screen off,
            // say). The job stays in the queue and starts when the app is
            // next opened, which is what MainActivity.onStart asks for.
            //
            // It is deliberately *not* run in this process instead. Android
            // freezes a background process within seconds, so that work
            // stops where it stands: no progress, no error, and a
            // "Pending..." row that stays pending for ever. That is where
            // "the app was closed before the transcription finished" came
            // from.
            AppLogger.w(TAG, "Foreground service refused (${e.message}); the job waits until the app is opened")
            "Transcription will start when the app is open"
        }
    }

    internal fun hasWork(): Boolean = !queue.isEmpty()

    /**
     * Put a waiting recording at the front, so it is transcribed next.
     *
     * The job running now is not interrupted: Whisper holds the whole
     * recording in memory and is minutes into its work, and throwing that away
     * to start again later would cost more than the wait. "Next" therefore
     * means next after the one in progress.
     *
     * Returns true when the queue changed. False when that recording is not
     * waiting — it is already running, already done, or was never queued.
     */
    fun doNext(audioFileId: String): Boolean {
        if (!queue.doNext(audioFileId)) return false
        _queued.value = queue.list()
        AppLogger.i(TAG, "Moved $audioFileId to the front of the transcription queue")
        return true
    }

    /**
     * Take a waiting recording out of the queue.
     *
     * Only one that is waiting: see [doNext] for why the running job is left
     * alone. [stopAll] is what stops that one.
     */
    fun remove(audioFileId: String): Boolean {
        if (!queue.remove(audioFileId)) return false
        _queued.value = queue.list()
        return true
    }

    /**
     * The order the waiting recordings will actually be worked in.
     *
     * The queue screen shows them newest first, like every other list in the
     * application, so the position a recording will be reached in has to be
     * said rather than read off the screen.
     */
    fun positionOf(audioFileId: String): Int? = queue.positionOf(audioFileId)

    /**
     * How much audio is in front of a waiting recording, in seconds.
     *
     * Null when any recording in front of it has no length recorded, so that no
     * estimate is offered rather than a wrong one. See [QueueEstimate].
     */
    fun audioSecondsAhead(audioFileId: String): Double? = queue.audioSecondsAhead(audioFileId)

    /**
     * Start the queue under a foreground service now that the app is on
     * screen.
     *
     * Android refuses to start a foreground service for an app that is not
     * on screen, so a transcription asked for from the background (the ADB
     * receiver, say) is left waiting in the queue. The restriction is lifted
     * once an activity is showing, so the activity calls this from
     * `onStart`: the waiting recordings are transcribed under a service,
     * with its notification, and carry on when the app is closed again.
     */
    fun ensureServiceRunning(context: Context) {
        if (!hasWork() && !draining.get()) return
        if (TranscriptionService.isRunning) return
        try {
            context.startForegroundService(Intent(context, TranscriptionService::class.java))
        } catch (e: Exception) {
            AppLogger.w(TAG, "Could not put the transcription under a service: ${e.message}")
        }
    }

    /**
     * Stop everything: forget what is waiting and cut short what is running.
     *
     * Whisper is inside one long call into native code, so it is stopped
     * from the inside: [requestTranscriptionCancel] raises a flag that the
     * model checks between windows of audio, and the call returns within a
     * second or so. The partial text is thrown away, and the "Pending..."
     * record of the stopped job is deleted, so the recording is left exactly
     * as it was before the user asked for it to be transcribed.
     *
     * This is what the Stop button on the notification calls.
     */
    fun stopAll() {
        queue.clear()
        _queued.value = emptyList()
        requestTranscriptionCancel()
        AppLogger.i(TAG, "Stopping transcription at the user's request")
    }

    private val draining = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Run every queued job, then return. Only one drain runs at a time: a
     * second caller (a service started again while the first is busy) waits
     * for the first to finish rather than running the queue twice.
     */
    internal suspend fun drain(context: Context, onStage: (TranscriptionJob) -> Unit) {
        if (!draining.compareAndSet(false, true)) {
            // Another drain is busy: stay until it is done, so the service
            // that called us keeps the process alive and on the fast cores
            // for the whole job.
            while (draining.get()) kotlinx.coroutines.delay(500)
            return
        }
        try {
            while (true) {
                val job = queue.takeNext()?.also { _queued.value = queue.list() } ?: break
                run(context, job, onStage)
                if (uniffi.voice_transcription.transcriptionCancelRequested()) {
                    // The user stopped the queue while that job was running.
                    queue.clear()
                    _queued.value = emptyList()
                    break
                }
            }
        } finally {
            draining.set(false)
        }
    }

/**
 * What the phone was doing while a transcription ran.
 *
 * Whisper on a phone is minutes of solid work, and how long it takes depends
 * on the model, the length of the recording, the memory the phone can spare
 * and how hot it is already. These numbers are recorded with every
 * transcription so that the choices can be compared afterwards on real
 * recordings rather than guessed at.
 *
 * Memory is watched rather than measured once: whisper.cpp allocates outside
 * the Java heap, and the peak is what decides whether a model fits on a
 * phone at all.
 */
private class WorkWatch {
    private val startWall = System.currentTimeMillis()
    private val startCpuMs = Process.getElapsedCpuTime()
    private val startNativeHeap = Debug.getNativeHeapAllocatedSize()
    private var peakNativeHeap = startNativeHeap
    private var peakJavaHeap = usedJavaHeap()
    private var samples = 0

    /** Take one reading. Cheap enough to call every second. */
    fun sample() {
        samples++
        val native = Debug.getNativeHeapAllocatedSize()
        if (native > peakNativeHeap) peakNativeHeap = native
        val java = usedJavaHeap()
        if (java > peakJavaHeap) peakJavaHeap = java
    }

    private fun usedJavaHeap(): Long =
        Runtime.getRuntime().let { it.totalMemory() - it.freeMemory() }

    /**
     * The readings, for the transcription's service response.
     *
     * [audioSeconds] is the length of the recording, which is what makes the
     * rest comparable: a transcription is fast or slow only relative to the
     * audio it was given.
     */
    fun report(audioSeconds: Double?): JSONObject {
        sample()
        val wallMs = System.currentTimeMillis() - startWall
        val cpuMs = Process.getElapsedCpuTime() - startCpuMs
        return JSONObject().apply {
            put("elapsed_seconds", wallMs / 1000.0)
            put("cpu_seconds", cpuMs / 1000.0)
            // More than one when several cores worked at once, which is what
            // whisper.cpp does; roughly the number of cores it kept busy.
            put("cpu_cores_busy", if (wallMs > 0) round3(cpuMs.toDouble() / wallMs) else JSONObject.NULL)
            put(
                "speed_vs_realtime",
                if (audioSeconds != null && audioSeconds > 0 && wallMs > 0) {
                    round3(audioSeconds / (wallMs / 1000.0))
                } else JSONObject.NULL
            )
            put("peak_native_heap_bytes", peakNativeHeap)
            put("native_heap_growth_bytes", peakNativeHeap - startNativeHeap)
            put("peak_java_heap_bytes", peakJavaHeap)
            put("java_heap_limit_bytes", Runtime.getRuntime().maxMemory())
            put("cpu_cores", Runtime.getRuntime().availableProcessors())
            put("samples", samples)
            put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("android_version", Build.VERSION.RELEASE)
            put("android_sdk", Build.VERSION.SDK_INT)
        }
    }

    private fun round3(value: Double): Double = Math.round(value * 1000) / 1000.0
}

    private suspend fun run(context: Context, start: TranscriptionJob, onStage: (TranscriptionJob) -> Unit) = withContext(Dispatchers.IO) {
        var job = start
        fun stage(s: TranscriptionStage, msg: String = "") { job = job.copy(stage = s, message = msg); _current.value = job; onStage(job) }
        val repo = VoiceRepository.getInstance(context)
        var transcriptionId: String? = null
        val startedAt = System.currentTimeMillis()
        var wav: File? = null
        try {
            repo.initialize().getOrThrow()
            val model = WhisperModels.byId(job.modelId) ?: throw IllegalStateException("Unknown model ${job.modelId}")
            val modelFile = WhisperModels.file(context, model)
            if (!WhisperModels.isInstalled(context, model)) throw IllegalStateException("Model ${model.title} is not downloaded")
            val path = repo.getAudioFilePath(job.audioFileId).getOrThrow()
                ?: throw IllegalStateException("The audio file is not on this phone")
            val source = File(path)

            val language = job.language.takeIf { it != TranscriptionPreferences.LANGUAGE_AUTO }
            val args = JSONObject().apply {
                put("provider_id", SERVICE_NAME)
                put("model", model.id)
                put("model_file", model.fileName)
                put("language", language ?: JSONObject.NULL)
                put("beam_size", job.beamSize)
                put("device", "android")
            }
            // A "Pending..." row left by a run the OS killed would stay pending forever
            for (old in repo.getTranscriptionsForAudioFile(job.audioFileId).getOrNull().orEmpty()) {
                if (old.service == SERVICE_NAME && old.content.startsWith(PENDING_PREFIX) && old.deletedAt == null) {
                    val why = "the app was closed before the transcription finished"
                    // Same wording as INTERRUPTED_PREFIX, which is how these
                    // are recognised and removed once the recording really
                    // has been transcribed.
                    repo.updateTranscriptionResult(old.id, "Error: $why", null, JSONObject().put("error", why).toString())
                }
            }
            // With the offset, so the time is readable on a device in another zone
            val submitted = SimpleDateFormat("yyyy-MM-dd HH:mm:ssXXX", Locale.US).format(Date())
            transcriptionId = repo.createTranscription(job.audioFileId, "Pending... ($submitted)", SERVICE_NAME, args.toString()).getOrThrow()
            job = job.copy(transcriptionId = transcriptionId)

            stage(TranscriptionStage.Converting, "Converting ${source.name} to 16 kHz")
            val target = File(context.cacheDir, "whisper-${job.audioFileId}.wav")
            wav = AudioToWav.convert(source, target)

            // The converted file gives the length exactly: 16 kHz, mono,
            // 16-bit is 32 000 bytes a second. This is the backstop for a
            // recording whose length the database did not know when the work
            // was asked for.
            val seconds = ((wav!!.length() - WavRecorder.HEADER_SIZE).coerceAtLeast(0)) / 32_000
            if (seconds > Magic.TRANSCRIBE_MAX_SECONDS_ON_PHONE) {
                throw IllegalStateException(tooLongForThisPhone(seconds))
            }
            // Now the length is certain, so the queue can say what the rest
            // of the work will take even for a recording whose length the
            // database did not know.
            if (job.durationSeconds == null) {
                job = job.copy(durationSeconds = seconds)
                _current.value = job
            }

            stage(TranscriptionStage.LoadingModel, "Loading ${model.title}")
            val c = clientFor(modelFile)

            stage(TranscriptionStage.Transcribing, "Transcribing ${source.name} with ${model.title}" + (language?.let { " (${TranscriptionPreferences.languageTitle(it)})" } ?: ""))
            val config = TranscriptionConfig(
                language = language,
                speakerCount = null,
                wordTimestamps = false,
                model = null,
                beamSize = job.beamSize.toUInt(),
            )
            // Watch the phone while whisper works, so the cost of a model on
            // a real recording can be read afterwards rather than guessed.
            val watch = WorkWatch()
            val watcher = launch {
                while (isActive) {
                    watch.sample()
                    delay(1000)
                }
            }
            val result = try {
                c.transcribeWithConfig(wav!!.absolutePath, config)
            } finally {
                watcher.cancel()
            }
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0
            val performance = watch.report(result.durationSeconds).apply {
                put("total_seconds", round3ms(System.currentTimeMillis() - startedAt))
                put("model", model.id)
                put("model_bytes", modelFile.length())
                put("beam_size", job.beamSize)
                put("audio_bytes", source.length())
                put("converted_wav_bytes", wav?.length() ?: 0L)
            }

            val segments = JSONArray()
            for (s in result.segments) {
                segments.put(JSONObject().apply {
                    put("text", s.text)
                    put("start_seconds", s.startSeconds)
                    put("end_seconds", s.endSeconds)
                    put("speaker", s.speaker ?: JSONObject.NULL)
                    put("confidence", s.confidence ?: JSONObject.NULL)
                })
            }
            val response = JSONObject().apply {
                put("elapsed_time", Math.round(elapsed * 1000) / 1000.0)
                put("duration_seconds", result.durationSeconds ?: JSONObject.NULL)
                put("languages", result.languages?.let { JSONArray(it) } ?: JSONObject.NULL)
                put("confidence", result.confidence ?: JSONObject.NULL)
                put("speaker_count", result.speakerCount?.toInt() ?: JSONObject.NULL)
                put("segment_count", result.segments.size)
                put("model", model.id)
                put("device", "android")
                put("performance", performance)
            }
            repo.updateTranscriptionResult(
                transcriptionId,
                result.content.trim(),
                if (result.segments.isEmpty()) null else segments.toString(),
                response.toString()
            ).getOrThrow()
            removeInterruptedPlaceholders(repo, job.audioFileId, transcriptionId)
            AppLogger.i(TAG, "Transcribed ${source.name} with ${model.id} in ${"%.1f".format(Locale.US, elapsed)} s: ${result.content.take(60)}")
            stage(TranscriptionStage.Done, "Done in ${formatElapsed(elapsed)}")
        } catch (e: TranscriptionException.Cancelled) {
            // The user pressed Stop. Nothing went wrong, so no error is
            // recorded: the pending row is removed and the recording is left
            // as it was.
            AppLogger.i(TAG, "Transcription of ${job.filename} stopped by the user")
            transcriptionId?.let { repo.deleteTranscription(it) }
            stage(TranscriptionStage.Stopped, "Stopped")
        } catch (e: Throwable) {
            AppLogger.e(TAG, "On-device transcription failed for ${job.audioFileId}", e)
            val msg = e.message ?: e.toString()
            transcriptionId?.let {
                repo.updateTranscriptionResult(it, "Error: $msg", null, JSONObject().put("error", msg).toString())
            }
            if (e is OutOfMemoryError || msg.contains("alloc", ignoreCase = true)) releaseModel()
            stage(TranscriptionStage.Failed, msg)
        } finally {
            if (wav != null && wav != File(repo.getAudioFilePath(job.audioFileId).getOrNull() ?: "")) wav.delete()
            _finished.tryEmit(job)
        }
    }

    /**
     * Remove the placeholders left by attempts that never finished.
     *
     * When the phone stops the app in the middle of a transcription, the
     * next run finds a "Pending..." row that will never be filled in and
     * turns it into "Error: the app was closed before the transcription
     * finished", so the user is told what became of it. Once the recording
     * really has been transcribed that message is only noise, and a note
     * that shows an error next to its transcription reads as though the
     * transcription failed. So it is deleted (a soft delete, so the removal
     * reaches the other devices too).
     *
     * Only these placeholders go. A transcription that failed for a real
     * reason (no model, a broken file) is kept: the user should see why.
     */
    private suspend fun removeInterruptedPlaceholders(
        repo: VoiceRepository,
        audioFileId: String,
        keepId: String,
    ) {
        for (t in repo.getTranscriptionsForAudioFile(audioFileId).getOrNull().orEmpty()) {
            if (t.id == keepId) continue
            if (isInterruptedPlaceholder(t.service, t.content, t.deletedAt?.at)) {
                AppLogger.i(TAG, "Removing the interrupted transcription ${t.id} of $audioFileId")
                repo.deleteTranscription(t.id)
            }
        }
    }

    /**
     * Whether this transcription is a leftover of an attempt that never
     * finished, and so should be removed once the recording has really been
     * transcribed.
     *
     * Two shapes count: the "Pending..." row of a run that was killed before
     * it could store anything, and the "Error: the app was closed..." that a
     * later run writes over it. Anything else stays, including a
     * transcription that failed for a reason the user needs to see (no
     * model, an unreadable file) and anything a different service produced.
     */
    internal fun isInterruptedPlaceholder(service: String, content: String, deletedAtSeconds: Long?): Boolean {
        if (deletedAtSeconds != null) return false
        if (service != SERVICE_NAME) return false
        return content.startsWith(INTERRUPTED_PREFIX) || content.startsWith(PENDING_PREFIX)
    }

    private fun clientFor(modelFile: File): TranscriptionClient {
        val existing = client
        if (existing != null && loadedModelPath == modelFile.absolutePath) return existing
        releaseModel()
        val c = createLocalWhisperClient(modelFile.absolutePath)
        client = c
        loadedModelPath = modelFile.absolutePath
        return c
    }

    /** Free the model's memory (called when the service stops). */
    internal fun releaseModel() {
        try { client?.close() } catch (_: Exception) {}
        client = null
        loadedModelPath = null
    }

    /** Milliseconds as seconds, to three places. */
    private fun round3ms(millis: Long): Double = Math.round(millis / 1000.0 * 1000) / 1000.0

    fun formatElapsed(seconds: Double): String {
        val s = seconds.toLong()
        return if (s >= 60) "${s / 60} min ${s % 60} s" else "$s s"
    }
}
