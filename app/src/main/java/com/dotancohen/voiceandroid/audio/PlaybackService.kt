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
import com.dotancohen.voiceandroid.util.Durations
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The recording being listened to, in the notification drawer.
 *
 * A voice note is listened to with the phone in a pocket: the screen is off,
 * or another application is in front. Without this, stopping the playback means
 * finding the app again, and Android may stop the process while a recording is
 * still playing. The notification says which recording is playing and how far
 * through it is, and carries Pause (Play once paused), Stop, and a tap that
 * opens the note the recording belongs to.
 *
 * It is a foreground service of type `mediaPlayback`, started when playback
 * starts and stopped when the playback stops, so the process is not killed
 * mid-recording. [AudioPlayerManager] starts and stops it; nothing else should.
 */
class PlaybackService : Service() {
    /** With a handler: the notification is not worth the process. */
    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Main + CoroutineExceptionHandler { _, e ->
            AppLogger.e(TAG, "The playback notification hit something it could not handle", e)
        }
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(CHANNEL, "Playing", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while a recording is being played"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val player = AudioPlayerManager.shared(applicationContext)

        when (intent?.action) {
            ACTION_PAUSE -> player.togglePlayPause()
            ACTION_STOP -> {
                player.stopPlayback()
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startInForeground(notification(player.nowPlaying.value, player.playbackState.value))

        if (!watching) {
            watching = true
            scope.launch {
                combine(player.nowPlaying, player.playbackState) { playing, state -> playing to state }
                    .collect { (playing, state) ->
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIFICATION_ID, notification(playing, state))
                    }
            }
        }
        return START_NOT_STICKY
    }

    private var watching = false

    private fun startInForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notification(playing: NowPlaying?, state: PlaybackState): Notification {
        // Tapping the notification opens the note the recording belongs to,
        // which is where the user can read along; without a note it opens the
        // notes list.
        val open = Intent(this, MainActivity::class.java).apply {
            playing?.noteId?.let { putExtra(MainActivity.EXTRA_ROUTE, "note/$it") }
        }
        val tap = PendingIntent.getActivity(
            this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val pauseOrPlay = PendingIntent.getService(
            this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(playing?.title ?: "Playing a recording")
            .setContentText(progressLine(state, playing))
            .setOngoing(state.isPlaying)
            .setOnlyAlertOnce(true)
            .setContentIntent(tap)
            .addAction(
                Notification.Action.Builder(
                    null,
                    if (state.isPlaying) "Pause" else "Play",
                    pauseOrPlay
                ).build()
            )
            .addAction(Notification.Action.Builder(null, "Stop", stop).build())
            .build()
    }

    override fun onDestroy() {
        scope.cancel()
        watching = false
        super.onDestroy()
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val CHANNEL = "playback"
        private const val NOTIFICATION_ID = 4823
        const val ACTION_PAUSE = "com.dotancohen.voiceandroid.playback.PAUSE"
        const val ACTION_STOP = "com.dotancohen.voiceandroid.playback.STOP"

        /**
         * Where the recording has reached, and which note it is in.
         *
         * Paused is said rather than shown, because a notification that only
         * gives a time looks the same stopped as playing.
         */
        fun progressLine(state: PlaybackState, playing: NowPlaying?): String {
            val position = Durations.ofSeconds((state.currentPosition / 1000).toInt())
            val whole = state.duration.takeIf { it > 0 }?.let { Durations.ofSeconds((it / 1000).toInt()) }
            val time = if (whole != null) "$position of $whole" else position
            val speed = state.playbackSpeed
            val rate = if (speed != 1.0f) " · ${trimZero(speed)}×" else ""
            val where = playing?.noteLine?.let { " · $it" } ?: ""
            return if (state.isPlaying) "$time$rate$where" else "Paused at $time$rate$where"
        }

        /** 1.5 rather than 1.5000001, and 2 rather than 2.0. */
        private fun trimZero(speed: Float): String {
            val rounded = Math.round(speed * 100) / 100.0
            return if (rounded == Math.floor(rounded)) rounded.toInt().toString() else rounded.toString()
        }

        /** Put the notification up, or bring it up to date. */
        fun start(context: Context) {
            try {
                context.startForegroundService(Intent(context, PlaybackService::class.java))
            } catch (e: Exception) {
                // Android refuses a foreground service to an app that is not
                // on screen. Playback itself is unaffected; only the
                // notification is missing, and the next tap of Play while the
                // app is open puts it back.
                AppLogger.w(TAG, "No playback notification: ${e.message}")
            }
        }

        /** Take the notification down: nothing is playing any more. */
        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, PlaybackService::class.java))
            } catch (e: Exception) {
                AppLogger.w(TAG, "Could not stop the playback service: ${e.message}")
            }
        }
    }
}
