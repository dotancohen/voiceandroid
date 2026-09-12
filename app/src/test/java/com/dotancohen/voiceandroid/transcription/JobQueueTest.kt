package com.dotancohen.voiceandroid.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The order recordings are transcribed in.
 *
 * First in, first out, because a user who queues a morning's notes expects them
 * in the order he recorded them — with one exception: he can say "this one
 * next" when he needs one transcription before the rest.
 */
class JobQueueTest {

    private fun job(id: String, seconds: Long? = 60) = TranscriptionJob(
        audioFileId = id,
        filename = "הקלטה-$id.opus",
        modelId = "small-q5_1",
        language = "he",
        beamSize = 5,
        durationSeconds = seconds,
    )

    @Test
    fun `a new queue holds nothing`() {
        val queue = JobQueue()
        assertTrue(queue.isEmpty())
        assertEquals(0, queue.size)
        assertNull(queue.takeNext())
    }

    @Test
    fun `recordings are worked in the order they were asked for`() {
        val queue = JobQueue()
        queue.add(job("a"))
        queue.add(job("b"))
        queue.add(job("c"))

        assertEquals("a", queue.takeNext()?.audioFileId)
        assertEquals("b", queue.takeNext()?.audioFileId)
        assertEquals("c", queue.takeNext()?.audioFileId)
        assertTrue(queue.isEmpty())
    }

    @Test
    fun `the same recording is not queued twice`() {
        val queue = JobQueue()
        assertTrue(queue.add(job("a")))
        assertFalse("asking twice would cost an extra wait for nothing", queue.add(job("a")))
        assertEquals(1, queue.size)
    }

    @Test
    fun `do next moves a recording to the front`() {
        val queue = JobQueue()
        queue.add(job("a"))
        queue.add(job("b"))
        queue.add(job("c"))

        assertTrue(queue.doNext("c"))

        assertEquals("c", queue.takeNext()?.audioFileId)
        assertEquals("a", queue.takeNext()?.audioFileId)
        assertEquals("b", queue.takeNext()?.audioFileId)
    }

    @Test
    fun `do next on the one already first changes nothing`() {
        val queue = JobQueue()
        queue.add(job("a"))
        queue.add(job("b"))

        assertFalse(queue.doNext("a"))
        assertEquals("a", queue.takeNext()?.audioFileId)
    }

    @Test
    fun `do next on a recording that is not waiting is refused`() {
        val queue = JobQueue()
        queue.add(job("a"))
        assertFalse(queue.doNext("never-queued"))
        assertEquals(1, queue.size)
    }

    @Test
    fun `a waiting recording can be taken out`() {
        val queue = JobQueue()
        queue.add(job("a"))
        queue.add(job("b"))

        assertTrue(queue.remove("a"))
        assertFalse(queue.remove("a"))
        assertEquals("b", queue.takeNext()?.audioFileId)
    }

    @Test
    fun `the position is what the user will actually wait for`() {
        val queue = JobQueue()
        queue.add(job("a"))
        queue.add(job("b"))
        queue.add(job("c"))

        assertEquals(1, queue.positionOf("a"))
        assertEquals(2, queue.positionOf("b"))
        assertEquals(3, queue.positionOf("c"))
        assertNull(queue.positionOf("not-here"))

        queue.doNext("c")
        assertEquals(1, queue.positionOf("c"))
        assertEquals(2, queue.positionOf("a"))
    }

    @Test
    fun `the audio in front of a recording is what decides its wait`() {
        val queue = JobQueue()
        queue.add(job("a", seconds = 120))
        queue.add(job("b", seconds = 300))
        queue.add(job("c", seconds = 60))

        assertEquals(0.0, queue.audioSecondsAhead("a")!!, 0.001)
        assertEquals(120.0, queue.audioSecondsAhead("b")!!, 0.001)
        assertEquals(420.0, queue.audioSecondsAhead("c")!!, 0.001)
    }

    @Test
    fun `a recording of unknown length in front means no estimate at all`() {
        val queue = JobQueue()
        queue.add(job("a", seconds = null))
        queue.add(job("b", seconds = 300))

        assertEquals("nothing is in front of the first one", 0.0, queue.audioSecondsAhead("a")!!, 0.001)
        assertNull("a guess here would mislead the user", queue.audioSecondsAhead("b"))
    }

    @Test
    fun `clearing leaves nothing waiting`() {
        val queue = JobQueue()
        queue.add(job("a"))
        queue.add(job("b"))
        queue.clear()
        assertTrue(queue.isEmpty())
        assertNull(queue.positionOf("a"))
    }

    @Test
    fun `the queue says whether it holds a recording`() {
        val queue = JobQueue()
        queue.add(job("a"))
        assertTrue(queue.holds("a"))
        assertFalse(queue.holds("b"))
        queue.takeNext()
        assertFalse("once taken it is the transcriber's, not the queue's", queue.holds("a"))
    }
}
