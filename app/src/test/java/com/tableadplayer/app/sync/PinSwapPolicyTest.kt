package com.tableadplayer.app.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinSwapPolicyTest {
    @Test
    fun emptyPlaylistCannotActivate() {
        assertFalse(PinSwapPolicy.canActivate(emptyList()))
    }

    @Test
    fun allReadyActivates() {
        assertTrue(
            PinSwapPolicy.canActivate(
                listOf(
                    ItemReadiness("a", ready = true),
                    ItemReadiness("b", ready = true),
                ),
            ),
        )
    }

    @Test
    fun anyNotReadyBlocksPinSwap() {
        assertFalse(
            PinSwapPolicy.canActivate(
                listOf(
                    ItemReadiness("a", ready = true),
                    ItemReadiness("b", ready = false),
                ),
            ),
        )
    }
}
