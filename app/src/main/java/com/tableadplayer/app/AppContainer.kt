package com.tableadplayer.app

import android.content.Context
import com.tableadplayer.app.core.device.DeviceIdentity
import com.tableadplayer.app.data.cache.MediaCache
import com.tableadplayer.app.data.cache.MediaFileStore
import com.tableadplayer.app.data.local.TableAdDatabase

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: TableAdDatabase = TableAdDatabase.create(appContext)
    val mediaFiles: MediaFileStore = MediaFileStore(appContext.filesDir)
    val mediaCache: MediaCache = MediaCache(database, mediaFiles)
    val deviceIdentity: DeviceIdentity = DeviceIdentity(appContext)
}
