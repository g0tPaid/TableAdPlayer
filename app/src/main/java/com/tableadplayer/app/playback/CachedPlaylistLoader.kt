package com.tableadplayer.app.playback

import com.tableadplayer.app.data.cache.MediaCache
import com.tableadplayer.app.data.local.AppConfigKeys
import com.tableadplayer.app.data.local.MediaOrigin
import com.tableadplayer.app.data.local.TableAdDatabase
import com.tableadplayer.app.data.seed.DemoPlaylistSeeder
import com.tableadplayer.app.scheduler.ScheduleParser
import java.time.ZoneId

/**
 * Maps the **active** Room playlist to playback items using READY cached files.
 * Asset/DEMO playlists are not mapped here (paths live in `assets/demo/`).
 */
object CachedPlaylistLoader {
    suspend fun load(db: TableAdDatabase, cache: MediaCache): List<PlaylistItem> {
        val active = db.playlistDao().getActive() ?: return emptyList()
        if (active.id == DemoPlaylistSeeder.PLAYLIST_ID) return emptyList()
        val tz = db.appConfigDao().getValue(AppConfigKeys.TIMEZONE) ?: "UTC"
        val zone = ScheduleParser.parseZone(tz) ?: ZoneId.of("UTC")
        val rows = db.playlistItemDao().forPlaylistWithSchedules(active.id)
        return rows.mapNotNull { row ->
            val media = db.mediaDao().get(row.item.mediaId) ?: return@mapNotNull null
            if (media.origin == MediaOrigin.ASSET) return@mapNotNull null
            val file = cache.playableFile(media.id) ?: return@mapNotNull null
            val kind = when (media.type.lowercase()) {
                "video" -> MediaKind.VIDEO
                "image" -> MediaKind.IMAGE
                else -> return@mapNotNull null
            }
            val schedule = row.schedules.firstOrNull()?.let { ScheduleParser.fromEntity(it, zone) }
            PlaylistItem(
                id = media.id,
                kind = kind,
                source = MediaSource.CachedFile(file.absolutePath),
                durationMs = row.item.durationMs ?: if (kind == MediaKind.IMAGE) 5_000L else null,
                schedule = schedule,
            )
        }
    }
}
