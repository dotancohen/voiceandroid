package com.dotancohen.voiceandroid.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** A fresh phone's device name (UI-11): the first source that names the phone, never "localhost". */
class DeviceNamesTest {

    private val wombat = { "Wombat 81:4c 7.21" }

    @Test
    fun theNameInAboutPhoneComesFirst() {
        assertEquals("הטלפון של דותן", DeviceNames.defaultName(" הטלפון של דותן ", "Galaxy A12", "samsung", "SM-A125F", wombat))
        assertEquals("Galaxy A12", DeviceNames.defaultName("Galaxy A12", null, "samsung", "SM-A125F", wombat))
    }

    @Test
    fun theBluetoothNameIsNextThenMakerAndModel() {
        assertEquals("Galaxy S24 Ultra", DeviceNames.defaultName(null, "Galaxy S24 Ultra", "samsung", "SM-S928B", wombat))
        assertEquals("Samsung SM-A125F", DeviceNames.defaultName("  ", "", "samsung", "SM-A125F", wombat))
    }

    @Test
    fun aModelThatAlreadyNamesItsMakerIsNotRepeated() {
        // "Pixel 8" does not name its maker, so the maker goes in front: maker and model
        assertEquals("Google Pixel 8", DeviceNames.defaultName(null, null, "Google", "Pixel 8", wombat))
        assertEquals("Nokia G21", DeviceNames.defaultName(null, null, "nokia", "Nokia G21", wombat))
        assertEquals("SM-A125F", DeviceNames.defaultName(null, null, null, "SM-A125F", wombat))
    }

    @Test
    fun localhostIsNeverTheName() {
        assertEquals("Samsung SM-A125F", DeviceNames.defaultName("localhost", "LOCALHOST", "samsung", "SM-A125F", wombat))
        assertEquals("Wombat 81:4c 7.21", DeviceNames.defaultName(null, null, "samsung", "localhost", wombat))
        assertEquals("Wombat 81:4c 7.21", DeviceNames.defaultName(null, null, null, null, wombat))
    }
}
