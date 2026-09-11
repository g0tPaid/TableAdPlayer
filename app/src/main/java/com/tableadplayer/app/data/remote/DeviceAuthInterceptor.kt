package com.tableadplayer.app.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/**
 * Device auth for the operator API. No Play Services, no Google account.
 * The backend may treat [HEADER_DEVICE_TOKEN] as optional until register succeeds.
 */
class DeviceAuthInterceptor(
    private val deviceId: () -> String,
    private val token: () -> String?,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val id = deviceId().trim()
        val bearer = token()?.trim().orEmpty()
        val request = chain.request().newBuilder().apply {
            if (id.isNotEmpty()) header(HEADER_DEVICE_ID, id)
            if (bearer.isNotEmpty()) header(HEADER_DEVICE_TOKEN, bearer)
        }.build()
        return chain.proceed(request)
    }

    companion object {
        const val HEADER_DEVICE_ID = "X-Device-Id"
        const val HEADER_DEVICE_TOKEN = "X-Device-Token"
    }
}

/** Mutable process-wide credentials used by [DeviceAuthInterceptor]. */
class DeviceAuthStore {
    @Volatile
    var deviceId: String = ""

    @Volatile
    var token: String? = null
}
