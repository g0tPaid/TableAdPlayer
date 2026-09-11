package com.tableadplayer.app.sync

import com.tableadplayer.app.data.cache.MediaCache
import com.tableadplayer.app.data.local.MediaEntity
import com.tableadplayer.app.data.remote.ApiOrigin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MediaDownloader(
    private val client: OkHttpClient,
    private val cache: MediaCache,
    private val baseUrl: String,
    private val freeSpace: FreeSpaceGuard,
    private val reserveBytes: () -> Long = { FreeSpacePolicy.DEFAULT_RESERVE_BYTES },
) {
    sealed class Result {
        data object Success : Result()
        data object SkippedLowSpace : Result()
        data object SkippedPlaceholder : Result()
        data class Failed(val reason: String) : Result()
    }

    suspend fun download(media: MediaEntity, essential: Boolean): Result {
        val skipped = MediaDownloadPolicy.preflight(
            baseUrl = baseUrl,
            remoteUrl = media.remoteUrl,
            freeBytes = freeSpace.usable(),
            essential = essential,
            reserveBytes = reserveBytes(),
        )
        if (skipped != null) return skipped
        val url = ApiOrigin.resolveMediaUrl(baseUrl, media.remoteUrl)
        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(url).get().build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@use Result.Failed("http ${response.code}")
                    }
                    val body = response.body ?: return@use Result.Failed("empty body")
                    val ok = cache.ingest(
                        mediaId = media.id,
                        type = media.type,
                        version = media.version,
                        checksum = media.checksum,
                        remoteUrl = media.remoteUrl,
                        input = body.byteStream(),
                        mimeType = media.mimeType,
                    )
                    if (ok) Result.Success else Result.Failed("ingest failed")
                }
            }.getOrElse { err ->
                Result.Failed(err.message ?: err.javaClass.simpleName)
            }
        }
    }
}
