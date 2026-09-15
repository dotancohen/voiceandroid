package com.dotancohen.voiceandroid.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uniffi.voicecore.VoiceClient
import uniffi.voicecore.VoiceCoreException
import java.io.File

/**
 * The core itself, through the phone's bindings, on this computer: where a
 * recording's copies are, refusing to remove this phone's copy while nothing
 * confirms another, the account's upload limit and the Issues list (FILE-22,
 * FILE-26, FILE-23, ISSUE-1). The same library the
 * phone loads, built for this computer by the hostCoreForTests task.
 */
class CoreLocationsTest {
    @get:Rule
    val folder = TemporaryFolder()

    /** Where a recording's file is, or goes: the audio folder and the row's name, as the app copies it (FILE-15). */
    private fun fileOf(client: VoiceClient, id: String): File =
        File(client.getAudiofileDirectory()!!, client.getAudioFile(id)!!.diskName)

    private fun client(): Pair<VoiceClient, File> {
        val audio = folder.newFolder("audio")
        val client = VoiceClient(folder.newFolder("data").absolutePath, null)
        client.setAudiofileDirectory(audio.absolutePath)
        return client to audio
    }

    private fun recording(client: VoiceClient, name: String, content: ByteArray): String {
        val imported = client.importAudioFile(name, null, null)
        fileOf(client, imported.audioFileId).writeBytes(content)
        client.storeContentHash(imported.audioFileId)
        return imported.audioFileId
    }

    @Test
    fun `this phone's copy is stated when it is hashed, and it is not removed while no other place confirms it`() {
        val (client, _) = client()
        val id = recording(client, "הקלטה בטלפון.ogg", ByteArray(5000) { (it % 251).toByte() })
        val here = client.getThisDeviceId()
        assertEquals(listOf(here to true), client.fileLocations(id).map { it.place to it.present })
        assertEquals("hashing already stated it", listOf(0u, 0u), client.checkFilesHere())

        try {
            client.removeLocalCopy(id)
            fail("the only copy was removed")
        } catch (e: VoiceCoreException) {
            assertTrue(e.message ?: "", (e.message ?: "").contains("no other place is known to hold it"))
        }
        val path = fileOf(client, id)
        assertTrue(path.isFile)

        // What the rows say is not enough (FILE-26): without a bucket to ask, nothing confirms it
        client.updateAudioFileStorage(id, "s3", "k.ogg")
        try {
            client.removeLocalCopy(id)
            fail("a copy was removed on the rows' word alone")
        } catch (e: VoiceCoreException) {
            assertTrue(e.message ?: "", (e.message ?: "").contains("no bucket is set up on this device"))
        }
        assertTrue(path.isFile)
        assertEquals(mapOf("cloud" to true, here to true), client.fileLocations(id).associate { it.place to it.present })
    }

    @Test
    fun `a file deleted by hand is stated gone`() {
        val (client, _) = client()
        val id = recording(client, "נמחק ביד.ogg", ByteArray(100))
        client.checkFilesHere()
        fileOf(client, id).delete()
        assertEquals(listOf(0u, 1u), client.checkFilesHere())
        assertFalse(client.fileLocations(id).single().present)
    }

    @Test
    fun `the upload limit is the account's and the issues give each reason`() {
        val (client, _) = client()
        assertEquals(100uL, client.getMaxUploadMb())
        try {
            client.setMaxUploadMb(1uL)
            fail("a limit was set before a bucket")
        } catch (e: VoiceCoreException) {
            // No bucket is configured yet
        }
        val big = recording(client, "שיעור ארוך.wav", ByteArray(1024 * 1024 + 1))
        val small = recording(client, "פתק.ogg", ByteArray(10))
        assertEquals(setOf("no_bucket"), client.issues().recordingsNotInCloud.map { it.reason }.toSet())

        client.setFileStorageConfig("s3", """{"bucket": "voice-abc", "region": "eu-central-1", "access_key_id": "k", "secret_access_key": "s"}""")
        client.setMaxUploadMb(1uL)
        assertEquals(1uL, client.getMaxUploadMb())
        val issues = client.issues()
        assertEquals(mapOf(big to "too_large", small to "waiting_for_upload"), issues.recordingsNotInCloud.associate { it.audioId to it.reason })
        assertEquals(listOf(client.getThisDeviceId()), issues.recordingsNotInCloud.first { it.audioId == small }.heldBy)
        assertEquals(1024uL * 1024uL, issues.maxUploadBytes)
    }
}
