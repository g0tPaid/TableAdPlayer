package com.tableadplayer.app.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tableadplayer.app.TableAdPlayerApp
import com.tableadplayer.app.core.crash.Watchdog
import com.tableadplayer.app.playback.EngineStatus
import com.tableadplayer.app.playback.PlaybackContent
import com.tableadplayer.app.playback.PlaylistEngine
import com.tableadplayer.app.playback.PlaylistResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PlaylistEngine(application, viewModelScope)
    private val reloadMutex = Mutex()
    private var loadedPlaylistId: String? = null

    val content: StateFlow<PlaybackContent> = engine.content
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackContent.Idle)

    val status: StateFlow<EngineStatus> = engine.status
        .stateIn(viewModelScope, SharingStarted.Eagerly, EngineStatus())

    val exoPlayer = engine.player

    private val _demo = MutableStateFlow(true)
    val demo: StateFlow<Boolean> = _demo.asStateFlow()

    private val _playlistId = MutableStateFlow<String?>(null)
    val playlistId: StateFlow<String?> = _playlistId.asStateFlow()

    init {
        engine.onItemStarted = { item ->
            Watchdog.noteHealthyPlayback(getApplication())
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    (getApplication<Application>() as? TableAdPlayerApp)
                        ?.container
                        ?.mediaCache
                        ?.markAccessed(item.id)
                }
            }
        }
        engine.onPlaybackEvent = { event ->
            val app = getApplication<Application>() as? TableAdPlayerApp
            val queue = app?.container?.reportingQueue
            if (queue != null) {
                queue.offer(event.copy(playlistId = event.playlistId ?: loadedPlaylistId))
            }
        }
        reload()
    }

    fun reload(force: Boolean = false) {
        viewModelScope.launch {
            reloadMutex.withLock {
                val resolved = runCatching { PlaylistResolver.resolve(getApplication()) }
                    .getOrDefault(
                        PlaylistResolver.Resolved(emptyList(), demo = true, playlistId = "demo-local"),
                    )
                if (!force && resolved.playlistId == loadedPlaylistId && loadedPlaylistId != null) {
                    return@withLock
                }
                loadedPlaylistId = resolved.playlistId
                _playlistId.value = resolved.playlistId
                _demo.value = resolved.demo
                engine.start(resolved.items)
            }
        }
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }
}
