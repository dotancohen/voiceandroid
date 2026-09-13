package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.SyncResult
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.util.AppLogger
import com.dotancohen.voiceandroid.util.CriticalLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)
    private val prefs = application.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)

    /** The peers of this phone (Stage 5), from the core; nothing is kept in preferences. */
    private val _peers = MutableStateFlow<List<com.dotancohen.voiceandroid.data.Peer>>(emptyList())
    val peers: StateFlow<List<com.dotancohen.voiceandroid.data.Peer>> = _peers.asStateFlow()

    /** The peer the one visible button names: the last used, else the only one. */
    val lastPeer: StateFlow<com.dotancohen.voiceandroid.data.Peer?> = _peers
        .map { list -> list.firstOrNull { it.isLast } ?: list.singleOrNull() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A sentence after adding, renaming or forgetting a peer, or null. */
    private val _peerMessage = MutableStateFlow<String?>(null)
    val peerMessage: StateFlow<String?> = _peerMessage.asStateFlow()

    private val _deviceId = MutableStateFlow("")
    val deviceId: StateFlow<String> = _deviceId.asStateFlow()

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    private val _audiofileDirectory = MutableStateFlow("")
    val audiofileDirectory: StateFlow<String> = _audiofileDirectory.asStateFlow()

    private val _defaultAudiofileDirectory = MutableStateFlow("")
    val defaultAudiofileDirectory: StateFlow<String> = _defaultAudiofileDirectory.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncResult = MutableStateFlow<SyncResult?>(null)
    val syncResult: StateFlow<SyncResult?> = _syncResult.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    // Debug info
    private val _debugInfo = MutableStateFlow<String?>(null)
    val debugInfo: StateFlow<String?> = _debugInfo.asStateFlow()

    // Max sync file size (in MB)
    /** The account's upload limit in megabytes (FILE-23), the same on every device. */
    private val _maxUploadMb = MutableStateFlow<ULong>(100u)
    val maxUploadMb: StateFlow<ULong> = _maxUploadMb.asStateFlow()

    // Unsynced changes indicator
    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    /** The last upload's outcome in one sentence, or null before the first. */
    private val _uploadMessage = MutableStateFlow<String?>(null)
    val uploadMessage: StateFlow<String?> = _uploadMessage.asStateFlow()

    /** The one line at the top of the sync screen (Stage 10), or null before it is known. */
    private val _notDuplicatedLine = MutableStateFlow<String?>(null)
    val notDuplicatedLine: StateFlow<String?> = _notDuplicatedLine.asStateFlow()

    /** Every peer dealt with: when it was last reached and by what (Stage 10). */
    private val _peerSummaries = MutableStateFlow<List<com.dotancohen.voiceandroid.data.PeerSummary>>(emptyList())
    val peerSummaries: StateFlow<List<com.dotancohen.voiceandroid.data.PeerSummary>> = _peerSummaries.asStateFlow()

    // Pending audio path awaiting permission grant (persisted to survive activity recreation)
    private val _pendingAudioPath = MutableStateFlow<String?>(
        prefs.getString("pending_audiofile_path", null)
    )
    val pendingAudioPath: StateFlow<String?> = _pendingAudioPath.asStateFlow()

    init {
        viewModelScope.launch { com.dotancohen.voiceandroid.data.OperationState.running.collect { _isSyncing.value = it } }
        viewModelScope.launch { com.dotancohen.voiceandroid.data.OperationState.result.collect { _syncResult.value = it; if (it != null) { updateDebugInfo(); refreshProof() } } }
        viewModelScope.launch { com.dotancohen.voiceandroid.data.OperationState.error.collect { _syncError.value = it } }
        loadSettings()
        refreshProof()
        loadMaxUploadMb()
    }

    /** The proof line and the peer summaries (Stage 10): read on the screen's opening and after every operation. */
    fun refreshProof() {
        viewModelScope.launch {
            repository.notDuplicated()
                .onSuccess { _notDuplicatedLine.value = it.sentence() }
                .onFailure { _notDuplicatedLine.value = null }
            repository.peerSummaries().onSuccess { _peerSummaries.value = it }
            repository.listPeers().onSuccess { _peers.value = it }
        }
    }

    /**
     * Set a pending audio path that will be saved after permission is granted.
     */
    fun setPendingAudioPath(path: String?) {
        _pendingAudioPath.value = path
        prefs.edit().apply {
            if (path != null) {
                putString("pending_audiofile_path", path)
            } else {
                remove("pending_audiofile_path")
            }
            apply()
        }
    }

    /**
     * Try to apply the pending audio path if permission is now granted.
     * Returns true if a pending path was applied.
     */
    fun tryApplyPendingAudioPath(): Boolean {
        val path = _pendingAudioPath.value ?: return false
        // Clear pending path first
        setPendingAudioPath(null)
        // Try to save it
        saveAudiofileDirectory(path)
        return true
    }

    private fun loadSettings() {
        viewModelScope.launch {
            repository.getDeviceId().onSuccess { id ->
                _deviceId.value = id.ifEmpty {
                    // Generate new device ID if none exists
                    val newId = repository.generateDeviceId()
                    repository.setDeviceId(newId)
                    newId
                }
            }

            repository.getDeviceName().onSuccess { name ->
                _deviceName.value = name
            }

            // Load audiofile directory
            _defaultAudiofileDirectory.value = repository.defaultAudioFileDir
            _audiofileDirectory.value = repository.audioFileDir
        }
    }

    private fun loadMaxUploadMb() {
        viewModelScope.launch {
            repository.getMaxUploadMb()
                .onSuccess { _maxUploadMb.value = it }
                .onFailure { AppLogger.e(TAG, "Failed to read the account's upload limit", it) }
        }
    }

    /**
     * Set the account's upload limit (FILE-23). Recordings larger than this
     * stay on the devices that hold them and are listed under Issues; the
     * limit reaches every device of the account at its next sync.
     */
    fun saveMaxUploadMb(megabytes: ULong) {
        viewModelScope.launch {
            repository.setMaxUploadMb(megabytes)
                .onSuccess {
                    _maxUploadMb.value = megabytes
                    AppLogger.i(TAG, "The account's upload limit is $megabytes MB")
                }
                .onFailure { e ->
                    _syncError.value = "The upload limit was not set: ${e.message}"
                    AppLogger.e(TAG, "Failed to set the account's upload limit", e)
                }
        }
    }

    /**
     * Save the audiofile directory path.
     * Returns true if successful, false if the directory is invalid.
     */
    fun saveAudiofileDirectory(path: String): Boolean {
        var success = false
        viewModelScope.launch {
            val pathToSave = path.trim().ifBlank { null }
            repository.setAudiofileDirectory(pathToSave)
                .onSuccess {
                    _audiofileDirectory.value = repository.audioFileDir
                    _syncError.value = null
                    success = true
                }
                .onFailure { e ->
                    _syncError.value = "Failed to set audio directory: ${e.message}"
                    success = false
                }
        }
        return success
    }

    /**
     * Reset audiofile directory to default.
     */
    fun resetAudiofileDirectory() {
        viewModelScope.launch {
            repository.setAudiofileDirectory(null)
            _audiofileDirectory.value = repository.audioFileDir
        }
    }

    fun saveSettings(deviceId: String, deviceName: String) {
        viewModelScope.launch {
            if (deviceId != _deviceId.value) {
                repository.setDeviceId(deviceId).onSuccess {
                    _deviceId.value = deviceId
                }
            }

            if (deviceName != _deviceName.value) {
                repository.setDeviceName(deviceName).onSuccess {
                    _deviceName.value = deviceName
                }
            }
        }
    }

    /** The peers, read again from the core. */
    fun refreshPeers() {
        viewModelScope.launch {
            repository.listPeers().onSuccess { _peers.value = it }
        }
    }

    /** A peer typed by hand (Stage 7): its device id, a name and where it listens. */
    fun addPeer(peerId: String, name: String, url: String) {
        viewModelScope.launch {
            repository.addPeer(peerId, name.ifBlank { peerId.take(8) }, url)
                .onSuccess { _peerMessage.value = "Added ${name.ifBlank { peerId.take(8) }}."; refreshPeers() }
                .onFailure { _peerMessage.value = "Not added: ${it.message}" }
        }
    }

    fun forgetPeer(peerId: String) {
        viewModelScope.launch {
            repository.forgetPeer(peerId)
                .onSuccess { _peerMessage.value = "Forgotten. Its card will not bring it back; add it again or pair again to undo."; refreshPeers() }
                .onFailure { _peerMessage.value = "Not forgotten: ${it.message}" }
        }
    }

    fun renamePeer(peerId: String, name: String) {
        viewModelScope.launch {
            repository.renamePeer(peerId, name)
                .onSuccess { refreshPeers() }
                .onFailure { _peerMessage.value = "Not renamed: ${it.message}" }
        }
    }

    fun generateNewDeviceId(): String {
        return repository.generateDeviceId()
    }

    /** Whether this phone listens for peers, and where; refreshed from the core. */
    private val _listening = MutableStateFlow(repository.listenerRunning())
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private val _listenUrls = MutableStateFlow<List<String>>(emptyList())
    val listenUrls: StateFlow<List<String>> = _listenUrls.asStateFlow()

    private val _certificateFingerprint = MutableStateFlow("")
    val certificateFingerprint: StateFlow<String> = _certificateFingerprint.asStateFlow()

    private val _accountId = MutableStateFlow("")
    val accountId: StateFlow<String> = _accountId.asStateFlow()

    /** The address, the fingerprint and the account, for the sync screen. */
    fun loadThisDevice() {
        viewModelScope.launch {
            repository.listenUrls(com.dotancohen.voiceandroid.data.SyncListenerService.PORT).onSuccess { _listenUrls.value = it }
            repository.certificateFingerprint().onSuccess { _certificateFingerprint.value = it }
            repository.getAccountId().onSuccess { _accountId.value = it }
            _listening.value = repository.listenerRunning()
        }
    }

    /** The switch: start or stop the listener service. Never started by itself. */
    fun setListening(on: Boolean) {
        val app = getApplication<Application>()
        if (on) {
            com.dotancohen.voiceandroid.data.SyncListenerService.start(app)
        } else {
            com.dotancohen.voiceandroid.data.SyncListenerService.stop(app)
        }
        _listening.value = on
    }

    /** The outcome of the last join in one sentence, or null. */
    private val _joinMessage = MutableStateFlow<String?>(null)
    val joinMessage: StateFlow<String?> = _joinMessage.asStateFlow()

    /** The peer just paired with (Stage 9): the next screen is it, with one Exchange button. */
    private val _justJoined = MutableStateFlow<com.dotancohen.voiceandroid.data.Joined?>(null)
    val justJoined: StateFlow<com.dotancohen.voiceandroid.data.Joined?> = _justJoined.asStateFlow()

    fun dismissJoined() { _justJoined.value = null }

    /** Use a setup text pasted, scanned or tapped as a link: join its account (PAIR-4), or grant a server this one (PAIR-5). */
    fun pairWith(setupText: String) {
        viewModelScope.launch {
            _joinMessage.value = null
            repository.pairWith(setupText.trim())
                .onSuccess { joined ->
                    _joinMessage.value = if (joined.granted) {
                        "${joined.peerName} now hosts this account. Press Deliver to send it your notes and recordings."
                    } else {
                        "Joined account ${joined.accountId.take(8)} through ${joined.peerName}."
                    }
                    refreshPeers()
                    _justJoined.value = joined
                }
                .onFailure { _joinMessage.value = "Could not join: ${it.message}" }
        }
    }

    /** This phone's setup text while it is shown (Stage 9, PAIR-1), and the seconds until it is hidden again. */
    private val _myCode = MutableStateFlow<String?>(null)
    val myCode: StateFlow<String?> = _myCode.asStateFlow()
    private val _codeSecondsLeft = MutableStateFlow(0)
    val codeSecondsLeft: StateFlow<Int> = _codeSecondsLeft.asStateFlow()
    private var codeTicker: kotlinx.coroutines.Job? = null

    /**
     * Show this phone's code: the listener is started, because the reading
     * device claims from it, and the code stays on screen for [CODE_SHOWN_SECONDS].
     */
    fun showMyCode() {
        viewModelScope.launch {
            if (!repository.listenerRunning()) setListening(true)
            repository.listenUrls(com.dotancohen.voiceandroid.data.SyncListenerService.PORT).onSuccess { _listenUrls.value = it }
            repository.offerCode(_listenUrls.value)
                .onSuccess { code ->
                    _myCode.value = code
                    codeTicker?.cancel()
                    codeTicker = viewModelScope.launch {
                        for (left in CODE_SHOWN_SECONDS downTo 1) {
                            _codeSecondsLeft.value = left
                            kotlinx.coroutines.delay(1000)
                        }
                        hideMyCode()
                    }
                }
                .onFailure { _joinMessage.value = "Could not make a code: ${it.message}" }
        }
    }

    fun hideMyCode() {
        codeTicker?.cancel()
        codeTicker = null
        _myCode.value = null
        _codeSecondsLeft.value = 0
    }

    /** The rows of the last connection check, or null (Stage 12). */
    private val _checkRows = MutableStateFlow<List<com.dotancohen.voiceandroid.data.CheckRow>?>(null)
    val checkRows: StateFlow<List<com.dotancohen.voiceandroid.data.CheckRow>?> = _checkRows.asStateFlow()

    /** Check the connection to a peer, the last one unless named: nothing is changed. */
    fun checkConnection(peerId: String? = null) {
        val peerId = peerId ?: lastPeer.value?.peerId
        if (peerId.isNullOrBlank()) {
            _checkRows.value = listOf(com.dotancohen.voiceandroid.data.CheckRow("Peer", false, "No peer yet: read a code shown by another device, or add one by its address", ""))
            return
        }
        viewModelScope.launch {
            _checkRows.value = null
            repository.checkConnection(peerId)
                .onSuccess { _checkRows.value = it }
                .onFailure { _checkRows.value = listOf(com.dotancohen.voiceandroid.data.CheckRow("Check", false, it.message ?: "The check could not run", "")) }
        }
    }

    /** The operation of the last button press, for the result sentence. */
    private val _lastOperation = MutableStateFlow("exchange")
    val lastOperation: StateFlow<String> = _lastOperation.asStateFlow()

    /**
     * One operation of the terms table ("sync", "deliver", "exchange", "send",
     * "fetch") with a peer: the one named, else the last used, else the only one.
     */
    fun operate(operation: String, peerId: String? = null) {
        if (com.dotancohen.voiceandroid.data.OperationState.running.value) return
        _lastOperation.value = operation
        // Every operation runs in the foreground service (Stage 4), with a
        // progress notification and a Cancel action; its state is mirrored here
        com.dotancohen.voiceandroid.data.OperationService.start(getApplication(), operation, peerId)
    }

    /** Cancel the operation under way: it stops at its next page, file or chunk. */
    fun cancelOperation() {
        com.dotancohen.voiceandroid.data.OperationService.cancel(getApplication())
    }

    /** One sentence of progress while an operation runs, or null. */
    val progressSentence: StateFlow<String?> = com.dotancohen.voiceandroid.data.OperationState.progress

    /** Hours of silence after which the listener stops itself; 0 means never (Stage 6). */
    private val _idleStopHours = MutableStateFlow(0)
    val idleStopHours: StateFlow<Int> = _idleStopHours.asStateFlow()

    fun loadIdleStop() { _idleStopHours.value = repository.listenerIdleStopHours() }

    fun setIdleStopHours(hours: Int) {
        repository.setListenerIdleStopHours(hours)
        _idleStopHours.value = hours
    }

    /** Exchange with the last peer: sync, then send and fetch recordings. */
    fun exchange() = operate("exchange")

    /** Upload every recording the bucket does not hold yet. */
    fun upload() {
        if (_isUploading.value) return

        viewModelScope.launch {
            _isUploading.value = true
            _uploadMessage.value = null
            repository.upload()
                .onSuccess { result ->
                    _uploadMessage.value = "Upload: ${result.describe()}"
                }
                .onFailure { exception ->
                    _uploadMessage.value = "Upload failed: ${exception.message}"
                    CriticalLog.logSyncError("upload", exception.message ?: "Unknown error")
                }
            updateDebugInfo()
            _isUploading.value = false
        }
    }

    /** Sync with the last peer: notes only. */
    fun sync() = operate("sync")

    fun updateDebugInfo() {
        viewModelScope.launch {
            val allAudioFiles = repository.getAllAudioFiles().getOrNull() ?: emptyList()
            val allNotes = repository.getAllNotes().getOrNull() ?: emptyList()

            val notesWithAudio = allNotes.count { note ->
                val audioForNote = repository.getAudioFilesForNote(note.id).getOrNull() ?: emptyList()
                audioForNote.isNotEmpty()
            }

            val audioFileDir = repository.getAudioFileDirectory()
            val syncState = repository.debugSyncState().getOrNull() ?: "N/A"

            _debugInfo.value = buildString {
                appendLine("Debug Info:")
                appendLine("- Audio files in DB: ${allAudioFiles.size}")
                appendLine("- Notes in DB: ${allNotes.size}")
                appendLine("- Notes with audio: $notesWithAudio")
                appendLine("- Audio dir: $audioFileDir")
                if (allAudioFiles.isNotEmpty()) {
                    appendLine("- First audio file: ${allAudioFiles[0].filename} (${allAudioFiles[0].id.take(8)}...)")
                }
                appendLine()
                appendLine("Sync State:")
                append(syncState)
            }
        }
    }

    /**
     * Perform initial sync to fetch full dataset from server.
     * This is useful if note_attachments or other data was missed during incremental sync.
     */
    fun fullResync() {
        if (_isSyncing.value) return

        viewModelScope.launch {
            _isSyncing.value = true
            _syncResult.value = null
            _syncError.value = null
            _debugInfo.value = "Performing full sync..."
            AppLogger.i(TAG, "Starting full resync")

            repository.initialSync(lastPeer.value?.peerId)
                .onSuccess { result ->
                    _syncResult.value = result
                    AppLogger.i(TAG, "Full resync completed: received=${result.notesReceived}, sent=${result.notesSent}")
                }
                .onFailure { exception ->
                    _syncError.value = exception.message
                    AppLogger.e(TAG, "Full resync failed", exception)
                    CriticalLog.logSyncError("fullResync", exception.message ?: "Unknown error")
                }

            // Get debug info about audio files
            updateDebugInfo()

            // Check for any remaining unsynced changes
            refreshProof()

            _isSyncing.value = false
        }
    }

    /**
     * Reset sync timestamps to force re-fetching all data from peers.
     * This preserves peer configuration but clears last_sync_at timestamps.
     */
    fun resetSyncTimestamps() {
        viewModelScope.launch {
            _syncResult.value = null
            _syncError.value = null
            AppLogger.i(TAG, "Resetting sync timestamps")

            repository.resetSyncTimestamps()
                .onSuccess {
                    _debugInfo.value = "Sync timestamps reset. Next sync will fetch all data."
                    AppLogger.i(TAG, "Sync timestamps reset successfully")
                }
                .onFailure { exception ->
                    _syncError.value = exception.message
                    AppLogger.e(TAG, "Failed to reset sync timestamps", exception)
                }

            // Update debug info to show new state
            updateDebugInfo()
        }
    }

    // Log viewing
    private val _logContent = MutableStateFlow("")
    val logContent: StateFlow<String> = _logContent.asStateFlow()

    /**
     * Load the application log content.
     */
    fun loadLogContent() {
        _logContent.value = AppLogger.readLog(1000)
    }

    /**
     * Get the log file path for display.
     */
    fun getLogFilePath(): String = AppLogger.getLogFilePath()

    companion object {
        private const val TAG = "SettingsViewModel"
        /** How long the code stays on screen; the token itself lives ten minutes. */
        const val CODE_SHOWN_SECONDS = 60
    }
}
