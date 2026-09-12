package com.dotancohen.voiceandroid.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.voicecore.Stamp

/**
 * Whether a recording can be fetched from cloud storage decides whether the
 * note screen offers a download or says the file is only on another phone.
 * Both halves of the address are needed: a provider without a key names no
 * object, and a key without a provider names no bucket.
 */
class AudioFileCloudTest {

    private fun audioFile(provider: String?, key: String?) = AudioFile(
        id = "a1",
        importedAt = Stamp(at = 1_757_419_500, offset = 10800, zone = "Asia/Jerusalem"),
        filename = "הקלטה.opus",
        deviceId = "phone",
        storageProvider = provider,
        storageKey = key,
    )

    @Test
    fun `a file with both a provider and a key is in the cloud`() {
        assertTrue(audioFile("s3", "notes/a1.opus").isInCloud)
    }

    @Test
    fun `a file that has never been uploaded is not in the cloud`() {
        assertFalse(audioFile(null, null).isInCloud)
    }

    @Test
    fun `half an address is not in the cloud`() {
        assertFalse("a provider names no object on its own", audioFile("s3", null).isInCloud)
        assertFalse("a key names no bucket on its own", audioFile(null, "notes/a1.opus").isInCloud)
    }
}
