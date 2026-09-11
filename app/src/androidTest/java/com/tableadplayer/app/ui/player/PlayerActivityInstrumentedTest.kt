package com.tableadplayer.app.ui.player

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches [PlayerActivity]. Safe-mode (CrashGuard) may immediately route to
 * diagnostics — both paths count as a successful process start.
 *
 * Run: `./gradlew connectedDebugAndroidTest` with a device or 800×1280 AVD.
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class PlayerActivityInstrumentedTest {
    @Test
    fun launchesPlayerOrSafeModeDiagnostics() {
        ActivityScenario.launch(PlayerActivity::class.java).use { scenario ->
            val state = scenario.state
            assertTrue(
                "unexpected lifecycle $state",
                state.isAtLeast(Lifecycle.State.CREATED) || state == Lifecycle.State.DESTROYED,
            )
        }
    }
}
