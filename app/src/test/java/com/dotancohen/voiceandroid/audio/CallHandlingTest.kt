package com.dotancohen.voiceandroid.audio

import android.media.AudioManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a recording does when the telephone rings.
 *
 * Android hands the microphone to the call, so a recording that carries on
 * records silence. The user chooses between pausing until the call is over
 * and keeping the silence; whichever they chose, the recording itself must
 * survive the call.
 */
class CallHandlingTest {

    @Test
    fun `a telephone call is a call`() {
        assertTrue(isCallMode(AudioManager.MODE_IN_CALL))
    }

    @Test
    fun `a call made by an application is a call too`() {
        // WhatsApp, a video call: the microphone is taken just the same.
        assertTrue(isCallMode(AudioManager.MODE_IN_COMMUNICATION))
    }

    @Test
    fun `an idle phone, or one merely playing music, is not on a call`() {
        assertFalse(isCallMode(AudioManager.MODE_NORMAL))
        assertFalse(isCallMode(AudioManager.MODE_RINGTONE))
        assertFalse("a ringing phone has not taken the microphone yet", isCallMode(1))
    }

    @Test
    fun `an audio mode nobody knows is not treated as a call`() {
        // A recording must not be stopped by a mode from a future Android.
        assertFalse(isCallMode(-1))
        assertFalse(isCallMode(99))
    }

    @Test
    fun `the recording pauses during a call when that is what was chosen`() {
        assertTrue(shouldPauseForCall(true, RecorderPreferences.CALL_PAUSE))
    }

    @Test
    fun `the recording carries on during a call when that is what was chosen`() {
        assertFalse(shouldPauseForCall(true, RecorderPreferences.CALL_SILENCE))
    }

    @Test
    fun `nothing pauses when there is no call`() {
        assertFalse(shouldPauseForCall(false, RecorderPreferences.CALL_PAUSE))
        assertFalse(shouldPauseForCall(false, RecorderPreferences.CALL_SILENCE))
    }

    @Test
    fun `a setting that was never written pauses only for the value that means it`() {
        assertFalse("an unreadable setting must not stop a recording", shouldPauseForCall(true, ""))
        assertFalse(shouldPauseForCall(true, "something else"))
    }

    @Test
    fun `the recorder's states are in the order the screen relies on`() {
        assertEquals(RecordingState.Idle, RecordingState.entries.first())
        assertTrue(RecordingState.Idle < RecordingState.Recording)
        assertTrue(RecordingState.Recording < RecordingState.Paused)
        assertTrue(RecordingState.Paused < RecordingState.Saving)
        assertTrue(RecordingState.Saving < RecordingState.Saved)
    }
}
