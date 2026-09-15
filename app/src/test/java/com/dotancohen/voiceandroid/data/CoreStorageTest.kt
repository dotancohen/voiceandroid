package com.dotancohen.voiceandroid.data

import com.dotancohen.voiceandroid.network.FaultyLink
import com.dotancohen.voiceandroid.network.LocalS3
import org.junit.AfterClass
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import uniffi.voicecore.VoiceClient
import java.io.File
import kotlin.random.Random

/**
 * The phone's storage through its own bindings against a real S3 server on
 * this computer (moto, with authentication on), directly and through a link
 * that fails the way a phone's connection does (FILE-14, FILE-19, FILE-22).
 */
class CoreStorageTest {
    companion object {
        private lateinit var s3: LocalS3

        @BeforeClass
        @JvmStatic
        fun startS3() {
            s3 = LocalS3.start()
        }

        @AfterClass
        @JvmStatic
        fun stopS3() {
            s3.close()
        }

        private const val MIB = 1024 * 1024
    }

    @get:Rule
    val folder = TemporaryFolder()

    /** Where a recording's file is, or goes: the audio folder and the row's name, as the app copies it (FILE-15). */
    private fun fileOf(client: VoiceClient, id: String): File =
        File(client.getAudiofileDirectory()!!, client.getAudioFile(id)!!.diskName)

    private fun phone(endpoint: String = s3.endpoint): VoiceClient {
        val client = VoiceClient(folder.newFolder().absolutePath, null)
        client.setAudiofileDirectory(folder.newFolder("audio").absolutePath)
        client.setFileStorageConfig("s3", s3.configJson(s3.takeBucket(), endpoint))
        return client
    }

    private fun recording(client: VoiceClient, name: String, content: ByteArray): Pair<String, File> {
        val id = client.importAudioFile(name, 1_735_689_600, null).audioFileId
        val file = fileOf(client, id)
        file.writeBytes(content)
        client.storeContentHash(id)
        return id to file
    }

    /** Run [operation]; fail if it has not ended in [seconds]. Returns (its result or its exception, seconds taken). */
    private fun <T> within(seconds: Long, operation: () -> T): Pair<Any?, Double> {
        var outcome: Any? = null
        val started = System.nanoTime()
        val worker = Thread {
            outcome = try {
                operation()
            } catch (e: Throwable) {
                e
            }
        }
        worker.isDaemon = true
        worker.start()
        worker.join(seconds * 1000)
        val took = (System.nanoTime() - started) / 1e9
        if (worker.isAlive) throw AssertionError("Still running after $seconds s: a dead link must end the operation, not hang it")
        return outcome to took
    }

    @Test
    fun `a recording goes up whole and comes back whole, and the bucket's copy is stated`() {
        val client = phone()
        val content = Random(1).nextBytes(200_000)
        val (id, file) = recording(client, "הקלטה בטלפון.m4a", content)
        val up = client.upload()
        assertEquals(emptyList<String>(), up.errors)
        assertEquals(1, up.uploaded)
        assertTrue(client.fileLocations(id).any { it.place == "cloud" && it.present })

        file.delete()
        val down = client.downloadAudioFile(id)
        assertEquals(emptyList<String>(), down.errors)
        assertEquals(1, down.downloaded)
        assertArrayEquals(content, file.readBytes())
        assertTrue(client.fileLocations(id).any { it.place == client.getThisDeviceId() && it.present })
    }

    @Test
    fun `a copy the bucket holds is removed from this phone once the bucket is asked`() {
        val client = phone()
        val (id, file) = recording(client, "להסרה מהטלפון.m4a", Random(2).nextBytes(50_000))
        assertEquals(1, client.upload().uploaded)
        assertEquals("Removed ${file.name} from this device; the bucket holds it", client.removeLocalCopy(id))
        assertFalse(file.exists())
        assertEquals(mapOf("cloud" to true, client.getThisDeviceId() to false), client.fileLocations(id).associate { it.place to it.present })
    }

    @Test
    fun `a recording larger than a part goes up in parts and comes back whole`() {
        val client = phone()
        val content = Random(2).nextBytes(20 * MIB)
        val (id, file) = recording(client, "שיעור ארוך.wav", content)
        assertEquals(1, client.upload().uploaded)
        file.delete()
        assertEquals(1, client.downloadAudioFile(id).downloaded)
        assertArrayEquals(content, file.readBytes())
    }

    @Test
    fun `an unreachable bucket fails the upload after three tries and the next run uploads`() {
        FaultyLink(s3.port).use { link ->
            val client = phone(link.url)
            recording(client, "בלי רשת.ogg", Random(3).nextBytes(30_000))
            link.refuse()
            val (result, took) = within(150) { client.upload() }
            result as uniffi.voicecore.UploadResultData
            assertEquals(0 to 1, result.uploaded to result.failed)
            // A refused connection is known at once: the second try comes straight after the first, the third a minute later (FILE-14)
            assertTrue("two tries at once and a third after a minute; it took $took s", took > 55 && took < 90)
            link.passThrough()
            assertEquals(1, client.upload().uploaded)
        }
    }

    @Test
    fun `an upload cut in the middle of a part completes at the next run`() {
        FaultyLink(s3.port).use { link ->
            val client = phone(link.url)
            val content = Random(4).nextBytes(20 * MIB)
            val (id, file) = recording(client, "נקטע.wav", content)
            link.cutAfter(bytesUp = 4L * MIB)
            val first = client.upload()
            assertEquals(0 to 1, first.uploaded to first.failed)
            link.passThrough()
            val second = client.upload()
            assertEquals(emptyList<String>(), second.errors)
            assertEquals(1, second.uploaded)
            file.delete()
            assertEquals(1, client.downloadAudioFile(id).downloaded)
            assertArrayEquals(content, file.readBytes())
        }
    }

    @Test
    fun `a download over a link that freezes ends in bounded time and leaves no file`() {
        FaultyLink(s3.port).use { link ->
            val client = phone(link.url)
            val content = Random(5).nextBytes(5 * MIB)
            val (id, file) = recording(client, "הורדה קפואה.ogg", content)
            assertEquals(1, client.upload().uploaded)
            file.delete()
            link.freezeAfter(bytesDown = 1L * MIB)
            val (result, took) = within(300) { client.downloadAudioFile(id) }
            // Three tries, each ended by the read timeout, the third a minute after the second (FILE-14)
            assertTrue("a frozen link must end each try within the read timeout; it took $took s", took < 210)
            val failed = result !is uniffi.voicecore.DownloadResultData || result.downloaded == 0
            assertTrue("the download did not arrive: $result", failed)
            assertFalse("a file that did not arrive whole is not in its place", file.exists())
            link.passThrough()
            assertEquals(1, client.downloadAudioFile(id).downloaded)
            assertArrayEquals(content, file.readBytes())
        }
    }

    @Test
    fun `a bucket that never answers ends the upload in bounded time`() {
        FaultyLink(s3.port).use { link ->
            val client = phone(link.url)
            recording(client, "דלי שותק.ogg", Random(6).nextBytes(20_000))
            link.stall()
            val (result, took) = within(480) { client.upload() }
            result as uniffi.voicecore.UploadResultData
            assertEquals(0 to 1, result.uploaded to result.failed)
            // The existence check's thirty seconds, then three tries of a minute's wait for an answer, the third a minute after the second (FILE-14)
            assertTrue("the existence check's thirty seconds and three tries of the answer's minute; it took $took s", took < 420)
        }
    }
}
