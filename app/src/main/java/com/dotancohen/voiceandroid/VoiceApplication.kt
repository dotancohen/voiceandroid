package com.dotancohen.voiceandroid

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.dotancohen.voiceandroid.data.VoiceRepository
import com.dotancohen.voiceandroid.util.AppLogger
import com.dotancohen.voiceandroid.util.CriticalLog

class VoiceApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize logging first
        AppLogger.init(this)
        CriticalLog.init(this)
        AppLogger.i(TAG, "VoiceApplication starting")

        // Load native library
        System.loadLibrary("voicecore")
        AppLogger.i(TAG, "Native library loaded")

        // Travelling changes which clock a new note should record, and Android
        // announces that. The core cannot see the framework's timezone, so it
        // is told again whenever it changes.
        registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    VoiceRepository.getInstance(this@VoiceApplication).reportTimeZone()
                }
            },
            IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)
        )
    }

    companion object {
        private const val TAG = "VoiceApplication"
    }
}
