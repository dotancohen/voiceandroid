package com.dotancohen.voiceandroid.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Sync Settings says which device is which: this phone first, by its name, then
 * the other devices of the account under a heading of their own. The same words
 * as the desktop's Sync window.
 */
class SyncScreenWordsTest {

    @Test
    fun theScreenStartsByNamingThisPhone() {
        assertEquals("This device: הטלפון של דותן", SyncScreenWords.thisDeviceLine("הטלפון של דותן"))
        assertEquals("This device: Galaxy A12", SyncScreenWords.thisDeviceLine("Galaxy A12"))
    }

    @Test
    fun aPhoneWithoutANameYetSaysSoRatherThanShowingNothing() {
        assertEquals("This device: (no name yet)", SyncScreenWords.thisDeviceLine("  "))
    }

    @Test
    fun theDevicesItSyncsWithAreTheOtherDevicesOfTheAccount() {
        assertEquals("Other devices of this account", SyncScreenWords.OTHER_DEVICES_HEADING)
        assertEquals("Other device מחשב העבודה", SyncScreenWords.otherDeviceDescription("מחשב העבודה"))
    }

    @Test
    fun thisPhonesOwnFieldsSayTheyAreThisDevice() {
        assertEquals("This device", SyncScreenWords.THIS_DEVICE_HEADING)
        assertEquals("This device's name", SyncScreenWords.THIS_DEVICE_NAME_LABEL)
        assertEquals("This device's ID", SyncScreenWords.THIS_DEVICE_ID_LABEL)
        assertEquals("Generate a new ID for this device", SyncScreenWords.NEW_THIS_DEVICE_ID_BUTTON)
    }
}
