package com.dotancohen.voiceandroid.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** The words for this phone's address (LISTEN-4), as the desktop says them. */
class AddressTextTest {
    private val candidates = "Only one of these addresses is correct; this device could not tell which. Another device tries each of them in turn."

    @Test
    fun `the address found is said alone`() {
        assertEquals("https://192.168.1.23:8384", AddressText.words(true, listOf("https://192.168.1.23:8384"), ""))
    }

    @Test
    fun `candidates are said with the sentence that only one is correct`() {
        assertEquals(
            "https://192.168.1.23:8384, https://10.0.0.5:8384. $candidates",
            AddressText.words(false, listOf("https://192.168.1.23:8384", "https://10.0.0.5:8384"), candidates)
        )
    }

    @Test
    fun `no address is said in words`() {
        assertEquals("No address on a local network was found. Is this device on a network?", AddressText.words(false, emptyList(), "No address on a local network was found. Is this device on a network?"))
    }
}
