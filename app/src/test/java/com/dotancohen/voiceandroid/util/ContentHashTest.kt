package com.dotancohen.voiceandroid.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ContentHashTest {
    @Test
    fun `the hash is the sha256 of the bytes in lowercase hex`() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", ContentHash.sha256(ByteArray(0).inputStream()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", ContentHash.sha256("abc".toByteArray().inputStream()))
    }
}
