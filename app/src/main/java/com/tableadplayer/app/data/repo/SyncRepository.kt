package com.tableadplayer.app.data.repo

import com.tableadplayer.app.sync.SyncCoordinator

class SyncRepository(
    private val coordinator: SyncCoordinator,
) {
    suspend fun sync(): SyncOutcome = coordinator.sync()
}
