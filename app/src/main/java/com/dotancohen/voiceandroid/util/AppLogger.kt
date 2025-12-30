package com.dotancohen.voiceandroid.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Application-wide logger that writes to both Android logcat and a log file.
 *
 * Usage:
 *   AppLogger.init(context)
 *   AppLogger.i("Tag", "Message")
 *   AppLogger.e("Tag", "Error message", exception)
 */
object AppLogger {
    private const val LOG_FILE_NAME = "voice.log"
    private const val MAX_LOG_SIZE = 1024 * 1024 // 1MB
    private const val MAX_LINES_TO_KEEP = 5000

    private var logFile: File? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    /**
     * Initialize the logger with the application context.
     * Must be called once at application startup.
     */
    fun init(context: Context) {
        logFile = File(context.filesDir, LOG_FILE_NAME)
        i("AppLogger", "Logger initialized: ${logFile?.absolutePath}")
    }

    /**
     * Log an info message.
     */
    fun i(tag: String, message: String) {
        Log.i(tag, message)
        writeToFile("INFO", tag, message)
    }

    /**
     * Log a debug message.
     */
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        writeToFile("DEBUG", tag, message)
    }

    /**
     * Log a warning message.
     */
    fun w(tag: String, message: String) {
        Log.w(tag, message)
        writeToFile("WARN", tag, message)
    }

    /**
     * Log an error message.
     */
    fun e(tag: String, message: String, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, message, throwable)
            writeToFile("ERROR", tag, "$message\n${throwable.stackTraceToString()}")
        } else {
            Log.e(tag, message)
            writeToFile("ERROR", tag, message)
        }
    }

    private fun writeToFile(level: String, tag: String, message: String) {
        val file = logFile ?: return

        try {
            val timestamp = dateFormat.format(Date())
            val logLine = "$timestamp - $tag - $level - $message\n"

            // Rotate log if too large
            if (file.exists() && file.length() > MAX_LOG_SIZE) {
                rotateLog(file)
            }

            file.appendText(logLine)
        } catch (e: Exception) {
            // Don't crash if logging fails
            Log.e("AppLogger", "Failed to write to log file", e)
        }
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
     * Read the log file contents.
     * Returns the last N lines of the log.
     */
    fun readLog(maxLines: Int = 1000): String {
        val file = logFile ?: return "Logger not initialized"

        return try {
            if (!file.exists()) {
                "Log file is empty"
            } else {
                val lines = file.readLines()
                lines.takeLast(maxLines).joinToString("\n")
            }
        } catch (e: Exception) {
            "Error reading log file: ${e.message}"
        }
    }

    /**
     * Get the log file path.
     */
    fun getLogFilePath(): String {
        return logFile?.absolutePath ?: "Not initialized"
    }

    /**
     * Clear the log file.
     */
    fun clearLog() {
        logFile?.writeText("")
        i("AppLogger", "Log cleared")
    }
}
