package com.dotancohen.voiceandroid.util

import org.junit.Assert.assertEquals
import org.junit.Test
import uniffi.voicecore.FileLocationData
import uniffi.voicecore.IssuesData
import uniffi.voicecore.OrphanedAttachmentData
import uniffi.voicecore.OrphanedTranscriptionData
import uniffi.voicecore.RecordingNotInCloudData
import uniffi.voicecore.TagWithWhitespaceData

/**
 * The words of the Issues screen (ISSUE-1) and of where a recording's copies
 * are (FILE-22): the same words as the desktop's tests/unit/test_issues_text.py.
 */
class IssuesTextTest {
    private val here = "01a09526bbbb70808f15a84d31aaa8d2"
    private val phone = "01a0952602bc70808f15a84d31aaa8d2"

    @Test
    fun `sizes read as people read them`() {
        assertEquals("size unknown", IssuesText.sizeWords(null))
        assertEquals("900 bytes", IssuesText.sizeWords(900))
        assertEquals("2 KB", IssuesText.sizeWords(2048))
        assertEquals("5.0 MB", IssuesText.sizeWords(5L * 1024 * 1024))
        assertEquals("3.0 GB", IssuesText.sizeWords(3L * 1024 * 1024 * 1024))
    }

    @Test
    fun `places are named the bucket, this device, or by name`() {
        val names = mapOf(phone to "הטלפון של דותן")
        assertEquals("the bucket", IssuesText.placeLabel("cloud", names, here))
        assertEquals("this device", IssuesText.placeLabel(here, names, here))
        assertEquals("הטלפון של דותן", IssuesText.placeLabel(phone, names, here))
        assertEquals("01a09526dddd", IssuesText.placeLabel("01a09526dddd70808f15a84d31aaa8d2", names, here))
    }

    @Test
    fun `every kind of issue has its section and empty kinds are left out`() {
        val issues = IssuesData(
            recordingsNotInCloud = listOf(
                RecordingNotInCloudData("a".repeat(32), "הרצאה.wav", 300L * 1024 * 1024, "too_large", listOf(here)),
                RecordingNotInCloudData("b".repeat(32), "פתק.m4a", 1000, "waiting_for_upload", listOf(phone, here)),
                RecordingNotInCloudData("c".repeat(32), "אבד.3gp", null, "no_copy_known", emptyList()),
                RecordingNotInCloudData("d".repeat(32), "בלי דלי.ogg", 10, "no_bucket", listOf(here)),
            ),
            maxUploadBytes = (100L * 1024 * 1024).toULong(),
            orphanedTranscriptions = listOf(OrphanedTranscriptionData("e".repeat(32), "f".repeat(32), "שלום")),
            orphanedAttachments = listOf(OrphanedAttachmentData("1".repeat(32), "2".repeat(32), "3".repeat(32), "audio_file", true, true)),
            orphanedRecordings = emptyList(),
            tagsWithWhitespace = listOf(TagWithWhitespaceData("4".repeat(32), "פגישת צוות", "עבודה/פגישת צוות")),
            count = 7u,
        )
        val sections = IssuesText.sections(issues, mapOf(phone to "הטלפון"), here).toMap()
        assertEquals(
            listOf(
                "Recordings not in cloud storage (4)",
                "Transcriptions whose recording is not there (1)",
                "Attachments whose note or recording is not there (1)",
                "Tags whose names contain spaces (1)",
            ),
            sections.keys.toList()
        )
        val lines = sections.getValue("Recordings not in cloud storage (4)")
        assertEquals("הרצאה.wav (300.0 MB): larger than the account's upload limit of 100.0 MB", lines[0])
        assertEquals("פתק.m4a (1000 bytes): waiting for הטלפון, this device to upload it", lines[1])
        assertEquals("אבד.3gp (size unknown): no device and no bucket is known to hold it", lines[2])
        assertEquals("בלי דלי.ogg (10 bytes): no bucket is set up for the account", lines[3])
        assertEquals(
            listOf("Attachment ${"1".repeat(12)}: its note ${"2".repeat(12)} and its recording ${"3".repeat(12)} is not there"),
            sections.getValue("Attachments whose note or recording is not there (1)")
        )
        assertEquals(listOf("עבודה/פגישת צוות"), sections.getValue("Tags whose names contain spaces (1)"))
    }

    @Test
    fun `where the copies are is one line per place`() {
        val lines = IssuesText.locationLines(
            listOf(FileLocationData("cloud", true, 5_000, phone), FileLocationData(here, false, 6_000, here)),
            emptyMap(),
            here,
        ) { millis -> "t$millis" }
        assertEquals(listOf("the bucket: holds it (since t5000)", "this device: does not hold it (since t6000)"), lines)
        assertEquals(listOf("No place is known to hold it"), IssuesText.locationLines(emptyList(), emptyMap(), here) { "" })
    }
}
