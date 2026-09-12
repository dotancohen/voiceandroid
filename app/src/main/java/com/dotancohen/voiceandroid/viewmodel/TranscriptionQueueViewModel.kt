package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.Transcription
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.data.isError
import com.dotancohen.voiceandroid.data.isPending
import com.dotancohen.voiceandroid.transcription.OnDeviceTranscriber
import com.dotancohen.voiceandroid.transcription.QueueEstimate
import com.dotancohen.voiceandroid.transcription.TranscriptionJob
import com.dotancohen.voiceandroid.transcription.TranscriptionStage
import com.dotancohen.voiceandroid.transcription.TranscriptionWork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * One row of the transcription queue.
 *
 * A row is the same thing whether the recording is waiting, being worked on or
 * finished; what differs is which fields are filled in. [note] and [noteLine]
 * are what makes a row mean something: a filename of digits says nothing, the
 * note the recording belongs to says everything.
 */
data class QueueRow(
    val audioFileId: String,
    val filename: String,
    val stage: TranscriptionStage,
    val model: String?,
    val language: String?,
    /** The note the recording is in, for the preview. */
    val noteId: String? = null,
    /** The note's first line, so the row says which note it is. */
    val noteLine: String? = null,
    /** How long the recording is. */
    val audioSeconds: Double? = null,
    /** Where it will be reached in the queue: 1 is next. Waiting rows only. */
    val position: Int? = null,
    /** How long until this one is done, in seconds. Null when it cannot be told. */
    val waitSeconds: Double? = null,
    /** What the work cost. Finished rows only. */
    val work: TranscriptionWork? = null,
    /** How many characters the transcription came to. Finished rows only. */
    val characters: Int? = null,
    /** What became of it, in the words the user reads. */
    val outcome: String? = null,
    /** When it finished, as Unix seconds. Finished rows only. */
    val finishedAt: Long? = null,
)

/**
 * Everything in the queue, in three groups.
 *
 * Waiting first, then what is being worked on, then what is done — reading down
 * the screen is reading forwards in time. Within each group the newest is at the
 * top, as in every other list in the application, so the order a recording will
 * actually be reached in is said on the row ([QueueRow.position]) rather than
 * left to be inferred.
 */
data class QueueView(
    val waiting: List<QueueRow> = emptyList(),
    val processing: List<QueueRow> = emptyList(),
    val completed: List<QueueRow> = emptyList(),
    /**
     * Seconds of work per second of audio on this phone, from what it has
     * already done. Null until something has finished here.
     */
    val rate: Double? = null,
)

/**
 * Settings → Transcription queue.
 *
 * Transcribing on a phone is minutes of solid work per recording, one at a
 * time, so a queue of a dozen notes is an hour of waiting. This is where that
 * hour is accounted for: what is waiting and how long it will take, what is
 * being worked on, and what the finished ones cost in clock time, processor
 * time and memory — which is how the user decides whether a bigger model is
 * worth it, and whether to move one recording to the front.
 */
class TranscriptionQueueViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)

    private val _view = MutableStateFlow(QueueView())
    val view: StateFlow<QueueView> = _view.asStateFlow()

    private val _preview = MutableStateFlow<NoteWithAudioFiles?>(null)
    /** The note row shown when a queue row is tapped. */
    val preview: StateFlow<NoteWithAudioFiles?> = _preview.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** Notes a recording belongs to, remembered so the list does not ask twice. */
    private val noteOf = mutableMapOf<String, Pair<String, String>?>()

    init {
        // The queue changes under us: a job starts, finishes, or is queued
        // from a note screen. Follow all three flows.
        viewModelScope.launch {
            OnDeviceTranscriber.queued.collect { refresh() }
        }
        viewModelScope.launch {
            OnDeviceTranscriber.current.collect { refresh() }
        }
        viewModelScope.launch {
            OnDeviceTranscriber.finished.collect { refresh() }
        }
    }

    /** Read the queue and the finished work again. */
    fun refresh() {
        viewModelScope.launch {
            val running = OnDeviceTranscriber.current.value
            val queued = OnDeviceTranscriber.queued.value

            val completed = withContext(Dispatchers.IO) {
                repository.getRecentTranscriptions(OnDeviceTranscriber.SERVICE_NAME, COMPLETED_SHOWN)
                    .getOrDefault(emptyList())
                    .filter { !it.isPending }
            }
            val finishedWork = completed.map { TranscriptionWork.of(it.serviceResponse) }

            // Lengths for the queued recordings, where the job did not carry
            // one: the estimate needs them, and the database knows.
            val lengths = withContext(Dispatchers.IO) { lengthsFor(queued + listOfNotNull(running)) }

            val rate = QueueEstimate.rateFrom(finishedWork, running?.modelId ?: queued.firstOrNull()?.modelId)

            // The queue runs oldest first, so the audio in front of a job is
            // the audio of everything before it in the run order.
            val runOrder = queued
            var ahead = running?.let { job ->
                // What is left of the recording being worked on now, as well
                // as can be told: its whole length, less nothing. A better
                // figure would need progress from inside whisper.cpp, which
                // it does not report.
                lengths[job.audioFileId] ?: job.durationSeconds?.toDouble()
            } ?: 0.0

            val waitingRows = mutableListOf<QueueRow>()
            for ((index, job) in runOrder.withIndex()) {
                val own = lengths[job.audioFileId] ?: job.durationSeconds?.toDouble()
                val note = noteFor(job.audioFileId)
                waitingRows += QueueRow(
                    audioFileId = job.audioFileId,
                    filename = job.filename,
                    stage = TranscriptionStage.Queued,
                    model = job.modelId,
                    language = job.language,
                    noteId = note?.first,
                    noteLine = note?.second,
                    audioSeconds = own,
                    position = index + 1,
                    waitSeconds = QueueEstimate.waitSeconds(ahead, own, rate),
                )
                if (own != null && ahead != null) ahead += own
            }

            val processingRows = running
                ?.takeIf { it.stage < TranscriptionStage.Done }
                ?.let { job ->
                    val note = noteFor(job.audioFileId)
                    listOf(
                        QueueRow(
                            audioFileId = job.audioFileId,
                            filename = job.filename,
                            stage = job.stage,
                            model = job.modelId,
                            language = job.language,
                            noteId = note?.first,
                            noteLine = note?.second,
                            audioSeconds = lengths[job.audioFileId] ?: job.durationSeconds?.toDouble(),
                            outcome = job.message.ifBlank { "Working" },
                        )
                    )
                }
                .orEmpty()

            val completedRows = completed.mapIndexed { index, transcription ->
                val note = noteFor(transcription.audioFileId)
                val work = finishedWork[index]
                QueueRow(
                    audioFileId = transcription.audioFileId,
                    filename = filenameOf(transcription),
                    stage = if (transcription.isError) TranscriptionStage.Failed else TranscriptionStage.Done,
                    model = work.model,
                    language = null,
                    noteId = note?.first,
                    noteLine = note?.second,
                    audioSeconds = work.audioSeconds,
                    work = work,
                    characters = transcription.content.length,
                    outcome = if (transcription.isError) transcription.content.take(120) else null,
                    finishedAt = transcription.modifiedAt?.at ?: transcription.createdAt.at,
                )
            }

            // Newest first within each group, like every other list here. The
            // waiting group carries its run position, so nothing is hidden by
            // the order.
            _view.value = QueueView(
                waiting = waitingRows.reversed(),
                processing = processingRows,
                completed = completedRows,
                rate = rate,
            )
        }
    }

    /** Transcribe this recording next, after the one being worked on. */
    fun doNext(audioFileId: String) {
        if (OnDeviceTranscriber.doNext(audioFileId)) {
            _message.value = "It will be transcribed next"
        } else {
            _message.value = "That recording is already next, or is being transcribed now"
        }
        refresh()
    }

    /** Take a waiting recording out of the queue. */
    fun remove(audioFileId: String) {
        if (OnDeviceTranscriber.remove(audioFileId)) {
            _message.value = "Taken out of the queue"
        }
        refresh()
    }

    fun stopAll() {
        OnDeviceTranscriber.stopAll()
        refresh()
    }

    /** Show the note a row belongs to, as the notes list draws it. */
    fun showPreview(noteId: String) {
        viewModelScope.launch {
            val note = withContext(Dispatchers.IO) {
                repository.getAllNotes().getOrNull()?.firstOrNull { it.id == noteId }
            }
            _preview.value = note?.let { withContext(Dispatchers.IO) { loadNoteRow(repository, it) } }
        }
    }

    fun clearPreview() {
        _preview.value = null
    }

    /** Where a recording's file is, for the player inside a preview. */
    suspend fun audioFilePath(audioFileId: String): String? =
        repository.getAudioFilePath(audioFileId).getOrNull()

    fun clearMessage() {
        _message.value = null
    }

    /** The note a recording is in, and that note's first line. */
    private suspend fun noteFor(audioFileId: String): Pair<String, String>? {
        noteOf[audioFileId]?.let { return it }
        if (noteOf.containsKey(audioFileId)) return null
        val found = withContext(Dispatchers.IO) {
            val noteId = repository.getNotesForAudioFile(audioFileId).getOrNull()?.firstOrNull()
            noteId?.let { id ->
                val note = repository.getAllNotes().getOrNull()?.firstOrNull { it.id == id }
                id to (note?.content?.lineSequence()?.firstOrNull()?.take(80)?.ifBlank { "(no text)" } ?: "(no text)")
            }
        }
        noteOf[audioFileId] = found
        return found
    }

    /** What the recording is called, for a row built from a transcription. */
    private suspend fun filenameOf(transcription: Transcription): String =
        withContext(Dispatchers.IO) {
            repository.getAudioFile(transcription.audioFileId).getOrNull()?.filename
                ?: transcription.audioFileId.take(8)
        }

    /** The length of each queued recording, from the database. */
    private suspend fun lengthsFor(jobs: List<TranscriptionJob>): Map<String, Double> {
        val out = mutableMapOf<String, Double>()
        for (job in jobs) {
            val seconds = job.durationSeconds
                ?: repository.getAudioFile(job.audioFileId).getOrNull()?.durationSeconds
            if (seconds != null && seconds > 0) out[job.audioFileId] = seconds.toDouble()
        }
        return out
    }

    private companion object {
        /** A queue view is a screenful of recent work, not a history. */
        const val COMPLETED_SHOWN = 40
    }
}
