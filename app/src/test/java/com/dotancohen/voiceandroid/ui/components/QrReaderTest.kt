package com.dotancohen.voiceandroid.ui.components

import androidx.camera.core.CameraSelector
import com.dotancohen.voiceandroid.data.PairingRequests
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The code reader's pure parts (Stage 9): the decoder and the camera order, without hardware (TECHNICAL-DECISIONS 6.7). */
@OptIn(androidx.camera.core.ExperimentalLensFacing::class)
class QrReaderTest {
    private val setupText = "voice://pair?v=1&a=0199abcdef0123456789abcdef012345&t=dG9rZW4&d=0199aaaaaaaaaaaaaaaaaaaaaaaaaaaa&u=https://192.168.1.20:8765&f=YWJj"

    /** A frame's brightness with the code drawn in it, white around, as a camera would see it. */
    private fun frameOf(text: String, scale: Int = 4, margin: Int = 24): Triple<ByteArray, Int, Int> {
        val modules = QrCode.modules(text)
        val side = modules.size * scale + 2 * margin
        val bytes = ByteArray(side * side) { 0xFF.toByte() }
        for (y in modules.indices) for (x in modules[y].indices) if (modules[y][x]) {
            for (dy in 0 until scale) for (dx in 0 until scale) bytes[(margin + y * scale + dy) * side + margin + x * scale + dx] = 0
        }
        return Triple(bytes, side, side)
    }

    @Test
    fun `a setup text drawn as a code is read back from the frame`() {
        val (bytes, w, h) = frameOf(setupText)
        assertEquals(setupText, QrDecoder.decode(bytes, w, h))
        assertEquals(setupText, QrDecoder.setupText(bytes, w, h))
    }

    @Test
    fun `a code for something else is not a setup text, and a blank frame is nothing`() {
        val (bytes, w, h) = frameOf("https://example.org/הערה")
        assertEquals("https://example.org/הערה", QrDecoder.decode(bytes, w, h))
        assertNull(QrDecoder.setupText(bytes, w, h))
        val blank = ByteArray(200 * 200) { 0x80.toByte() }
        assertNull(QrDecoder.decode(blank, 200, 200))
    }

    @Test
    fun `cameras are tried rear first, then external or unknown, front last, round and round`() {
        val choice = CameraChoice(listOf("front", "usb", "rear", "odd")) {
            when (it) { "front" -> CameraSelector.LENS_FACING_FRONT; "rear" -> CameraSelector.LENS_FACING_BACK; "usb" -> CameraSelector.LENS_FACING_EXTERNAL; else -> null }
        }
        assertEquals(listOf("rear", "usb", "odd", "front"), choice.ordered)
        assertEquals("rear", choice.current)
        assertTrue(choice.hasAnother)
        assertEquals("usb", choice.next())
        assertEquals("odd", choice.next())
        assertEquals("front", choice.next())
        assertEquals("rear", choice.next())
    }

    @Test
    fun `no camera at all is no choice`() {
        val choice = CameraChoice(emptyList<String>()) { null }
        assertNull(choice.current)
        assertNull(choice.next())
        assertFalse(choice.hasAnother)
    }

    @Test
    fun `only a setup text is a setup link`() {
        assertTrue(PairingRequests.isSetupLink(" voice://pair?v=1&a=x "))
        assertFalse(PairingRequests.isSetupLink("https://voice.example/pair"))
        assertFalse(PairingRequests.isSetupLink(null))
    }
}
