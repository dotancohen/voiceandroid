package com.dotancohen.voiceandroid.data

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uniffi.voicecore.Stamp

/**
 * The transcription flags, as agreed with the desktop application.
 *
 * A transcription's flags are five words in its `state` field, and that field
 * syncs between this application and VOICE on the desktop. Neither can read
 * the other's code, so the agreement is written down in
 * `app/src/test/resources/transcription_flags_contract.json` and both test
 * suites check their own implementation against it. The desktop copy is
 * `tests/fixtures/transcription_flags_contract.json` and must be identical;
 * `tests/sync/test_transcription_flags_contract.py` reads the same cases, and
 * `tests/sync/test_transcription_flags_sync.py` sends each of these fields
 * through a real sync between two installations.
 *
 * If a case here fails, the two applications no longer agree on what a
 * transcription says about itself, and a flag set on this phone would be
 * misread on the desktop after the next sync.
 */
class TranscriptionFlagsContractTest {

    private val contract: JSONObject by lazy {
        val stream = javaClass.classLoader!!.getResourceAsStream("transcription_flags_contract.json")
            ?: error("the contract file is missing from the test resources")
        JSONObject(stream.bufferedReader().use { it.readText() })
    }

    private fun transcription(state: String) = Transcription(
        id = "t1",
        audioFileId = "a1",
        content = "שלום עולם",
        service = "local_whisper",
        state = state,
        deviceId = "phone",
        createdAt = Stamp(at = 1_757_419_500, offset = 10800, zone = "Asia/Jerusalem"),
    )

    private fun JSONObject.strings(key: String): List<String> {
        val array = getJSONArray(key)
        return (0 until array.length()).map { array.getString(it) }
    }

    @Test
    fun `the contract is the one this code was written against`() {
        assertEquals(1, contract.getInt("version"))
    }

    @Test
    fun `the five flags are the five in the contract`() {
        assertEquals(contract.strings("flags"), TranscriptionFlags.ALL.map { it.name })
    }

    @Test
    fun `a new transcription carries the agreed field`() {
        assertEquals(contract.getString("default_field"), TranscriptionFlags.DEFAULT_FLAGS)
    }

    @Test
    fun `every description is the agreed wording`() {
        val descriptions = contract.getJSONObject("descriptions")
        for (flag in TranscriptionFlags.ALL) {
            assertEquals(descriptions.getString(flag.name), flag.description)
        }
        assertEquals(descriptions.length(), TranscriptionFlags.ALL.size)
    }

    @Test
    fun `a field written by the desktop is read the same way here`() {
        val cases = contract.getJSONArray("reads")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val field = case.getString("field")
            val row = transcription(field)
            for (flag in case.strings("set")) {
                assertTrue("'$field': $flag should be set", row.hasFlag(flag))
            }
            for (flag in case.strings("not_set")) {
                assertFalse("'$field': $flag should not be set", row.hasFlag(flag))
            }
        }
    }

    @Test
    fun `a flag turned here is written as the desktop would write it`() {
        val cases = contract.getJSONArray("toggles")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val field = case.getString("field")
            val flag = case.getString("flag")
            val result = transcription(field).toggleFlag(flag)
            assertEquals("toggling $flag in '$field'", case.getString("expect_field"), result)

            val set = TranscriptionFlags.ALL
                .map { it.name }
                .filter { transcription(result).hasFlag(it) }
            assertEquals(case.strings("expect_set").sorted(), set.sorted())
        }
    }

    @Test
    fun `nothing written by either side grows a stray space`() {
        val cases = contract.getJSONArray("toggles")
        for (i in 0 until cases.length()) {
            val case = cases.getJSONObject(i)
            val result = transcription(case.getString("field")).toggleFlag(case.getString("flag"))
            assertFalse(result, result.contains("  "))
            assertEquals(result.trim(), result)
        }
    }
}
