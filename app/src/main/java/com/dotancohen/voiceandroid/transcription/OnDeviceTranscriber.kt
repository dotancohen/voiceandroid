package com.dotancohen.voiceandroid.transcription

import android.content.Context
import android.content.Intent
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import uniffi.voice_transcription.TranscriptionClient
import uniffi.voice_transcription.TranscriptionConfig
import uniffi.voice_transcription.createLocalWhisperClient
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a queued or running on-device transcription is doing. */
enum class TranscriptionStage { Queued, Converting, LoadingModel, Transcribing, Done, Failed }

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
)

/**
 * Runs Whisper on the phone. Jobs are queued here and executed one at a time
 * by [TranscriptionService] (a foreground service, so the OS does not kill a
 * twenty-minute transcription when the screen turns off). Each job creates a
 * "Pending..." transcription record first and then stores the result, exactly
 * the way the desktop's transcription service does, so peers see the same
 * thing whichever device transcribed.
 *
 * The loaded model is kept in memory between jobs and released when the
 * service stops.
 */
object OnDeviceTranscriber {
    private const val TAG = "OnDeviceTranscriber"
    const val SERVICE_NAME = "local_whisper"

    private val queue = ArrayDeque<TranscriptionJob>()
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
    fun enqueue(
        context: Context,
        audioFileId: String,
        filename: String,
        languageOverride: String? = null,
        modelOverride: String? = null
    ): String? {
        val prefs = TranscriptionPreferences(context)
        val model = WhisperModels.byId(modelOverride ?: prefs.modelId) ?: WhisperModels.byId(WhisperModels.DEFAULT_MODEL_ID)!!
        if (!WhisperModels.isInstalled(context, model)) {
            return "Download the model \"${model.title}\" under Settings → Transcription first"
        }
        synchronized(queue) {
            if (queue.any { it.audioFileId == audioFileId } || _current.value?.let { it.audioFileId == audioFileId && it.stage < TranscriptionStage.Done } == true) {
                return "This recording is already being transcribed"
            }
            queue.addLast(TranscriptionJob(audioFileId, filename, model.id, languageOverride ?: prefs.language, prefs.beamSize))
            _queued.value = queue.toList()
        }
        val intent = Intent(context, TranscriptionService::class.java)
        try {
            context.startForegroundService(intent)
        } catch (e: Exception) {
            // Android refuses a foreground service started while the app is in
            // the background (for example from the ADB receiver with the screen
            // off). Run in-process instead: the work is still done as long as
            // the process lives, only without the notification.
            AppLogger.w(TAG, "Foreground service refused (${e.message}); transcribing in-process")
            val app = context.applicationContext
            inProcessScope.launch { drain(app) { } }
        }
        return null
    }

    internal fun hasWork(): Boolean = synchronized(queue) { queue.isNotEmpty() }

    private val inProcessScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val draining = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Run every queued job, then return. Only one drain runs at a time: a
     * second caller (the service starting while an in-process drain is busy)
     * returns at once.
     */
    internal suspend fun drain(context: Context, onStage: (TranscriptionJob) -> Unit) {
        if (!draining.compareAndSet(false, true)) {
            // Another drain (in-process) is busy: stay until it is done, so a
            // foreground service that called us keeps the process alive and
            // on the fast cores for the whole job.
            while (draining.get()) kotlinx.coroutines.delay(500)
            return
        }
        try {
            while (true) {
                val job = synchronized(queue) { queue.removeFirstOrNull()?.also { _queued.value = queue.toList() } } ?: break
                run(context, job, onStage)
            }
        } finally {
            draining.set(false)
        }
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
                if (old.service == SERVICE_NAME && old.content.startsWith("Pending...") && old.deletedAt == null) {
                    val why = "the app was closed before the transcription finished"
                    repo.updateTranscriptionResult(old.id, "Error: $why", null, JSONObject().put("error", why).toString())
                }
            }
            val submitted = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            transcriptionId = repo.createTranscription(job.audioFileId, "Pending... ($submitted)", SERVICE_NAME, args.toString()).getOrThrow()
            job = job.copy(transcriptionId = transcriptionId)

            stage(TranscriptionStage.Converting, "Converting ${source.name} to 16 kHz")
            val target = File(context.cacheDir, "whisper-${job.audioFileId}.wav")
            wav = AudioToWav.convert(source, target)

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
            val result = c.transcribeWithConfig(wav!!.absolutePath, config)
            val elapsed = (System.currentTimeMillis() - startedAt) / 1000.0

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
            }
            repo.updateTranscriptionResult(
                transcriptionId,
                result.content.trim(),
                if (result.segments.isEmpty()) null else segments.toString(),
                response.toString()
            ).getOrThrow()
            AppLogger.i(TAG, "Transcribed ${source.name} with ${model.id} in ${"%.1f".format(Locale.US, elapsed)} s: ${result.content.take(60)}")
            stage(TranscriptionStage.Done, "Done in ${formatElapsed(elapsed)}")
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

    fun formatElapsed(seconds: Double): String {
        val s = seconds.toLong()
        return if (s >= 60) "${s / 60} min ${s % 60} s" else "$s s"
    }
}
