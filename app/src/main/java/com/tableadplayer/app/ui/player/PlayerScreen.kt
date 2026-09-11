package com.tableadplayer.app.ui.player

import android.graphics.BitmapFactory
import android.view.ViewGroup
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.tableadplayer.app.playback.PlaybackContent

@Composable
fun PlayerScreen(
    content: PlaybackContent,
    exoPlayer: ExoPlayer?,
    onOpenAdmin: () -> Unit,
    showDemoBadge: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        when (content) {
            PlaybackContent.Idle, PlaybackContent.Empty -> {
                Text(
                    text = if (content is PlaybackContent.Empty) "No playlist" else "TableAdPlayer",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            is PlaybackContent.Image -> {
                val bitmap = remember(content.item.id, content.bytes) {
                    BitmapFactory.decodeByteArray(content.bytes, 0, content.bytes.size)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = content.item.id,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        "Unreadable image ${content.item.id}",
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            is PlaybackContent.Video -> {
                if (exoPlayer != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                useController = false
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                setShutterBackgroundColor(android.graphics.Color.BLACK)
                                keepScreenOn = true
                            }
                        },
                        update = { view ->
                            if (view.player !== exoPlayer) {
                                view.player = exoPlayer
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(96.dp)
                .pointerInput(Unit) {
                    detectTapGestures(onLongPress = { onOpenAdmin() })
                },
        )

        if (showDemoBadge) {
            Text(
                text = "DEMO",
                color = Color.White.copy(alpha = 0.35f),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
            )
        }
    }
}
