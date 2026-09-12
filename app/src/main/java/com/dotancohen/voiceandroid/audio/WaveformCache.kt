package com.dotancohen.voiceandroid.audio

import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * The waveforms already worked out, kept on this device.
 *
 * Reading a waveform means decoding the whole recording, which takes seconds
 * on a long one and needs a hardware decoder — of which a phone has only a
 * few, shared with whatever else is decoding audio (a transcription
 * converting a recording, above all). That is why a waveform sometimes never
 * appeared: the work was being done again on every visit, and competing with
 * the transcription queue for the same scarce decoder. Worked out once and
 * kept, it is there at once every time after.
 *
 * **Not synced, and never will be.** A waveform is not the user's data: it is
 * a picture this device drew from their data, at this device's resolution and
 * with this device's settings. Another phone, with a wider screen or a
 * different bar count, would draw a different picture from the same
 * recording. Each device keeps its own.
 *
 * **Kept with the settings that made it.** Every entry records the settings
 * fingerprint it was drawn with and the size and date of the recording it was
 * drawn from. An entry whose fingerprint no longer matches is ignored and
 * drawn again, so changing how waveforms look cannot leave old pictures
 * behind; so is an entry whose recording has changed underneath it.
 */
class WaveformCache(private val directory: File) {

    /**
     * The waveform of [source] as this device would draw it now, or null when
     * there is none to be had: never drawn, drawn with other settings, or
     * drawn from a recording that has since changed.
     */
    fun read(source: File, fingerprint: String): List<Float>? {
        val file = entryFor(source)
        if (!file.exists()) return null
        return try {
            val json = JSONObject(file.readText())
            if (json.optInt("version") != FORMAT_VERSION) return null
            if (json.optString("fingerprint") != fingerprint) return null
            if (json.optLong("source_bytes") != source.length()) return null
            if (json.optLong("source_modified") != source.lastModified()) return null
            val bars = json.optJSONArray("bars") ?: return null
            (0 until bars.length()).map { bars.getDouble(it).toFloat() }
                .takeIf { it.isNotEmpty() }
        } catch (e: Throwable) {
            // A half-written or hand-edited entry is not worth a crash; it is
            // worth forgetting.
            runCatching { file.delete() }
            null
        }
    }

    /** Keep this waveform for next time. Failure to write costs nothing. */
    fun write(source: File, fingerprint: String, bars: List<Float>) {
        if (bars.isEmpty()) return
        try {
            directory.mkdirs()
            val json = JSONObject().apply {
                put("version", FORMAT_VERSION)
                put("fingerprint", fingerprint)
                put("source_bytes", source.length())
                put("source_modified", source.lastModified())
                // Three places is finer than any screen can draw, and keeps an
                // entry under a kilobyte.
                put("bars", JSONArray(bars.map { Math.round(it * 1000) / 1000.0 }))
            }
            // Written beside and moved into place, so a reader never meets a
            // half-written entry.
            val entry = entryFor(source)
            val pending = File(entry.parentFile, entry.name + ".part")
            pending.writeText(json.toString())
            if (!pending.renameTo(entry)) {
                pending.delete()
            }
        } catch (_: Throwable) {
            // The picture will simply be drawn again next time.
        }
    }

    /** Forget every waveform, for when what they should look like changes. */
    fun clear() {
        runCatching { directory.listFiles()?.forEach { it.delete() } }
    }

    /** How much room the kept waveforms take, in bytes. */
    fun sizeBytes(): Long =
        directory.listFiles()?.sumOf { it.length() } ?: 0L

    /** How many waveforms are kept. */
    fun count(): Int = directory.listFiles()?.count { it.name.endsWith(SUFFIX) } ?: 0

    /**
     * The file an entry lives in.
     *
     * Named after the recording's own file, which is `<audio file id>.<ext>`
     * and therefore unique, so two recordings can never share an entry.
     */
    private fun entryFor(source: File): File = File(directory, source.name + SUFFIX)

    companion object {
        private const val FORMAT_VERSION = 1
        private const val SUFFIX = ".waveform"

        /**
         * What the settings were when a waveform was drawn.
         *
         * Everything that changes what a waveform looks like belongs in here.
         * Today that is the number of bars; when the appearance becomes the
         * user's to choose, their choices join it, and every entry drawn
         * before the change is quietly ignored.
         */
        fun fingerprint(barCount: Int, appearance: String = ""): String =
            "bars=$barCount;$appearance"
    }
}
