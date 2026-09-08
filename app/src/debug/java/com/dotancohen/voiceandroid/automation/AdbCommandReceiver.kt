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

            "SYNC_NOW" -> syncResult(repo.syncNow().getOrThrow())
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
                    Log.i(TAG, "VERSION " + JSONObject().put("id", v.id).put("created_at", v.createdAt)
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
                        Log.i(TAG, "TRANSCRIPTION " + JSONObject().put("id", t.id).put("audio_id", audio.id).put("filename", audio.filename)
                            .put("service", t.service).put("state", t.state).put("created_at", t.createdAt)
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
                // --es file: 1-based position in the note, or part of the file name
                val which = intent.arg("file")
                val audio = when {
                    which == null -> files.first()
                    which.toIntOrNull() != null -> files.getOrNull(which.toInt() - 1) ?: throw IllegalArgumentException("note has only ${files.size} audio file(s)")
                    else -> files.firstOrNull { it.filename.contains(which, ignoreCase = true) } ?: throw IllegalArgumentException("no audio file named like $which")
                }
                // Android lets only an app with a visible activity start a
                // foreground service, and it freezes a background app, which
                // would stall the job. Bring the note to the front first (the
                // screen must be on and unlocked), then queue the work.
                open(context, "note/${note.id}")
                kotlinx.coroutines.delay(1500)
                val problem = OnDeviceTranscriber.enqueue(context, audio.id, audio.filename, intent.arg("language"), intent.arg("model"))
                if (problem != null) throw IllegalStateException(problem)
                "queued audio=${audio.id} file=${audio.filename}"
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
            "SET_RECORDING_FORMAT" -> {
                val f = intent.need("format")
                if (f !in RecorderPreferences.FORMATS) throw IllegalArgumentException("format must be opus, aac or wav16")
                RecorderPreferences(context).recordingFormat = f
                "format=$f"
            }

            "OPEN_SCREEN" -> {
                val route = when (intent.need("screen")) {
                    "notes" -> "notes"; "settings" -> "settings"; "sync" -> "sync_settings"
                    "tags" -> "tag_hierarchy"; "import" -> "import_audio"
                    "recorder" -> "recorder_settings"; "recording" -> "recording"
                    "transcription" -> "transcription_settings"
                    else -> throw IllegalArgumentException("screen must be notes, settings, sync, tags, import, recorder, recording or transcription")
                }
                open(context, route); "route=$route"
            }
            "OPEN_NOTE" -> {
                val note = findNote(repo, intent.need("note"))
                open(context, "note/${note.id}"); "route=note/${note.id}"
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
