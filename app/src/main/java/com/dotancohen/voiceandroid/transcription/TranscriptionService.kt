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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Foreground service that works through [OnDeviceTranscriber]'s queue.
 *
 * It is a foreground service so that the transcription carries on when the
 * user leaves the app, locks the phone or swipes the app away: Android keeps
 * the process alive as long as a foreground service is running, and
 * `stopWithTask="false"` in the manifest keeps it running when the task is
 * removed from the recent-apps list. A twenty-minute recording takes minutes
 * of solid CPU, so this matters.
 *
 * The notification in the shade names the recording being transcribed, says
 * how many are waiting behind it, and carries a Stop button that abandons
 * the whole queue (see [OnDeviceTranscriber.stopAll]).
 */
class TranscriptionService : Service() {
    /**
     * Where the queue runs. With a handler: a transcription that fails must
     * leave the application standing, and a crash here would take a recording
     * being worked on with it.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            AppLogger.e(TAG, "The transcription queue hit something it could not handle", e)
        }
    )
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
        if (intent?.action == ACTION_STOP) {
            // The Stop button in the shade. Tell the queue to stop, then show
            // that something is happening: the model checks the flag between
            // windows of audio, so it takes about a second to come back.
            OnDeviceTranscriber.stopAll()
            startInForeground(notification("Stopping", "Finishing the current second of audio", stoppable = false))
            return START_NOT_STICKY
        }
        startInForeground(notification("Transcribing", "Starting"))
        isRunning = true
        if (worker?.isActive != true) {
            worker = scope.launch {
                try {
                    OnDeviceTranscriber.drain(applicationContext) { job ->
                        val title = when (job.stage) {
                            TranscriptionStage.Done -> "Transcription finished"
                            TranscriptionStage.Failed -> "Transcription failed"
                            TranscriptionStage.Stopped -> "Transcription stopped"
                            else -> "Transcribing ${job.filename}"
                        }
                        val waiting = OnDeviceTranscriber.queued.value.size
                        val text = if (waiting > 0) "${job.message} · $waiting waiting" else job.message
                        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(title, text))
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

    private fun notification(title: String, text: String, stoppable: Boolean = true): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
        if (stoppable) {
            val stop = PendingIntent.getService(
                this, 1,
                Intent(this, TranscriptionService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // A real icon, not null: some launchers draw the action icon in
            // the shade and will not accept a missing one.
            val icon = android.graphics.drawable.Icon.createWithResource(
                this, android.R.drawable.ic_menu_close_clear_cancel
            )
            builder.addAction(Notification.Action.Builder(icon, "Stop", stop).build())
        }
        return builder.build()
    }

    override fun onDestroy() {
        isRunning = false
        scope.cancel()
        OnDeviceTranscriber.releaseModel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TranscriptionService"
        private const val CHANNEL = "transcription"
        private const val NOTIFICATION_ID = 4821
        /** Sent by the Stop button on the notification. */
        const val ACTION_STOP = "com.dotancohen.voiceandroid.STOP_TRANSCRIBING"

        /**
         * Whether the service is up. Read by
         * [OnDeviceTranscriber.ensureServiceRunning], which promotes work
         * that had to start without a service (Android refuses to start one
         * from the background) as soon as the app is on screen again.
         */
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}
