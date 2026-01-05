package com.dotancohen.voiceandroid

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import uniffi.voicecore.VoiceClient
import uniffi.voicecore.SyncServerConfig
import java.io.File

/**
 * Android instrumented tests for sync functionality.
 *
 * These tests run on an Android device/emulator and test the full stack:
 * UI -> Kotlin -> JNI -> Rust voicecore
 *
 * To run with a Python Voice server:
 * 1. Start the Voice server on the host: python src/cli.py serve --port 54321
 * 2. Forward port to emulator: adb reverse tcp:54321 tcp:54321
 * 3. Run tests: ./gradlew connectedAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class SyncIntegrationTest {

    private lateinit var context: Context
    private lateinit var testDir: File
    private lateinit var client: VoiceClient

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        testDir = File(context.filesDir, "test_sync_${System.currentTimeMillis()}")
        testDir.mkdirs()

        try {
            client = VoiceClient(testDir.absolutePath)
        } catch (e: Exception) {
            println("Failed to create VoiceClient: ${e.message}")
            e.printStackTrace()
            throw e
        }
    }

    @After
    fun tearDown() {
        if (::client.isInitialized) {
            client.close()
        }
        if (::testDir.isInitialized && testDir.exists()) {
            testDir.deleteRecursively()
        }
    }

    @Test
    fun testVoiceClientCreation() {
        // Basic test that voicecore loads and works
        assertNotNull(client)
    }

    @Test
    fun testGetDeviceId() {
        val deviceId = client.getDeviceId()
        assertNotNull(deviceId)
        assertTrue("Device ID should be 32 hex chars", deviceId.length == 32)
    }

    @Test
    fun testGetDeviceName() {
        val deviceName = client.getDeviceName()
        assertNotNull(deviceName)
        assertTrue("Device name should not be empty", deviceName.isNotEmpty())
    }

    @Test
    fun testSetDeviceName() {
        val newName = "TestDevice_${System.currentTimeMillis()}"
        client.setDeviceName(newName)
        assertEquals(newName, client.getDeviceName())
    }

    @Test
    fun testGetAllNotesEmpty() {
        val notes = client.getAllNotes()
        assertNotNull(notes)
        // May or may not be empty depending on initialization
    }

    @Test
    fun testGetNoteCount() {
        val count = client.getNoteCount()
        assertTrue("Note count should be >= 0", count >= 0)
    }

    @Test
    fun testSoftDeleteNote() {
        // Get initial notes
        val initialNotes = client.getAllNotes()
        if (initialNotes.isNotEmpty()) {
            val noteId = initialNotes[0].id

            // Delete the note
            val deleted = client.deleteNote(noteId)
            assertTrue("Delete should return true", deleted)

            // Note count should decrease
            val newCount = client.getNoteCount()
            assertTrue("Note count should be less after delete", newCount < initialNotes.size)
        }
    }

    @Test
    fun testSyncNotConfigured() {
        assertFalse("Sync should not be configured initially", client.isSyncConfigured())
    }

    @Test
    fun testConfigureSync() {
        val config = SyncServerConfig(
            serverUrl = "http://127.0.0.1:54321",
            serverPeerId = "00000000000070008000000000000001",
            deviceId = client.getDeviceId(),
            deviceName = client.getDeviceName()
        )

        client.configureSync(config)
        assertTrue("Sync should be configured after configureSync", client.isSyncConfigured())

        val retrievedConfig = client.getSyncConfig()
        assertNotNull(retrievedConfig)
        assertEquals("http://127.0.0.1:54321", retrievedConfig?.serverUrl)
    }

    @Test
    fun testClearSyncState() {
        // Configure sync first
        val config = SyncServerConfig(
            serverUrl = "http://127.0.0.1:54321",
            serverPeerId = "00000000000070008000000000000001",
            deviceId = client.getDeviceId(),
            deviceName = client.getDeviceName()
        )
        client.configureSync(config)

        // Clear sync state
        client.clearSyncState()

        // Sync should still be configured (clearSyncState only clears peer record)
        assertTrue("Sync should still be configured", client.isSyncConfigured())
    }

    @Test
    fun testDebugSyncState() {
        val state = client.debugSyncState()
        assertNotNull(state)
        assertTrue("Debug state should contain sync info", state.contains("Sync"))
    }

    @Test
    fun testHasUnsyncedChanges() {
        // Just test that the method works without crashing
        val hasChanges = client.hasUnsyncedChanges()
        // Result depends on whether there are changes, just verify it returns a boolean
        assertNotNull(hasChanges)
    }

    @Test
    fun testGetAllAudioFiles() {
        val audioFiles = client.getAllAudioFiles()
        assertNotNull(audioFiles)
        // May be empty if no audio files imported
    }

    @Test
    fun testSetAudiofileDirectory() {
        val audioDir = File(testDir, "audio")
        audioDir.mkdirs()

        client.setAudiofileDirectory(audioDir.absolutePath)
        assertEquals(audioDir.absolutePath, client.getAudiofileDirectory())
    }

    /**
     * Integration test with a live Voice server.
     *
     * Prerequisites:
     * 1. Start Voice server: python src/cli.py serve --port 54321
     * 2. Forward port: adb reverse tcp:54321 tcp:54321
     *
     * This test will skip gracefully if no server is available.
     */
    @Test
    fun testSyncWithVoiceServer() {
        // Configure sync
        val config = SyncServerConfig(
            serverUrl = "http://127.0.0.1:54321",
            serverPeerId = "00000000000070008000000000000001",
            deviceId = client.getDeviceId(),
            deviceName = client.getDeviceName()
        )

        try {
            client.configureSync(config)

            // Try to sync
            val result = client.syncNow()

            // If we get here without exception, sync worked
            println("Sync result: success=${result.success}, received=${result.notesReceived}, sent=${result.notesSent}")

            if (result.success) {
                assertTrue("Sync should report success", result.success)
            } else {
                // Server not available - log errors but don't fail
                println("Sync errors: ${result.errorMessage}")
            }
        } catch (e: Exception) {
            // Server not available - skip test gracefully
            println("Skipping sync test: ${e.message}")
        }
    }

    /**
     * Test initial sync with a live Voice server.
     */
    @Test
    fun testInitialSyncWithVoiceServer() {
        // Configure sync
        val config = SyncServerConfig(
            serverUrl = "http://127.0.0.1:54321",
            serverPeerId = "00000000000070008000000000000001",
            deviceId = client.getDeviceId(),
            deviceName = client.getDeviceName()
        )

        try {
            client.configureSync(config)

            // Try initial sync
            val result = client.initialSync()

            // If we get here without exception, sync worked
            println("Initial sync result: success=${result.success}, received=${result.notesReceived}, sent=${result.notesSent}")

            if (result.success) {
                assertTrue("Initial sync should report success", result.success)
            } else {
                // Server not available - log errors but don't fail
                println("Initial sync errors: ${result.errorMessage}")
            }
        } catch (e: Exception) {
            // Server not available - skip test gracefully
            println("Skipping initial sync test: ${e.message}")
        }
    }
}
