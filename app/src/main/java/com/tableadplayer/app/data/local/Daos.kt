package com.tableadplayer.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

@Dao
interface DeviceDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    @Query("SELECT * FROM devices WHERE id = :id LIMIT 1")
    suspend fun get(id: String): DeviceEntity?

    @Query("SELECT * FROM devices LIMIT 1")
    suspend fun getPrimary(): DeviceEntity?
}

@Dao
interface MediaDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(media: MediaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(media: List<MediaEntity>)

    @Update
    suspend fun update(media: MediaEntity)

    @Query("SELECT * FROM media WHERE id = :id LIMIT 1")
    suspend fun get(id: String): MediaEntity?

    @Query("SELECT * FROM media")
    suspend fun getAll(): List<MediaEntity>

    @Query("SELECT * FROM media WHERE state = :state")
    suspend fun getByState(state: MediaState): List<MediaEntity>

    @Query("SELECT * FROM media WHERE localPath = :localPath LIMIT 1")
    suspend fun findByLocalPath(localPath: String): MediaEntity?

    @Query(
        """
        UPDATE media SET lastAccessedAt = :accessedAt, updatedAt = :accessedAt
        WHERE id = :id
        """,
    )
    suspend fun touchAccessed(id: String, accessedAt: Long)

    @Query(
        """
        UPDATE media SET state = :state, errorMessage = :error, updatedAt = :updatedAt
        WHERE id = :id
        """,
    )
    suspend fun setState(id: String, state: MediaState, error: String?, updatedAt: Long)

    @Query("DELETE FROM media WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PlaylistDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun get(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): PlaylistEntity?

    @Query("UPDATE playlists SET isActive = 0, updatedAt = :updatedAt")
    suspend fun deactivateAll(updatedAt: Long)

    @Transaction
    suspend fun activate(playlist: PlaylistEntity, at: Long) {
        deactivateAll(at)
        upsert(
            playlist.copy(
                isActive = true,
                activatedAt = at,
                updatedAt = at,
            ),
        )
    }

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getWithItems(id: String): PlaylistWithItems?

    @Transaction
    @Query("SELECT * FROM playlists WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveWithItems(): PlaylistWithItems?
}

@Dao
interface PlaylistItemDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PlaylistItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteForPlaylist(playlistId: String)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun forPlaylist(playlistId: String): List<PlaylistItemEntity>

    @Query(
        """
        SELECT playlist_items.mediaId FROM playlist_items
        INNER JOIN playlists ON playlists.id = playlist_items.playlistId
        WHERE playlists.isActive = 1
        """,
    )
    suspend fun mediaIdsForActivePlaylist(): List<String>

    @Query("SELECT DISTINCT mediaId FROM playlist_items")
    suspend fun allReferencedMediaIds(): List<String>

    @Transaction
    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun forPlaylistWithSchedules(playlistId: String): List<PlaylistItemWithSchedule>
}

@Dao
interface ScheduleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(schedule: ScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(schedules: List<ScheduleEntity>)

    @Query("SELECT * FROM schedules WHERE playlistId = :playlistId")
    suspend fun forPlaylist(playlistId: String): List<ScheduleEntity>

    @Query("SELECT * FROM schedules WHERE playlistItemRowId = :itemRowId")
    suspend fun forPlaylistItem(itemRowId: Long): List<ScheduleEntity>

    @Query("DELETE FROM schedules WHERE playlistId = :playlistId")
    suspend fun deleteForPlaylist(playlistId: String)
}

@Dao
interface PlaybackEventDao {
    @Insert
    suspend fun insert(event: PlaybackEventEntity): Long

    @Query(
        """
        SELECT * FROM playback_events
        WHERE uploadedAt IS NULL
        ORDER BY createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun pending(limit: Int): List<PlaybackEventEntity>

    @Query("UPDATE playback_events SET uploadedAt = :uploadedAt, attempts = attempts + 1 WHERE id = :id")
    suspend fun markUploaded(id: Long, uploadedAt: Long)

    @Query("SELECT COUNT(*) FROM playback_events WHERE uploadedAt IS NULL")
    suspend fun pendingCount(): Int

    @Query("SELECT COUNT(*) FROM playback_events WHERE uploadedAt IS NULL AND type = :type")
    suspend fun pendingCountByType(type: String): Int

    @Query(
        """
        SELECT * FROM playback_events
        WHERE uploadedAt IS NULL AND type = :type AND nextAttemptAt <= :now
        ORDER BY createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun pendingByType(type: String, now: Long, limit: Int): List<PlaybackEventEntity>

    @Query(
        """
        SELECT id FROM playback_events
        WHERE uploadedAt IS NULL AND type = :type
        ORDER BY createdAt ASC
        LIMIT :count
        """,
    )
    suspend fun oldestPendingIds(type: String, count: Int): List<Long>

    @Query("DELETE FROM playback_events WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query(
        """
        UPDATE playback_events SET attempts = :attempts, nextAttemptAt = :nextAttemptAt
        WHERE id = :id
        """,
    )
    suspend fun scheduleRetry(id: Long, attempts: Int, nextAttemptAt: Long)
}

@Dao
interface SyncJobDao {
    @Insert
    suspend fun insert(job: SyncJobEntity): Long

    @Query("SELECT * FROM sync_jobs WHERE status = :status ORDER BY createdAt ASC")
    suspend fun byStatus(status: String): List<SyncJobEntity>

    @Query(
        """
        SELECT * FROM sync_jobs
        WHERE status = 'PENDING' AND (nextAttemptAt IS NULL OR nextAttemptAt <= :now)
        ORDER BY createdAt ASC
        LIMIT :limit
        """,
    )
    suspend fun duePending(now: Long, limit: Int): List<SyncJobEntity>

    @Query(
        """
        UPDATE sync_jobs SET status = :status, lastError = :error, attempts = :attempts,
        updatedAt = :updatedAt, nextAttemptAt = :nextAttemptAt
        WHERE id = :id
        """,
    )
    suspend fun updateStatus(
        id: Long,
        status: String,
        error: String?,
        attempts: Int,
        updatedAt: Long,
        nextAttemptAt: Long?,
    )

    @Query(
        """
        SELECT * FROM sync_jobs
        WHERE kind = :kind AND mediaId = :mediaId AND status IN ('PENDING', 'RUNNING')
        LIMIT 1
        """,
    )
    suspend fun findActive(kind: String, mediaId: String): SyncJobEntity?

    @Query("SELECT * FROM sync_jobs WHERE id = :id LIMIT 1")
    suspend fun get(id: Long): SyncJobEntity?
}

@Dao
interface AppConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: AppConfigEntity)

    @Query("SELECT * FROM app_config WHERE `key` = :key LIMIT 1")
    suspend fun get(key: String): AppConfigEntity?

    @Query("SELECT value FROM app_config WHERE `key` = :key LIMIT 1")
    suspend fun getValue(key: String): String?

    @Query("SELECT * FROM app_config")
    suspend fun getAll(): List<AppConfigEntity>
}
