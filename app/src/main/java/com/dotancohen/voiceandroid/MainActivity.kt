package com.dotancohen.voiceandroid

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import com.dotancohen.voiceandroid.ui.VoiceApp
import com.dotancohen.voiceandroid.ui.theme.VoiceTheme

class MainActivity : ComponentActivity() {
    /** A navigation route requested from outside (see automation.AdbCommandReceiver). */
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute.value = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        /** String extra naming a navigation route, e.g. "settings" or "note/<id>". */
        const val EXTRA_ROUTE = "com.dotancohen.voiceandroid.extra.ROUTE"
    }
}
