package com.tableadplayer.app.data.cache

import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AtomicFileStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun writesAtomicallyAndVerifiesChecksum() {
        val target = tmp.newFile("clip.bin")
        target.delete()
        val payload = "table-ad-bytes".toByteArray()
        val digest = sha256(payload)
        val ok = AtomicFileStore.writeAtomically(
            target,
            ByteArrayInputStream(payload),
            digest,
        )
        assertTrue(ok)
        assertTrue(target.isFile)
        assertEquals(payload.toList(), target.readBytes().toList())
        assertFalse(File(target.parentFile, "${target.name}.part").exists())
        assertEquals(digest, AtomicFileStore.sha256Hex(target))
    }

    @Test
    fun badChecksumDeletesPartAndLeavesNoTarget() {
        val target = File(tmp.root, "missing.bin")
        val payload = "hello".toByteArray()
        val ok = AtomicFileStore.writeAtomically(
            target,
            ByteArrayInputStream(payload),
            expectedSha256 = "00".repeat(32),
        )
        assertFalse(ok)
        assertFalse(target.exists())
        assertFalse(File(tmp.root, "missing.bin.part").exists())
    }

    @Test
    fun renameReplacesExistingTarget() {
        val target = tmp.newFile("replace.bin")
        target.writeText("old")
        val payload = "new-content".toByteArray()
        val ok = AtomicFileStore.writeAtomically(
            target,
            ByteArrayInputStream(payload),
            expectedSha256 = sha256(payload),
        )
        assertTrue(ok)
        assertEquals("new-content", target.readText())
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
