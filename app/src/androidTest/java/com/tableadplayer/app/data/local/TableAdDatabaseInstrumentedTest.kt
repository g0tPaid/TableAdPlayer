package com.tableadplayer.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * In-memory Room smoke. Confirms schema version 1, no destructive fallback
 * required, and the active-playlist media-id query used by cache cleanup.
 */
@RunWith(AndroidJUnit4::class)
class TableAdDatabaseInstrumentedTest {
    private lateinit var db: TableAdDatabase

    @Before
    fun openDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TableAdDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun schemaVersionIsOne() {
        assertEquals(1, TableAdDatabase.VERSION)
        assertTrue(TableAdDatabase.ALL_MIGRATIONS.isEmpty())
    }

    @Test
    fun upsertDeviceAndConfig() = runBlocking {
        val now = 1_700_000_000_000L
        db.deviceDao().upsert(
            DeviceEntity(
                id = "TABLE-abcd1234",
                displayName = "Test Tablet",
                timezone = "UTC",
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.appConfigDao().upsert(
            AppConfigEntity(AppConfigKeys.DEVICE_STATUS, DeviceStatus.REGISTERED, now),
        )
        assertEquals("TABLE-abcd1234", db.deviceDao().get("TABLE-abcd1234")?.id)
        assertEquals(DeviceStatus.REGISTERED, db.appConfigDao().getValue(AppConfigKeys.DEVICE_STATUS))
    }

    @Test
    fun activePlaylistMediaIdsProtectCleanup() = runBlocking {
        val now = 1L
        db.mediaDao().upsert(
            MediaEntity(
                id = "keep-me",
                type = "image",
                state = MediaState.READY,
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.mediaDao().upsert(
            MediaEntity(
                id = "orphan",
                type = "image",
                state = MediaState.READY,
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.playlistDao().upsert(
            PlaylistEntity(
                id = "demo-local",
                revision = 1L,
                name = "DEMO",
                isActive = true,
                createdAt = now,
                updatedAt = now,
            ),
        )
        db.playlistItemDao().upsert(
            PlaylistItemEntity(
                playlistId = "demo-local",
                mediaId = "keep-me",
                position = 0,
                createdAt = now,
                updatedAt = now,
            ),
        )
        val active = db.playlistItemDao().mediaIdsForActivePlaylist()
        assertEquals(listOf("keep-me"), active)
        assertNotNull(db.mediaDao().get("orphan"))
    }
}
