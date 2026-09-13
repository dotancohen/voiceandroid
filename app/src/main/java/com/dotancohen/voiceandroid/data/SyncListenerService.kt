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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * The phone as a listener (Stage 6): a foreground service that holds the
 * port while peers may reach this phone, with a Stop action. Never started
 * by itself; the switch on the sync screen starts it, and only then does
 * the phone cost anything.
 */
class SyncListenerService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var discovery: PeerDiscovery? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "Listening for peers", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while other devices may reach this phone to sync"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground(notification("Starting..."))
        scope.launch {
            val repository = VoiceRepository.getInstance(applicationContext)
            repository.startListener(PORT)
                .onSuccess { urls ->
                    AppLogger.i(TAG, "Listening at ${urls.joinToString()}")
                    getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(urls.firstOrNull() ?: "no address"))
                    // Announced on the network while listening (Stage 7)
                    val accountId = repository.getAccountId().getOrNull() ?: ""
                    val deviceId = repository.getDeviceId().getOrNull() ?: ""
                    val name = repository.getDeviceName().getOrNull() ?: ""
                    val fingerprint = repository.certificateFingerprint().getOrNull() ?: ""
                    if (accountId.isNotEmpty() && deviceId.isNotEmpty()) {
                        discovery = PeerDiscovery(applicationContext).also { it.announce(PORT, accountId, deviceId, name, fingerprint) }
                    }
                }
                .onFailure {
                    AppLogger.e(TAG, "The listener could not start", it)
                    stopSelf()
                }
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

    private fun notification(address: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 1,
            Intent(this, SyncListenerService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Listening for peers")
            .setContentText(address)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    override fun onDestroy() {
        discovery?.stopAnnouncing()
        discovery = null
        VoiceRepository.getInstance(applicationContext).stopListener()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "SyncListenerService"
        private const val CHANNEL = "listener"
        private const val NOTIFICATION_ID = 4823
        const val ACTION_STOP = "com.dotancohen.voiceandroid.LISTENER_STOP"
        /** The port every Voice listener uses. */
        const val PORT = 8384

        fun start(context: Context) {
            context.startForegroundService(Intent(context, SyncListenerService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SyncListenerService::class.java))
        }
    }
}
