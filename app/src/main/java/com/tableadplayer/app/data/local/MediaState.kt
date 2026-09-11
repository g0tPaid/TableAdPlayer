package com.tableadplayer.app.data.local

/**
 * Disk/cache lifecycle for a [MediaEntity]. Playback may use a file only when [READY].
 */
enum class MediaState {
    /** Known to the catalog; bytes are not on disk yet. */
    REMOTE,

    /** Atomic `*.part` write in progress. */
    DOWNLOADING,

    /** Checksum verified; [MediaEntity.localPath] is playable. */
    READY,

    /** Last download or verify failed. */
    FAILED,

    /** Past retention / server expiry; file may still exist until cleanup. */
    EXPIRED,

    /** File removed (or never stored); row may remain as a tombstone. */
    DELETED,
}
