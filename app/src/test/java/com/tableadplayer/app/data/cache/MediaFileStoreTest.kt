package com.tableadplayer.app.data.cache

import java.io.ByteArrayInputStream
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MediaFileStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun ingestWritesUnderMediaDirWithoutPartLeftover() {
        val store = MediaFileStore(tmp.root)
        val payload = "cached-slide".toByteArray()
        val file = store.ingest("welcome", 3L, ByteArrayInputStream(payload), expectedChecksum = null)
        checkNotNull(file)
        assertTrue(file.path.contains("${File.separator}media${File.separator}"))
        assertEquals("welcome_v3", file.name)
        assertEquals(payload.toList(), file.readBytes().toList())
        assertTrue(store.listPartFiles().isEmpty())
        assertEquals("media/welcome_v3", store.relativeOf(file))
    }

    @Test
    fun sanitizeStripsPathSeparators() {
        assertEquals("a_b_c", MediaFileStore.sanitize("a/b\\c"))
    }

    @Test
    fun deleteRemovesFinalAndPart() {
        val store = MediaFileStore(tmp.root)
        val file = store.ingest("clip", 1L, ByteArrayInputStream(byteArrayOf(1, 2, 3)), null)!!
        val part = File(file.parentFile, "${file.name}.part")
        part.writeText("leftover")
        store.delete(store.relativeOf(file))
        assertFalse(file.exists())
        assertFalse(part.exists())
    }
}
