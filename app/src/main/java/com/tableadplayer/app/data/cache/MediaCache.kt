package com.tableadplayer.app.data.cache

import com.tableadplayer.app.data.local.AppConfigKeys
import com.tableadplayer.app.data.local.MediaEntity
import com.tableadplayer.app.data.local.MediaOrigin
import com.tableadplayer.app.data.local.MediaState
import com.tableadplayer.app.data.local.SyncJobKind
import com.tableadplayer.app.data.local.SyncJobStatus
import com.tableadplayer.app.data.local.SyncJobEntity
import com.tableadplayer.app.data.local.TableAdDatabase
import com.tableadplayer.app.data.local.toCleanupSnapshot
import java.io.File
import java.io.InputStream

/**
 * Coordinates Room metadata with app-private files. [com.tableadplayer.app.sync.SyncWorker]
 * downloads through [ingest] and pin-swaps only when [isReady] is true for every item.
 */
class MediaCache(
    private val db: TableAdDatabase,
    private val files: MediaFileStore,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    /**
     * Atomic ingest: mark DOWNLOADING, write `*.part`, verify checksum, rename, mark READY.
     * On failure the row is FAILED and the part file is gone ([AtomicFileStore]).
     */
    suspend fun ingest(
        mediaId: String,
        type: String,
        version: Long,
        checksum: String?,
        remoteUrl: String,
        input: InputStream,
        mimeType: String? = null,
    ): Boolean {
        val now = clock()
        val previous = db.mediaDao().get(mediaId)
        db.mediaDao().upsert(
            (previous ?: MediaEntity(
                id = mediaId,
                remoteUrl = remoteUrl,
                type = type,
                state = MediaState.DOWNLOADING,
                checksum = checksum,
                version = version,
                mimeType = mimeType,
                origin = MediaOrigin.REMOTE,
                createdAt = now,
                updatedAt = now,
            )).copy(
                remoteUrl = remoteUrl.ifBlank { previous?.remoteUrl.orEmpty() },
                type = type,
                state = MediaState.DOWNLOADING,
                checksum = checksum ?: previous?.checksum,
                version = version,
                mimeType = mimeType ?: previous?.mimeType,
                errorMessage = null,
                updatedAt = now,
            ),
        )
        val stored = files.ingest(mediaId, version, input, checksum)
        if (stored == null) {
            db.mediaDao().setState(mediaId, MediaState.FAILED, "atomic write or checksum failed", clock())
            return false
        }
        val verified = checksum ?: AtomicFileStore.sha256Hex(stored)
        val row = db.mediaDao().get(mediaId) ?: return false
        db.mediaDao().upsert(
            row.copy(
                state = MediaState.READY,
                checksum = verified,
                fileSize = stored.length(),
                localPath = files.relativeOf(stored),
                downloadedAt = clock(),
                errorMessage = null,
                updatedAt = clock(),
            ),
        )
        return true
    }

    suspend fun markAccessed(mediaId: String) {
        db.mediaDao().touchAccessed(mediaId, clock())
    }

    /** Playable file for [mediaId] only when state is READY and the file exists. */
    suspend fun playableFile(mediaId: String): File? {
        val row = db.mediaDao().get(mediaId) ?: return null
        if (row.state != MediaState.READY) return null
        val path = row.localPath ?: return null
        val file = files.resolve(path)
        return if (file.isFile && file.length() > 0L) file else null
    }

    /**
     * True when bytes on disk match this row's [MediaEntity.version] (filename
     * `{id}_v{version}`). A version bump for a new checksum keeps the old file
     * playable but not pin-swap-ready until ingest finishes.
     */
    suspend fun isReady(mediaId: String): Boolean {
        val row = db.mediaDao().get(mediaId) ?: return false
        if (row.origin == MediaOrigin.ASSET && row.state == MediaState.READY) return true
        if (row.state != MediaState.READY) return false
        val file = playableFile(mediaId) ?: return false
        return file.name == files.fileName(mediaId, row.version)
    }

    suspend fun enqueueDownload(mediaId: String, playlistId: String? = null) {
        val now = clock()
        db.syncJobDao().insert(
            SyncJobEntity(
                kind = SyncJobKind.MEDIA_DOWNLOAD,
                status = SyncJobStatus.PENDING,
                mediaId = mediaId,
                playlistId = playlistId,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val row = db.mediaDao().get(mediaId)
        if (row != null && row.state != MediaState.READY && row.state != MediaState.DOWNLOADING) {
            db.mediaDao().setState(mediaId, MediaState.REMOTE, null, now)
        }
    }

    /**
     * Deletes leftover `*.part` files first, then unused complete media.
     * Never removes files required by the active playlist.
     */
    suspend fun cleanup() {
        val activeIds = db.playlistItemDao().mediaIdsForActivePlaylist().toSet()
        val all = db.mediaDao().getAll()
        discardStalePartFiles(all, activeIds)

        val maxBytes = db.appConfigDao().getValue(AppConfigKeys.CACHE_MAX_BYTES)
            ?.toLongOrNull()
            ?: MediaCleanupPolicy.DEFAULT_MAX_READY_BYTES
        val plan = MediaCleanupPolicy.plan(
            media = all.map { it.toCleanupSnapshot() },
            activePlaylistMediaIds = activeIds,
            nowMs = clock(),
            maxReadyBytes = maxBytes,
        )
        val referenced = db.playlistItemDao().allReferencedMediaIds().toSet()
        for (id in plan.mediaIdsToDelete) {
            if (id in activeIds) continue
            val row = db.mediaDao().get(id) ?: continue
            files.delete(row.localPath)
            val now = clock()
            if (id in referenced) {
                db.mediaDao().upsert(
                    row.copy(
                        state = MediaState.DELETED,
                        localPath = null,
                        fileSize = 0L,
                        errorMessage = null,
                        updatedAt = now,
                    ),
                )
            } else {
                db.mediaDao().deleteById(id)
            }
        }
    }

    /**
     * Incomplete downloads: keep a `*.part` only when its READY/DOWNLOADING owner is
     * required by the active playlist. Everything else is deleted first (architecture:
     * prefer dropping parts before complete media).
     */
    suspend fun discardStalePartFiles() {
        val activeIds = db.playlistItemDao().mediaIdsForActivePlaylist().toSet()
        discardStalePartFiles(db.mediaDao().getAll(), activeIds)
    }

    private fun discardStalePartFiles(all: List<MediaEntity>, activeIds: Set<String>) {
        val keepRelative = all
            .filter {
                it.id in activeIds &&
                    (it.state == MediaState.DOWNLOADING || it.state == MediaState.READY) &&
                    !it.localPath.isNullOrBlank()
            }
            .mapNotNull { it.localPath }
            .toSet()
        val keepNames = keepRelative.map { File(it).name }.toSet()

        for (part in files.listPartFiles()) {
            val finalName = part.name.removeSuffix(MediaFileStore.PART_SUFFIX)
            val downloadingOwner = all.any { media ->
                media.id in activeIds &&
                    media.state == MediaState.DOWNLOADING &&
                    (media.localPath == null || File(media.localPath).name == finalName)
            }
            if (finalName in keepNames || downloadingOwner) continue
            part.delete()
        }
    }
}
