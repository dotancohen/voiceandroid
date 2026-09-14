package com.dotancohen.voiceandroid.util

import java.io.InputStream
import java.security.MessageDigest

/** The SHA-256 of a file's bytes, lowercase hex, as the core stores it (FILE-18). */
object ContentHash {
    fun sha256(input: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
