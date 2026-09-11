package com.tableadplayer.app.ui.admin

import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Launches the Phase 9 admin service menu. Not exported in release; androidTest
 * still starts it via [ActivityScenario].
 */
@LargeTest
@RunWith(AndroidJUnit4::class)
class AdminActivityInstrumentedTest {
    @Test
    fun launchesAndResumes() {
        ActivityScenario.launch(AdminActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                assertTrue(activity.packageName.endsWith(".debug") || activity.packageName == "com.tableadplayer.app.debug")
            }
        }
    }
}
