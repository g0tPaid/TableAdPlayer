package com.tableadplayer.app.sync

import com.tableadplayer.app.BuildConfig
import com.tableadplayer.app.data.cache.MediaCache
import com.tableadplayer.app.data.local.AppConfigKeys
import com.tableadplayer.app.data.local.SyncJobKind
import com.tableadplayer.app.data.local.SyncJobStatus
import com.tableadplayer.app.data.local.TableAdDatabase
import com.tableadplayer.app.data.remote.ApiOrigin
import com.tableadplayer.app.data.remote.HeartbeatDto
import com.tableadplayer.app.data.repo.ContentRepository
import com.tableadplayer.app.data.repo.DeviceRepository
import com.tableadplayer.app.data.repo.DrainResult
import com.tableadplayer.app.data.repo.ReportingRepository
import com.tableadplayer.app.data.repo.SyncOutcome
import com.tableadplayer.app.data.seed.DemoPlaylistSeeder

/**
 * Offline-first sync: register, heartbeat drain, playlist fetch, atomic downloads,
 * pin-swap only when every required item is READY. Failures never wipe the
 * currently playing (cached or DEMO) playlist.
 */
class SyncCoordinator(
    private val db: TableAdDatabase,
    private val cache: MediaCache,
    private val devices: DeviceRepository,
    private val content: ContentRepository,
    private val reporting: ReportingRepository,
    private val downloader: MediaDownloader,
    private val ingestor: PlaylistIngestor,
    private val freeSpace: FreeSpaceGuard,
    private val baseUrl: String = BuildConfig.API_BASE_URL,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun sync(): SyncOutcome {
        cache.discardStalePartFiles()
        devices.hydrateAuth()
        val registered = devices.ensureRegistered()
        devices.fetchAndStoreConfig()
        enqueueHeartbeat()
        val drainedHb = runCatching { reporting.drainHeartbeats() }.getOrDefault(DrainResult(0, 0, 0))
        val drainedEv = runCatching { reporting.drainPlaybackEvents() }.getOrDefault(DrainResult(0, 0, 0))

        val live = ApiOrigin.usesLiveNetwork(baseUrl)
        val playlistResult = content.currentPlaylist()
        val dto = playlistResult.getOrNull()
        var downloadsAttempted = 0
        var downloadsSucceeded = 0
        var skippedLowSpace = 0
        var pinSwapped = false
        var fetched = false

        if (dto != null) {
            fetched = true
            if (shouldDownloadFromOrigin(live, playlistFetched = true)) {
                val timezone = db.appConfigDao().getValue(AppConfigKeys.TIMEZONE) ?: "UTC"
                ingestor.persistPending(dto, timezone)
                val drain = drainDownloads(dto.playlistId, dto.items.map { it.id }.toSet())
                downloadsAttempted = drain.attempted
                downloadsSucceeded = drain.succeeded
                skippedLowSpace = drain.skippedLowSpace
                pinSwapped = ingestor.tryActivate(dto.playlistId)
            }
        }

        cache.cleanup()
        val error = outcomeError(
            playlistError = playlistResult.exceptionOrNull()?.message,
            registered = registered.isRegistered,
            live = live,
        )
        return SyncOutcome(
            registered = registered.isRegistered,
            playlistFetched = fetched,
            downloadsAttempted = downloadsAttempted,
            downloadsSucceeded = downloadsSucceeded,
            downloadsSkippedLowSpace = skippedLowSpace,
            pinSwapped = pinSwapped,
            heartbeatsDrained = drainedHb.uploaded,
            eventsDrained = drainedEv.uploaded,
            error = error,
        )
    }

    private suspend fun enqueueHeartbeat() {
        val reg = devices.registration()
        val active = db.playlistDao().getActive()
        reporting.enqueueHeartbeat(
            HeartbeatDto(
                deviceId = reg.deviceId,
                appVersion = BuildConfig.VERSION_NAME,
                capturedAt = DeviceRepository.utcNow(clock()),
                status = reg.status,
                playlistId = active?.id,
                playlistRevision = active?.revision,
                storageFreeBytes = freeSpace.usable(),
            ),
        )
    }

    private suspend fun drainDownloads(playlistId: String, requiredIds: Set<String>): DownloadDrain {
        val now = clock()
        val due = db.syncJobDao().duePending(now, limit = 32)
            .filter { it.kind == SyncJobKind.MEDIA_DOWNLOAD }
        var attempted = 0
        var succeeded = 0
        var skippedLowSpace = 0
        val reserve = db.appConfigDao().getValue(AppConfigKeys.FREE_SPACE_RESERVE_BYTES)
            ?.toLongOrNull()
            ?: FreeSpacePolicy.DEFAULT_RESERVE_BYTES

        for (job in due) {
            val mediaId = job.mediaId
            if (DownloadJobPolicy.skipMissingMediaId(mediaId)) continue
            val media = db.mediaDao().get(mediaId!!) ?: continue
            val essential = DownloadJobPolicy.isEssential(
                mediaId = mediaId,
                requiredIds = requiredIds,
                jobPlaylistId = job.playlistId,
                playlistId = playlistId,
            )
            attempted += 1
            db.syncJobDao().updateStatus(
                id = job.id,
                status = SyncJobStatus.RUNNING,
                error = null,
                attempts = job.attempts,
                updatedAt = clock(),
                nextAttemptAt = null,
            )
            val result = downloader.download(media, essential)
            val attempts = job.attempts + 1
            val transition = DownloadJobPolicy.after(result, attempts, clock(), reserve)
            succeeded += transition.successDelta
            skippedLowSpace += transition.lowSpaceDelta
            db.syncJobDao().updateStatus(
                id = job.id,
                status = transition.status,
                error = transition.error,
                attempts = attempts,
                updatedAt = clock(),
                nextAttemptAt = transition.nextAttemptAt,
            )
        }
        return DownloadDrain(attempted, succeeded, skippedLowSpace)
    }

    private data class DownloadDrain(
        val attempted: Int,
        val succeeded: Int,
        val skippedLowSpace: Int,
    )

    companion object {
        /** Keep DEMO pin unless a fully READY remote playlist replaced it. */
        fun shouldKeepDemoPin(activePlaylistId: String?, pinSwapped: Boolean): Boolean {
            return !pinSwapped && (activePlaylistId == null || activePlaylistId == DemoPlaylistSeeder.PLAYLIST_ID)
        }

        /** Placeholder origin never downloads; live origin does when a DTO arrived. */
        fun shouldDownloadFromOrigin(live: Boolean, playlistFetched: Boolean): Boolean {
            return live && playlistFetched
        }

        fun outcomeError(playlistError: String?, registered: Boolean, live: Boolean): String? {
            return playlistError ?: if (!registered && live) "unregistered" else null
        }
    }
}
