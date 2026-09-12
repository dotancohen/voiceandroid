package com.dotancohen.voiceandroid.audio

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
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps a recording going while the user is somewhere else.
 *
 * Android stops an ordinary application from holding the microphone once it
 * leaves the screen, so the recording runs behind a foreground service with
 * the microphone type. The notification shows the elapsed time and says when
 * a telephone call has taken the microphone; tapping it returns to the
 * recorder in the note.
 */
class RecordingService : Service() {
    /** With a handler: the notification is not worth the process. */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            AppLogger.e("RecordingService", "The notification hit something it could not handle", e)
        }
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "Recording", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while a voice recording is being made"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInForeground(notification("00:00:00", null))
        scope.launch {
            combine(VoiceRecorder.elapsedSeconds, VoiceRecorder.state, VoiceRecorder.inCall) { seconds, state, inCall ->
                Triple(seconds, state, inCall)
            }.collect { (seconds, state, inCall) ->
                val note = when {
                    inCall && state == RecordingState.Paused -> "Waiting for the call to end"
                    inCall -> "A call is using the microphone"
                    state == RecordingState.Paused -> "Paused"
                    else -> null
                }
                getSystemService(NotificationManager::class.java)
                    .notify(NOTIFICATION_ID, notification(hms(seconds), note))
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(elapsed: String, note: String?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "recording"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Recording $elapsed")
            .setContentText(note ?: "Tap to return to the recording")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(false)
            .setContentIntent(open)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 4822

        fun start(context: Context) {
            context.startForegroundService(Intent(context, RecordingService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RecordingService::class.java))
        }

        /** hh:mm:ss, the same shape the recorder in the note shows. */
        fun hms(seconds: Long): String =
            String.format(java.util.Locale.US, "%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60)
    }
}
