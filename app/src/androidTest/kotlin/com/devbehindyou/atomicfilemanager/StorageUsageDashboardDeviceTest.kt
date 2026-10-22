package com.devbehindyou.atomicfilemanager

import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
            val nativeVolumes = manager.storageVolumes.filter { it.state == Environment.MEDIA_MOUNTED }
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
    fun actualStorageTabShowsEveryCategoryRefreshesAndSurvivesNavigationAndRecreation() {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag("tab_storage").fetchSemanticsNodes().isNotEmpty() }
        try {
            compose.onNodeWithTag("tab_storage").performClick()
            awaitTerminalBreakdown()
            compose.onNodeWithTag("storage_capacity_summary").performScrollTo().assertIsDisplayed()
            for (category in StorageUsageCategory.entries) {
                compose.onNodeWithTag("storage_usage_${category.name}").performScrollTo().assertIsDisplayed()
            }
            compose.onNodeWithTag("refresh_storage_usage").performScrollTo().assertIsEnabled().performClick()
            // A small directory can finish between frames; do not require seeing a transient spinner.
            compose.waitForIdle()
            awaitTerminalBreakdown()
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("storage_usage_dashboard").assertDoesNotExist()
            compose.onNodeWithTag("tab_storage").performClick()
            awaitTerminalBreakdown()
            recreateActivity()
            compose.onNodeWithTag("storage_screen").assertIsDisplayed()
            compose.onNodeWithTag("storage_usage_dashboard").assertExists()
            // Recreation can legitimately start a new scan; navigation away must remain responsive.
            compose.onNodeWithTag("tab_home").performClick()
            compose.onNodeWithTag("home_screen").assertIsDisplayed()
            compose.onNodeWithTag("storage_usage_dashboard").assertDoesNotExist()
        } finally {
            runCatching { compose.onNodeWithTag("tab_home").performClick() }
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
