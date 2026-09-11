package com.tableadplayer.app.core.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceIdFormatterTest {
    @Test
    fun formatsEightHexChars() {
        val id = DeviceIdFormatter.fromHexBytes(byteArrayOf(0x0A, 0x1B, 0x2C, 0x3D, 0x4E))
        assertEquals("TABLE-0a1b2c3d", id)
        assertTrue(DeviceIdFormatter.isValid(id))
    }

    @Test
    fun rejectsBadIds() {
        assertFalse(DeviceIdFormatter.isValid("TABLE-ABC"))
        assertFalse(DeviceIdFormatter.isValid("table-0a1b2c3d"))
        assertFalse(DeviceIdFormatter.isValid("TABLE-0a1b2c3d9"))
    }
}
