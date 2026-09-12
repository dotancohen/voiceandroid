package com.dotancohen.voiceandroid.data

import java.io.File

/**
 * Calculating data that was never calculated.
 *
 * Some facts about a recording are not known when it arrives: a file imported
 * before lengths were recorded has none; a recording copied without its
 * filesystem dates has no reliable creation time; a note written before the
 * display caches existed has none. None of that is lost data — it can be read
 * back off the file, or recomputed — but until it is, the application shows
 * less than it knows, and the decisions that depend on a length (whether a
 * waveform is drawn without asking, whether the phone transcribes a recording)
 * have nothing to go on.
 *
 * This is the same operation as the desktop's `src/core/missing_data.py`, in
 * the same words, with the same gap keys, so a report from either reads the
 * same.
 *
 * What is **not** attempted: anything that would be a guess. A recording's
 * timezone, where it was never recorded, cannot be derived from the file —
 * writing this phone's offset would state something false about where the user
 * was. Those gaps are counted and reported, never invented.
 */
object MissingData {

    /** One kind of missing data, how many there are, and whether it can be calculated. */
    data class Gap(
        val key: String,
        val description: String,
        val count: Int,
        /** False for a gap that nothing on this phone can close. */
        val calculable: Boolean = true,
        val note: String = "",
    )

    /** What is missing, before anything is changed. */
    data class Survey(val gaps: List<Gap>) {
        val totalCalculable: Int get() = gaps.filter { it.calculable }.sumOf { it.count }
        val anythingMissing: Boolean get() = gaps.any { it.count > 0 }

        /** One line per gap, for a person to read. */
        fun summary(): String {
            if (!anythingMissing) return "Nothing is missing."
            return gaps.filter { it.count > 0 }.joinToString("\n") { gap ->
                val suffix = if (gap.calculable) "" else "  (cannot be calculated: ${gap.note})"
                "${gap.count}  ${gap.description}$suffix"
            }
        }
    }

    /** What was calculated, and what could not be. */
    data class Report(
        val calculated: Map<String, Int> = emptyMap(),
        val failed: Map<String, Int> = emptyMap(),
        val details: List<String> = emptyList(),
    ) {
        val totalCalculated: Int get() = calculated.values.sum()

        fun summary(): String {
            if (calculated.isEmpty() && failed.isEmpty()) return "Nothing needed calculating."
            val lines = mutableListOf<String>()
            calculated.entries.sortedBy { it.key }.filter { it.value > 0 }
                .forEach { lines.add("${it.value}  ${it.key}") }
            failed.entries.sortedBy { it.key }.filter { it.value > 0 }
                .forEach { lines.add("${it.value}  ${it.key} — could not be read") }
            return lines.joinToString("\n")
        }
    }

    /** What this operation needs from the phone and from the database. */
    interface Store {
        suspend fun recordings(): List<AudioFile>
        suspend fun notes(): List<Note>

        /** The file behind a recording, or null when it is not on this phone. */
        suspend fun fileFor(recording: AudioFile): File?

        /** The length of a recording in seconds, read off the file; null when it cannot be read. */
        fun lengthSeconds(file: File): Long?

        /**
         * When the recording was made, as Unix seconds, or null when nothing says.
         *
         * [recordedName] is the name the file arrived under, which is where a
         * recorder's date survives: the stored file is named after its id.
         */
        fun madeAt(file: File, recordedName: String): Long?

        suspend fun saveLength(recordingId: String, seconds: Long): Boolean
        suspend fun saveMadeAt(recordingId: String, at: Long): Boolean
        suspend fun rebuildCaches(noteId: String): Boolean
    }

    /**
     * What is missing, without changing anything.
     *
     * Cheap: it reads the database and checks which files are here, and opens
     * no audio.
     */
    suspend fun survey(store: Store): Survey {
        var missingLength = 0
        var missingMadeAt = 0
        var absentFile = 0
        var missingZone = 0

        for (recording in store.recordings()) {
            if (recording.deletedAt != null) continue
            if (store.fileFor(recording) == null) absentFile++
            if ((recording.durationSeconds ?: 0L) <= 0L) missingLength++
            if (recording.fileCreatedAt == null) missingMadeAt++
            if (recording.importedAt.offset == null) missingZone++
        }

        // A note missing the list cache is a note whose caches were never
        // built, and rebuilding builds both of them.
        val missingCache = store.notes().count { it.deletedAt == null && it.listDisplayCache.isNullOrEmpty() }

        return Survey(
            listOf(
                Gap("duration", "Recordings with no length recorded", missingLength),
                Gap("file_created_at", "Recordings with no creation date", missingMadeAt),
                Gap("note_cache", "Notes with no display cache", missingCache),
                Gap(
                    "absent_file", "Recordings whose file is not on this phone",
                    absentFile, calculable = false,
                    note = "download them first, or run this on the device that has them",
                ),
                Gap(
                    "timezone", "Recordings with no timezone recorded",
                    missingZone, calculable = false,
                    note = "it cannot be derived from the file, and guessing it would state " +
                        "something false about where the recording was made",
                ),
            )
        )
    }

