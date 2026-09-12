package com.dotancohen.voiceandroid.util

import com.dotancohen.voiceandroid.audio.PlaybackPreferences
import com.dotancohen.voiceandroid.audio.RecorderPreferences
import com.dotancohen.voiceandroid.testing.FakePreferences
import com.dotancohen.voiceandroid.transcription.TranscriptionPreferences
import com.dotancohen.voiceandroid.transcription.WhisperModels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every setting in the app lives in one file, `voice_settings`, written by
 * five different classes. Two of them using the same key would mean one
 * setting silently changing another: choosing a recording format that
 * switches the playback speed, say. Nothing in the type system prevents it,
 * so it is checked here.
 */
class SettingsFileTest {

    @Test
    fun `every settings class writes to the one file`() {
        assertEquals("voice_settings", UiPreferences.SETTINGS_FILE)
    }

    @Test
    fun `settings written by different parts of the app do not overwrite each other`() {
        val shared = FakePreferences()
        val ui = UiPreferences(shared)
        val recorder = RecorderPreferences(shared, null)
        val transcription = TranscriptionPreferences(shared)
        val playback = PlaybackPreferences(shared)

        // Something different from the default in every one of them.
        ui.spotlightDurationMs = 400
        ui.notesListLines = 4
        ui.uiSizeMode = UiPreferences.SIZE_LARGE
        ui.toggledLarge = true
        ui.autoplayOnOpen = true
        ui.timeFormat = TimeFormat.CUSTOM
        ui.timeFormatCustom = "yyyy-MM-dd HH:mm"
        ui.iconSize = UiPreferences.ICONS_LARGE

        recorder.recordingFormat = RecorderPreferences.FORMAT_WAV16
        recorder.duringCall = RecorderPreferences.CALL_SILENCE
        recorder.startRecordingImmediately = true
        recorder.defaultNewAction = RecorderPreferences.ACTION_RECORDING
        recorder.selectedMicKey = "mic-3"

        val model = WhisperModels.CATALOGUE.last()
        transcription.modelId = model.id
        transcription.language = "ar"
        transcription.beamSize = 2
        transcription.setLanguageUse("ru", TranscriptionPreferences.USE_BUTTON)

        playback.speed = 1.75f

        // and every one of them still reads back what it was given
        assertEquals(400, ui.spotlightDurationMs)
        assertEquals(4, ui.notesListLines)
        assertEquals(UiPreferences.SIZE_LARGE, ui.uiSizeMode)
        assertTrue(ui.toggledLarge)
        assertTrue(ui.autoplayOnOpen)
        assertEquals(TimeFormat.CUSTOM, ui.timeFormat)
        assertEquals("yyyy-MM-dd HH:mm", ui.timeFormatCustom)
        assertEquals(UiPreferences.ICONS_LARGE, ui.iconSize)

        assertEquals(RecorderPreferences.FORMAT_WAV16, recorder.recordingFormat)
        assertEquals(RecorderPreferences.CALL_SILENCE, recorder.duringCall)
        assertTrue(recorder.startRecordingImmediately)
        assertEquals(RecorderPreferences.ACTION_RECORDING, recorder.defaultNewAction)
        assertEquals("mic-3", recorder.selectedMicKey)

        assertEquals(model.id, transcription.modelId)
        assertEquals("ar", transcription.language)
        assertEquals(2, transcription.beamSize)
        assertEquals(TranscriptionPreferences.USE_BUTTON, transcription.languageUse("ru"))

        assertEquals(1.75f, playback.speed, 0f)
    }

    @Test
    fun `no two settings classes claim the same key`() {
        val ui = FakePreferences()
        UiPreferences(ui).apply {
            spotlightDurationMs = 400
            notesListLines = 4
            uiSizeMode = UiPreferences.SIZE_LARGE
            toggledLarge = true
            autoplayOnOpen = true
            timeFormat = TimeFormat.CUSTOM
            timeFormatCustom = "yyyy-MM-dd"
            iconSize = UiPreferences.ICONS_LARGE
        }

        val recorder = FakePreferences()
        RecorderPreferences(recorder, null).apply {
            recordingFormat = RecorderPreferences.FORMAT_WAV16
            duringCall = RecorderPreferences.CALL_SILENCE
            startRecordingImmediately = true
            defaultNewAction = RecorderPreferences.ACTION_RECORDING
            selectedMicKey = "mic-3"
        }

        val transcription = FakePreferences()
        TranscriptionPreferences(transcription).apply {
            modelId = "large-v3-q5_0"
            language = "ar"
            beamSize = 2
            setLanguageUse("ru", TranscriptionPreferences.USE_BUTTON)
        }

        val playback = FakePreferences()
        PlaybackPreferences(playback).speed = 1.75f

        val byClass = mapOf(
            "UiPreferences" to ui.all.keys,
            "RecorderPreferences" to recorder.all.keys,
            "TranscriptionPreferences" to transcription.all.keys,
            "PlaybackPreferences" to playback.all.keys,
        )
        for ((nameA, keysA) in byClass) {
            for ((nameB, keysB) in byClass) {
                if (nameA >= nameB) continue
                val shared = keysA intersect keysB
                assertTrue("$nameA and $nameB both write $shared", shared.isEmpty())
            }
        }
    }

    @Test
    fun `the sync settings keys are not used by anything else`() {
        // SettingsViewModel writes these two into the same file directly.
        val used = FakePreferences()
        UiPreferences(used).apply {
            notesListLines = 3
            timeFormat = TimeFormat.CUSTOM
        }
        RecorderPreferences(used, null).recordingFormat = RecorderPreferences.FORMAT_WAV16
        TranscriptionPreferences(used).language = "he"
        PlaybackPreferences(used).speed = 2f
        for (key in listOf("server_url", "server_peer_id")) {
            assertTrue("$key is written by a settings class as well", key !in used.all.keys)
        }
    }

    @Test
    fun `a settings file from an older version still opens`() {
        // Only the keys it knows are read; the rest are left where they are.
        val old = FakePreferences()
        old.edit()
            .putString("some_setting_that_was_removed", "x")
            .putInt("notes_list_lines", 5)
            .apply()
        val ui = UiPreferences(old)
        assertEquals(5, ui.notesListLines)
        assertNull("a format nobody chose means the phone's own", ui.timeFormat)
        assertEquals(TimeFormat.DEFAULT_CUSTOM, ui.timeFormatCustom)
        assertEquals("x", old.getString("some_setting_that_was_removed", null))
    }
}
