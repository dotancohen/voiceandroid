package com.dotancohen.voiceandroid.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dotancohen.voiceandroid.data.SyncResult
import com.dotancohen.voiceandroid.data.VoiceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = VoiceRepository.getInstance(application)
    private val prefs = application.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)

    private val _serverUrl = MutableStateFlow(prefs.getString("server_url", "") ?: "")
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    private val _serverPeerId = MutableStateFlow(prefs.getString("server_peer_id", "") ?: "")
    val serverPeerId: StateFlow<String> = _serverPeerId.asStateFlow()

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

    // Unsynced changes indicator
    private val _hasUnsyncedChanges = MutableStateFlow(false)
    val hasUnsyncedChanges: StateFlow<Boolean> = _hasUnsyncedChanges.asStateFlow()

    // Pending audio path awaiting permission grant (persisted to survive activity recreation)
    private val _pendingAudioPath = MutableStateFlow<String?>(
        prefs.getString("pending_audiofile_path", null)
    )
    val pendingAudioPath: StateFlow<String?> = _pendingAudioPath.asStateFlow()

    init {
        loadSettings()
        checkUnsyncedChanges()
    }

    /**
     * Check if there are local changes that haven't been synced.
     */
    fun checkUnsyncedChanges() {
        viewModelScope.launch {
            repository.hasUnsyncedChanges()
                .onSuccess { _hasUnsyncedChanges.value = it }
                .onFailure { _hasUnsyncedChanges.value = false }
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

    fun saveSettings(
        serverUrl: String,
        serverPeerId: String,
        deviceId: String,
        deviceName: String
    ) {
        viewModelScope.launch {
            // Save to SharedPreferences (for UI persistence)
            prefs.edit()
                .putString("server_url", serverUrl)
                .putString("server_peer_id", serverPeerId)
                .apply()

            _serverUrl.value = serverUrl
            _serverPeerId.value = serverPeerId

            // Save to Voice Core
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

            // Configure sync if all required fields are present
            if (serverUrl.isNotBlank() && serverPeerId.isNotBlank()) {
                repository.configureSync(
                    serverUrl = serverUrl,
                    serverPeerId = serverPeerId,
                    deviceId = _deviceId.value,
                    deviceName = _deviceName.value
                )
            }
        }
    }

    fun generateNewDeviceId(): String {
        return repository.generateDeviceId()
    }

    fun syncNow() {
        if (_isSyncing.value) return

        viewModelScope.launch {
            _isSyncing.value = true
            _syncResult.value = null
            _syncError.value = null
            _debugInfo.value = null

            repository.syncNow()
                .onSuccess { result ->
                    _syncResult.value = result
                }
                .onFailure { exception ->
                    _syncError.value = exception.message
                }

            // Get debug info about audio files
            updateDebugInfo()

            // Check for any remaining unsynced changes
            checkUnsyncedChanges()

            _isSyncing.value = false
        }
    }

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

            repository.initialSync()
                .onSuccess { result ->
                    _syncResult.value = result
                }
                .onFailure { exception ->
                    _syncError.value = exception.message
                }

            // Get debug info about audio files
            updateDebugInfo()

            // Check for any remaining unsynced changes
            checkUnsyncedChanges()

            _isSyncing.value = false
        }
    }
}
