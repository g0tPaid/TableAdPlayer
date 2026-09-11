package com.tableadplayer.app.reporting

import com.tableadplayer.app.core.json.AppJson
import com.tableadplayer.app.data.remote.AckDto
import com.tableadplayer.app.data.remote.EventBatchDto
import com.tableadplayer.app.data.remote.PlayerEventDto
import com.tableadplayer.app.data.repo.DeviceRepository
import com.tableadplayer.app.data.repo.DrainResult

/**
 * Pure drain step: map outbox rows to `POST /v1/device/events`, decide
 * uploaded vs retry. Callers persist those decisions. Failures never throw
 * to the playlist loop (this type is not invoked from it).
 */
object PlaybackEventDrain {
    data class OutboxRow(
        val id: Long,
        val type: String,
        val mediaId: String?,
        val playlistId: String?,
        val payloadJson: String?,
        val createdAt: Long,
        val attempts: Int,
    )

    data class Retry(
        val id: Long,
        val attempts: Int,
        val nextAttemptAt: Long,
        val drop: Boolean,
    )

    data class Outcome(
        val result: DrainResult,
        val uploadedIds: List<Long>,
        val retries: List<Retry>,
        val droppedPoisonIds: List<Long> = emptyList(),
    )

    fun toDto(row: OutboxRow): PlayerEventDto? {
        decode(row.payloadJson)?.let { return it }
        if (!PlaybackEventOutbox.isPlaybackType(row.type)) return null
        return PlayerEventDto(
            type = row.type,
            itemId = row.mediaId,
            at = DeviceRepository.utcNow(row.createdAt),
            detail = null,
        )
    }

    suspend fun drain(
        pending: List<OutboxRow>,
        deviceId: String,
        nowMs: Long,
        post: suspend (EventBatchDto) -> AckDto,
    ): Outcome {
        if (pending.isEmpty()) {
            return Outcome(DrainResult(0, 0, 0), emptyList(), emptyList())
        }
        val poison = mutableListOf<Long>()
        val events = ArrayList<PlayerEventDto>(pending.size)
        val sendable = ArrayList<OutboxRow>(pending.size)
        for (row in pending) {
            val dto = toDto(row)
            if (dto == null) {
                poison += row.id
            } else {
                events += dto
                sendable += row
            }
        }
        if (sendable.isEmpty()) {
            return Outcome(
                result = DrainResult(attempted = pending.size, uploaded = 0, failed = 0),
                uploadedIds = emptyList(),
                retries = emptyList(),
                droppedPoisonIds = poison,
            )
        }
        val ack = runCatching { post(EventBatchDto(deviceId = deviceId, events = events)) }
            .getOrNull()
        return if (ack?.ok == true) {
            Outcome(
                result = DrainResult(
                    attempted = pending.size,
                    uploaded = sendable.size,
                    failed = 0,
                ),
                uploadedIds = sendable.map { it.id },
                retries = emptyList(),
                droppedPoisonIds = poison,
            )
        } else {
            val retries = sendable.map { row ->
                val attempts = row.attempts + 1
                Retry(
                    id = row.id,
                    attempts = attempts,
                    nextAttemptAt = PlaybackEventOutbox.nextAttemptAt(attempts, nowMs),
                    drop = PlaybackEventOutbox.shouldDropAfterAttempts(attempts),
                )
            }
            Outcome(
                result = DrainResult(
                    attempted = pending.size,
                    uploaded = 0,
                    failed = sendable.size,
                ),
                uploadedIds = emptyList(),
                retries = retries,
                droppedPoisonIds = poison,
            )
        }
    }

    private fun decode(json: String?): PlayerEventDto? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            AppJson.compact.decodeFromString(PlayerEventDto.serializer(), json)
        }.getOrNull()
    }
}
