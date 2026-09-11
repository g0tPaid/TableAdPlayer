package com.tableadplayer.app.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tableadplayer.app.playback.DemoPlaylistLoader
import com.tableadplayer.app.playback.EngineStatus
import com.tableadplayer.app.playback.PlaybackContent
import com.tableadplayer.app.playback.PlaylistEngine
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val engine = PlaylistEngine(application, viewModelScope)

    val content: StateFlow<PlaybackContent> = engine.content
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlaybackContent.Idle)

    val status: StateFlow<EngineStatus> = engine.status
        .stateIn(viewModelScope, SharingStarted.Eagerly, EngineStatus())

    val exoPlayer = engine.player

    init {
        val items = runCatching { DemoPlaylistLoader.load(application) }.getOrDefault(emptyList())
        engine.start(items)
    }

    override fun onCleared() {
        engine.stop()
        super.onCleared()
    }
}
