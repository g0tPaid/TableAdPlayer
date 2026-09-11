package com.tableadplayer.app.playback

import kotlinx.serialization.Serializable

@Serializable
data class DemoPlaylistFile(
    val items: List<PlaylistItemDto> = emptyList(),
)

@Serializable
data class PlaylistItemDto(
    val id: String,
    val type: String,
    val path: String,
    val durationMs: Long? = null,
)

enum class MediaKind { IMAGE, VIDEO }

data class PlaylistItem(
    val id: String,
    val kind: MediaKind,
    val assetPath: String,
    val durationMs: Long?,
)

sealed class PlaybackContent {
    data object Idle : PlaybackContent()
    data object Empty : PlaybackContent()
    data class Image(val item: PlaylistItem, val bytes: ByteArray) : PlaybackContent() {
        override fun equals(other: Any?): Boolean =
            other is Image && other.item == item && other.bytes.contentEquals(bytes)

        override fun hashCode(): Int = 31 * item.hashCode() + bytes.contentHashCode()
    }
    data class Video(val item: PlaylistItem, val assetPath: String) : PlaybackContent()
}

data class EngineStatus(
    val playingIndex: Int = -1,
    val skipped: Int = 0,
    val loopCount: Int = 0,
    val lastError: String? = null,
    val waitingRetry: Boolean = false,
)
