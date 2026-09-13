package com.dotancohen.voiceandroid.util

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/** The wrapping of a secret (Stage 14), with a key of the test's own instead of the Keystore's. */
class SecretWrapTest {
    private fun key(): SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()

    @Test
    fun `a secret comes back as it went, and never travels in clear`() {
        val wrap = SecretWrap(key().let { k -> { k } })
        val clear = "מפתח-המכשיר-0123456789abcdefghijklmnopqrstuv".toByteArray()
        val wrapped = wrap.wrap(clear)
        assertArrayEquals(clear, wrap.unwrap(wrapped))
        assertFalse(String(wrapped, Charsets.ISO_8859_1).contains("0123456789"))
        assertFalse("a fresh nonce every time", wrapped.contentEquals(wrap.wrap(clear)))
    }

    @Test
    fun `another key, or a changed byte, does not unwrap`() {
        val clear = "secret".toByteArray()
        val wrapped = SecretWrap(key().let { k -> { k } }).wrap(clear)
        assertThrows(Exception::class.java) { SecretWrap(key().let { k -> { k } }).unwrap(wrapped) }
        val changed = wrapped.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() }
        val same = SecretWrap(key().let { k -> { k } })
        assertThrows(Exception::class.java) { same.unwrap(changed) }
        assertThrows(Exception::class.java) { same.unwrap(ByteArray(5)) }
    }
}
