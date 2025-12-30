package com.dotancohen.voiceandroid

import android.app.Application
import com.dotancohen.voiceandroid.util.AppLogger

class VoiceApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize logging first
        AppLogger.init(this)
        AppLogger.i(TAG, "VoiceApplication starting")

        // Load native library
        System.loadLibrary("voicecore")
        AppLogger.i(TAG, "Native library loaded")
    }

    companion object {
        private const val TAG = "VoiceApplication"
    }
}
