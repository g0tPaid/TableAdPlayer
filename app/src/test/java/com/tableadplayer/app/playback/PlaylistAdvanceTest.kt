package com.tableadplayer.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistAdvanceTest {
    @Test
    fun wrapsAround() {
        assertEquals(1, PlaylistAdvance.nextIndex(0, 3))
        assertEquals(0, PlaylistAdvance.nextIndex(2, 3))
    }

    @Test
    fun backsOffWhenAllItemsFail() {
        assertTrue(PlaylistAdvance.shouldBackoff(3, 3))
        assertFalse(PlaylistAdvance.shouldBackoff(2, 3))
        assertTrue(PlaylistAdvance.shouldBackoff(1, 0))
    }
}
