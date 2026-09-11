package com.tableadplayer.app.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Sequential looping playlist. Never freezes: bad media is skipped, total failure
 * backs off, video errors/end both advance, images use a duration timer plus grace.
 */
class PlaylistEngine(
    private val context: Context,
    parent: CoroutineScope,
) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob())
    private var loopJob: Job? = null

    private val _content = MutableStateFlow<PlaybackContent>(PlaybackContent.Idle)
    val content: StateFlow<PlaybackContent> = _content.asStateFlow()

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()

    fun start(items: List<PlaylistItem>) {
        stop()
        if (items.isEmpty()) {
            _content.value = PlaybackContent.Empty
            return
        }
        loopJob = scope.launch {
            var index = 0
            var consecutiveFailures = 0
            var loopCount = 0
            while (isActive) {
                if (PlaylistAdvance.shouldBackoff(consecutiveFailures, items.size)) {
                    _status.value = _status.value.copy(waitingRetry = true, lastError = "All items failed; backing off")
                    _content.value = PlaybackContent.Idle
                    delay(PlaylistAdvance.ALL_FAILED_BACKOFF_MS)
                    consecutiveFailures = 0
                    continue
                }
                val item = items[index]
                _status.value = _status.value.copy(
                    playingIndex = index,
                    waitingRetry = false,
                    loopCount = loopCount,
                )
                val ok = playItem(item)
                if (ok) {
                    consecutiveFailures = 0
                } else {
                    consecutiveFailures += 1
                    _status.value = _status.value.copy(
                        skipped = _status.value.skipped + 1,
                        lastError = "Skipped ${item.id}",
                    )
                }
                index = PlaylistAdvance.nextIndex(index, items.size)
                if (index == 0) loopCount += 1
            }
        }
    }

    fun stop() {
        loopJob?.cancel()
        loopJob = null
        releasePlayer()
        _content.value = PlaybackContent.Idle
    }

    private suspend fun playItem(item: PlaylistItem): Boolean {
        return when (item.kind) {
            MediaKind.IMAGE -> playImage(item)
            MediaKind.VIDEO -> playVideo(item)
        }
    }

    private suspend fun playImage(item: PlaylistItem): Boolean {
        val bytes = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(item.assetPath).use { it.readBytes() }
            }.getOrNull()
        }
        if (bytes == null || bytes.isEmpty()) return false
        _content.value = PlaybackContent.Image(item, bytes)
        val duration = (item.durationMs ?: 5_000L).coerceAtLeast(500L)
        delay(duration)
        return true
    }

    private suspend fun playVideo(item: PlaylistItem): Boolean {
        val exists = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(item.assetPath).use { true }
            }.getOrDefault(false)
        }
        if (!exists) return false

        val exo = withContext(Dispatchers.Main) {
            ExoPlayer.Builder(context).build().also { player ->
                player.playWhenReady = true
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.volume = 1f
                _player.value = player
                _content.value = PlaybackContent.Video(item, item.assetPath)
                val uri = Uri.parse("asset:///${item.assetPath}")
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
            }
        }

        return try {
            val completed = withTimeoutOrNull(PlaylistAdvance.VIDEO_MAX_MS) {
                awaitVideoTerminal(exo)
            }
            completed == true
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { releasePlayer() }
        }
    }

    private suspend fun awaitVideoTerminal(player: ExoPlayer): Boolean =
        suspendCancellableCoroutine { cont ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED && cont.isActive) {
                        player.removeListener(this)
                        cont.resume(true)
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    if (cont.isActive) {
                        player.removeListener(this)
                        cont.resume(false)
                    }
                }
            }
            player.addListener(listener)
            if (player.playerError != null && cont.isActive) {
                player.removeListener(listener)
                cont.resume(false)
            } else if (player.playbackState == Player.STATE_ENDED && cont.isActive) {
                player.removeListener(listener)
                cont.resume(true)
            }
            cont.invokeOnCancellation {
                player.removeListener(listener)
            }
        }

    private fun releasePlayer() {
        _player.value?.let { p ->
            p.release()
        }
        _player.value = null
    }
}
