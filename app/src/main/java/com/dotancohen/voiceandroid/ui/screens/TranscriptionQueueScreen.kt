package com.dotancohen.voiceandroid.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.dotancohen.voiceandroid.transcription.OnDeviceTranscriber
import com.dotancohen.voiceandroid.transcription.QueueEstimate
import com.dotancohen.voiceandroid.transcription.TranscriptionJob
import com.dotancohen.voiceandroid.transcription.TranscriptionStage
import com.dotancohen.voiceandroid.transcription.TranscriptionWork
import com.dotancohen.voiceandroid.ui.components.PendingTranscriptionIcon
import com.dotancohen.voiceandroid.util.Durations
import com.dotancohen.voiceandroid.util.UiPreferences
import com.dotancohen.voiceandroid.util.format
import com.dotancohen.voiceandroid.viewmodel.QueueRow
import com.dotancohen.voiceandroid.viewmodel.TranscriptionQueueViewModel

/**
 * Settings → Transcription queue.
 *
 * Three groups, read downwards as time runs forwards: what is **waiting**, what
 * is being **worked on**, and what is **done**. Within each group the newest is
 * at the top, as in the notes list, so the order the waiting recordings will
 * actually be reached in is printed on each row rather than left to the
 * ordering.
 *
 * Every row says which note the recording belongs to, and tapping it shows that
 * note exactly as the notes list draws it — the same preview that a held-down
 * Previous or Next button gives inside a note.
 *
 * A finished row carries what the work cost: how much text came out, how long
 * the recording was, and the clock time, processor time and peak memory it took.
 * Those numbers are what the waiting estimates are calculated from, and what
 * tells the user whether a larger model is worth its wait.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionQueueScreen(
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit = {},
    viewModel: TranscriptionQueueViewModel = viewModel(),
) {
    val view by viewModel.view.collectAsState()
    val preview by viewModel.preview.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(Unit) { viewModel.refresh() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transcription queue") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (view.waiting.isNotEmpty() || view.processing.isNotEmpty()) {
                        TextButton(onClick = { viewModel.stopAll() }) {
                            Text("Stop all", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { innerPadding ->
        if (view.waiting.isEmpty() && view.processing.isEmpty() && view.completed.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(10.dp)) {
                Text(
                    "Nothing has been transcribed on this phone yet. A recording sent to be " +
                        "transcribed appears here with the ones waiting behind it, and stays here " +
                        "afterwards with what the work cost.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (view.waiting.isNotEmpty()) {
                item {
                    SectionHeading(
                        "Waiting (${view.waiting.size})",
                        // Said once, at the top, rather than on every row.
                        if (view.rate == null) {
                            "Nothing has finished on this phone yet, so there is nothing to " +
                                "estimate the wait from."
                        } else {
                            "About ${"%.1f".format(view.rate)} seconds of work per second of " +
                                "recording on this phone."
                        }
                    )
                }
                items(view.waiting, key = { "waiting-${it.audioFileId}" }) { row ->
                    QueueRowCard(
                        row = row,
                        onPreview = { row.noteId?.let { viewModel.showPreview(it) } },
                        onDoNext = { viewModel.doNext(row.audioFileId) },
                        onRemove = { viewModel.remove(row.audioFileId) },
                    )
                }
            }

            if (view.processing.isNotEmpty()) {
                item { SectionHeading("Processing", null) }
                items(view.processing, key = { "processing-${it.audioFileId}" }) { row ->
                    QueueRowCard(
                        row = row,
                        onPreview = { row.noteId?.let { viewModel.showPreview(it) } },
                    )
                }
            }

            if (view.completed.isNotEmpty()) {
                item { SectionHeading("Completed (${view.completed.size})", null) }
                items(view.completed, key = { "done-${it.audioFileId}-${it.finishedAt}" }) { row ->
                    QueueRowCard(
                        row = row,
                        onPreview = { row.noteId?.let { viewModel.showPreview(it) } },
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }

    // The note a row belongs to, drawn as the notes list draws it: the same
    // preview a held-down Previous or Next gives inside a note. Tapping it
    // opens the note; tapping anywhere else puts it away.
    preview?.let { row ->
        Dialog(
            onDismissRequest = { viewModel.clearPreview() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            // Scrollable: opening the section adds a player and a waveform, and
            // on a long note that is taller than the screen.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
            NoteCard(
                noteWithAudio = row,
                contentLines = UiPreferences(LocalContext.current).notesListLines * 2,
                onClick = {
                    val id = row.note.id
                    viewModel.clearPreview()
                    onOpenNote(id)
                },
                // The chevron opens the player inside the preview, so a
                // recording waiting to be transcribed can be listened to here.
                getAudioFilePath = { audioId -> viewModel.audioFilePath(audioId) },
                modifier = Modifier.fillMaxWidth()
            )
            }
        }
    }
}

@Composable
private fun SectionHeading(title: String, note: String?) {
    Column(modifier = Modifier.padding(top = 10.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One recording in the queue.
 *
 * Top-aligned throughout: a note's line wraps, and the numbers beside it belong
 * next to its first line.
 */
