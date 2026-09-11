package com.tableadplayer.app.sync

import androidx.room.withTransaction
import com.tableadplayer.app.data.cache.MediaCache
import com.tableadplayer.app.data.local.AppConfigEntity
import com.tableadplayer.app.data.local.AppConfigKeys
import com.tableadplayer.app.data.local.MediaEntity
import com.tableadplayer.app.data.local.MediaOrigin
import com.tableadplayer.app.data.local.MediaState
import com.tableadplayer.app.data.local.PlaylistEntity
import com.tableadplayer.app.data.local.PlaylistItemEntity
import com.tableadplayer.app.data.local.ScheduleEntity
import com.tableadplayer.app.data.local.SyncJobKind
import com.tableadplayer.app.data.local.TableAdDatabase
import com.tableadplayer.app.data.remote.PlaylistDto
import com.tableadplayer.app.data.remote.RemoteMediaDto

/**
 * Writes a fetched playlist as **pending**. Activation is [PinSwapPolicy] via [tryActivate].
 */
class PlaylistIngestor(
    private val db: TableAdDatabase,
    private val cache: MediaCache,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    data class IngestResult(
        val playlistId: String,
        val revision: Long,
        val enqueuedDownloads: Int,
        val alreadyReady: Int,
    )

    suspend fun persistPending(dto: PlaylistDto, timezone: String): IngestResult {
        val now = clock()
        var enqueued = 0
        var ready = 0
        db.withTransaction {
            val existingPlaylist = db.playlistDao().get(dto.playlistId)
            db.playlistDao().upsert(
                PlaylistEntity(
                    id = dto.playlistId,
                    revision = dto.revision,
                    name = dto.playlistId,
                    isActive = existingPlaylist?.isActive == true,
                    activatedAt = existingPlaylist?.activatedAt,
                    createdAt = existingPlaylist?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            db.scheduleDao().deleteForPlaylist(dto.playlistId)
            db.playlistItemDao().deleteForPlaylist(dto.playlistId)

            dto.items.forEachIndexed { index, remote ->
                val previous = db.mediaDao().get(remote.id)
                val checksumChanged = PlaylistIngestPolicy.checksumRequiresNewVersion(
                    previous?.checksum,
                    remote.sha256,
                )
                val version = PlaylistIngestPolicy.nextVersion(
                    previousVersion = previous?.version,
                    playlistRevision = dto.revision,
                    checksumChanged = checksumChanged,
                )
                val fileExists = cache.playableFile(remote.id) != null
                val currentRevisionReady = !checksumChanged && cache.isReady(remote.id)
                if (currentRevisionReady) ready += 1
                val keepPlaying = fileExists && previous?.state == MediaState.READY
                db.mediaDao().upsert(
                    MediaEntity(
                        id = remote.id,
                        remoteUrl = remote.url,
                        type = remote.type,
                        state = when {
                            currentRevisionReady -> MediaState.READY
                            keepPlaying -> MediaState.READY
                            else -> MediaState.REMOTE
                        },
                        checksum = remote.sha256 ?: previous?.checksum,
                        fileSize = previous?.fileSize ?: 0L,
                        localPath = previous?.localPath,
                        downloadedAt = previous?.downloadedAt,
                        lastAccessedAt = previous?.lastAccessedAt,
                        version = version,
                        mimeType = previous?.mimeType,
                        errorMessage = if (currentRevisionReady) null else previous?.errorMessage,
                        origin = MediaOrigin.REMOTE,
                        createdAt = previous?.createdAt ?: now,
                        updatedAt = now,
                    ),
                )
                val rowId = db.playlistItemDao().upsert(
                    PlaylistItemEntity(
                        playlistId = dto.playlistId,
                        mediaId = remote.id,
                        position = index,
                        durationMs = remote.durationMs,
                        createdAt = now,
                        updatedAt = now,
                    ),
                )
                val schedule = scheduleOf(remote, dto.playlistId, rowId, timezone, now)
                if (schedule != null) {
                    db.scheduleDao().upsert(schedule)
                }
                val existingJob = db.syncJobDao().findActive(SyncJobKind.MEDIA_DOWNLOAD, remote.id)
                if (PlaylistIngestPolicy.shouldEnqueueDownload(
                        currentRevisionReady = currentRevisionReady,
                        hasActiveJob = existingJob != null,
                    )
                ) {
                    cache.enqueueDownload(remote.id, dto.playlistId)
                    enqueued += 1
                }
            }
        }
        return IngestResult(
            playlistId = dto.playlistId,
            revision = dto.revision,
            enqueuedDownloads = enqueued,
            alreadyReady = ready,
        )
    }

    suspend fun tryActivate(playlistId: String): Boolean {
        val items = db.playlistItemDao().forPlaylist(playlistId)
        val readiness = items.map { item ->
            ItemReadiness(
                mediaId = item.mediaId,
                ready = cache.isReady(item.mediaId),
            )
        }
        if (!PinSwapPolicy.canActivate(readiness)) return false
        val playlist = db.playlistDao().get(playlistId) ?: return false
        val now = clock()
        db.playlistDao().activate(playlist, now)
        db.appConfigDao().upsert(
            AppConfigEntity(
                key = AppConfigKeys.ACTIVE_PLAYLIST_ID,
                value = playlistId,
                updatedAt = now,
            ),
        )
        return true
    }

    private fun scheduleOf(
        remote: RemoteMediaDto,
        playlistId: String,
        itemRowId: Long,
        timezone: String,
        now: Long,
    ): ScheduleEntity? {
        val has = listOf(
            remote.startDate,
            remote.endDate,
            remote.startTime,
            remote.endTime,
            remote.daysOfWeek,
        ).any { !it.isNullOrBlank() }
        if (!has) return null
        return ScheduleEntity(
            playlistId = playlistId,
            playlistItemRowId = itemRowId,
            startDate = remote.startDate,
            endDate = remote.endDate,
            startTime = remote.startTime,
            endTime = remote.endTime,
            daysOfWeek = remote.daysOfWeek,
            timezone = timezone,
            createdAt = now,
            updatedAt = now,
        )
    }
}
