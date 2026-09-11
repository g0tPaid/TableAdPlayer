package com.tableadplayer.app.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaStateTest {
    @Test
    fun requiredLifecycleStatesArePresent() {
        assertEquals(
            setOf(
                MediaState.REMOTE,
                MediaState.DOWNLOADING,
                MediaState.READY,
                MediaState.FAILED,
                MediaState.EXPIRED,
                MediaState.DELETED,
            ),
            MediaState.entries.toSet(),
        )
    }

    @Test
    fun convertersRoundTripEveryState() {
        val converters = AppTypeConverters()
        for (state in MediaState.entries) {
            assertEquals(state, converters.mediaStateFromString(converters.mediaStateToString(state)))
        }
    }
}
