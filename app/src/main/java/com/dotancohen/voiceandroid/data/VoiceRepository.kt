package com.dotancohen.voiceandroid.data

import android.content.Context
import android.os.Build
import android.os.Environment
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.voicecore.VoiceClient
import uniffi.voicecore.VoiceCoreException
import uniffi.voicecore.ConflictData
import uniffi.voicecore.VersionData
import uniffi.voicecore.NoteData as UniFFINoteData
import uniffi.voicecore.SyncResultData as UniFFISyncResultData
import uniffi.voicecore.AudioFileData as UniFFIAudioFileData
import uniffi.voicecore.NoteAttachmentData as UniFFINoteAttachmentData
import uniffi.voicecore.TranscriptionData as UniFFITranscriptionData
import uniffi.voicecore.TagData as UniFFITagData
import uniffi.voicecore.SearchResultData as UniFFISearchResultData
import uniffi.voicecore.TagChangeResultData as UniFFITagChangeResultData
import uniffi.voicecore.ImportAudioResultData as UniFFIImportAudioResultData
import uniffi.voicecore.generateDeviceId as uniffiGenerateDeviceId
import java.io.File

/**
 * Repository for interacting with the Voice Core Rust library.
 */
class VoiceRepository(private val context: Context) {

    private val dataDir: String = context.filesDir.absolutePath
    private val prefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)

    /**
     * Where recordings go unless the user chose a folder (Stage 13): the
     * `Voice` folder of the shared Recordings directory, which survives the
     * application being replaced or removed and is reachable over a cable;
     * the application's own directory only until all-files access is granted.
     * The folder holds recordings and nothing else.
     */
    val defaultAudioFileDir: String
        get() {
            val shared = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS), "Voice")
            val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager() && (shared.isDirectory || shared.mkdirs())) {
                shared
            } else {
                context.getExternalFilesDir("audio") ?: File(context.filesDir, "audio")
            }
            dir.mkdirs()
            return dir.absolutePath
        }

    // Current audio file directory (may be user-configured or default)
    private var _audioFileDir: String? = null
    val audioFileDir: String
        get() = _audioFileDir ?: defaultAudioFileDir

    private var client: VoiceClient? = null

    init {
        // Load saved audiofile directory from preferences immediately on creation
        loadSavedAudiofileDirectory()
    }

    /**
     * Load the saved audiofile directory from SharedPreferences.
     * Called in init and can be called again to refresh.
     */
    private fun loadSavedAudiofileDirectory() {
        val savedPath = prefs.getString("audiofile_directory_path", null)
        if (savedPath != null && savedPath.isNotBlank()) {
            val dir = File(savedPath)
            // On Android 11+, File.canWrite() doesn't work reliably for external storage
            // even when MANAGE_EXTERNAL_STORAGE is granted. Check the permission instead.
            val canAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // For Android 11+, trust the path if we have all files access
                Environment.isExternalStorageManager() && dir.exists()
            } else {
                // For older versions, use the traditional check
                dir.exists() && dir.canWrite()
            }
            if (canAccess) {
                _audioFileDir = savedPath
            }
        }
    }

    /**
     * Initialize the Voice client.
     * This should be called once when the app starts.
     */
    /**
     * Tell the core which timezone this phone is in. Android keeps the zone in
     * its framework, where the native library cannot see it, so it is passed
     * in: at start, and again whenever Android says it changed.
     */
    fun reportTimeZone() {
        val zone = java.util.TimeZone.getDefault()
        val offsetSeconds = zone.getOffset(System.currentTimeMillis()) / 1000
        client?.setLocalTimezone(offsetSeconds, zone.id)
        AppLogger.i(TAG, "Timezone reported to the core: ${zone.id} (${offsetSeconds}s)")
    }

    suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Initializing VoiceRepository, dataDir=$dataDir")
            if (client == null) {
                client = VoiceClient(dataDir)
                AppLogger.i(TAG, "VoiceClient created")
            }
            // A card should read "Galaxy A14", not the core's placeholder (Stage 5)
            ensureInitialized().let { c ->
                if (c.getDeviceName() == CORE_DEFAULT_DEVICE_NAME) {
                    c.setDeviceName(defaultDeviceName())
                    AppLogger.i(TAG, "Device name set to ${defaultDeviceName()}")
                }
            }

            // Every timestamp written from here records the clock this phone
            // is reading, so a note keeps its time after the user travels
            reportTimeZone()

            // Reload audiofile directory in case permission was granted after creation
            loadSavedAudiofileDirectory()

            // Configure audiofile directory for sync
            ensureInitialized().setAudiofileDirectory(audioFileDir)
            AppLogger.i(TAG, "Audio file directory set to: $audioFileDir")

            // Rebuild list caches to ensure duration/tags/marked are populated
            try {
                val count = ensureInitialized().rebuildAllNoteListCaches()
                AppLogger.i(TAG, "Rebuilt list caches for $count notes")
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to rebuild list caches: ${e.message}")
            }

            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            AppLogger.e(TAG, "Failed to initialize VoiceClient", e)
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to initialize VoiceClient", e)
            Result.failure(e)
        }
    }

    /** The model name as Android reports it, for a fresh phone's card. */
    private fun defaultDeviceName(): String {
        val model = android.os.Build.MODEL?.trim().orEmpty()
        val maker = android.os.Build.MANUFACTURER?.trim().orEmpty()
        return when {
            model.isEmpty() -> "Phone"
            maker.isEmpty() || model.startsWith(maker, ignoreCase = true) -> model
            else -> "$maker $model"
        }
    }

    @Synchronized
    private fun ensureInitialized(): VoiceClient {
        return client ?: VoiceClient(dataDir).also {
            client = it
            // Configure audiofile directory for the new client
            it.setAudiofileDirectory(audioFileDir)
            AppLogger.i(TAG, "VoiceClient created, dataDir=$dataDir, audioFileDir=$audioFileDir")

            // Rebuild list caches to ensure duration/tags/marked are populated
            try {
                val count = it.rebuildAllNoteListCaches()
                AppLogger.i(TAG, "Rebuilt list caches for $count notes")
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to rebuild list caches: ${e.message}")
            }
        }
    }

    /**
     * Set the audiofile directory path.
     * Pass null or empty string to use the default directory.
     * Returns true if the directory was set successfully, false if it doesn't exist or isn't writable.
     */
    suspend fun setAudiofileDirectory(path: String?): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            if (path.isNullOrBlank()) {
                _audioFileDir = null
                prefs.edit().remove("audiofile_directory_path").apply()
            } else {
                val dir = File(path)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                // On Android 11+, File.canWrite() doesn't work reliably for external storage
                // even when MANAGE_EXTERNAL_STORAGE is granted. Check the permission instead.
                val canAccess = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager() && dir.exists()
                } else {
                    dir.exists() && dir.canWrite()
                }
                if (!canAccess) {
                    return@withContext Result.failure(Exception("Directory does not exist or is not writable: $path"))
                }
                _audioFileDir = path
                prefs.edit().putString("audiofile_directory_path", path).apply()
            }

            // Update voicecore with new directory
            ensureInitialized().setAudiofileDirectory(audioFileDir)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all notes from the local database.
     */
    suspend fun getAllNotes(): Result<List<Note>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val notes = voiceClient.getAllNotes().map { noteData ->
                Note(
                    id = noteData.id,
                    content = noteData.content,
                    createdAt = noteData.createdAt,
                    modifiedAt = noteData.modifiedAt,
                    deletedAt = noteData.deletedAt,
                    listDisplayCache = noteData.listDisplayCache
                )
            }
            Result.success(notes)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Every peer of this phone (Stage 5). */
    suspend fun listPeers(): Result<List<Peer>> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().listPeers().map {
                Peer(it.peerId, it.name, it.url, it.certificateFingerprint, it.lastReachedAt, it.lastOperation, it.isLast)
            })
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** A peer typed by hand (Stage 7, the third way): its device id, a name and where it listens. */
    suspend fun addPeer(peerId: String, name: String, url: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized().addPeer(peerId.trim(), name, url)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Forget a peer on this phone: its card does not bring it back until it is added again. */
    suspend fun forgetPeer(peerId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().forgetPeer(peerId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** A local name for a peer, shown in place of its card's. */
    suspend fun renamePeer(peerId: String, name: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().renamePeer(peerId, name))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Sync with the last peer, or the only one: database changes both ways.
     * Files never move here; [upload] sends recordings to the bucket.
     */
    suspend fun sync(): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Starting sync")
            val voiceClient = ensureInitialized()
            val result = voiceClient.sync()
            AppLogger.i(TAG, "Sync completed: success=${result.success}, received=${result.notesReceived}, sent=${result.notesSent}")
            Result.success(SyncResult(
                success = result.success,
                notesReceived = result.notesReceived,
                notesSent = result.notesSent,
                errorMessage = result.errorMessage,
                warnings = result.warnings,
                requestId = result.requestId,
                clockSkewSeconds = result.clockSkewSeconds
            ))
        } catch (e: VoiceCoreException) {
            AppLogger.e(TAG, "Sync failed", e)
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * One operation with the configured peer, by the terms table: "sync",
     * "deliver" (sync then send), "exchange" (sync, send and fetch), "send" or "fetch".
     */
    suspend fun operate(operation: String, peerId: String? = null): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Starting $operation" + (peerId?.let { " with ${it.take(8)}" } ?: ""))
            val result = ensureInitialized().operate(operation, peerId)
            AppLogger.i(TAG, "$operation completed: success=${result.success}, received=${result.notesReceived}, sent=${result.notesSent}, files sent=${result.filesSent}, fetched=${result.filesFetched}")
            Result.success(SyncResult(
                success = result.success,
                notesReceived = result.notesReceived,
                notesSent = result.notesSent,
                filesSent = result.filesSent,
                filesFetched = result.filesFetched,
                bytesMoved = result.bytesMoved.toLong(),
                errorMessage = result.errorMessage,
                warnings = result.warnings,
                requestId = result.requestId,
                clockSkewSeconds = result.clockSkewSeconds
            ))
        } catch (e: VoiceCoreException) {
            AppLogger.e(TAG, "$operation failed", e)
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            AppLogger.e(TAG, "$operation failed", e)
            Result.failure(e)
        }
    }

    /** Check the connection to a peer: one row per thing that can be wrong (Stage 12). Nothing is changed. */
    suspend fun checkConnection(peerId: String): Result<List<CheckRow>> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().checkConnection(peerId).map { CheckRow(it.name, it.passed, it.detail, it.code) })
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear sync state to force a full re-sync from scratch.
     */
    suspend fun clearSyncState(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.clearSyncState()
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Reset sync timestamps to force re-fetching all data from peers.
     * Unlike clearSyncState, this preserves peer configuration.
     */
    suspend fun resetSyncTimestamps(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.resetSyncTimestamps()
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Perform initial sync - fetches full dataset from server.
     * Use this for first-time sync or to re-fetch everything.
     */
    suspend fun initialSync(peerId: String? = null): Result<SyncResult> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Starting initial sync (full dataset fetch)")
            val voiceClient = ensureInitialized()
            val result = voiceClient.initialSync(peerId)
            AppLogger.i(TAG, "Initial sync completed: success=${result.success}, received=${result.notesReceived}, sent=${result.notesSent}")
            Result.success(SyncResult(
                success = result.success,
                notesReceived = result.notesReceived,
                notesSent = result.notesSent,
                errorMessage = result.errorMessage,
                warnings = result.warnings,
                requestId = result.requestId,
                clockSkewSeconds = result.clockSkewSeconds
            ))
        } catch (e: VoiceCoreException) {
            AppLogger.e(TAG, "Initial sync failed", e)
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Initial sync failed", e)
            Result.failure(e)
        }
    }

    /**
     * Get the current device ID.
     */
    /** Move this phone to another account by its code (Stage 1); the full current id typed by hand is the proof. */
    suspend fun moveToAccount(setupText: String, typedCurrentId: String): Result<Moved> = withContext(Dispatchers.IO) {
        try {
            val moved = ensureInitialized().moveToAccountByCode(setupText.trim(), typedCurrentId.trim())
            AppLogger.i(TAG, "Moved ${moved.notesMoved} notes to account ${moved.accountId.take(8)} through ${moved.peerName}")
            Result.success(Moved(moved.accountId, moved.peerName, moved.notesMoved, moved.tagsMerged))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Join an account from a setup text: a scanned code or a pasted text. */
    suspend fun pairWith(setupText: String): Result<Joined> = withContext(Dispatchers.IO) {
        try {
            val joined = ensureInitialized().pairWith(setupText)
            if (joined.granted) {
                AppLogger.i(TAG, "${joined.peerName} now hosts account ${joined.accountId.take(8)}")
            } else {
                AppLogger.i(TAG, "Joined account ${joined.accountId.take(8)} through ${joined.peerName}")
            }
            Result.success(Joined(joined.accountId, joined.peerId, joined.peerName, joined.peerUrl, joined.granted))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Show a code for another device to join this phone's account. */
    suspend fun offerCode(urls: List<String>): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().offerCode(urls))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Withdraw the code. */
    suspend fun withdrawCode(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized().withdrawCode()
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Start listening for peers on `port`; returns the URLs peers can use. */
    suspend fun startListener(port: Int): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().startListener(port.toUShort()))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Stop listening. Safe to call when nothing listens. */
    fun stopListener() {
        try {
            client?.stopListener()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Could not stop the listener", e)
        }
    }

    fun listenerRunning(): Boolean = try { client?.listenerRunning() ?: false } catch (e: Exception) { false }

    /** This phone's certificate fingerprint, what a peer pins. */
    suspend fun certificateFingerprint(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().certificateFingerprint())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Where this phone would be reachable at `port`. */
    suspend fun listenUrls(port: Int): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().listenUrls(port.toUShort()))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** The account this phone's database belongs to: 32 hex characters. */
    suspend fun getAccountId(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().accountId())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Copy the database into its snapshot directory now. Returns the path. */
    suspend fun takeSnapshot(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().snapshot())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Every snapshot beside the database, newest first. */
    suspend fun listSnapshots(): Result<List<Snapshot>> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().listSnapshots().map {
                Snapshot(name = it.name, path = it.path, sizeBytes = it.sizeBytes.toLong(), noteCount = it.noteCount)
            })
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Replace the database with a snapshot; the state replaced is snapshotted first. */
    suspend fun restoreSnapshot(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized().restoreSnapshot(name)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDeviceId(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getDeviceId())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set the device ID.
     */
    suspend fun setDeviceId(deviceId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.setDeviceId(deviceId)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the current device name.
     */
    suspend fun getDeviceName(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getDeviceName())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set the device name.
     */
    suspend fun setDeviceName(name: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.setDeviceName(name)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if sync is configured.
     */
    suspend fun isSyncConfigured(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.isSyncConfigured())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Generate a new device ID.
     */
    fun generateDeviceId(): String {
        return uniffiGenerateDeviceId()
    }

    /**
     * Get all attachments for a note.
     */
    suspend fun getAttachmentsForNote(noteId: String): Result<List<NoteAttachment>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val attachments = voiceClient.getAttachmentsForNote(noteId).map { data ->
                NoteAttachment(
                    id = data.id,
                    noteId = data.noteId,
                    attachmentId = data.attachmentId,
                    attachmentType = data.attachmentType,
                    createdAt = data.createdAt,
                    deviceId = data.deviceId,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt
                )
            }
            Result.success(attachments)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all audio files for a note (via note_attachments).
     */
    suspend fun getAudioFilesForNote(noteId: String): Result<List<AudioFile>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val audioFiles = voiceClient.getAudioFilesForNote(noteId).map { data ->
                AudioFile(
                    id = data.id,
                    importedAt = data.importedAt,
                    filename = data.filename,
                    fileCreatedAt = data.fileCreatedAt,
                    durationSeconds = data.durationSeconds,
                    summary = data.summary,
                    deviceId = data.deviceId,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt,
                    storageProvider = data.storageProvider,
                    storageKey = data.storageKey,
                    localName = data.localName
                )
            }
            Result.success(audioFiles)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a single audio file by ID.
     */
    suspend fun getAudioFile(audioFileId: String): Result<AudioFile?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val audioFile = voiceClient.getAudioFile(audioFileId)?.let { data ->
                AudioFile(
                    id = data.id,
                    importedAt = data.importedAt,
                    filename = data.filename,
                    fileCreatedAt = data.fileCreatedAt,
                    durationSeconds = data.durationSeconds,
                    summary = data.summary,
                    deviceId = data.deviceId,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt,
                    storageProvider = data.storageProvider,
                    storageKey = data.storageKey,
                    localName = data.localName
                )
            }
            Result.success(audioFile)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the file path for an audio file (if it exists on disk).
     */
    suspend fun getAudioFilePath(audioFileId: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getAudioFilePath(audioFileId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all audio files in the database (for debugging).
     */
    suspend fun getAllAudioFiles(): Result<List<AudioFile>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val audioFiles = voiceClient.getAllAudioFiles().map { data ->
                AudioFile(
                    id = data.id,
                    importedAt = data.importedAt,
                    filename = data.filename,
                    fileCreatedAt = data.fileCreatedAt,
                    durationSeconds = data.durationSeconds,
                    summary = data.summary,
                    deviceId = data.deviceId,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt,
                    storageProvider = data.storageProvider,
                    storageKey = data.storageKey,
                    localName = data.localName
                )
            }
            Result.success(audioFiles)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update a note's content.
     */
    suspend fun updateNote(noteId: String, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateNote(noteId, content))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** What is on this phone only (Stage 10): notes and recordings not duplicated anywhere else. */
    suspend fun notDuplicated(): Result<NotDuplicated> = withContext(Dispatchers.IO) {
        try {
            val counts = ensureInitialized().notDuplicated()
            Result.success(NotDuplicated(counts.notes, counts.recordings))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** The peers known to hold a copy of a recording (Stage 10). */
    suspend fun copiesOf(audioId: String): Result<List<RecordingCopy>> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().copiesOf(audioId).map { RecordingCopy(it.peerId, it.at) })
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Every peer dealt with: when it was last reached and by which operation (Stage 10). */
    suspend fun peerSummaries(): Result<List<PeerSummary>> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().peerSummaries().map { PeerSummary(it.peerId, it.peerName, it.lastReachedAt, it.lastOperation) })
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Debug: get sync state details.
     */
    suspend fun debugSyncState(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.debugSyncState())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the audio file directory path.
     */
    fun getAudioFileDirectory(): String = audioFileDir

    // =========================================================================
    // Transcription Methods
    // =========================================================================

    /**
     * Get all transcriptions for an audio file.
     */
    suspend fun getTranscriptionsForAudioFile(audioFileId: String): Result<List<Transcription>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val transcriptions = voiceClient.getTranscriptionsForAudioFile(audioFileId).map { data ->
                data.toModel()
            }
            Result.success(transcriptions)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** One reading of a transcription row, wherever it came from. */
    private fun uniffi.voicecore.TranscriptionData.toModel() = Transcription(
        id = id,
        audioFileId = audioFileId,
        content = content,
        contentSegments = contentSegments,
        service = service,
        serviceArguments = serviceArguments,
        serviceResponse = serviceResponse,
        state = state,
        deviceId = deviceId,
        createdAt = createdAt,
        modifiedAt = modifiedAt,
        deletedAt = deletedAt,
    )

    /**
     * The most recent transcriptions, newest first.
     *
     * What the transcription queue shows under "Completed". [service] narrows
     * it to one transcription service — `OnDeviceTranscriber.SERVICE_NAME` is
     * the work this phone did — and null returns every service.
     */
    suspend fun getRecentTranscriptions(
        service: String? = null,
        limit: Int = 50,
    ): Result<List<Transcription>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(
                voiceClient.getRecentTranscriptions(service, limit.toUInt()).map { it.toModel() }
            )
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Which notes a recording is attached to. Normally one.
     *
     * The queue view uses it to say which note each transcription belongs to,
     * so the user can look at that note without leaving the queue.
     */
    suspend fun getNotesForAudioFile(audioFileId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getNotesForAudioFile(audioFileId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get a single transcription by ID.
     */
    suspend fun getTranscription(transcriptionId: String): Result<Transcription?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val transcription = voiceClient.getTranscription(transcriptionId)?.let { data ->
                data.toModel()
            }
            Result.success(transcription)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update a transcription's state.
     * State is a space-separated list of tags.
     */
    suspend fun updateTranscriptionState(transcriptionId: String, state: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateTranscriptionState(transcriptionId, state))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Merge two notes into one: the older note keeps its own content with the
     * newer note's appended, takes over its tags and attachments, and the
     * newer note is deleted. Returns the surviving note's id.
     */
    suspend fun mergeNotes(noteId1: String, noteId2: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.mergeNotes(noteId1, noteId2))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a transcription record (on-device transcription writes a
     * "Pending..." row first, then the result). Returns the transcription id.
     */
    suspend fun createTranscription(
        audioFileId: String,
        content: String,
        service: String,
        serviceArguments: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.createTranscription(audioFileId, content, null, service, serviceArguments, null))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Store a finished (or failed) transcription: text, segments JSON and the
     * service response, the same fields the desktop fills in.
     */
    suspend fun updateTranscriptionResult(
        transcriptionId: String,
        content: String,
        contentSegments: String?,
        serviceResponse: String?,
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateTranscriptionResult(transcriptionId, content, contentSegments, serviceResponse))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update a transcription's content and optionally its state.
     */
    suspend fun updateTranscription(transcriptionId: String, content: String, state: String? = null): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateTranscription(transcriptionId, content, state))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * The attachment that stands for a note, if one was chosen: the
     * recording played when the note is opened, and the one whose
     * transcription the notes list shows.
     */
    suspend fun getPrimaryAttachment(noteId: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().getPrimaryAttachment(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Choose that attachment, or pass null to go back to the first one. */
    suspend fun setPrimaryAttachment(noteId: String, attachmentId: String?): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(ensureInitialized().setPrimaryAttachment(noteId, attachmentId))
            } catch (e: VoiceCoreException) {
                Result.failure(Exception(e.message))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** The transcription that stands for a recording, if one was chosen. */
    suspend fun getPrimaryTranscription(audioFileId: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            Result.success(ensureInitialized().getPrimaryTranscription(audioFileId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Choose that transcription, or pass null to go back to the first one. */
    suspend fun setPrimaryTranscription(audioFileId: String, transcriptionId: String?): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(ensureInitialized().setPrimaryTranscription(audioFileId, transcriptionId))
            } catch (e: VoiceCoreException) {
                Result.failure(Exception(e.message))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * The notes in the trash: deleted, still here, newest deletion first.
     *
     * Deleting a note has always been a soft delete, so nothing was lost:
     * the note is still here with its history and its recordings.
     */
    suspend fun getDeletedNotes(): Result<List<Note>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(
                voiceClient.getDeletedNotes().map { noteData ->
                    Note(
                        id = noteData.id,
                        content = noteData.content,
                        createdAt = noteData.createdAt,
                        modifiedAt = noteData.modifiedAt,
                        deletedAt = noteData.deletedAt,
                        listDisplayCache = noteData.listDisplayCache
                    )
                }
            )
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Take a note out of the trash. False when it was not in there. */
    suspend fun undeleteNote(noteId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.undeleteNote(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Empty one note out of the trash for good, on every device.
     *
     * The recordings that hung on that note alone go with it: their files
     * are deleted from the phone here, since the core knows which
     * recordings went but not where this platform keeps them.
     */
    suspend fun purgeNote(noteId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val audioIds = voiceClient.purgeNote(noteId)
            var filesRemoved = 0
            for (audioId in audioIds) {
                File(audioFileDir)
                    .listFiles { f -> f.name.substringBeforeLast('.') == audioId }
                    ?.forEach { file -> if (file.delete()) filesRemoved++ }
            }
            AppLogger.i(TAG, "Removed note ${noteId.take(8)} for good with $filesRemoved file(s)")
            Result.success(filesRemoved)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a transcription (a soft delete, so the removal syncs).
     *
     * Used to clear the placeholder left by a transcription the phone cut
     * short, once the recording has really been transcribed.
     */
    suspend fun deleteTranscription(transcriptionId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.deleteTranscription(transcriptionId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a note (soft delete - sets deleted_at timestamp).
     */
    suspend fun deleteNote(noteId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.deleteNote(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a tag (soft delete - sets deleted_at timestamp).
     */
    suspend fun deleteTag(tagId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.deleteTag(tagId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new tag.
     *
     * @param name The tag name
     * @param parentId Optional parent tag ID (null for root-level tag)
     * @return The ID of the newly created tag
     */
    suspend fun createTag(name: String, parentId: String? = null): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.createTag(name, parentId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rename a tag.
     *
     * @param tagId The tag ID
     * @param newName The new name for the tag
     * @return True if the tag was renamed, false if not found
     */
    suspend fun renameTag(tagId: String, newName: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.renameTag(tagId, newName))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Move a tag to a different parent (or make it a root tag).
     *
     * @param tagId The tag ID to move
     * @param newParentId The new parent ID, or null to make it a root tag
     * @return True if the tag was moved, false if not found
     */
    suspend fun reparentTag(tagId: String, newParentId: String?): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.reparentTag(tagId, newParentId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Filter notes by tag IDs.
     *
     * @param tagIds List of tag IDs to filter by
     * @return Notes that have all the specified tags
     */
    suspend fun filterNotesByTags(tagIds: List<String>): Result<List<Note>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val notes = voiceClient.filterNotes(tagIds).map { data ->
                Note(
                    id = data.id,
                    content = data.content,
                    createdAt = data.createdAt,
                    modifiedAt = data.modifiedAt,
                    deletedAt = data.deletedAt,
                    listDisplayCache = data.listDisplayCache
                )
            }
            Result.success(notes)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Tag and Search Methods
    // =========================================================================

    /**
     * Get all tags from the database.
     */
    suspend fun getAllTags(): Result<List<Tag>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val tags = voiceClient.getAllTags().map { data ->
                Tag(
                    id = data.id,
                    name = data.name,
                    parentId = data.parentId,
                    createdAt = data.createdAt,
                    modifiedAt = data.modifiedAt
                )
            }
            Result.success(tags)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get all tags for a specific note.
     */
    suspend fun getTagsForNote(noteId: String): Result<List<Tag>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val tags = voiceClient.getTagsForNote(noteId).map { data ->
                Tag(
                    id = data.id,
                    name = data.name,
                    parentId = data.parentId,
                    createdAt = data.createdAt,
                    modifiedAt = data.modifiedAt
                )
            }
            Result.success(tags)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Add a tag to a note.
     * Creates a note_tag association between the note and tag.
     *
     * @return TagChangeResult with changed (true if tag was added), noteId, and listCacheRebuilt
     */
    suspend fun addTagToNote(noteId: String, tagId: String): Result<TagChangeResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val result = voiceClient.addTagToNote(noteId, tagId)
            Result.success(TagChangeResult(
                changed = result.changed,
                noteId = result.noteId,
                listCacheRebuilt = result.listCacheRebuilt
            ))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Remove a tag from a note.
     * Soft-deletes the note_tag association between the note and tag.
     *
     * @return TagChangeResult with changed (true if tag was removed), noteId, and listCacheRebuilt
     */
    suspend fun removeTagFromNote(noteId: String, tagId: String): Result<TagChangeResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val result = voiceClient.removeTagFromNote(noteId, tagId)
            Result.success(TagChangeResult(
                changed = result.changed,
                noteId = result.noteId,
                listCacheRebuilt = result.listCacheRebuilt
            ))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Note Marking (Star/Bookmark) Methods
    // =========================================================================

    /**
     * Check if a note is marked (starred/bookmarked).
     */
    suspend fun isNoteMarked(noteId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.isNoteMarked(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Mark a note (add the _system/_marked tag).
     * Returns true if the note was marked, false if already marked.
     */
    suspend fun markNote(noteId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.markNote(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Unmark a note (remove the _system/_marked tag).
     * Returns true if the note was unmarked, false if not marked.
     */
    suspend fun unmarkNote(noteId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.unmarkNote(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Toggle a note's marked state.
     * Returns the new marked state (true if now marked, false if now unmarked).
     */
    suspend fun toggleNoteMarked(noteId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.toggleNoteMarked(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the _system tag ID as a hex string.
     * Used for filtering system tags from UI display.
     */
    suspend fun getSystemTagIdHex(): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getSystemTagIdHex())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Execute a search query.
     * Supports "tag:Name" syntax for tag filtering and free text search.
     * Multiple tags can be combined: "tag:Work tag:Important meeting notes"
     */
    suspend fun searchNotes(query: String): Result<SearchResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val result = voiceClient.searchNotes(query)
            val searchResult = SearchResult(
                notes = result.notes.map { data ->
                    Note(
                        id = data.id,
                        content = data.content,
                        createdAt = data.createdAt,
                        modifiedAt = data.modifiedAt,
                        deletedAt = data.deletedAt,
                        listDisplayCache = data.listDisplayCache
                    )
                },
                ambiguousTags = result.ambiguousTags,
                notFoundTags = result.notFoundTags
            )
            Result.success(searchResult)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the types of unresolved conflicts for a specific note.
     * Returns a list of conflict type strings (e.g., ["content", "delete"]).
     */
    suspend fun getNoteConflictTypes(noteId: String): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getNoteConflictTypes(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Every unresolved conflict that concerns a note: its content, deletion,
     * tag links, attachments and their transcriptions. Each entry names the
     * two devices that disagreed.
     */
    suspend fun getNoteConflicts(noteId: String): Result<List<ConflictData>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getNoteConflicts(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Accept the merged values of every conflict on a note as they stand.
     * The acceptance is a new version and reaches every peer on the next sync.
     * Returns how many conflicts were accepted.
     */
    suspend fun acceptNoteConflicts(noteId: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.acceptNoteConflicts(noteId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Every version of a note's content, oldest first.
     */
    suspend fun getNoteHistory(noteId: String): Result<List<VersionData>> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getFieldHistory("note", noteId, "content"))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * One version by id (the sides of a conflict, for example).
     */
    suspend fun getVersion(versionId: String): Result<VersionData?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getVersion(versionId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Resolve a conflict by writing a new value for its field. Syncs to every peer.
     */
    suspend fun resolveConflictWithContent(conflictId: String, content: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.resolveConflictWithContent(conflictId, content))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Number of unresolved conflicts in the whole database.
     */
    suspend fun getUnresolvedConflictCount(): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getUnresolvedConflictCount())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * A synced setting shared by every device (e.g. transcription.preferred_languages), or null.
     */
    suspend fun getSetting(key: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getSetting(key))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set a synced setting. Concurrent changes on two devices are merged and flagged.
     */
    suspend fun setSetting(key: String, value: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.setSetting(key, value))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Audio Import Methods
    // =========================================================================

    /**
     * Import an audio file, creating all necessary database records.
     *
     * This creates:
     * 1. An AudioFile record
     * 2. A Note record (with created_at = file_created_at if provided)
     * 3. A NoteAttachment linking them
     *
     * @param filename Original filename of the audio file
     * @param fileCreatedAt Unix timestamp of when the file was created (optional)
     * @param durationSeconds Duration of the audio file in seconds (optional)
     * @return ImportAudioResult with noteId and audioFileId
     */
    suspend fun importAudioFile(
        filename: String,
        fileCreatedAt: Long?,
        durationSeconds: Long?
    ): Result<ImportAudioResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            val result = voiceClient.importAudioFile(filename, fileCreatedAt, durationSeconds)
            Result.success(ImportAudioResult(
                noteId = result.noteId,
                audioFileId = result.audioFileId
            ))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Import a recording into a note that already exists, and return the new
     * audio file's id.
     *
     * This is what the phone's recorder uses: the note is made first and the
     * recording happens inside it, so saving attaches the file to that note
     * instead of creating a second one.
     */
    suspend fun importAudioFileIntoNote(
        noteId: String,
        filename: String,
        fileCreatedAt: Long?,
        durationSeconds: Long?
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.importAudioFileIntoNote(noteId, filename, fileCreatedAt, durationSeconds))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Copy an audio file from a source URI to the audio file storage directory.
     *
     * @param context Application context for content resolver
     * @param sourceUri The URI of the source audio file
     * @param audioFileId The ID of the audio file record (used as the destination filename)
     * @param extension The file extension (e.g., "mp3", "m4a")
     * @return Result indicating success or failure
     */
    suspend fun copyAudioFileToStorage(
        context: Context,
        sourceUri: android.net.Uri,
        audioFileId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // The row names the file (Stage 13): the recording's start, the tail of its id, the extension
            val localName = ensureInitialized().getAudioFile(audioFileId)?.localName?.takeIf { it.isNotEmpty() }
                ?: return@withContext Result.failure(Exception("The recording $audioFileId has no row yet"))
            val destFile = File(audioFileDir, localName)
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext Result.failure(Exception("Could not open source file"))
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Create a new note with empty content.
     *
     * @param content Initial content for the note (can be empty)
     * @return The ID of the created note
     */
    suspend fun createNote(content: String = ""): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.createNote(content))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // File Storage Configuration Methods
    // =========================================================================

    /**
     * Get the current file storage configuration as JSON.
     * Returns null if no configuration is set.
     */
    suspend fun getFileStorageConfig(): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getFileStorageConfig())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set the file storage configuration.
     *
     * @param provider The storage provider name (e.g., "s3", "none")
     * @param config Provider-specific configuration as JSON, or null to disable
     */
    suspend fun setFileStorageConfig(provider: String, config: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.setFileStorageConfig(provider, config)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get the current file storage provider name.
     * Returns "none" if no provider is configured.
     */
    suspend fun getFileStorageProvider(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getFileStorageProvider())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if file storage is enabled (provider is not "none").
     */
    suspend fun isFileStorageEnabled(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.isFileStorageEnabled())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Update the storage info for an audio file after successful cloud upload.
     *
     * @param audioFileId The ID of the audio file
     * @param storageProvider The provider name (e.g., "s3")
     * @param storageKey The key/path in cloud storage
     * @return True if the audio file was found and updated
     */
    suspend fun updateAudioFileStorage(
        audioFileId: String,
        storageProvider: String,
        storageKey: String
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateAudioFileStorage(audioFileId, storageProvider, storageKey))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set how long a recording is, for a row that never had it.
     *
     * A repair, not an edit: see [MissingData]. The length is read off the file
     * by whichever device has the file.
     */
    suspend fun updateAudioFileDuration(
        audioFileId: String,
        durationSeconds: Long
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateAudioFileDuration(audioFileId, durationSeconds))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set when a recording was made, as Unix seconds, for a row that never had it.
     *
     * The timezone it was made in is not written: it cannot be read off a file.
     */
    suspend fun updateAudioFileCreatedAt(
        audioFileId: String,
        fileCreatedAt: Long
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.updateAudioFileCreatedAt(audioFileId, fileCreatedAt))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Rebuild every display cache of one note: the note pane's and the list's.
     */
    suspend fun rebuildAllCachesForNote(noteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.rebuildAllCachesForNote(noteId)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Clear the storage info for an audio file (marks it as pending upload again).
     *
     * @param audioFileId The ID of the audio file
     * @return True if the audio file was found and updated
     */
    suspend fun clearAudioFileStorage(audioFileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.clearAudioFileStorage(audioFileId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Sync Configuration Methods
    // =========================================================================

    /**
     * Get the maximum sync file size in MB.
     * Files larger than this will be tagged as _system/_nonsynced/_too-big.
     */
    suspend fun getMaxSyncFileSizeMb(): Result<UInt> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.getMaxSyncFileSizeMb())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Set the maximum sync file size in MB.
     * Files larger than this will be tagged as _system/_nonsynced/_too-big.
     *
     * @param sizeMb The maximum file size in MB (e.g., 100 for 100MB)
     */
    suspend fun setMaxSyncFileSizeMb(sizeMb: UInt): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            voiceClient.setMaxSyncFileSizeMb(sizeMb)
            Result.success(Unit)
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // =========================================================================
    // Cloud Storage Download Methods
    // =========================================================================

    private fun uniffi.voicecore.UploadResultData.toModel() = UploadResult(
        uploaded = uploaded,
        skipped = skipped,
        failed = failed,
        deferred = deferred,
        errors = errors
    )

    /**
     * Upload every recording the bucket does not hold yet. Runs only when the
     * user asks; a sync never uploads.
     */
    suspend fun upload(): Result<UploadResult> = withContext(Dispatchers.IO) {
        try {
            AppLogger.i(TAG, "Starting upload")
            val voiceClient = ensureInitialized()
            val result = voiceClient.upload().toModel()
            AppLogger.i(TAG, "Upload completed: ${result.describe()}")
            Result.success(result)
        } catch (e: VoiceCoreException) {
            AppLogger.e(TAG, "Upload failed", e)
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            AppLogger.e(TAG, "Upload failed", e)
            Result.failure(e)
        }
    }

    private fun uniffi.voicecore.DownloadResultData.toModel() = DownloadResult(
        downloaded = downloaded,
        alreadyLocal = alreadyLocal,
        notInCloud = notInCloud,
        failed = failed,
        errors = errors
    )

    /**
     * Download every audio file of a note that is in cloud storage but not on
     * this device. This is the on-demand "media missing, download" action.
     *
     * Fails (Result.failure) when the cloud configuration has not arrived via
     * sync yet, when offline, or when an object is missing in the bucket.
     */
    suspend fun downloadAudioFilesForNote(noteId: String): Result<DownloadResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.downloadAudioFilesForNote(noteId).toModel())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download a single audio file on demand.
     */
    suspend fun downloadAudioFile(audioFileId: String): Result<DownloadResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.downloadAudioFile(audioFileId).toModel())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Download every audio file that is in cloud storage but not on this device.
     * Only for explicit "fetch everything" actions; sync never does this on Android.
     *
     * @return DownloadResult with count of downloaded files and any errors
     */
    suspend fun downloadMissingAudioFiles(): Result<DownloadResult> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.downloadMissingAudioFiles().toModel())
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if an audio file exists locally on disk.
     *
     * @param audioFileId The audio file ID
     * @return True if the file exists locally
     */
    suspend fun audioFileExistsLocally(audioFileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.audioFileExistsLocally(audioFileId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Check if an audio file is available in cloud storage.
     *
     * @param audioFileId The audio file ID
     * @return True if the file has been uploaded to cloud storage
     */
    suspend fun audioFileInCloud(audioFileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val voiceClient = ensureInitialized()
            Result.success(voiceClient.audioFileInCloud(audioFileId))
        } catch (e: VoiceCoreException) {
            Result.failure(Exception(e.message))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Close the client and release resources.
     */
    fun close() {
        client = null
    }

    companion object {
        /** The name the core gives a phone until the application names it. */
        const val CORE_DEFAULT_DEVICE_NAME = "Voice Mobile"

        private const val TAG = "VoiceRepository"

        @Volatile
        private var instance: VoiceRepository? = null

        fun getInstance(context: Context): VoiceRepository {
            return instance ?: synchronized(this) {
                instance ?: VoiceRepository(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}


/**
 * Result of a tag change operation (add/remove tag from note).
 */
data class TagChangeResult(
    /** Whether the tag association was actually changed */
    val changed: Boolean,
    /** The note ID that was affected */
    val noteId: String,
    /** Whether the list pane cache was rebuilt */
    val listCacheRebuilt: Boolean
)

/**
 * Result of importing an audio file.
 */
data class ImportAudioResult(
    /** The ID of the created note */
    val noteId: String,
    /** The ID of the created audio file record */
    val audioFileId: String
)

/** What a successful join gives back. */
data class Joined(val accountId: String, val peerId: String, val peerName: String, val peerUrl: String, val granted: Boolean)

/** What a move to another account gave back (Stage 1). */
data class Moved(val accountId: String, val peerName: String, val notesMoved: Long, val tagsMerged: Long)

/**
 * One snapshot of the database, as listed by [VoiceRepository.listSnapshots].
 */
data class Snapshot(
    /** File name; what [VoiceRepository.restoreSnapshot] takes */
    val name: String,
    val path: String,
    val sizeBytes: Long,
    /** Notes in the snapshot that are not in the trash */
    val noteCount: Long,
)

/**
 * Result of uploading recordings to the bucket.
 */
data class UploadResult(
    /** Files uploaded in this run */
    val uploaded: Int,
    /** Pending rows whose file is not on this device (another device owns them) */
    val skipped: Int = 0,
    /** Files that failed to upload */
    val failed: Int = 0,
    /** Files not attempted because an earlier failure stopped the batch */
    val deferred: Int = 0,
    /** One message per failure */
    val errors: List<String>
) {
    /** One-line description of the outcome. */
    fun describe(): String {
        val parts = mutableListOf<String>()
        if (uploaded > 0) parts.add("uploaded $uploaded")
        if (skipped > 0) parts.add("$skipped belong to another device")
        if (failed > 0) parts.add("$failed failed")
        if (deferred > 0) parts.add("$deferred not attempted")
        return if (parts.isEmpty()) "nothing to upload" else parts.joinToString(", ")
    }
}

/**
 * Result of downloading audio files from cloud storage.
 */
data class DownloadResult(
    /** Number of files successfully downloaded and verified */
    val downloaded: Int,
    /** Number of files that were already on this device */
    val alreadyLocal: Int = 0,
    /** Number of files whose owning device has not uploaded them yet */
    val notInCloud: Int = 0,
    /** Number of downloads that failed */
    val failed: Int = 0,
    /** Error messages for any failed downloads */
    val errors: List<String>
) {
    /** One-line human description of the outcome. */
    fun describe(): String {
        val parts = mutableListOf<String>()
        if (downloaded > 0) parts.add("downloaded $downloaded")
        if (alreadyLocal > 0) parts.add("$alreadyLocal already on this device")
        if (notInCloud > 0) parts.add("$notInCloud not uploaded by their device yet")
        if (failed > 0) parts.add("$failed failed")
        return if (parts.isEmpty()) "nothing to download" else parts.joinToString(", ")
    }
}
