package com.dotancohen.voiceandroid

import android.app.Application

class VoiceApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        // Load native library
        System.loadLibrary("voicecore")
    }

    companion object {
        private const val TAG = "VoiceApplication"
    }
}
