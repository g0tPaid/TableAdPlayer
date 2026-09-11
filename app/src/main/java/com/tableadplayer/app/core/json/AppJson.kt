package com.tableadplayer.app.core.json

import kotlinx.serialization.json.Json

object AppJson {
    val pretty: Json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    val compact: Json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}
