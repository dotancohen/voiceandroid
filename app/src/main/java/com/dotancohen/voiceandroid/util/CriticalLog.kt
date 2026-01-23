package com.dotancohen.voiceandroid.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Critical error log for persistent logging of errors that users should be aware of.
 *
 * This is separate from the debug log (AppLogger) and is intended for errors that
 * affect user data or functionality, such as failed audio imports.
 *
 * Usage:
 *   CriticalLog.init(context)
 *   CriticalLog.log("IMPORT_FAILED", "Failed to import recording.mp3", "File not found")
 *   CriticalLog.logImportFailure("recording.mp3", "File not found")
 */
object CriticalLog {
    private const val LOG_FILE_NAME = "critical.log"
    private const val MAX_SIZE_BYTES = 1_000_000 // 1MB
    private const val MAX_LINES_TO_KEEP = 2000

    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)

    /**
     * Initialize the critical log with the application context.
     * Must be called once at application startup.
     */
    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
    }

    /**
     * Log a critical error.
     *
     * @param category Short category identifier (e.g., "IMPORT_FAILED", "SYNC_ERROR")
     * @param message Main error message
     * @param details Optional additional details about the error
     */
    fun log(category: String, message: String, details: String? = null) {
        val file = logFile ?: return

        try {
            val timestamp = dateFormat.format(Date())
            val entry = buildString {
                append("[$timestamp] [$category] $message")
                if (!details.isNullOrBlank()) {
                    append("\n  Details: $details")
                }
                append("\n")
            }

            // Rotate log if too large
            if (file.exists() && file.length() > MAX_SIZE_BYTES) {
                rotateLog(file)
            }

            file.appendText(entry)
        } catch (e: Exception) {
            // Don't crash if logging fails
            AppLogger.e("CriticalLog", "Failed to write to critical log", e)
        }
    }

    /**
     * Log a failed audio import.
     *
     * @param filename The name of the file that failed to import
     * @param error Description of what went wrong
     */
    fun logImportFailure(filename: String, error: String) {
        log("IMPORT_FAILED", "Failed to import: $filename", error)
    }

    /**
     * Log a sync error.
     *
     * @param operation The sync operation that failed
     * @param error Description of what went wrong
     */
    fun logSyncError(operation: String, error: String) {
        log("SYNC_ERROR", "Sync operation failed: $operation", error)
    }

    private fun rotateLog(file: File) {
        try {
            val lines = file.readLines()
            val linesToKeep = lines.takeLast(MAX_LINES_TO_KEEP / 2)
            file.writeText(linesToKeep.joinToString("\n") + "\n")
        } catch (e: Exception) {
            // If rotation fails, just truncate the file
            file.writeText("")
        }
    }

    /**
     * Read the critical log contents.
     *
     * @param maxLines Maximum number of lines to return (from the end)
     * @return The log contents as a string
     */
    fun getLogContents(maxLines: Int = 500): String {
        val file = logFile ?: return "Critical log not initialized"

        return try {
            if (!file.exists() || file.length() == 0L) {
                ""
            } else {
                val lines = file.readLines()
                lines.takeLast(maxLines).joinToString("\n")
            }
        } catch (e: Exception) {
            "Error reading critical log: ${e.message}"
        }
    }

    /**
     * Check if there are any entries in the critical log.
     */
    fun hasEntries(): Boolean {
        val file = logFile ?: return false
        return file.exists() && file.length() > 0
    }

    /**
     * Get the number of entries in the critical log.
     */
    fun getEntryCount(): Int {
        val file = logFile ?: return 0
        return try {
            if (!file.exists()) 0
            else file.readLines().count { it.startsWith("[") }
        } catch (e: Exception) {
            0
        }
    }

    /**
     * Get the log file path.
     */
    fun getLogFilePath(): String {
        return logFile?.absolutePath ?: "Not initialized"
    }

    /**
     * Clear the critical log.
     */
    fun clearLog() {
        try {
            logFile?.writeText("")
        } catch (e: Exception) {
            AppLogger.e("CriticalLog", "Failed to clear critical log", e)
        }
    }
}
