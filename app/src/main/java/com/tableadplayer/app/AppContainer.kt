package com.tableadplayer.app

import android.content.Context
import com.tableadplayer.app.BuildConfig
import com.tableadplayer.app.core.device.DeviceIdentity
import com.tableadplayer.app.data.cache.MediaCache
import com.tableadplayer.app.data.cache.MediaFileStore
import com.tableadplayer.app.data.local.TableAdDatabase
import com.tableadplayer.app.data.remote.ApiFactory
import com.tableadplayer.app.data.remote.ApiOrigin
import com.tableadplayer.app.data.remote.DeviceAuthInterceptor
import com.tableadplayer.app.data.remote.DeviceAuthStore
import com.tableadplayer.app.data.remote.FixtureTableAdApi
import com.tableadplayer.app.data.remote.TableAdApi
import com.tableadplayer.app.data.repo.ContentRepository
import com.tableadplayer.app.data.repo.DeviceRepository
import com.tableadplayer.app.data.repo.ReportingRepository
import com.tableadplayer.app.data.repo.SyncRepository
import com.tableadplayer.app.reporting.ReportingQueue
import com.tableadplayer.app.sync.FreeSpaceGuard
import com.tableadplayer.app.sync.MediaDownloader
import com.tableadplayer.app.sync.PlaylistIngestor
import com.tableadplayer.app.sync.SyncCoordinator
import okhttp3.OkHttpClient

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: TableAdDatabase = TableAdDatabase.create(appContext)
    val mediaFiles: MediaFileStore = MediaFileStore(appContext.filesDir)
    val mediaCache: MediaCache = MediaCache(database, mediaFiles)
    val deviceIdentity: DeviceIdentity = DeviceIdentity(appContext)
    val deviceAuth: DeviceAuthStore = DeviceAuthStore()

    val liveApi: Boolean = ApiOrigin.usesLiveNetwork(BuildConfig.API_BASE_URL)

    val httpClient: OkHttpClient = ApiFactory.httpClient(
        debug = BuildConfig.DEBUG,
        auth = DeviceAuthInterceptor(
            deviceId = { deviceAuth.deviceId },
            token = { deviceAuth.token },
        ),
    )

    val api: TableAdApi = if (liveApi) {
        ApiFactory.create(
            baseUrl = BuildConfig.API_BASE_URL,
            debug = BuildConfig.DEBUG,
            client = httpClient,
        )
    } else {
        FixtureTableAdApi.fromAssets(appContext.assets)
    }

    val deviceRepository: DeviceRepository = DeviceRepository(
        api = api,
        db = database,
        identity = deviceIdentity,
        auth = deviceAuth,
    )

    val contentRepository: ContentRepository = ContentRepository(
        api = api,
        devices = deviceRepository,
    )

    val reportingRepository: ReportingRepository = ReportingRepository(
        api = api,
        db = database,
        devices = deviceRepository,
    )

    val reportingQueue: ReportingQueue = ReportingQueue()

    private val freeSpace = FreeSpaceGuard { appContext.filesDir.usableSpace }

    private val downloader = MediaDownloader(
        client = httpClient,
        cache = mediaCache,
        baseUrl = BuildConfig.API_BASE_URL,
        freeSpace = freeSpace,
    )

    private val ingestor = PlaylistIngestor(database, mediaCache)

    private val coordinator = SyncCoordinator(
        db = database,
        cache = mediaCache,
        devices = deviceRepository,
        content = contentRepository,
        reporting = reportingRepository,
        downloader = downloader,
        ingestor = ingestor,
        freeSpace = freeSpace,
    )

    val syncRepository: SyncRepository = SyncRepository(coordinator)
}
