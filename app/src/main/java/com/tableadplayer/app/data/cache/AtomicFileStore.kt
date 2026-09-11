package com.tableadplayer.app.data.cache

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

/**
 * Atomic download helper (Phase 6). Write to `*.part`, fsync, then rename into place.
 * Playback must only reference the final path after a successful rename.
 */
object AtomicFileStore {
    fun writeAtomically(target: File, input: InputStream, expectedSha256: String? = null): Boolean {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, "${target.name}.part")
        return try {
            part.outputStream().use { out ->
                input.copyTo(out)
                out.flush()
                out.fd.sync()
            }
            if (expectedSha256 != null) {
                val actual = sha256Hex(part)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    part.delete()
                    return false
                }
            }
            if (target.exists()) target.delete()
            part.renameTo(target)
        } catch (_: Exception) {
            part.delete()
            false
        }
    }

    fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
