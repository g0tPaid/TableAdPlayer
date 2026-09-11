package com.tableadplayer.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(tableName = "devices")
data class DeviceEntity(
    @PrimaryKey val id: String,
    val displayName: String? = null,
    val timezone: String = "UTC",
    val manufacturer: String? = null,
    val model: String? = null,
    val lastSeenAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "media",
    indices = [
        Index("state"),
        Index("lastAccessedAt"),
        Index("version"),
    ],
)
data class MediaEntity(
    @PrimaryKey val id: String,
    val remoteUrl: String = "",
    val type: String,
    val state: MediaState,
    val checksum: String? = null,
    val fileSize: Long = 0L,
    /** Path relative to [android.content.Context.getFilesDir], e.g. `media/welcome_v1`. */
    val localPath: String? = null,
    val downloadedAt: Long? = null,
    val lastAccessedAt: Long? = null,
    val version: Long = 1L,
    val mimeType: String? = null,
    val errorMessage: String? = null,
    val origin: String = MediaOrigin.REMOTE,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlists",
    indices = [Index("isActive")],
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val revision: Long,
    val name: String? = null,
    val isActive: Boolean = false,
    val activatedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_items",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MediaEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.NO_ACTION,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index("mediaId"),
        Index(value = ["playlistId", "position"], unique = true),
    ],
)
data class PlaylistItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val playlistId: String,
    val mediaId: String,
    val position: Int,
    val durationMs: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "schedules",
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = PlaylistItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistItemRowId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("playlistId"),
        Index("playlistItemRowId"),
    ],
)
data class ScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val playlistId: String? = null,
    val playlistItemRowId: Long? = null,
    /** Inclusive `yyyy-MM-dd`, or null for no date bound. */
    val startDate: String? = null,
    /** Inclusive `yyyy-MM-dd`, or null for no date bound. */
    val endDate: String? = null,
    /** Inclusive `HH:mm` or `HH:mm:ss`. */
    val startTime: String? = null,
    /** Exclusive `HH:mm` or `HH:mm:ss`. Overnight windows wrap midnight. */
    val endTime: String? = null,
    /**
     * ISO day numbers `1=Mon … 7=Sun`, e.g. `"1,2,3,4,5"`.
     * Empty/null = every day.
     */
    val daysOfWeek: String? = null,
    val timezone: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playback_events",
    indices = [
        Index("uploadedAt"),
        Index("createdAt"),
        Index("nextAttemptAt"),
    ],
)
data class PlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val type: String,
    val mediaId: String? = null,
    val playlistId: String? = null,
    val payloadJson: String? = null,
    val createdAt: Long,
    val uploadedAt: Long? = null,
    val attempts: Int = 0,
    val nextAttemptAt: Long = 0L,
)

@Entity(
    tableName = "sync_jobs",
    indices = [
        Index("status"),
        Index("kind"),
        Index("nextAttemptAt"),
    ],
)
data class SyncJobEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val kind: String,
    val status: String,
    val mediaId: String? = null,
    val playlistId: String? = null,
    val attempts: Int = 0,
    val lastError: String? = null,
    val nextAttemptAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "app_config")
data class AppConfigEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long,
)

data class PlaylistWithItems(
    @Embedded val playlist: PlaylistEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistId",
    )
    val items: List<PlaylistItemEntity>,
)

data class PlaylistItemWithSchedule(
    @Embedded val item: PlaylistItemEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "playlistItemRowId",
    )
    val schedules: List<ScheduleEntity>,
)

/** Snapshot used by [com.tableadplayer.app.data.cache.MediaCleanupPolicy] (no Room types required). */
data class MediaCleanupSnapshot(
    val id: String,
    val state: MediaState,
    val fileSize: Long,
    val lastAccessedAt: Long?,
    val downloadedAt: Long?,
    val localPath: String?,
)

fun MediaEntity.toCleanupSnapshot(): MediaCleanupSnapshot = MediaCleanupSnapshot(
    id = id,
    state = state,
    fileSize = fileSize,
    lastAccessedAt = lastAccessedAt,
    downloadedAt = downloadedAt,
    localPath = localPath,
)
