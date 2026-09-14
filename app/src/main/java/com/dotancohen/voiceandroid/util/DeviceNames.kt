package com.dotancohen.voiceandroid.util

/**
 * A fresh phone's device name (UI-11), from what Android says about the phone,
 * best first. Pure, so it is tested without a phone; the repository reads the
 * values from Android.
 */
object DeviceNames {
    /**
     * @param settingsName the device name in Settings → About phone
     *   (`Settings.Global.DEVICE_NAME`): "Galaxy A12" until the user renames it
     * @param bluetoothName the name the phone shows other Bluetooth devices
     * @param maker `Build.MANUFACTURER`, e.g. "samsung"
     * @param model `Build.MODEL`, e.g. "SM-A125F"
     * @param animalName the name when nothing else names the phone: the core's
     *   animal with the ends of the phone's addresses, "Wombat 81:4c 7.21"
     *
     * The names the user gave come first (the device name, the Bluetooth name),
     * then the phone's type (maker and model), then the animal.
     */
    fun defaultName(settingsName: String?, bluetoothName: String?, maker: String?, model: String?, animalName: () -> String): String {
        usable(settingsName)?.let { return it }
        usable(bluetoothName)?.let { return it }
        val modelName = usable(model) ?: return animalName()
        val makerName = usable(maker)?.replaceFirstChar { it.uppercaseChar() }
        return when {
            makerName == null || modelName.startsWith(makerName, ignoreCase = true) -> modelName
            else -> "$makerName $modelName"
        }
    }

    /** The value trimmed, or null when it is empty or "localhost", which names no phone. */
    private fun usable(value: String?): String? =
        value?.trim()?.takeIf { it.isNotEmpty() && !it.startsWith("localhost", ignoreCase = true) }
}
