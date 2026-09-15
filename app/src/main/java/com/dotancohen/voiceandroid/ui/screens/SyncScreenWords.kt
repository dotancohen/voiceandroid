package com.dotancohen.voiceandroid.ui.screens

/**
 * The words of Sync Settings that say which device is which: this phone's name
 * heads the screen, and every device it syncs with is called another device.
 * The desktop's Sync window uses the same words (`Voice/src/ui/sync_dialog.py`).
 * Pure, so a JVM test reads them without the native library.
 */
object SyncScreenWords {
    /** The first line of the screen. */
    fun thisDeviceLine(thisDeviceName: String): String = "This device: ${thisDeviceName.ifBlank { "(no name yet)" }}"

    /** The heading over the devices this phone syncs with. */
    const val OTHER_DEVICES_HEADING = "Other devices of this account"

    /** What a screen reader says for one of them. */
    fun otherDeviceDescription(name: String): String = "Other device $name"

    /** The heading of the card that holds this phone's own name and id. */
    const val THIS_DEVICE_HEADING = "This device"

    const val THIS_DEVICE_NAME_LABEL = "This device's name"
    const val THIS_DEVICE_ID_LABEL = "This device's ID"
    const val NEW_THIS_DEVICE_ID_BUTTON = "Generate a new ID for this device"
}
