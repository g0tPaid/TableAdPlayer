package com.tableadplayer.app.playback

import android.content.Context
import com.tableadplayer.app.BuildConfig
import com.tableadplayer.app.TableAdPlayerApp
import com.tableadplayer.app.data.seed.DemoPlaylistSeeder

/**
 * Prefer a fully cached (READY) remote playlist; otherwise keep DEMO assets.
 * Offline devices never go blank.
 */
object PlaylistResolver {
    data class Resolved(
        val items: List<PlaylistItem>,
        val demo: Boolean,
        val playlistId: String,
    )

    suspend fun resolve(context: Context): Resolved {
        val app = context.applicationContext as? TableAdPlayerApp
        val db = app?.container?.database
        val cache = app?.container?.mediaCache
        if (db != null && cache != null) {
            val activeId = db.playlistDao().getActive()?.id
            if (activeId != null && activeId != DemoPlaylistSeeder.PLAYLIST_ID) {
                val cached = CachedPlaylistLoader.load(db, cache)
                if (cached.isNotEmpty()) {
                    return Resolved(cached, demo = false, playlistId = activeId)
                }
            }
        }
        val demo = DemoPlaylistLoader.load(context)
        if (demo.isNotEmpty()) {
            return Resolved(demo, demo = true, playlistId = DemoPlaylistSeeder.PLAYLIST_ID)
        }
        // Last resort: still try cached even if DEMO assets are missing.
        if (db != null && cache != null) {
            val cached = CachedPlaylistLoader.load(db, cache)
            if (cached.isNotEmpty()) {
                return Resolved(
                    cached,
                    demo = false,
                    playlistId = db.playlistDao().getActive()?.id ?: "cached",
                )
            }
        }
        return Resolved(emptyList(), demo = BuildConfig.DEMO_MODE, playlistId = DemoPlaylistSeeder.PLAYLIST_ID)
    }
}
