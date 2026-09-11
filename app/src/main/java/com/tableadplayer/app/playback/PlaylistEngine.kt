package com.tableadplayer.app.playback

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.tableadplayer.app.data.local.PlaybackEventType
import com.tableadplayer.app.reporting.PlaybackReporting
import com.tableadplayer.app.reporting.QueuedEvent
import com.tableadplayer.app.scheduler.ScheduleEvaluator
import java.io.File
import java.time.ZonedDateTime
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Sequential looping playlist. Never freezes: bad media is skipped, total failure
 * backs off, video errors/end both advance, images use a duration timer plus grace.
 * Items outside their [com.tableadplayer.app.scheduler.ScheduleWindow] are skipped
 * without counting as a media failure.
 */
class PlaylistEngine(
    private val context: Context,
    parent: CoroutineScope,
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(ZoneOffset.UTC) },
) {
    private val scope = CoroutineScope(parent.coroutineContext + SupervisorJob())
    private var loopJob: Job? = null

    var onItemStarted: (PlaylistItem) -> Unit = {}

    /**
     * Non-suspending telemetry sink. The engine never waits on this callback;
     * exceptions are swallowed. Typical sink: [com.tableadplayer.app.reporting.ReportingQueue.offer].
     */
    var onPlaybackEvent: (QueuedEvent) -> Unit = {}

    private val _content = MutableStateFlow<PlaybackContent>(PlaybackContent.Idle)
    val content: StateFlow<PlaybackContent> = _content.asStateFlow()

    private val _status = MutableStateFlow(EngineStatus())
    val status: StateFlow<EngineStatus> = _status.asStateFlow()

    private val _player = MutableStateFlow<ExoPlayer?>(null)
    val player: StateFlow<ExoPlayer?> = _player.asStateFlow()
    private val playerRef = AtomicReference<ExoPlayer?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())

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
                val now = clock()
                if (items.none { ScheduleEvaluator.isActive(it.schedule, now) }) {
                    _status.value = _status.value.copy(
                        waitingRetry = true,
                        lastError = "Outside schedule window",
                    )
                    _content.value = PlaybackContent.Idle
                    delay(ScheduleEvaluator.POLL_MS)
                    continue
                }
                if (PlaylistAdvance.shouldBackoff(consecutiveFailures, items.size)) {
                    _status.value = _status.value.copy(waitingRetry = true, lastError = "All items failed; backing off")
                    _content.value = PlaybackContent.Idle
                    delay(PlaylistAdvance.ALL_FAILED_BACKOFF_MS)
                    consecutiveFailures = 0
                    continue
                }
                val item = items[index]
                if (!ScheduleEvaluator.isActive(item.schedule, clock())) {
                    emitEvent(PlaybackEventType.SKIP, item, "outside_schedule")
                    index = PlaylistAdvance.nextIndex(index, items.size)
                    if (index == 0) loopCount += 1
                    continue
                }
                _status.value = _status.value.copy(
                    playingIndex = index,
                    waitingRetry = false,
                    loopCount = loopCount,
                )
                runCatching { onItemStarted(item) }
                emitEvent(PlaybackEventType.PLAY, item)
                val outcome = playItem(item)
                if (outcome is PlayOutcome.Played) {
                    consecutiveFailures = 0
                    emitEvent(PlaybackEventType.COMPLETED, item)
                } else {
                    val reason = (outcome as? PlayOutcome.Failed)?.reason ?: "skipped"
                    consecutiveFailures += 1
                    _status.value = _status.value.copy(
                        skipped = _status.value.skipped + 1,
                        lastError = "Skipped ${item.id}",
                    )
                    emitEvent(PlaybackEventType.ERROR, item, reason)
                    emitEvent(PlaybackEventType.SKIP, item, reason)
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

    private fun emitEvent(type: String, item: PlaylistItem, detail: String? = null) {
        PlaybackReporting.emitIsolated(
            sink = onPlaybackEvent,
            event = QueuedEvent(
                type = type,
                itemId = item.id,
                atEpochMs = System.currentTimeMillis(),
                detail = detail,
            ),
        )
    }

    private suspend fun playItem(item: PlaylistItem): PlayOutcome {
        return when (item.kind) {
            MediaKind.IMAGE -> playImage(item)
            MediaKind.VIDEO -> playVideo(item)
        }
    }

    private suspend fun playImage(item: PlaylistItem): PlayOutcome {
        val bytes = withContext(Dispatchers.IO) { readBytes(item.source) }
        if (bytes == null || bytes.isEmpty()) return PlayOutcome.Failed("unreadable")
        _content.value = PlaybackContent.Image(item, bytes)
        val duration = (item.durationMs ?: 5_000L).coerceAtLeast(500L)
        delay(duration)
        return PlayOutcome.Played
    }

    private suspend fun playVideo(item: PlaylistItem): PlayOutcome {
        val uri = withContext(Dispatchers.IO) { videoUri(item.source) }
            ?: return PlayOutcome.Failed("missing_uri")

        val exo = withContext(Dispatchers.Main) {
            ExoPlayer.Builder(context).build().also { player ->
                player.playWhenReady = true
                player.repeatMode = Player.REPEAT_MODE_OFF
                player.volume = 1f
                adoptPlayer(player)
                _content.value = PlaybackContent.Video(item, uri.toString())
                player.setMediaItem(MediaItem.fromUri(uri))
                player.prepare()
            }
        }

        return try {
            val completed = withTimeoutOrNull(PlaylistAdvance.VIDEO_MAX_MS) {
                awaitVideoTerminal(exo)
            }
            when (completed) {
                true -> PlayOutcome.Played
                false -> PlayOutcome.Failed("player_error")
                null -> PlayOutcome.Failed("timeout")
            }
        } finally {
            withContext(NonCancellable + Dispatchers.Main) { releasePlayer() }
        }
    }

    private fun readBytes(source: MediaSource): ByteArray? {
        return runCatching {
            when (source) {
                is MediaSource.Asset -> context.assets.open(source.path).use { it.readBytes() }
                is MediaSource.CachedFile -> {
                    val file = File(source.absolutePath)
                    if (!file.isFile) null else file.readBytes()
                }
            }
        }.getOrNull()
    }

    private fun videoUri(source: MediaSource): Uri? {
        return when (source) {
            is MediaSource.Asset -> {
                val exists = runCatching {
                    context.assets.open(source.path).use { true }
                }.getOrDefault(false)
                if (!exists) null else Uri.parse("asset:///${source.path}")
            }
            is MediaSource.CachedFile -> {
                val file = File(source.absolutePath)
                if (!file.isFile) null else file.toUri()
            }
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

    private fun adoptPlayer(player: ExoPlayer) {
        releasePlayer()
        playerRef.set(player)
        _player.value = player
    }

    /**
     * Idempotent. Safe if [stop] races with a video `finally` block.
     * ExoPlayer is released on the main thread it was created on.
     */
    private fun releasePlayer() {
        val current = playerRef.getAndSet(null)
        _player.value = null
        if (current == null) return
        val release = { current.release() }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            release()
        } else {
            mainHandler.post(release)
        }
    }
}
