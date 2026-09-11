package com.tableadplayer.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke: the debug APK is the instrumented package. Full player/admin UI
 * coverage needs a device or 800×1280 emulator — see TESTING.md.
 */
@RunWith(AndroidJUnit4::class)
class DebugPackageInstrumentedTest {
    @Test
    fun debugApplicationId() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.tableadplayer.app.debug", context.packageName)
        assertTrue(context.filesDir.isDirectory)
    }
}
