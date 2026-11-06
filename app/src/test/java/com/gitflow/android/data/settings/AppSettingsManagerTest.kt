package com.gitflow.android.data.settings

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AppSettingsManagerTest {

    private val context = ApplicationProvider.getApplicationContext<Application>()

    @Test
    fun writesThroughAnotherInstance_reachTheFlows() {
        val observed = AppSettingsManager(context)
        val writer = AppSettingsManager(context)

        writer.setGraphPreset("Wide")
        writer.setDarkMode(AppSettingsManager.DARK_MODE_DARK)
        writer.addPreviewExtension("rs")
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals("Wide", observed.graphPresetFlow.value)
        assertEquals(AppSettingsManager.DARK_MODE_DARK, observed.darkModeFlow.value)
        assertTrue("rs" in observed.previewExtensionsFlow.value)
    }

    @Test
    fun recentCloneParents_keepOrderWithoutDuplicatesAndAtMostFive() {
        val settings = AppSettingsManager(context)

        listOf("/a", "/b", "/c", "/d", "/e", "/f", "/b").forEach { settings.rememberCloneParent(it) }

        assertEquals(listOf("/b", "/f", "/e", "/d", "/c"), settings.getRecentCloneParents())
    }
}
