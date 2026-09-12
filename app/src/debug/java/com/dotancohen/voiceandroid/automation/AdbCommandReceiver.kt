package com.dotancohen.voiceandroid.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import com.dotancohen.voiceandroid.MainActivity
import com.dotancohen.voiceandroid.audio.RecorderPreferences
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.transcription.OnDeviceTranscriber
import com.dotancohen.voiceandroid.transcription.TranscriptionPreferences
import com.dotancohen.voiceandroid.transcription.WhisperModels
import com.dotancohen.voiceandroid.util.format
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Debug-only automation entry point: every action a tester would do by hand
 * can be triggered from ADB with an explicit broadcast Intent, and the
 * outcome is written to logcat under the tag [TAG] as one `OK`/`ERROR` line
 * (plus JSON lines for listings). `tools/voice-adb` wraps this.
 *
 * Example:
 *   adb shell am broadcast -n com.dotancohen.voiceandroid/.automation.AdbCommandReceiver \
 *       -a com.dotancohen.voiceandroid.action.CREATE_NOTE --es text "פתק"
 *   adb logcat -d -s VoiceAdb
 *
 * Ids may be given as prefixes; tags may be given by name.
 */
class AdbCommandReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action?.removePrefix(ACTION_PREFIX) ?: return
        val pending = goAsync()
        val app = context.applicationContext
        val repo = VoiceRepository.getInstance(app)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repo.initialize().getOrThrow()
                val result = handle(app, repo, action, intent)
                Log.i(TAG, "$action OK $result")
            } catch (e: Exception) {
                Log.i(TAG, "$action ERROR ${e.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private fun Intent.arg(name: String): String? = getStringExtra(name)?.takeIf { it.isNotEmpty() }
    private fun Intent.need(name: String): String = arg(name) ?: throw IllegalArgumentException("missing --es $name")

    private suspend fun handle(context: Context, repo: VoiceRepository, action: String, intent: Intent): String {
        return when (action) {
            "PING" -> "pong device=${repo.getDeviceName().getOrNull()} id=${repo.getDeviceId().getOrNull()}"

            "SYNC" -> syncResult(repo.sync().getOrThrow())
            "UPLOAD" -> "OK " + repo.upload().getOrThrow().describe()
            "INITIAL_SYNC" -> syncResult(repo.initialSync().getOrThrow())
            "SET_SYNC" -> {
                val deviceId = repo.getDeviceId().getOrThrow()
                val deviceName = intent.arg("name") ?: repo.getDeviceName().getOrThrow()
                repo.configureSync(intent.need("url"), intent.need("peer"), deviceId, deviceName).getOrThrow()
                "url=${intent.need("url")} peer=${intent.need("peer")} device=$deviceName id=$deviceId"
            }
            "SET_DEVICE_NAME" -> { repo.setDeviceName(intent.need("name")).getOrThrow(); "name=${intent.need("name")}" }
            "STATUS" -> {
                val cfg = repo.getSyncConfig().getOrNull()
                val conflicts = repo.getUnresolvedConflictCount().getOrNull() ?: -1
                val notes = repo.getAllNotes().getOrNull()?.size ?: -1
                "device=${repo.getDeviceName().getOrNull()} id=${repo.getDeviceId().getOrNull()} server=${cfg?.serverUrl} peer=${cfg?.serverPeerId} notes=$notes unresolved_conflicts=$conflicts pending_changes=${repo.hasUnsyncedChanges().getOrNull()}"
            }

            "CREATE_NOTE" -> {
                val id = repo.createNote(intent.need("text")).getOrThrow()
                "id=$id"
            }
            "EDIT_NOTE" -> {
                val note = findNote(repo, intent.need("note"))
                repo.updateNote(note.id, intent.need("text")).getOrThrow()
                "id=${note.id}"
            }
            "DELETE_NOTE" -> {
                val note = findNote(repo, intent.need("note"))
                repo.deleteNote(note.id).getOrThrow()
                "id=${note.id}"
            }
            "LIST_NOTES" -> {
                val notes = repo.getAllNotes().getOrThrow()
                for (n in notes) {
                    Log.i(TAG, "NOTE " + noteJson(repo, n).toString())
                }
                "count=${notes.size}"
            }
            "SHOW_NOTE" -> {
                val note = findNote(repo, intent.need("note"))
                val json = noteJson(repo, note)
                json.put("content", note.content)
                json.put("history_versions", repo.getNoteHistory(note.id).getOrNull()?.size ?: 0)
                Log.i(TAG, "NOTE " + json.toString())
                "id=${note.id}"
            }
            "HISTORY" -> {
                val note = findNote(repo, intent.need("note"))
                val versions = repo.getNoteHistory(note.id).getOrThrow()
                for (v in versions) {
                    Log.i(TAG, "VERSION " + JSONObject().put("id", v.id)
                        .put("created_at", v.createdAt.format(context))
                        .put("created_at_offset", v.createdAt.offset ?: JSONObject.NULL)
                        .put("created_at_zone", v.createdAt.zone ?: JSONObject.NULL)
                        .put("device", v.deviceLabel).put("kind", if (v.mergeParentId != null) "merge" else if (v.parentId == null) "original" else "edit")
                        .put("conflict", v.conflictKind).put("current", v.content == note.content)
                        .put("first_line", v.content.lineSequence().firstOrNull() ?: "").toString())
                }
                "versions=${versions.size}"
            }
            "RESTORE_VERSION" -> {
                val note = findNote(repo, intent.need("note"))
                val prefix = intent.need("version")
                val v = repo.getNoteHistory(note.id).getOrThrow().filter { it.id.startsWith(prefix) }
                if (v.size != 1) throw IllegalArgumentException("version prefix matches ${v.size} versions")
                repo.updateNote(note.id, v[0].content).getOrThrow()
                "id=${note.id} version=${v[0].id}"
            }

            "LIST_TAGS" -> {
                val tags = repo.getAllTags().getOrThrow()
                for (t in tags) {
                    Log.i(TAG, "TAG " + JSONObject().put("id", t.id).put("name", t.name).put("parent_id", t.parentId).toString())
                }
                "count=${tags.size}"
            }
            "CREATE_TAG" -> {
                val parent = intent.arg("parent")?.let { findTag(repo, it).id }
                "id=${repo.createTag(intent.need("name"), parent).getOrThrow()}"
            }
            "RENAME_TAG" -> {
                val tag = findTag(repo, intent.need("tag"))
                repo.renameTag(tag.id, intent.need("name")).getOrThrow()
                "id=${tag.id} name=${intent.need("name")}"
            }
            "MOVE_TAG" -> {
                val tag = findTag(repo, intent.need("tag"))
                val parent = intent.arg("parent")?.let { findTag(repo, it).id }
                repo.reparentTag(tag.id, parent).getOrThrow()
                "id=${tag.id} parent=${parent ?: "(top level)"}"
            }
            "DELETE_TAG" -> {
                val tag = findTag(repo, intent.need("tag"))
                repo.deleteTag(tag.id).getOrThrow()
                "id=${tag.id}"
            }
            "TAG_NOTE" -> {
                val note = findNote(repo, intent.need("note"))
                val tag = findTag(repo, intent.need("tag"))
                repo.addTagToNote(note.id, tag.id).getOrThrow()
                "note=${note.id} tag=${tag.id}"
            }
            "UNTAG_NOTE" -> {
                val note = findNote(repo, intent.need("note"))
                val tag = findTag(repo, intent.need("tag"))
                repo.removeTagFromNote(note.id, tag.id).getOrThrow()
                "note=${note.id} tag=${tag.id}"
            }

            "IMPORT_AUDIO" -> importFolder(context, repo, File(intent.need("folder")))
            "DOWNLOAD_MEDIA" -> {
                val note = findNote(repo, intent.need("note"))
                val r = repo.downloadAudioFilesForNote(note.id).getOrThrow()
                "note=${note.id} ${r.describe()}"
            }

            "LIST_CONFLICTS" -> {
                var count = 0
                for (n in repo.getAllNotes().getOrThrow()) {
                    for (c in repo.getNoteConflicts(n.id).getOrThrow()) {
                        count++
                        Log.i(TAG, "CONFLICT " + JSONObject().put("id", c.id).put("note", n.id).put("kind", c.kind)
                            .put("display_kind", c.displayKind).put("description", c.description).put("resolved_at", c.resolvedAt).toString())
                    }
                }
                "unresolved_on_notes=$count total_unresolved=${repo.getUnresolvedConflictCount().getOrNull()}"
            }
            "ACCEPT_CONFLICTS" -> {
                val note = findNote(repo, intent.need("note"))
                "note=${note.id} accepted=${repo.acceptNoteConflicts(note.id).getOrThrow()}"
            }
            "RESOLVE_CONFLICT" -> {
                val prefix = intent.need("conflict")
                var found: String? = null
                for (n in repo.getAllNotes().getOrThrow()) {
                    for (c in repo.getNoteConflicts(n.id).getOrThrow()) if (c.id.startsWith(prefix)) found = c.id
                }
                val id = found ?: throw IllegalArgumentException("no unresolved conflict starts with $prefix")
                "conflict=$id resolved=${repo.resolveConflictWithContent(id, intent.need("text")).getOrThrow()}"
            }

            "GET_SETTING" -> "${intent.need("key")}=${repo.getSetting(intent.need("key")).getOrThrow()}"
            "SET_SETTING" -> { repo.setSetting(intent.need("key"), intent.need("value")).getOrThrow(); "${intent.need("key")}=${intent.need("value")}" }
            "SET_TRANSCRIPTION_STATE" -> {
                val note = findNote(repo, intent.need("note"))
                val audio = repo.getAudioFilesForNote(note.id).getOrThrow().firstOrNull() ?: throw IllegalArgumentException("note has no audio file")
                val tr = repo.getTranscriptionsForAudioFile(audio.id).getOrThrow().firstOrNull() ?: throw IllegalArgumentException("audio file has no transcription")
                repo.updateTranscriptionState(tr.id, intent.need("state")).getOrThrow()
                "transcription=${tr.id} state=${intent.need("state")}"
            }

            "LIST_TRANSCRIPTIONS" -> {
                val note = findNote(repo, intent.need("note"))
                var count = 0
                for (audio in repo.getAudioFilesForNote(note.id).getOrThrow().filter { it.deletedAt == null }) {
                    for (t in repo.getTranscriptionsForAudioFile(audio.id).getOrThrow()) {
                        count++
                        // The settings the row was made with are stored as JSON;
                        // language and model are the two that explain a result
                        val args = t.serviceArguments?.let { runCatching { JSONObject(it) }.getOrNull() }
                        Log.i(TAG, "TRANSCRIPTION " + JSONObject().put("id", t.id).put("audio_id", audio.id).put("filename", audio.filename)
                            .put("service", t.service)
                            .put("language", args?.opt("language") ?: JSONObject.NULL)
                            .put("model", args?.opt("model") ?: JSONObject.NULL)
                            .put("state", t.state)
                            .put("created_at", t.createdAt.format(context))
                            .put("created_at_offset", t.createdAt.offset ?: JSONObject.NULL)
                            .put("created_at_zone", t.createdAt.zone ?: JSONObject.NULL)
                            .put("has_segments", t.contentSegments != null).put("service_response", t.serviceResponse ?: JSONObject.NULL)
                            .put("content", t.content))
                    }
                }
                "count=$count"
            }
            "TRANSCRIBE" -> {
                val note = findNote(repo, intent.need("note"))
                val files = repo.getAudioFilesForNote(note.id).getOrThrow().filter { it.deletedAt == null }
                if (files.isEmpty()) throw IllegalArgumentException("note has no audio file")
                // --es file: 1-based position in the note, part of the file
                // name, or "all" for every recording of the note
                val which = intent.arg("file")
                val chosen = when {
                    which == null -> listOf(files.first())
                    which.equals("all", ignoreCase = true) -> files
                    which.toIntOrNull() != null -> listOf(files.getOrNull(which.toInt() - 1) ?: throw IllegalArgumentException("note has only ${files.size} audio file(s)"))
                    else -> listOf(files.firstOrNull { it.filename.contains(which, ignoreCase = true) } ?: throw IllegalArgumentException("no audio file named like $which"))
                }
                // Android lets only an app with a visible activity start a
                // foreground service, and it freezes a background app, which
                // would stall the job. Bring the note to the front first (the
                // screen must be on and unlocked), then queue the work.
                open(context, "note/${note.id}")
                kotlinx.coroutines.delay(1500)
                // They run one at a time, in the order queued here
                for (audio in chosen) {
                    val problem = OnDeviceTranscriber.enqueue(context, audio.id, audio.filename, intent.arg("language"), intent.arg("model"))
                    if (problem != null) throw IllegalStateException(problem)
                }
                "queued=${chosen.size} file=${chosen.joinToString(", ") { it.filename }}"
            }
            "LIST_MODELS" -> {
                val prefs = TranscriptionPreferences(context)
                for (m in WhisperModels.CATALOGUE) {
                    Log.i(TAG, "MODEL " + JSONObject().put("id", m.id).put("title", m.title).put("size", m.sizeBytes)
                        .put("installed", WhisperModels.isInstalled(context, m)).put("selected", m.id == prefs.modelId))
                }
                "models=${WhisperModels.CATALOGUE.size} language=${prefs.language} beam_size=${prefs.beamSize}"
            }
            "DOWNLOAD_MODEL" -> {
                val model = WhisperModels.byId(intent.need("model")) ?: throw IllegalArgumentException("unknown model; see LIST_MODELS")
                if (WhisperModels.isInstalled(context, model)) "already installed ${model.id}" else {
                    var last = 0L
                    WhisperModels.download(context, model) { done, total ->
                        if (done - last > 100_000_000) { last = done; Log.i(TAG, "PROGRESS ${model.id} $done/$total") }
                    }
                    "installed ${model.id}"
                }
            }
            "SET_TRANSCRIPTION" -> {
                val prefs = TranscriptionPreferences(context)
                intent.arg("model")?.let { id ->
                    WhisperModels.byId(id) ?: throw IllegalArgumentException("unknown model; see LIST_MODELS")
                    prefs.modelId = id
                }
                intent.arg("language")?.let { prefs.language = it }
                intent.arg("beam_size")?.let { prefs.beamSize = it.toInt() }
                "model=${prefs.modelId} language=${prefs.language} beam_size=${prefs.beamSize}"
            }
            "MERGE_NOTES" -> {
                // The oldest of them survives, exactly as the button does
                val wanted = intent.need("notes").split(",").map { it.trim() }.filter { it.isNotEmpty() }
                if (wanted.size < 2) throw IllegalArgumentException("give at least two notes, separated by commas")
                val notes = wanted.map { findNote(repo, it) }.sortedBy { it.createdAt.at }
                var survivor = notes.first().id
                for (other in notes.drop(1)) {
                    survivor = repo.mergeNotes(survivor, other.id).getOrThrow()
                }
                "merged=${notes.size} survivor=$survivor"
            }
            "SET_RECORDER" -> {
                val prefs = RecorderPreferences(context)
                intent.arg("format")?.let {
                    if (it !in RecorderPreferences.FORMATS) throw IllegalArgumentException("format must be opus, aac or wav16")
                    prefs.recordingFormat = it
                }
                intent.arg("start_immediately")?.let { prefs.startRecordingImmediately = it.toBoolean() }
                intent.arg("during_call")?.let {
                    if (it !in RecorderPreferences.CALL_BEHAVIOURS) throw IllegalArgumentException("during_call must be pause or silence")
                    prefs.duringCall = it
                }
                intent.arg("default_action")?.let {
                    if (it != RecorderPreferences.ACTION_NOTE && it != RecorderPreferences.ACTION_RECORDING) {
                        throw IllegalArgumentException("default_action must be note or recording")
                    }
                    prefs.defaultNewAction = it
                }
                "format=${prefs.recordingFormat} start_immediately=${prefs.startRecordingImmediately} " +
                    "during_call=${prefs.duringCall} default_action=${prefs.defaultNewAction}"
            }
            "SET_RECORDING_FORMAT" -> {
                val f = intent.need("format")
                if (f !in RecorderPreferences.FORMATS) throw IllegalArgumentException("format must be opus, aac or wav16")
                RecorderPreferences(context).recordingFormat = f
                "format=$f"
            }

            "QUEUE" -> {
                // The transcription queue as the screen shows it: one JSON line
                // per row, in the three groups.
                val queued = OnDeviceTranscriber.queued.value
                val running = OnDeviceTranscriber.current.value
                for ((index, job) in queued.withIndex()) {
                    Log.i(TAG, "WAITING " + JSONObject().put("audio_file_id", job.audioFileId)
                        .put("filename", job.filename).put("position", index + 1)
                        .put("model", job.modelId).put("language", job.language)
                        .put("audio_seconds", job.durationSeconds ?: JSONObject.NULL).toString())
                }
                running?.let { job ->
                    Log.i(TAG, "PROCESSING " + JSONObject().put("audio_file_id", job.audioFileId)
                        .put("filename", job.filename).put("stage", job.stage.name)
                        .put("message", job.message).toString())
                }
                for (t in repo.getRecentTranscriptions(OnDeviceTranscriber.SERVICE_NAME, 20).getOrThrow()) {
                    Log.i(TAG, "COMPLETED " + JSONObject().put("transcription_id", t.id)
                        .put("audio_file_id", t.audioFileId)
                        .put("characters", t.content.length)
                        .put("service_response", t.serviceResponse ?: JSONObject.NULL).toString())
                }
                "waiting=${queued.size} processing=${if (running != null && running.stage < com.dotancohen.voiceandroid.transcription.TranscriptionStage.Done) 1 else 0}"
            }
            "QUEUE_NEXT" -> {
                val audio = findAudioFile(repo, intent.need("recording"))
                if (!OnDeviceTranscriber.doNext(audio.id)) {
                    throw IllegalStateException("that recording is not waiting, or is already next")
                }
                "next=${audio.filename}"
            }
            "QUEUE_REMOVE" -> {
                val audio = findAudioFile(repo, intent.need("recording"))
                if (!OnDeviceTranscriber.remove(audio.id)) {
                    throw IllegalStateException("that recording is not waiting")
                }
                "removed=${audio.filename}"
            }
            "MISSING_DATA" -> {
                // What is missing, changing nothing. One JSON line per gap.
                val store = com.dotancohen.voiceandroid.data.RepositoryMissingDataStore(repo)
                val survey = com.dotancohen.voiceandroid.data.MissingData.survey(store)
                for (gap in survey.gaps) {
                    Log.i(TAG, "GAP " + JSONObject().put("key", gap.key)
                        .put("count", gap.count).put("calculable", gap.calculable)
                        .put("description", gap.description).toString())
                }
                "calculable=${survey.totalCalculable}"
            }
            "CALCULATE_MISSING_DATA" -> {
                val store = com.dotancohen.voiceandroid.data.RepositoryMissingDataStore(repo)
                val report = com.dotancohen.voiceandroid.data.MissingData.calculate(
                    store,
                    durations = intent.arg("durations") != "false",
                    fileDates = intent.arg("dates") != "false",
                    caches = intent.arg("caches") != "false",
                    limit = intent.arg("limit")?.toIntOrNull(),
                )
                Log.i(TAG, "CALCULATED " + JSONObject(report.calculated as Map<*, *>).toString())
                if (report.failed.isNotEmpty()) {
                    Log.i(TAG, "FAILED " + JSONObject(report.failed as Map<*, *>).toString())
                }
                "calculated=${report.totalCalculated}"
            }
            "OPEN_SCREEN" -> {
                val screen = intent.need("screen")
                // "recording" is no longer a screen of its own: recording
                // happens inside a note, so this makes the note the New
                // button would have made and opens it with its recorder.
                val route = when (screen) {
                    "notes" -> "notes"; "settings" -> "settings"; "sync" -> "sync_settings"
                    "tags" -> "tag_hierarchy"; "import" -> "import_audio"
                    "recorder" -> "recorder_settings"
                    "recording" -> "note/" + repo.createNote("").getOrThrow() + "?record=true"
                    "transcription" -> "transcription_settings"; "advanced" -> "advanced_settings"
                    "missing-data" -> "missing_data"
                    else -> throw IllegalArgumentException("screen must be notes, settings, sync, tags, import, recorder, recording, transcription, advanced or missing-data")
                }
                open(context, route); "route=$route"
            }
            "OPEN_NOTE" -> {
                val note = findNote(repo, intent.need("note"))
                open(context, "note/${note.id}?record=false"); "route=note/${note.id}"
            }
            else -> throw IllegalArgumentException("unknown action $action")
        }
    }

    private fun open(context: Context, route: String) {
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_ROUTE, route)
        }
        context.startActivity(launch)
    }

    private fun syncResult(r: com.dotancohen.voiceandroid.data.SyncResult): String {
        val warnings = if (r.warnings.isEmpty()) "" else " warnings=" + JSONArray(r.warnings)
        return if (r.success) "success received=${r.notesReceived} sent=${r.notesSent}$warnings"
        else "failed error=${r.errorMessage}$warnings"
    }

    private suspend fun noteJson(repo: VoiceRepository, n: com.dotancohen.voiceandroid.data.Note): JSONObject {
        val media = JSONArray()
        for (af in repo.getAudioFilesForNote(n.id).getOrNull() ?: emptyList()) {
            if (af.deletedAt != null) continue
            val local = repo.audioFileExistsLocally(af.id).getOrNull() ?: false
            val inCloud = repo.audioFileInCloud(af.id).getOrNull() ?: false
            media.put(JSONObject().put("audio_id", af.id).put("filename", af.filename)
                .put("state", if (local) "local" else if (inCloud) "in_cloud_not_downloaded" else "not_uploaded_yet"))
        }
        return JSONObject()
            .put("id", n.id)
            .put("first_line", n.content.lineSequence().firstOrNull() ?: "")
            .put("tags", JSONArray(repo.getTagsForNote(n.id).getOrNull()?.map { it.name } ?: emptyList<String>()))
            .put("conflicts", JSONArray(repo.getNoteConflictTypes(n.id).getOrNull() ?: emptyList<String>()))
            .put("media", media)
    }

    private suspend fun findNote(repo: VoiceRepository, ref: String): com.dotancohen.voiceandroid.data.Note {
        val notes = repo.getAllNotes().getOrThrow()
        val byId = notes.filter { it.id.startsWith(ref.lowercase()) }
        if (byId.size == 1) return byId[0]
        if (byId.size > 1) throw IllegalArgumentException("note prefix '$ref' matches ${byId.size} notes")
        // Fall back to the first line of the content
        val byText = notes.filter { (it.content.lineSequence().firstOrNull() ?: "") == ref }
        if (byText.size == 1) return byText[0]
        throw IllegalArgumentException(if (byText.isEmpty()) "no note with id prefix or first line '$ref'" else "${byText.size} notes have the first line '$ref'; use the id")
    }

    /** A recording by the first characters of its id, or by part of its name. */
    private suspend fun findAudioFile(repo: VoiceRepository, ref: String): com.dotancohen.voiceandroid.data.AudioFile {
        val recordings = repo.getAllAudioFiles().getOrThrow().filter { it.deletedAt == null }
        val byId = recordings.filter { it.id.startsWith(ref.lowercase()) }
        if (byId.size == 1) return byId[0]
        if (byId.size > 1) throw IllegalArgumentException("recording prefix '$ref' matches ${byId.size} recordings")
        val byName = recordings.filter { it.filename.contains(ref, ignoreCase = true) }
        if (byName.size == 1) return byName[0]
        throw IllegalArgumentException(
            if (byName.isEmpty()) "no recording with id prefix or name like '$ref'"
            else "${byName.size} recordings are named like '$ref'; use the id"
        )
    }

    private suspend fun findTag(repo: VoiceRepository, ref: String): com.dotancohen.voiceandroid.data.Tag {
        val tags = repo.getAllTags().getOrThrow()
        val byName = tags.filter { it.name == ref }
        if (byName.size == 1) return byName[0]
        val byId = tags.filter { it.id.startsWith(ref.lowercase()) }
        if (byId.size == 1) return byId[0]
        throw IllegalArgumentException("no single tag named or starting with '$ref' (${byName.size} by name, ${byId.size} by id)")
    }

    private suspend fun importFolder(context: Context, repo: VoiceRepository, folder: File): String {
        if (!folder.isDirectory) throw IllegalArgumentException("${folder.path} is not a folder (is storage permission granted?)")
        val files = folder.listFiles { f -> f.isFile && f.extension.lowercase() in AUDIO_EXTENSIONS }?.sortedBy { it.name } ?: emptyList()
        var imported = 0
        val names = JSONArray()
        for (f in files) {
            val duration = try {
                val r = MediaMetadataRetriever(); r.setDataSource(f.path)
                val ms = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull(); r.release(); ms?.let { it / 1000 }
            } catch (e: Exception) { null }
            val result = repo.importAudioFile(f.name, f.lastModified() / 1000, duration).getOrThrow()
            repo.copyAudioFileToStorage(context, Uri.fromFile(f), result.audioFileId, f.extension.lowercase()).getOrThrow()
            names.put(JSONObject().put("file", f.name).put("note", result.noteId).put("audio_id", result.audioFileId))
            imported++
        }
        Log.i(TAG, "IMPORTED $names")
        return "folder=${folder.path} imported=$imported"
    }

    companion object {
        const val TAG = "VoiceAdb"
        const val ACTION_PREFIX = "com.dotancohen.voiceandroid.action."
        private val AUDIO_EXTENSIONS = setOf("mp3", "m4a", "wav", "ogg", "opus", "aac", "flac", "3gp", "amr")
    }
}
