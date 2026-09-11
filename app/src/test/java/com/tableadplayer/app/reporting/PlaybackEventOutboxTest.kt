package com.tableadplayer.app.reporting

import com.tableadplayer.app.data.local.PlaybackEventType
import com.tableadplayer.app.sync.Backoff
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackEventOutboxTest {
    @Test
    fun dropsOldestWhenAtCapacity() {
        assertFalse(PlaybackEventOutbox.shouldDropOldest(0))
        assertFalse(PlaybackEventOutbox.shouldDropOldest(PlaybackEventOutbox.MAX_PENDING - 1))
        assertTrue(PlaybackEventOutbox.shouldDropOldest(PlaybackEventOutbox.MAX_PENDING))
        assertEquals(1, PlaybackEventOutbox.overflowCount(PlaybackEventOutbox.MAX_PENDING))
        assertEquals(11, PlaybackEventOutbox.overflowCount(PlaybackEventOutbox.MAX_PENDING + 10))
    }

    @Test
    fun retryUsesBackoff() {
        val now = 1_000_000L
        assertEquals(now + Backoff.delayMs(1), PlaybackEventOutbox.nextAttemptAt(1, now))
        assertEquals(now + Backoff.delayMs(8), PlaybackEventOutbox.nextAttemptAt(8, now))
    }

    @Test
    fun playbackTypesExcludeHeartbeat() {
        assertTrue(PlaybackEventOutbox.isPlaybackType(PlaybackEventType.PLAY))
        assertTrue(PlaybackEventOutbox.isPlaybackType(PlaybackEventType.SKIP))
        assertTrue(PlaybackEventOutbox.isPlaybackType(PlaybackEventType.ERROR))
        assertTrue(PlaybackEventOutbox.isPlaybackType(PlaybackEventType.COMPLETED))
        assertFalse(PlaybackEventOutbox.isPlaybackType(PlaybackEventType.HEARTBEAT))
        assertFalse(PlaybackEventOutbox.isPlaybackType("unknown"))
    }

    @Test
    fun dropsAfterMaxAttempts() {
        assertFalse(PlaybackEventOutbox.shouldDropAfterAttempts(PlaybackEventOutbox.MAX_ATTEMPTS - 1))
        assertTrue(PlaybackEventOutbox.shouldDropAfterAttempts(PlaybackEventOutbox.MAX_ATTEMPTS))
    }
}
