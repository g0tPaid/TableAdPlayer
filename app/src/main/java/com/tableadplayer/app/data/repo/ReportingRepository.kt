package com.tableadplayer.app.data.repo

import com.tableadplayer.app.core.json.AppJson
import com.tableadplayer.app.data.local.PlaybackEventEntity
import com.tableadplayer.app.data.local.PlaybackEventType
import com.tableadplayer.app.data.local.TableAdDatabase
import com.tableadplayer.app.data.remote.HeartbeatDto
import com.tableadplayer.app.data.remote.TableAdApi
import com.tableadplayer.app.sync.Backoff

/**
 * Heartbeat outbox (Phase 5). Offline devices enqueue locally and drain later.
 * Playback-event drain remains Phase 7 — this type never blocks [com.tableadplayer.app.playback.PlaylistEngine].
 */
class ReportingRepository(
    private val api: TableAdApi,
    private val db: TableAdDatabase,
    private val devices: DeviceRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun enqueueHeartbeat(payload: HeartbeatDto) {
        dropOldestIfFull()
        val now = clock()
        db.playbackEventDao().insert(
            PlaybackEventEntity(
                type = PlaybackEventType.HEARTBEAT,
                mediaId = payload.playbackItemId,
                playlistId = payload.playlistId,
                payloadJson = AppJson.compact.encodeToString(HeartbeatDto.serializer(), payload),
                createdAt = now,
                uploadedAt = null,
                attempts = 0,
                nextAttemptAt = 0L,
            ),
        )
    }

    suspend fun drainHeartbeats(limit: Int = HeartbeatOutbox.DRAIN_BATCH): DrainResult {
        val now = clock()
        val pending = db.playbackEventDao().pendingByType(PlaybackEventType.HEARTBEAT, now, limit)
        var uploaded = 0
        var failed = 0
        for (row in pending) {
            val payload = decode(row.payloadJson) ?: HeartbeatDto(
                deviceId = devices.deviceId(),
                appVersion = "",
                capturedAt = DeviceRepository.utcNow(row.createdAt),
                playbackItemId = row.mediaId,
                playlistId = row.playlistId,
            )
            val ack = runCatching { api.heartbeat(payload) }.getOrNull()
            if (ack?.ok == true) {
                db.playbackEventDao().markUploaded(row.id, clock())
                devices.markHeartbeatSent(payload.capturedAt)
                uploaded += 1
            } else {
                val attempts = row.attempts + 1
                db.playbackEventDao().scheduleRetry(
                    id = row.id,
                    attempts = attempts,
                    nextAttemptAt = HeartbeatOutbox.nextAttemptAt(attempts, clock()),
                )
                failed += 1
            }
        }
        return DrainResult(attempted = pending.size, uploaded = uploaded, failed = failed)
    }

    private suspend fun dropOldestIfFull() {
        val pending = db.playbackEventDao().pendingCountByType(PlaybackEventType.HEARTBEAT)
        if (!HeartbeatOutbox.shouldDropOldest(pending)) return
        val overflow = pending - HeartbeatOutbox.MAX_PENDING + 1
        val ids = db.playbackEventDao().oldestPendingIds(PlaybackEventType.HEARTBEAT, overflow)
        if (ids.isNotEmpty()) db.playbackEventDao().deleteByIds(ids)
    }

    private fun decode(json: String?): HeartbeatDto? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            AppJson.compact.decodeFromString(HeartbeatDto.serializer(), json)
        }.getOrNull()
    }
}

object HeartbeatOutbox {
    const val TYPE = PlaybackEventType.HEARTBEAT
    const val MAX_PENDING = 64
    const val DRAIN_BATCH = 16

    fun shouldDropOldest(pendingCount: Int, max: Int = MAX_PENDING): Boolean = pendingCount >= max

    fun nextAttemptAt(attempts: Int, nowMs: Long): Long = nowMs + Backoff.delayMs(attempts)
}