    /**
     * Calculate what can be calculated, and report what happened.
     *
     * @param durations Read the length of recordings that have none.
     * @param fileDates Read the creation date of recordings that have none.
     * @param caches Rebuild the display caches of notes that have none.
     * @param limit At most this many recordings, for a run that should not take
     *   all night. Caches are not limited: rebuilding one is milliseconds.
     * @param progress Called with a line of text as each item is done, so the
     *   screen can show what is happening.
     */
    suspend fun calculate(
        store: Store,
        durations: Boolean = true,
        fileDates: Boolean = true,
        caches: Boolean = true,
        limit: Int? = null,
        progress: ((String) -> Unit)? = null,
    ): Report {
        val calculated = mutableMapOf<String, Int>()
        val failed = mutableMapOf<String, Int>()
        val details = mutableListOf<String>()

        fun say(line: String) {
            progress?.invoke(line)
        }

        if (durations || fileDates) {
            var done = 0
            for (recording in store.recordings()) {
                if (recording.deletedAt != null) continue
                if (limit != null && done >= limit) break
                val needsLength = durations && (recording.durationSeconds ?: 0L) <= 0L
                val needsDate = fileDates && recording.fileCreatedAt == null
                if (!needsLength && !needsDate) continue

                val file = store.fileFor(recording)
                if (file == null) {
                    failed["absent_file"] = (failed["absent_file"] ?: 0) + 1
                    continue
                }

                var didSomething = false

                if (needsLength) {
                    val seconds = store.lengthSeconds(file)
                    if (seconds != null && seconds > 0) {
                        store.saveLength(recording.id, seconds)
                        calculated["duration"] = (calculated["duration"] ?: 0) + 1
                        didSomething = true
                        say("${recording.filename}: $seconds seconds")
                    } else {
                        failed["duration"] = (failed["duration"] ?: 0) + 1
                    }
                }

                if (needsDate) {
                    val at = store.madeAt(file, recording.filename)
                    if (at != null && at > 0) {
                        store.saveMadeAt(recording.id, at)
                        calculated["file_created_at"] = (calculated["file_created_at"] ?: 0) + 1
                        didSomething = true
                        say("${recording.filename}: creation date calculated")
                    } else {
                        failed["file_created_at"] = (failed["file_created_at"] ?: 0) + 1
                    }
                }

                if (didSomething) done++
            }
        }

        if (caches) {
            var rebuilt = 0
            for (note in store.notes()) {
                if (note.deletedAt != null || !note.listDisplayCache.isNullOrEmpty()) continue
                if (store.rebuildCaches(note.id)) {
                    rebuilt++
                } else {
                    failed["note_cache"] = (failed["note_cache"] ?: 0) + 1
                }
            }
            if (rebuilt > 0) {
                calculated["note_cache"] = rebuilt
                say("Rebuilt the display cache of $rebuilt note(s)")
            }
        }

        return Report(calculated, failed, details)
    }

    /**
     * The date a recorder wrote into a file name, as Unix seconds, or null.
     *
     * The shapes are the ones the desktop's `parse_date_from_filename` reads,
     * so both applications believe the same names. Every shape carries a time
     * as well as a date, so a name holding only a date is not treated as one.
     */
    fun dateInName(filename: String): Long? {
        for (pattern in NAME_DATE_PATTERNS) {
            val match = pattern.find(filename) ?: continue
            val (y, mo, d, h, mi, s) = match.destructured
            val calendar = java.util.Calendar.getInstance()
            calendar.clear()
            try {
                // The time in a name is the local time where it was recorded,
                // read in this phone's zone, as the filesystem dates are.
                calendar.set(y.toInt(), mo.toInt() - 1, d.toInt(), h.toInt(), mi.toInt(), s.toInt())
                if (mo.toInt() !in 1..12 || d.toInt() !in 1..31 ||
                    h.toInt() > 23 || mi.toInt() > 59 || s.toInt() > 59
                ) continue
            } catch (e: NumberFormatException) {
                continue
            }
            return calendar.timeInMillis / 1000
        }
        return null
    }

    /**
     * When the recording was made, by the same rule as the desktop.
     *
     * The filesystem is asked first, because it is right whenever the file was
     * copied with its dates intact. A date in the name is used only when it is
     * more than [NAME_DATE_MARGIN_SECONDS] older than the filesystem date,
     * which is what a copy made without its dates looks like.
     */
    fun madeAtFrom(fileModifiedSeconds: Long?, recordedName: String): Long? {
        val nameDate = dateInName(recordedName)
        if (nameDate == null) return fileModifiedSeconds
        if (fileModifiedSeconds == null) return nameDate
        return if (fileModifiedSeconds - nameDate > NAME_DATE_MARGIN_SECONDS) nameDate
        else fileModifiedSeconds
    }

    /** How much older than the filesystem date a name's date must be to be believed. */
    const val NAME_DATE_MARGIN_SECONDS = 48L * 60 * 60

    private val NAME_DATE_PATTERNS = listOf(
        // 2026-09-08 14-53-14, 2026-09-08T14:53:14, 2026-09-08_14.53.14
        Regex("""(\d{4})-(\d{2})-(\d{2})[ T_](\d{2})[-:.](\d{2})[-:.](\d{2})"""),
        // 20260908_145314 and REC_20260908_145314
        Regex("""(\d{4})(\d{2})(\d{2})[_-](\d{2})(\d{2})(\d{2})"""),
    )
}
