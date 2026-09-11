package com.tableadplayer.app.data.repo

import com.tableadplayer.app.core.json.AppJson
import com.tableadplayer.app.data.local.PlaybackEventEntity
import com.tableadplayer.app.data.local.PlaybackEventType
import com.tableadplayer.app.data.local.TableAdDatabase
import com.tableadplayer.app.data.remote.HeartbeatDto
import com.tableadplayer.app.data.remote.PlayerEventDto
import com.tableadplayer.app.data.remote.TableAdApi
import com.tableadplayer.app.reporting.PlaybackEventDrain
import com.tableadplayer.app.reporting.PlaybackEventOutbox
import com.tableadplayer.app.reporting.QueuedEvent
import com.tableadplayer.app.sync.Backoff
import kotlinx.serialization.Serializable

/**
 * Heartbeat + playback-event outbox. Offline devices enqueue locally and drain
 * later. Callers must never [kotlinx.coroutines.Job.join] this type from
 * [com.tableadplayer.app.playback.PlaylistEngine] — enqueue is fire-and-forget
 * via [com.tableadplayer.app.reporting.ReportingQueue] / [com.tableadplayer.app.reporting.ReportingPump].
 */
class ReportingRepository(
    private val api: TableAdApi,
    private val db: TableAdDatabase,
    private val devices: DeviceRepository,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun enqueueHeartbeat(payload: HeartbeatDto) {
        dropOldestHeartbeatsIfFull()
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

    suspend fun enqueuePlaybackEvent(event: QueuedEvent) {
        val type = event.type.trim().lowercase()
        if (!PlaybackEventOutbox.isPlaybackType(type)) return
        dropOldestPlaybackIfFull()
        val now = clock()
        val at = if (event.atEpochMs > 0L) event.atEpochMs else now
        val dto = PlayerEventDto(
            type = type,
            itemId = event.itemId,
            at = DeviceRepository.utcNow(at),
            detail = event.detail,
        )
        db.playbackEventDao().insert(
            PlaybackEventEntity(
                type = type,
                mediaId = event.itemId,
                playlistId = event.playlistId,
                payloadJson = AppJson.compact.encodeToString(PlayerEventDto.serializer(), dto),
                createdAt = at,
                uploadedAt = null,
                attempts = 0,
                nextAttemptAt = 0L,
            ),
        )
    }

    suspend fun drainHeartbeats(limit: Int = HeartbeatOutbox.DRAIN_BATCH): DrainResult {
        return runCatching {
            val now = clock()
            val pending = db.playbackEventDao().pendingByType(PlaybackEventType.HEARTBEAT, now, limit)
            var uploaded = 0
            var failed = 0
            for (row in pending) {
                val payload = decodeHeartbeat(row.payloadJson) ?: HeartbeatDto(
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
            DrainResult(attempted = pending.size, uploaded = uploaded, failed = failed)
        }.getOrElse { DrainResult(attempted = 0, uploaded = 0, failed = 0) }
    }

    suspend fun drainPlaybackEvents(limit: Int = PlaybackEventOutbox.DRAIN_BATCH): DrainResult {
        return runCatching {
            val now = clock()
            val pending = db.playbackEventDao()
                .pendingExcludingType(PlaybackEventType.HEARTBEAT, now, limit)
            val rows = pending.map { row ->
                PlaybackEventDrain.OutboxRow(
                    id = row.id,
                    type = row.type,
                    mediaId = row.mediaId,
                    playlistId = row.playlistId,
                    payloadJson = row.payloadJson,
                    createdAt = row.createdAt,
                    attempts = row.attempts,
                )
            }
            val outcome = PlaybackEventDrain.drain(
                pending = rows,
                deviceId = devices.deviceId(),
                nowMs = now,
                post = { batch -> api.reportEvents(batch) },
            )
            applyDrainOutcome(outcome)
            outcome.result
        }.getOrElse { DrainResult(attempted = 0, uploaded = 0, failed = 0) }
    }

    suspend fun drainAll(): DrainResult {
        val heartbeats = drainHeartbeats()
        val events = drainPlaybackEvents()
        return DrainResult(
            attempted = heartbeats.attempted + events.attempted,
            uploaded = heartbeats.uploaded + events.uploaded,
            failed = heartbeats.failed + events.failed,
        )
    }

    suspend fun pendingSnapshot(): ReportingPendingSnapshot {
        val dao = db.playbackEventDao()
        return ReportingPendingSnapshot(
            heartbeats = dao.pendingCountByType(PlaybackEventType.HEARTBEAT),
            playback = dao.pendingCountExcludingType(PlaybackEventType.HEARTBEAT),
            play = dao.pendingCountByType(PlaybackEventType.PLAY),
            skip = dao.pendingCountByType(PlaybackEventType.SKIP),
            error = dao.pendingCountByType(PlaybackEventType.ERROR),
            completed = dao.pendingCountByType(PlaybackEventType.COMPLETED),
        )
    }

    private suspend fun applyDrainOutcome(outcome: PlaybackEventDrain.Outcome) {
        val now = clock()
        for (id in outcome.uploadedIds) {
            db.playbackEventDao().markUploaded(id, now)
        }
        val dropIds = ArrayList<Long>()
        dropIds.addAll(outcome.droppedPoisonIds)
        for (retry in outcome.retries) {
            if (retry.drop) {
                dropIds += retry.id
            } else {
                db.playbackEventDao().scheduleRetry(retry.id, retry.attempts, retry.nextAttemptAt)
            }
        }
        if (dropIds.isNotEmpty()) {
            db.playbackEventDao().deleteByIds(dropIds)
        }
    }

    private suspend fun dropOldestHeartbeatsIfFull() {
        val pending = db.playbackEventDao().pendingCountByType(PlaybackEventType.HEARTBEAT)
        if (!HeartbeatOutbox.shouldDropOldest(pending)) return
        val overflow = pending - HeartbeatOutbox.MAX_PENDING + 1
        val ids = db.playbackEventDao().oldestPendingIds(PlaybackEventType.HEARTBEAT, overflow)
        if (ids.isNotEmpty()) db.playbackEventDao().deleteByIds(ids)
    }

    private suspend fun dropOldestPlaybackIfFull() {
        val pending = db.playbackEventDao().pendingCountExcludingType(PlaybackEventType.HEARTBEAT)
        if (!PlaybackEventOutbox.shouldDropOldest(pending)) return
        val overflow = PlaybackEventOutbox.overflowCount(pending)
        val ids = db.playbackEventDao().oldestPendingIdsExcludingType(PlaybackEventType.HEARTBEAT, overflow)
        if (ids.isNotEmpty()) db.playbackEventDao().deleteByIds(ids)
    }

    private fun decodeHeartbeat(json: String?): HeartbeatDto? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            AppJson.compact.decodeFromString(HeartbeatDto.serializer(), json)
        }.getOrNull()
    }
}

@Serializable
data class ReportingPendingSnapshot(
    val heartbeats: Int = 0,
    val playback: Int = 0,
    val play: Int = 0,
    val skip: Int = 0,
    val error: Int = 0,
    val completed: Int = 0,
)

object HeartbeatOutbox {
    const val TYPE = PlaybackEventType.HEARTBEAT
    const val MAX_PENDING = 64
    const val DRAIN_BATCH = 16

    fun shouldDropOldest(pendingCount: Int, max: Int = MAX_PENDING): Boolean = pendingCount >= max

    fun nextAttemptAt(attempts: Int, nowMs: Long): Long = nowMs + Backoff.delayMs(attempts)
}
