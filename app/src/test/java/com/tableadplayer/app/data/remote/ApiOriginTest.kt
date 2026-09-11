package com.tableadplayer.app.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiOriginTest {
    @Test
    fun placeholderDefaultIsNotLive() {
        assertTrue(ApiOrigin.isPlaceholder("https://api.example.invalid/"))
        assertTrue(ApiOrigin.isPlaceholder("https://foo.example.invalid/v1/"))
        assertFalse(ApiOrigin.usesLiveNetwork("https://api.example.invalid/"))
    }

    @Test
    fun lanMockIsLive() {
        assertTrue(ApiOrigin.usesLiveNetwork("http://10.0.2.2:8787/"))
        assertTrue(ApiOrigin.usesLiveNetwork("http://192.168.1.10:8787"))
        assertFalse(ApiOrigin.isPlaceholder("https://ops.example.com/tablead/"))
    }

    @Test
    fun resolveRelativeMediaAgainstBase() {
        val abs = ApiOrigin.resolveMediaUrl("http://10.0.2.2:8787/", "/v1/media/welcome.png")
        assertEquals("http://10.0.2.2:8787/v1/media/welcome.png", abs)
        val already = ApiOrigin.resolveMediaUrl(
            "http://10.0.2.2:8787/",
            "https://cdn.example.com/a.png",
        )
        assertEquals("https://cdn.example.com/a.png", already)
    }
}
