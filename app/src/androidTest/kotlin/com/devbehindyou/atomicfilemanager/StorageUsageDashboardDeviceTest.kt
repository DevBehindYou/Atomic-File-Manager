package com.devbehindyou.atomicfilemanager

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.util.Log
import android.view.WindowManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.data.volume.LocalStorageUsageSource
import com.devbehindyou.atomicfilemanager.data.volume.StorageVolumes
import com.devbehindyou.atomicfilemanager.domain.model.StorageUsageCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.max

/** Reads real mounted metadata only; no grants, preferences or user files are changed. */
@RunWith(AndroidJUnit4::class)
class StorageUsageDashboardDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun mountedCapacityMatchesNativeFilesystemStatsIncludingRemovableStorage() =
        runBlocking(Dispatchers.IO) {
            val context = compose.activity.applicationContext
            val manager = checkNotNull(context.getSystemService(StorageManager::class.java))
            val nativeVolumes =
                manager.storageVolumes.filter {
                    it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY
                }
            assertTrue("This device must have mounted internal storage", nativeVolumes.any { it.isPrimary })
            val appDirectories = if (Build.VERSION.SDK_INT < 30) context.getExternalFilesDirs(null) else emptyArray()
            val probes =
                nativeVolumes.mapNotNull { volume ->
                    val directory =
                        if (Build.VERSION.SDK_INT >= 30) {
                            volume.directory
                        } else {
                            appDirectories.filterNotNull().firstOrNull { manager.getStorageVolume(it) == volume }
                        }
                    directory?.takeIf { it.canRead() } ?: context.filesDir.takeIf { volume.isPrimary }
                }.map { it.canonicalFile }.distinct()
            val before = readNativeCapacity(probes)
            val source =
                LocalStorageUsageSource(
                    volumesNow = { StorageVolumes.enumerate(context) },
                    primaryPath = { context.filesDir.absolutePath },
                )
            val actual = source.plan(StorageVolumes.enumerate(context)).capacity
            val after = readNativeCapacity(probes)
            assertEquals("Mounted removable storage must participate too", nativeVolumes.size, actual.mountedVolumes)
            assertEquals(
                "Unreadable mounted volumes remain explicit",
                nativeVolumes.size - probes.size,
                actual.unknownVolumes,
            )
            assertEquals("Total capacity must remain stable while sampling", before.first, after.first)
            assertEquals("Dashboard total must sum native filesystem totals", before.first, actual.totalBytes)
            val tolerance = max(64L * 1024 * 1024, actual.totalBytes / 1_000)
            val low = minOf(before.second, after.second) - tolerance
            val high = maxOf(before.second, after.second) + tolerance
            assertTrue(
                "Free capacity should match native samples within background-write tolerance",
                actual.freeBytes in low..high,
            )
            assertEquals(actual.totalBytes - actual.freeBytes, actual.usedBytes)
        }

    @Test
    fun storageStaysLightweightAndAnalysisRequiresAnExplicitFileTypeRequest() {
        val application = compose.activity.application
        val lifecycleLog =
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(
                    activity: Activity,
                    state: Bundle?,
                ) = lifecycle("created", activity)

                override fun onActivityStarted(activity: Activity) = lifecycle("started", activity)

                override fun onActivityResumed(activity: Activity) = lifecycle("resumed", activity)

                override fun onActivityPaused(activity: Activity) = lifecycle("paused", activity)

                override fun onActivityStopped(activity: Activity) = lifecycle("stopped", activity)

                override fun onActivitySaveInstanceState(
                    activity: Activity,
                    state: Bundle,
                ) = lifecycle("saved", activity)

                override fun onActivityDestroyed(activity: Activity) = lifecycle("destroyed", activity)
            }
        var originallyKeptAwake = false
        var keepAwakeApplied = false
        var lifecycleLogRegistered = false
        try {
            application.registerActivityLifecycleCallbacks(lifecycleLog)
            lifecycleLogRegistered = true
            compose.activityRule.scenario.onActivity {
                originallyKeptAwake = it.window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON != 0
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                keepAwakeApplied = true
            }
            compose.waitUntil(15_000) { compose.onAllNodesWithTag("tab_storage").fetchSemanticsNodes().isNotEmpty() }
            stage("open-lightweight-storage")
            compose.onNodeWithTag("tab_storage").performClick()
            compose.onNodeWithTag("storage_usage_dashboard").assertDoesNotExist()
            compose.onNodeWithTag("storage_capacity_summary").performScrollTo().assertIsDisplayed()
            openAnalysis()
            assertAnalysisIsUnrequested()
            stage("request-file-types")
            compose.onNodeWithTag("refresh_storage_usage").performScrollTo().assertIsEnabled().performClick()
            awaitTerminalBreakdown()
            stage("check-eleven-categories")
            for (category in StorageUsageCategory.entries) {
                compose.onNodeWithTag("storage_usage_${category.name}").performScrollTo().assertIsDisplayed()
            }
            stage("refresh-file-types")
            compose.onNodeWithTag("refresh_storage_usage").performScrollTo().assertIsEnabled().performClick()
            // A small directory can finish between frames; do not require seeing a transient spinner.
            compose.waitForIdle()
            awaitTerminalBreakdown()
            stage("return-to-storage")
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("storage_screen").assertIsDisplayed()
            compose.onNodeWithTag("storage_usage_dashboard").assertDoesNotExist()
            openAnalysis()
            assertAnalysisIsUnrequested()
            stage("recreate-unrequested-analysis")
            recreateActivity()
            compose.activityRule.scenario.onActivity {
                it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            assertAnalysisIsUnrequested()
            stage("leave-analysis")
            compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("home_screen").assertIsDisplayed()
            compose.onNodeWithTag("storage_usage_dashboard").assertDoesNotExist()
        } finally {
            if (lifecycleLogRegistered) application.unregisterActivityLifecycleCallbacks(lifecycleLog)
            runCatching {
                compose.activityRule.scenario.onActivity {
                    if (keepAwakeApplied && !originallyKeptAwake) {
                        it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }
            }
            runCatching { compose.onNodeWithTag("tab_home").performClick() }
        }
    }

    private fun openAnalysis() {
        stage("open-analysis")
        compose.onNodeWithTag("open_storage_intelligence_button").performScrollTo().performClick()
        compose.onNodeWithTag("storage_intelligence_screen").assertIsDisplayed()
    }

    private fun assertAnalysisIsUnrequested() {
        compose.waitUntil(15_000) {
            val button = compose.onAllNodesWithTag("refresh_storage_usage").fetchSemanticsNodes().singleOrNull()
            button != null && !button.config.contains(SemanticsProperties.Disabled)
        }
        compose.onNodeWithTag("storage_intelligence_screen").assertIsDisplayed()
        compose.onNodeWithTag("refresh_storage_usage").performScrollTo().assertIsEnabled()
            .assertContentDescriptionEquals("Analyze file types")
        compose.onNodeWithTag("storage_readable_total").assertDoesNotExist()
        StorageUsageCategory.entries.forEach {
            compose.onNodeWithTag("storage_usage_${it.name}").assertDoesNotExist()
        }
    }

    private fun stage(name: String) {
        Log.i("StorageUsageTest", "stage=$name scenario=${compose.activityRule.scenario.state}")
    }

    private fun lifecycle(
        event: String,
        activity: Activity,
    ) {
        if (activity is MainActivity) {
            Log.i(
                "StorageUsageTest",
                "activity=$event focus=${activity.hasWindowFocus()} finishing=${activity.isFinishing}",
            )
        }
    }

    private fun readNativeCapacity(probes: List<File>): Pair<Long, Long> {
        val samples = probes.map { StatFs(it.absolutePath) }
        return samples.sumOf { it.totalBytes } to samples.sumOf { it.availableBytes }
    }

    private fun awaitTerminalBreakdown() {
        compose.waitUntil(80_000) {
            val node = compose.onAllNodesWithTag("storage_usage_status").fetchSemanticsNodes().singleOrNull()
            val text = node?.config?.get(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty()
            text.startsWith("Partial results") || text.startsWith("Readable-folder scan complete")
        }
        compose.onNodeWithTag("storage_readable_total").assertExists()
        compose.onNodeWithTag("refresh_storage_usage").assertIsEnabled()
    }

    private fun recreateActivity() {
        compose.waitForIdle()
        val worker = Executors.newSingleThreadExecutor()
        try {
            val recreated = worker.submit { compose.activityRule.scenario.recreate() }
            compose.waitUntil(15_000) { recreated.isDone }
            recreated.get()
        } finally {
            worker.shutdownNow()
        }
        compose.waitForIdle()
    }
}
