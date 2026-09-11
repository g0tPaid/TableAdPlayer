package com.tableadplayer.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration

/**
 * Version 1 schema. Future versions **must** add a [Migration] or `@AutoMigration`
 * — [Room.databaseBuilder] is never given `fallbackToDestructiveMigration()`, so
 * upgrades cannot wipe the cache.
 */
@Database(
    entities = [
        DeviceEntity::class,
        MediaEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        ScheduleEntity::class,
        PlaybackEventEntity::class,
        SyncJobEntity::class,
        AppConfigEntity::class,
    ],
    version = TableAdDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(AppTypeConverters::class)
abstract class TableAdDatabase : RoomDatabase() {
    abstract fun deviceDao(): DeviceDao
    abstract fun mediaDao(): MediaDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playlistItemDao(): PlaylistItemDao
    abstract fun scheduleDao(): ScheduleDao
    abstract fun playbackEventDao(): PlaybackEventDao
    abstract fun syncJobDao(): SyncJobDao
    abstract fun appConfigDao(): AppConfigDao

    companion object {
        const val VERSION = 1
        const val NAME = "tableadplayer.db"

        /** Append [Migration] instances here. Never replace this with a destructive fallback. */
        val ALL_MIGRATIONS: Array<Migration> = emptyArray()

        fun create(context: Context): TableAdDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                TableAdDatabase::class.java,
                NAME,
            )
                .addMigrations(*ALL_MIGRATIONS)
                .build()
        }
    }
}
