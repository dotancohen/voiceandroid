package com.dotancohen.voiceandroid.transcription

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.dotancohen.voiceandroid.MainActivity
import com.dotancohen.voiceandroid.R
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that works through [OnDeviceTranscriber]'s queue. The
 * notification shows the stage of the current job; the service stops itself
 * when the queue is empty and frees the model.
 */
class TranscriptionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var worker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "On-device transcription", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Progress of transcriptions running on this phone"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(notification("Transcribing", "Starting"))
        if (worker?.isActive != true) {
            worker = scope.launch {
                try {
                    OnDeviceTranscriber.drain(applicationContext) { job ->
                        val title = when (job.stage) {
                            TranscriptionStage.Done -> "Transcription finished"
                            TranscriptionStage.Failed -> "Transcription failed"
                            else -> "Transcribing ${job.filename}"
                        }
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, job.message))
                    }
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Transcription worker stopped", e)
                } finally {
                    OnDeviceTranscriber.releaseModel()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(n: Notification) {
        if (Build.VERSION.SDK_INT >= 35) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
        } else if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, n)
        }
    }

    private fun notification(title: String, text: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        OnDeviceTranscriber.releaseModel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TranscriptionService"
        private const val CHANNEL = "transcription"
        private const val NOTIFICATION_ID = 4821
    }
}
