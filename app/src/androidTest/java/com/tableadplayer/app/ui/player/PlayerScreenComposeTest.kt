package com.tableadplayer.app.ui.player

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tableadplayer.app.playback.PlaybackContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Compose stub for the DEMO player chrome (idle + DEMO badge). Requires a device.
 */
@RunWith(AndroidJUnit4::class)
class PlayerScreenComposeTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun idleShowsTitleAndDemoBadge() {
        compose.setContent {
            PlayerScreen(
                content = PlaybackContent.Idle,
                exoPlayer = null,
                onOpenAdmin = {},
                showDemoBadge = true,
            )
        }
        compose.onNodeWithText("TableAdPlayer").assertIsDisplayed()
        compose.onNodeWithText("DEMO").assertIsDisplayed()
    }
}
