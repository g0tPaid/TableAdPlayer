package com.tableadplayer.app.data.remote

import com.tableadplayer.app.BuildConfig
import com.tableadplayer.app.core.json.AppJson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

object ApiFactory {
    fun httpClient(
        debug: Boolean = BuildConfig.DEBUG,
        auth: DeviceAuthInterceptor? = null,
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = if (debug) HttpLoggingInterceptor.Level.BASIC else HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .apply { if (auth != null) addInterceptor(auth) }
            .addInterceptor(logging)
            .build()
    }

    fun create(
        baseUrl: String = BuildConfig.API_BASE_URL,
        debug: Boolean = BuildConfig.DEBUG,
        auth: DeviceAuthInterceptor? = null,
        client: OkHttpClient = httpClient(debug, auth),
    ): TableAdApi {
        val contentType = "application/json".toMediaType()
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(AppJson.compact.asConverterFactory(contentType))
            .build()
            .create(TableAdApi::class.java)
    }
}
