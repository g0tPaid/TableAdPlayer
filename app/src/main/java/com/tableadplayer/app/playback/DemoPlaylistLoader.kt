package com.tableadplayer.app.playback

import android.content.Context
import com.tableadplayer.app.core.json.AppJson

object DemoPlaylistLoader {
    private const val MANIFEST = "demo/playlist.json"

    fun load(context: Context): List<PlaylistItem> {
        val json = context.assets.open(MANIFEST).bufferedReader().use { it.readText() }
        val file = AppJson.compact.decodeFromString(DemoPlaylistFile.serializer(), json)
        return file.items.mapNotNull { dto ->
            val kind = when (dto.type.lowercase()) {
                "image" -> MediaKind.IMAGE
                "video" -> MediaKind.VIDEO
                else -> return@mapNotNull null
            }
            PlaylistItem(
                id = dto.id,
                kind = kind,
                assetPath = dto.path,
                durationMs = dto.durationMs ?: if (kind == MediaKind.IMAGE) 5_000L else null,
            )
        }
    }
}
