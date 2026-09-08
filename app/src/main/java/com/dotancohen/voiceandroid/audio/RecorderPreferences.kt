package com.dotancohen.voiceandroid.audio

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Recorder settings kept on the device (not synced): which microphone to
 * record with, the friendly names the user gave the microphones, and what
 * the toolbar "New" button does by default.
 */
class RecorderPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("voice_settings", Context.MODE_PRIVATE)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** "note" or "recording" */
    var defaultNewAction: String
        get() = prefs.getString(KEY_DEFAULT_NEW_ACTION, ACTION_NOTE) ?: ACTION_NOTE
        set(value) = prefs.edit().putString(KEY_DEFAULT_NEW_ACTION, value).apply()

    /** One of [FORMAT_OPUS], [FORMAT_AAC], [FORMAT_WAV16]. */
    var recordingFormat: String
        get() = prefs.getString(KEY_FORMAT, FORMAT_OPUS)?.takeIf { it in FORMATS } ?: FORMAT_OPUS
        set(value) = prefs.edit().putString(KEY_FORMAT, value.takeIf { it in FORMATS } ?: FORMAT_OPUS).apply()

    /** Stable key of the selected microphone, or null for the system default. */
    var selectedMicKey: String?
        get() = prefs.getString(KEY_SELECTED_MIC, null)
        set(value) = prefs.edit().putString(KEY_SELECTED_MIC, value).apply()

    fun friendlyName(mic: AudioDeviceInfo): String =
        prefs.getString(KEY_MIC_NAME_PREFIX + micKey(mic), null) ?: defaultName(mic)

    fun setFriendlyName(mic: AudioDeviceInfo, name: String) {
        prefs.edit().putString(KEY_MIC_NAME_PREFIX + micKey(mic), name.trim().ifEmpty { null }).apply()
    }

    /** Every microphone the phone reports right now. */
    fun microphones(): List<AudioDeviceInfo> =
        audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).filter { it.isSource }.toList()

    /** The selected microphone if it is still present, else null (system default). */
    fun selectedMic(): AudioDeviceInfo? {
        val key = selectedMicKey ?: return null
        return microphones().firstOrNull { micKey(it) == key }
    }

    companion object {
        const val ACTION_NOTE = "note"
        const val ACTION_RECORDING = "recording"
        private const val KEY_DEFAULT_NEW_ACTION = "default_new_action"
        private const val KEY_FORMAT = "recording_format"

        /** Opus 128 kb/s at 48 kHz in an Ogg container (.ogg). */
        const val FORMAT_OPUS = "opus"
        /** AAC 96 kb/s at 44.1 kHz in an MP4 container (.m4a). */
        const val FORMAT_AAC = "aac"
        /** Uncompressed 16 kHz mono 16-bit PCM (.wav): Whisper's native input. */
        const val FORMAT_WAV16 = "wav16"
        val FORMATS = listOf(FORMAT_OPUS, FORMAT_AAC, FORMAT_WAV16)

        fun formatExtension(format: String): String = when (format) {
            FORMAT_OPUS -> "ogg"
            FORMAT_WAV16 -> "wav"
            else -> "m4a"
        }

        fun formatTitle(format: String): String = when (format) {
            FORMAT_OPUS -> "Opus, 128 kb/s, 48 kHz (.ogg)"
            FORMAT_WAV16 -> "WAV, 16 kHz, 16-bit mono (.wav)"
            else -> "AAC, 96 kb/s, 44.1 kHz (.m4a)"
        }

        fun formatDescription(format: String): String = when (format) {
            FORMAT_OPUS -> "Best sound per megabyte and more than Whisper can use. About 1 MB per minute."
            FORMAT_WAV16 -> "Exactly what Whisper listens to, so on-device transcription needs no conversion. About 1.9 MB per minute."
            else -> "Plays everywhere. About 0.7 MB per minute."
        }
        private const val KEY_SELECTED_MIC = "recorder_mic"
        private const val KEY_MIC_NAME_PREFIX = "recorder_mic_name_"

        /** Identity that survives reboots better than the numeric id. */
        fun micKey(mic: AudioDeviceInfo): String = "${mic.type}:${mic.address}:${mic.productName}"

        fun defaultName(mic: AudioDeviceInfo): String {
            val kind = when (mic.type) {
                AudioDeviceInfo.TYPE_BUILTIN_MIC -> "Built-in microphone"
                AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired headset microphone"
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth headset microphone"
                AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> "USB microphone"
                AudioDeviceInfo.TYPE_TELEPHONY -> "Telephony microphone"
                else -> "Microphone (type ${mic.type})"
            }
            val address = mic.address.takeIf { it.isNotEmpty() }?.let { " $it" } ?: ""
            return "$kind$address"
        }
    }
}
