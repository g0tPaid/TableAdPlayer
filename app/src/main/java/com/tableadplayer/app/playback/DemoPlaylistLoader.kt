package com.tableadplayer.app.playback

import android.content.Context
import com.tableadplayer.app.core.json.AppJson
import com.tableadplayer.app.scheduler.ScheduleParser
import com.tableadplayer.app.scheduler.ScheduleWindow
import java.time.ZoneId

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
                source = MediaSource.Asset(dto.path),
                durationMs = dto.durationMs ?: if (kind == MediaKind.IMAGE) 5_000L else null,
                schedule = scheduleOf(dto),
            )
        }
    }

    private fun scheduleOf(dto: PlaylistItemDto): ScheduleWindow? {
        val window = ScheduleWindow(
            startDate = ScheduleParser.parseDate(dto.startDate),
            endDate = ScheduleParser.parseDate(dto.endDate),
            startTime = ScheduleParser.parseTime(dto.startTime),
            endTime = ScheduleParser.parseTime(dto.endTime),
            daysOfWeek = ScheduleParser.parseDaysOfWeek(dto.daysOfWeek),
            zone = ZoneId.of("UTC"),
        )
        return window.takeUnless { it.isUnconstrained }
    }
}
