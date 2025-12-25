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

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _syncResult = MutableStateFlow<SyncResult?>(null)
    val syncResult: StateFlow<SyncResult?> = _syncResult.asStateFlow()

    private val _syncError = MutableStateFlow<String?>(null)
    val syncError: StateFlow<String?> = _syncError.asStateFlow()

    init {
        loadSettings()
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

            repository.syncNow()
                .onSuccess { result ->
                    _syncResult.value = result
                }
                .onFailure { exception ->
                    _syncError.value = exception.message
                }

            _isSyncing.value = false
        }
    }
}
