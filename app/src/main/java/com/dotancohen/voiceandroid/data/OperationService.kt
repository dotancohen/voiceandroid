package com.dotancohen.voiceandroid.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.dotancohen.voiceandroid.MainActivity
import com.dotancohen.voiceandroid.R
import com.dotancohen.voiceandroid.util.AppLogger
import com.dotancohen.voiceandroid.util.CriticalLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What the operation under way is doing, for every screen that shows it
 * (Stage 4): the service writes, the view models read.
 */
object OperationState {
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** The operation and the device of the run under way, or of the last one. */
    private val _operation = MutableStateFlow("exchange")
    val operation: StateFlow<String> = _operation.asStateFlow()

    /** One sentence of progress, or null between runs. */
    private val _progress = MutableStateFlow<String?>(null)
    val progress: StateFlow<String?> = _progress.asStateFlow()

    private val _result = MutableStateFlow<SyncResult?>(null)
    val result: StateFlow<SyncResult?> = _result.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    internal fun started(operation: String) {
        _operation.value = operation
        _running.value = true
        _progress.value = "Starting…"
        _result.value = null
        _error.value = null
    }

    internal fun progressed(sentence: String) { _progress.value = sentence }

    internal fun finished(result: SyncResult?, error: String?) {
        _result.value = result
        _error.value = error
        _progress.value = null
        _running.value = false
    }
}

/**
 * Every operation on the phone runs here (Stage 4): a foreground service
 * with a progress notification and a Cancel action, so an eight-hour
 * recording reaches the desktop with the phone in a pocket. The
 * notification is the operation's own progress and disappears when it ends;
 * it is not a reminder.
 */
class OperationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "Sync operations", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while notes and recordings move between this phone and another device"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val repository = VoiceRepository.getInstance(applicationContext)
        if (intent?.action == ACTION_CANCEL) {
            repository.cancelOperation()
            OperationState.progressed("Cancelling at the next page, file or chunk…")
            return START_NOT_STICKY
        }
        if (OperationState.running.value) {
            return START_NOT_STICKY
        }
        val operation = intent?.getStringExtra(EXTRA_OPERATION) ?: "exchange"
        val deviceId = intent?.getStringExtra(EXTRA_DEVICE)
        OperationState.started(operation)
        startInForeground(notification(operation, "Starting…"))
        scope.launch {
            val manager = getSystemService(NotificationManager::class.java)
            val onProgress: (String) -> Unit = { sentence ->
                OperationState.progressed(sentence)
                manager.notify(NOTIFICATION_ID, notification(operation, sentence))
            }
            var outcome = repository.operate(operation, deviceId, onProgress)
            // The remembered address first; when the device is not reached
            // there, the network is asked where it is (Stage 7)
            val silence = outcome.getOrNull()?.let { !it.success && DeviceDiscovery.looksUnreachable(it.errorMessage) }
                ?: DeviceDiscovery.looksUnreachable(outcome.exceptionOrNull()?.message)
            val devices = repository.listDevices().getOrNull() ?: emptyList()
            val device = devices.firstOrNull { it.deviceId == deviceId } ?: devices.firstOrNull { it.isLast } ?: devices.singleOrNull()
            if (silence && device != null) {
                val accountId = repository.getAccountId().getOrNull() ?: ""
                val found = runCatching { DeviceDiscovery(applicationContext).find(accountId, device.deviceId) }.getOrNull()
                if (found != null && found.url.trimEnd('/') != device.url.trimEnd('/')) {
                    AppLogger.i(TAG, "${device.name} answered from ${found.url}; remembering it")
                    repository.addDevice(device.deviceId, device.name, found.url)
                    outcome = repository.operate(operation, device.deviceId, onProgress)
                }
            }
            outcome
                .onSuccess { result ->
                    AppLogger.i(TAG, "$operation completed: received=${result.notesReceived}, sent=${result.notesSent}, files sent=${result.filesSent}, fetched=${result.filesFetched}")
                    OperationState.finished(result, null)
                }
                .onFailure { exception ->
                    AppLogger.e(TAG, "$operation failed", exception)
                    CriticalLog.logSyncError(operation, exception.message ?: "Unknown error")
                    OperationState.finished(null, exception.message)
                }
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(operation: String, sentence: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            this, 1,
            Intent(this, OperationService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(operation.replaceFirstChar { it.uppercase() })
            .setContentText(sentence)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Cancel", cancel).build())
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "OperationService"
        private const val CHANNEL = "operation"
        private const val NOTIFICATION_ID = 4824
        const val ACTION_CANCEL = "com.dotancohen.voiceandroid.OPERATION_CANCEL"
        const val EXTRA_OPERATION = "operation"
        const val EXTRA_DEVICE = "device"

        /** Start an operation; nothing happens if one is under way. */
        fun start(context: Context, operation: String, deviceId: String?) {
            val intent = Intent(context, OperationService::class.java)
                .putExtra(EXTRA_OPERATION, operation)
                .putExtra(EXTRA_DEVICE, deviceId)
            context.startForegroundService(intent)
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, OperationService::class.java).setAction(ACTION_CANCEL))
        }
    }
}
