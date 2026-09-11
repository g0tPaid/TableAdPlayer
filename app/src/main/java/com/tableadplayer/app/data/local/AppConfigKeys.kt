package com.tableadplayer.app.data.local

object AppConfigKeys {
    const val ACTIVE_PLAYLIST_ID = "active_playlist_id"
    const val TIMEZONE = "timezone"
    const val HEARTBEAT_INTERVAL_SEC = "heartbeat_interval_sec"
    const val SYNC_INTERVAL_SEC = "sync_interval_sec"
    const val CACHE_MAX_BYTES = "cache_max_bytes"
    const val DEVICE_STATUS = "device_status"
    const val DEVICE_TOKEN = "device_token"
    const val SERVER_URL = "server_url"
    const val LAST_HEARTBEAT_AT = "last_heartbeat_at"
    const val FREE_SPACE_RESERVE_BYTES = "free_space_reserve_bytes"
    const val SCREEN_BRIGHTNESS = "screen_brightness"
}

object DeviceStatus {
    const val UNREGISTERED = "UNREGISTERED"
    const val REGISTERED = "REGISTERED"
}

object PlaybackEventType {
    const val HEARTBEAT = "heartbeat"
    const val PLAY = "play"
    const val SKIP = "skip"
    const val ERROR = "error"
    const val COMPLETED = "completed"
}

object MediaOrigin {
    const val REMOTE = "remote"
    const val ASSET = "asset"
}

object SyncJobKind {
    const val PLAYLIST_FETCH = "PLAYLIST_FETCH"
    const val MEDIA_DOWNLOAD = "MEDIA_DOWNLOAD"
    const val CLEANUP = "CLEANUP"
    const val HEARTBEAT = "HEARTBEAT"
}

object SyncJobStatus {
    const val PENDING = "PENDING"
    const val RUNNING = "RUNNING"
    const val SUCCESS = "SUCCESS"
    const val FAILED = "FAILED"
}
