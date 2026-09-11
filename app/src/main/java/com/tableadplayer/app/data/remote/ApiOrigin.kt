package com.tableadplayer.app.data.remote

import java.net.URI

/**
 * [BuildConfig.API_BASE_URL] is injected at build time. The default placeholder
 * host is never contacted — DEMO MODE uses [FixtureTableAdApi] instead.
 */
object ApiOrigin {
    const val PLACEHOLDER_HOST = "api.example.invalid"

    fun isPlaceholder(baseUrl: String): Boolean {
        val host = runCatching { URI(baseUrl.trim()).host?.lowercase() }.getOrNull().orEmpty()
        return host.isBlank() ||
            host == PLACEHOLDER_HOST ||
            host.endsWith(".example.invalid")
    }

    fun usesLiveNetwork(baseUrl: String): Boolean = !isPlaceholder(baseUrl)

    /**
     * Turns a playlist media URL into an absolute HTTP(S) URL against [baseUrl].
     * Absolute `http(s)://` values are returned unchanged.
     */
    fun resolveMediaUrl(baseUrl: String, url: String): String {
        val trimmed = url.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return trimmed
        }
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        val rel = trimmed.removePrefix("/")
        return base + rel
    }
}