@Composable
private fun QueueRowCard(
    row: QueueRow,
    onPreview: () -> Unit = {},
    onDoNext: (() -> Unit)? = null,
    onRemove: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                when (row.stage) {
                    TranscriptionStage.Done -> Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = "finished",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    TranscriptionStage.Failed, TranscriptionStage.Stopped -> Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = "did not finish",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                    else -> PendingTranscriptionIcon(size = 18.dp)
                }
                Spacer(modifier = Modifier.width(6.dp))

                Column(modifier = Modifier.weight(1f)) {
                    // The note is what the user recognises; the file name is
                    // underneath it, because a recorder's name says less.
                    Text(
                        text = row.noteLine ?: "(the note this belongs to is not on this phone)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clickable(enabled = row.noteId != null) { onPreview() }
                    )
                    Text(
                        text = fileLine(row),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onDoNext != null && row.position != 1) {
                    IconButton(onClick = onDoNext, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Transcribe this one next",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                if (onRemove != null) {
                    IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Take out of the queue",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // What it is waiting for, what it is doing, or what it cost.
            when {
                row.stage == TranscriptionStage.Queued -> {
                    Text(
                        text = waitingLine(row),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                row.stage < TranscriptionStage.Done -> {
                    row.outcome?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                row.stage == TranscriptionStage.Done -> {
                    WorkTable(row)
                }
                else -> {
                    Text(
                        text = row.outcome ?: "Did not finish",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/**
 * What a finished transcription cost, as a small table.
 *
 * Top-aligned, two columns, one line per measure. These are the numbers the
 * user compares models on, and the ones every waiting estimate comes from.
 */
@Composable
private fun WorkTable(row: QueueRow) {
    val work = row.work ?: return
    val lines = buildList {
        row.characters?.let { add("Text" to "$it characters") }
        work.audioSeconds?.let { add("Recording" to Durations.ofSeconds(it.toInt())) }
        work.clockSeconds?.let { add("Clock time" to secondsInWords(it)) }
        work.cpuSeconds?.let { add("Processor time" to secondsInWords(it)) }
        work.coresBusy?.let { add("Cores busy" to "%.2f".format(it)) }
        work.peakMemoryBytes?.let { add("Memory, peak" to bytesInWords(it)) }
        work.speedVsRealtime?.let { add("Speed" to "%.2f× real time".format(it)) }
        work.model?.let { add("Model" to it) }
    }
    if (lines.isEmpty()) return

    Spacer(modifier = Modifier.height(4.dp))
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        for ((label, value) in lines) {
            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(110.dp)
                )
                Text(text = value, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** The recording itself: its file, its length, the model and the language. */
private fun fileLine(row: QueueRow): String {
    val parts = mutableListOf(row.filename)
    row.audioSeconds?.let { parts += Durations.ofSeconds(it.toInt()) }
    row.model?.let { parts += it }
    row.language?.let { parts += it }
    return parts.joinToString(" · ")
}

/** Where it is in the queue, and how long until it is done. */
private fun waitingLine(row: QueueRow): String {
    val place = when (row.position) {
        null -> "Waiting"
        1 -> "Next"
        else -> "${row.position}th in line"
    }
    val wait = QueueEstimate.inWords(row.waitSeconds)
    return if (wait == null) "$place · no estimate yet" else "$place · done in $wait"
}

/** A count of seconds as a person reads it. */
fun secondsInWords(value: Double): String = when {
    value >= 3600 -> "${(value / 3600).toInt()} h ${"%.0f".format((value % 3600) / 60)} min"
    value >= 60 -> "${(value / 60).toInt()} min ${"%.0f".format(value % 60)} s"
    value >= 1 -> "%.1f s".format(value)
    else -> "%.2f s".format(value)
}

/** A count of bytes as a person reads it. */
fun bytesInWords(value: Long): String = when {
    value >= 1_000_000_000L -> "%.2f GB".format(value / 1e9)
    value >= 1_000_000L -> "%.0f MB".format(value / 1e6)
    value >= 1_000L -> "%.0f kB".format(value / 1e3)
    else -> "$value B"
}

/** What this job is doing, or what became of it, in one line. */
fun jobLine(job: TranscriptionJob): String {
    val where = "${job.modelId} · ${job.language}"
    return when (job.stage) {
        TranscriptionStage.Queued -> "Waiting · $where"
        TranscriptionStage.Done -> "Finished · ${job.message}"
        TranscriptionStage.Failed -> "Failed · ${job.message}"
        TranscriptionStage.Stopped -> "Stopped"
        else -> "${job.message.ifBlank { "Working" }} · $where"
    }
}
