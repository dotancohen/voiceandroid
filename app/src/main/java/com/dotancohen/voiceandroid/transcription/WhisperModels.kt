package com.dotancohen.voiceandroid.transcription

import android.content.Context
import com.dotancohen.voiceandroid.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/** One downloadable ggml Whisper model. */
data class WhisperModel(
    val id: String,
    val title: String,
    val description: String,
    val url: String,
    val sizeBytes: Long,
    /** Languages the model is good at; empty = all of Whisper's languages. */
    val languages: List<String> = emptyList(),
) {
    val fileName: String get() = "$id.bin"
    val sizeText: String get() = String.format(java.util.Locale.US, "%.2f GB", sizeBytes / 1e9)
}

/**
 * The models the app can download, where they live on the phone, and the
 * download itself. Models are plain ggml files from Hugging Face stored in
 * the app's private files directory (`files/whisper-models`), so uninstalling
 * the app removes them.
 */
object WhisperModels {
    private const val HF = "https://huggingface.co"

    val CATALOGUE: List<WhisperModel> = listOf(
        WhisperModel(
            id = "large-v3-q5_0",
            title = "Whisper large-v3 (5-bit)",
            description = "OpenAI's largest model, quantised to 5 bits. Accuracy is very close to the full model at a third of the size. Hebrew, English, Arabic, Russian and 95 other languages. Recommended.",
            url = "$HF/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-q5_0.bin",
            sizeBytes = 1_081_140_203,
        ),
        WhisperModel(
            id = "ivrit-large-v3-turbo",
            title = "ivrit.ai large-v3-turbo (Hebrew)",
            description = "Whisper large-v3-turbo fine-tuned by ivrit.ai on about 390 hours of transcribed Hebrew. The most accurate choice for Hebrew speech. Hebrew only: it no longer detects other languages.",
            url = "$HF/ivrit-ai/whisper-large-v3-turbo-ggml/resolve/main/ggml-model.bin",
            sizeBytes = 1_624_555_275,
            languages = listOf("he"),
        ),
        WhisperModel(
            id = "ivrit-large-v3",
            title = "ivrit.ai large-v3 (Hebrew, full size)",
            description = "The full-size large-v3 fine-tuned by ivrit.ai for Hebrew. Needs about 4 GB of free memory while it runs. Hebrew only.",
            url = "$HF/ivrit-ai/whisper-large-v3-ggml/resolve/main/ggml-model.bin",
            sizeBytes = 3_095_033_483,
            languages = listOf("he"),
        ),
        WhisperModel(
            id = "large-v3",
            title = "Whisper large-v3 (full size)",
            description = "The full 16-bit large-v3. Needs about 4 GB of free memory while it runs; on this phone the 5-bit version is just as accurate in practice.",
            url = "$HF/ggerganov/whisper.cpp/resolve/main/ggml-large-v3.bin",
            sizeBytes = 3_095_033_483,
        ),
        WhisperModel(
            id = "large-v3-turbo-q5_0",
            title = "Whisper large-v3-turbo (5-bit)",
            description = "Large encoder with a small decoder: several times faster than large-v3, slightly less accurate on Hebrew.",
            url = "$HF/ggerganov/whisper.cpp/resolve/main/ggml-large-v3-turbo-q5_0.bin",
            sizeBytes = 574_041_195,
        ),
        WhisperModel(
            id = "medium-q5_0",
            title = "Whisper medium (5-bit)",
            description = "Smaller and faster; noticeably weaker than large-v3 on Hebrew.",
            url = "$HF/ggerganov/whisper.cpp/resolve/main/ggml-medium-q5_0.bin",
            sizeBytes = 539_212_467,
        ),
    )

    const val DEFAULT_MODEL_ID = "large-v3-q5_0"

    fun byId(id: String): WhisperModel? = CATALOGUE.firstOrNull { it.id == id }

    fun modelsDir(context: Context): File = File(context.filesDir, "whisper-models").also { it.mkdirs() }

    fun file(context: Context, model: WhisperModel): File = File(modelsDir(context), model.fileName)

    /** A model counts as installed when its file exists with the full size. */
    fun isInstalled(context: Context, model: WhisperModel): Boolean =
        isComplete(file(context, model), model)

    /**
     * Whether this file is the whole model.
     *
     * The size is checked, not merely the name: a download stopped by a lost
     * connection leaves a file that is there but short, and handing a
     * half-model to whisper.cpp fails in a way that reads as a broken
     * recording rather than as a broken download.
     */
    internal fun isComplete(file: File, model: WhisperModel): Boolean =
        file.exists() && file.length() == model.sizeBytes

    fun installed(context: Context): List<WhisperModel> = CATALOGUE.filter { isInstalled(context, it) }

    fun delete(context: Context, model: WhisperModel): Boolean {
        File(modelsDir(context), model.fileName + ".part").delete()
        return file(context, model).delete()
    }

    /**
     * Download a model, resuming a partial file if there is one. [onProgress]
     * receives bytes done and total. Cancelling the coroutine stops the
     * download and keeps the partial file for next time.
     */
    suspend fun download(context: Context, model: WhisperModel, onProgress: (Long, Long) -> Unit) = withContext(Dispatchers.IO) {
        val target = file(context, model)
        val part = File(modelsDir(context), model.fileName + ".part")
        var done = if (part.exists()) part.length() else 0L
        if (done >= model.sizeBytes) { part.delete(); done = 0L }
        var url = URL(model.url)
        var redirects = 0
        while (true) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            if (done > 0) conn.setRequestProperty("Range", "bytes=$done-")
            val code = conn.responseCode
            if (code in 301..308) {
                val next = conn.getHeaderField("Location") ?: throw IllegalStateException("Redirect without Location")
                conn.disconnect()
                url = URL(url, next)
                if (++redirects > 10) throw IllegalStateException("Too many redirects")
                continue
            }
            if (code == 200 && done > 0) { done = 0L; part.delete() } // server ignored the range
            if (code != 200 && code != 206) throw IllegalStateException("Download failed: HTTP $code")
            AppLogger.i(TAG, "Downloading ${model.id} from $url starting at $done")
            conn.inputStream.use { input ->
                java.io.FileOutputStream(part, done > 0).use { out ->
                    val buf = ByteArray(256 * 1024)
                    var lastReport = 0L
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (done - lastReport > 2_000_000) { lastReport = done; onProgress(done, model.sizeBytes) }
                    }
                }
            }
            conn.disconnect()
            break
        }
        if (part.length() != model.sizeBytes) {
            throw IllegalStateException("Download ended early: ${part.length()} of ${model.sizeBytes} bytes")
        }
        if (!part.renameTo(target)) throw IllegalStateException("Could not move the downloaded file into place")
        onProgress(model.sizeBytes, model.sizeBytes)
        AppLogger.i(TAG, "Model ${model.id} installed at ${target.absolutePath}")
    }

    private const val TAG = "WhisperModels"
}
