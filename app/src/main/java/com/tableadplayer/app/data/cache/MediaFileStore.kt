package com.tableadplayer.app.data.cache

import java.io.File
import java.io.InputStream

/**
 * App-private media tree under `files/media/`. Downloads go through [AtomicFileStore]
 * (`*.part` → checksum → rename). Playback must only open the final path.
 */
class MediaFileStore(private val filesDir: File) {

    val root: File
        get() = File(filesDir, DIR).apply { mkdirs() }

    fun relativePath(mediaId: String, version: Long): String =
        "$DIR/${fileName(mediaId, version)}"

    fun fileName(mediaId: String, version: Long): String =
        "${sanitize(mediaId)}_v$version"

    fun target(mediaId: String, version: Long): File =
        File(filesDir, relativePath(mediaId, version))

    fun resolve(relativeOrAbsolute: String): File {
        val asIs = File(relativeOrAbsolute)
        return if (asIs.isAbsolute) asIs else File(filesDir, relativeOrAbsolute)
    }

    /**
     * Write [input] atomically into `files/media/{id}_v{version}`.
     * Returns the final file, or null if the checksum failed / IO error.
     */
    fun ingest(
        mediaId: String,
        version: Long,
        input: InputStream,
        expectedChecksum: String?,
    ): File? {
        val target = target(mediaId, version)
        val ok = AtomicFileStore.writeAtomically(target, input, expectedChecksum)
        return if (ok && target.exists()) target else null
    }

    fun delete(relativeOrAbsolute: String?) {
        if (relativeOrAbsolute.isNullOrBlank()) return
        val file = resolve(relativeOrAbsolute)
        if (file.exists()) file.delete()
        val part = File(file.parentFile, "${file.name}.part")
        if (part.exists()) part.delete()
    }

    fun listPartFiles(): List<File> {
        val dir = File(filesDir, DIR)
        if (!dir.isDirectory) return emptyList()
        return dir.listFiles { _, name -> name.endsWith(PART_SUFFIX) }?.toList().orEmpty()
    }

    fun relativeOf(file: File): String {
        val prefix = filesDir.canonicalPath.trimEnd('/') + "/"
        val path = file.canonicalPath
        return if (path.startsWith(prefix)) path.removePrefix(prefix) else file.path
    }

    companion object {
        const val DIR = "media"
        const val PART_SUFFIX = ".part"

        fun sanitize(id: String): String = id.replace(Regex("[^A-Za-z0-9._-]"), "_")
    }
}
