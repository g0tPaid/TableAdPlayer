package com.tableadplayer.app.data.local

/**
 * Intended Room schema (Phase 3). Not a @Database yet — keep playback independent
 * of schema migrations during Phase 1–2.
 */
data class CachedMediaRow(
    val mediaId: String,
    val sha256: String,
    val localPath: String,
    val bytes: Long,
    val complete: Boolean,
    val updatedAt: Long,
)

data class PlaylistPinRow(
    val playlistId: String,
    val revision: Long,
    val json: String,
    val activatedAt: Long,
)

data class OutboxEventRow(
    val id: Long,
    val type: String,
    val payloadJson: String,
    val createdAt: Long,
    val attempts: Int,
    val nextAttemptAt: Long,
)
