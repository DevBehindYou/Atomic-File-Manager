package com.devbehindyou.atomicfilemanager

import android.graphics.Bitmap
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileOptionsVisualCaptureTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun capturesFileOptionsAndItsSubmenus() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("captureAtomicFileOptions") == "true")
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val emulatorCheck = instrumentation.uiAutomation.executeShellCommand("getprop ro.kernel.qemu")
        val isEmulator =
            ParcelFileDescriptor.AutoCloseInputStream(emulatorCheck).bufferedReader().use {
                it.readText().trim() == "1"
            }
        assumeTrue(isEmulator)

        compose.waitUntil(15_000) { compose.onAllNodesWithTag("tab_browse").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("tab_browse").performClick()
        compose.onNodeWithTag("browse_options_button").performClick()
        compose.onNodeWithTag("browse_options_popup").assertIsDisplayed()
        capture("file-options")

        val submenus = listOf("View" to "view", "Sort by" to "sort", "Filter" to "filter")
        for ((label, id) in submenus) {
            compose.onNodeWithText(label).performClick()
            compose.onNodeWithContentDescription("Back to file options").assertIsDisplayed()
            capture(id)
            compose.onNodeWithContentDescription("Back to file options").performClick()
        }
        compose.onNodeWithText("Sort by").assertIsDisplayed()
        compose.onNodeWithContentDescription("Close file options").performClick()
        compose.onNodeWithTag("browse_options_popup").assertDoesNotExist()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val destination = File(instrumentation.targetContext.filesDir, "atomic-file-options-visual")
        check(destination.isDirectory || destination.mkdir())
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        try {
            File(destination, "$name.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            val config = compose.activity.resources.configuration
            val details =
                listOf(
                    "widthDp=${config.screenWidthDp}",
                    "heightDp=${config.screenHeightDp}",
                    "densityDpi=${config.densityDpi}",
                    "fontScale=${config.fontScale}",
                    "orientation=${config.orientation}",
                    "uiMode=${config.uiMode}",
                    "bitmapPx=${bitmap.width}x${bitmap.height}",
                )
            File(destination, "$name.txt").writeText(details.joinToString("\n", postfix = "\n"))
        } finally {
            bitmap.recycle()
        }
    }
}
