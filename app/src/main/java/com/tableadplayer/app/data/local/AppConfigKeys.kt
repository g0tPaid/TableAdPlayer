package com.tableadplayer.app.data.local

object AppConfigKeys {
    const val ACTIVE_PLAYLIST_ID = "active_playlist_id"
    const val TIMEZONE = "timezone"
    const val HEARTBEAT_INTERVAL_SEC = "heartbeat_interval_sec"
    const val SYNC_INTERVAL_SEC = "sync_interval_sec"
    const val CACHE_MAX_BYTES = "cache_max_bytes"
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
