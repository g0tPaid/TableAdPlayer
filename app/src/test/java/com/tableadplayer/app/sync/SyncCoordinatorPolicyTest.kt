package com.tableadplayer.app.sync

import com.tableadplayer.app.data.seed.DemoPlaylistSeeder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCoordinatorPolicyTest {
    @Test
    fun demoPinRemainsUntilRemotePinSwap() {
        assertTrue(SyncCoordinator.shouldKeepDemoPin(DemoPlaylistSeeder.PLAYLIST_ID, pinSwapped = false))
        assertTrue(SyncCoordinator.shouldKeepDemoPin(null, pinSwapped = false))
        assertFalse(SyncCoordinator.shouldKeepDemoPin(DemoPlaylistSeeder.PLAYLIST_ID, pinSwapped = true))
        assertFalse(SyncCoordinator.shouldKeepDemoPin("venue-lobby", pinSwapped = false))
    }
}
