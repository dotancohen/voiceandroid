package com.dotancohen.voiceandroid

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.dotancohen.voiceandroid.transcription.OnDeviceTranscriber
import com.dotancohen.voiceandroid.ui.VoiceApp
import com.dotancohen.voiceandroid.ui.theme.VoiceTheme

class MainActivity : ComponentActivity() {
    /** A navigation route requested from outside (see automation.AdbCommandReceiver). */
    private val pendingRoute = mutableStateOf<String?>(null)

    /**
     * Asking for permission to show notifications. Recording and
     * transcription both run as foreground services, and on Android 13 and
     * newer their notification is only shown if this permission was granted.
     * Without it the work still runs, but the user cannot see that it is
     * running and has no Stop button, so it is asked for once at startup.
     */
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Either answer is fine: the services run regardless. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        takeSetupLink(intent)
        askForNotificationPermission()

        setContent {
            VoiceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    VoiceApp(
                        pendingRoute = pendingRoute.value,
                        onRouteConsumed = { pendingRoute.value = null }
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Work that had to start without a foreground service (Android
        // refuses to start one from the background) is given one now that
        // the app is on screen, so it survives the app being closed.
        OnDeviceTranscriber.ensureServiceRunning(this)
    }

    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT < 33) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
        takeSetupLink(intent)
    }

    /** A `voice://pair?…` link tapped in another app (Stage 9): the sync screen uses it. */
    private fun takeSetupLink(intent: Intent?) {
        val text = intent?.dataString ?: return
        if (!com.dotancohen.voiceandroid.data.PairingRequests.isSetupLink(text)) return
        com.dotancohen.voiceandroid.data.PairingRequests.link.value = text
        pendingRoute.value = "sync_settings"
    }

    companion object {
        /** String extra naming a navigation route, e.g. "settings" or "note/<id>". */
        const val EXTRA_ROUTE = "com.dotancohen.voiceandroid.extra.ROUTE"
    }
}
