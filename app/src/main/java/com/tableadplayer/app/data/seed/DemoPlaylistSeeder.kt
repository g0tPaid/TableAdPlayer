package com.tableadplayer.app.data.seed

import android.content.Context
import android.os.Build
import com.tableadplayer.app.BuildConfig
import com.tableadplayer.app.core.device.DeviceIdentity
import com.tableadplayer.app.data.local.AppConfigEntity
import com.tableadplayer.app.data.local.AppConfigKeys
import com.tableadplayer.app.data.local.DeviceEntity
import com.tableadplayer.app.data.local.MediaEntity
import com.tableadplayer.app.data.local.MediaOrigin
import com.tableadplayer.app.data.local.MediaState
import com.tableadplayer.app.data.local.PlaylistEntity
import com.tableadplayer.app.data.local.PlaylistItemEntity
import com.tableadplayer.app.data.local.TableAdDatabase
import com.tableadplayer.app.playback.DemoPlaylistLoader
import com.tableadplayer.app.playback.MediaKind

/**
 * Pins the bundled DEMO playlist in Room so cleanup always sees an active playlist.
 * Media stays in `assets/`; no server and no `files/media/` copy is required.
 */
object DemoPlaylistSeeder {
    const val PLAYLIST_ID = "demo-local"
    const val REVISION = 1L

    suspend fun seed(context: Context, db: TableAdDatabase, deviceIdentity: DeviceIdentity) {
        if (!BuildConfig.DEMO_MODE) return
        val now = System.currentTimeMillis()
        val items = runCatching { DemoPlaylistLoader.load(context) }.getOrDefault(emptyList())

        val deviceId = deviceIdentity.getOrCreate()
        db.deviceDao().upsert(
            DeviceEntity(
                id = deviceId,
                displayName = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                timezone = "UTC",
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                lastSeenAt = now,
                createdAt = db.deviceDao().get(deviceId)?.createdAt ?: now,
                updatedAt = now,
            ),
        )

        val mediaRows = items.map { item ->
            val existing = db.mediaDao().get(item.id)
            MediaEntity(
                id = item.id,
                remoteUrl = "",
                type = if (item.kind == MediaKind.IMAGE) "image" else "video",
                state = MediaState.READY,
                checksum = existing?.checksum,
                fileSize = existing?.fileSize ?: 0L,
                localPath = existing?.localPath,
                downloadedAt = existing?.downloadedAt ?: now,
                lastAccessedAt = existing?.lastAccessedAt,
                version = existing?.version ?: 1L,
                origin = MediaOrigin.ASSET,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
            )
        }
        if (mediaRows.isNotEmpty()) {
            db.mediaDao().upsertAll(mediaRows)
        }

        val playlist = PlaylistEntity(
            id = PLAYLIST_ID,
            revision = REVISION,
            name = "DEMO",
            isActive = true,
            activatedAt = now,
            createdAt = db.playlistDao().get(PLAYLIST_ID)?.createdAt ?: now,
            updatedAt = now,
        )
        db.playlistDao().activate(playlist, now)
        db.playlistItemDao().deleteForPlaylist(PLAYLIST_ID)
        db.playlistItemDao().upsertAll(
            items.mapIndexed { index, item ->
                PlaylistItemEntity(
                    playlistId = PLAYLIST_ID,
                    mediaId = item.id,
                    position = index,
                    durationMs = item.durationMs,
                    createdAt = now,
                    updatedAt = now,
                )
            },
        )
        db.appConfigDao().upsert(
            AppConfigEntity(
                key = AppConfigKeys.ACTIVE_PLAYLIST_ID,
                value = PLAYLIST_ID,
                updatedAt = now,
            ),
        )
        db.appConfigDao().upsert(
            AppConfigEntity(
                key = AppConfigKeys.TIMEZONE,
                value = "UTC",
                updatedAt = now,
            ),
        )
    }
}
