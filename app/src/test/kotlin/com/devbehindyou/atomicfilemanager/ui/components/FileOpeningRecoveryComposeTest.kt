package com.devbehindyou.atomicfilemanager.ui.components

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Looper
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.AtomicApp
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleKey
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenRuleScope
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenTarget
import com.devbehindyou.atomicfilemanager.domain.model.FileOpeningPreferences
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.ThemeMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.concurrent.atomic.AtomicInteger

/** Exercise recovery and once/always persistence through the real production opening pane. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = AtomicApp::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class FileOpeningRecoveryComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: AtomicApp get() = ApplicationProvider.getApplicationContext()
    private val repository get() = app.container.settingsRepository
    private val render = mutableStateOf(true)
    private val closed = AtomicInteger()
    private val key = FileOpenRuleKey(FileOpenRuleScope.EXTENSION, "pdf")
    private val saved = FileOpenTarget(FileOpenMode.EXTERNAL, "saved.reader/saved.reader.View", "Saved reader")
    private val replacement = ComponentName("replacement.reader", "replacement.reader.View")
    private val baseline =
        FileOpeningPreferences().withRule(key, saved).withRule(
            FileOpenRuleKey(FileOpenRuleScope.EXTENSION, "txt"),
            FileOpenTarget(FileOpenMode.ASK),
        )
    private val uri = Uri.parse("content://opening-fixture/tree/root/document/report")
    private val node =
        FileNode(
            FileNodeId.saf(uri.toString()), "report.pdf", "report.pdf", "application/pdf", 0, 0, false, false,
            null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
        )

    @Before
    fun seedOnlyTheSandboxPreferencesAndRegisterACompatibleReader() {
        repository.update { it.copy(themeMode = ThemeMode.DARK, showHiddenFiles = false, fileOpening = baseline) }
        registerReader(replacement, "Replacement reader", "application/pdf")
    }

    @After
    fun disposePendingPaneWorkAndRestoreLaunchValidation() {
        compose.runOnIdle { render.value = false }
        shadowOf(app).checkActivities(false)
    }

    @Test
    fun missingSavedReaderShowsRecoveryWithoutLaunchingOrChangingPreferences() {
        showRecovery()
        assertRecovery()
    }

    @Test
    fun incompatibleSavedReaderIsNotLaunchedAndOtherChoicesRemainAvailable() {
        registerReader(ComponentName.unflattenFromString(saved.component!!)!!, "Saved reader", "image/png")
        showRecovery()
        assertRecovery()
        compose.onNodeWithContentDescription("Saved reader").assertDoesNotExist()
    }

    @Test
    fun choosingAvailableReaderOncePreservesTheSavedRuleAndOtherSettings() {
        showRecovery()
        compose.onNode(isToggleable()).assertExists()
        clickReplacement()
        awaitCondition { closed.get() == 1 }
        assertLaunchedReplacement()
        assertPreferences(baseline)
    }

    @Test
    fun choosingAvailableReaderAlwaysReplacesTheRuleOnlyAfterASuccessfulLaunch() {
        showRecovery()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        assertPreferences(baseline)
        assertNull(shadowOf(app).peekNextStartedActivity())
        clickReplacement()
        awaitCondition { closed.get() == 1 }
        assertLaunchedReplacement()
        assertPreferences(
            baseline.withRule(
                key,
                FileOpenTarget(FileOpenMode.EXTERNAL, replacement.flattenToString(), "Replacement reader"),
            ),
        )
    }

    @Test
    fun readerRemovedAfterDiscoveryRejectsLaunchWithoutSavingOrClosing() {
        showRecovery()
        compose.onNode(isToggleable()).performScrollTo().performClick()
        // Enable the pinned shadow's real resolve-before-launch check; removal alone does not reject startActivity.
        shadowOf(app).checkActivities(true)
        shadowOf(app.packageManager).removeActivity(replacement)
        val rejected =
            Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf")
                .setComponent(replacement).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        assertThrows(ActivityNotFoundException::class.java) { app.startActivity(rejected) }
        assertNull(shadowOf(app).peekNextStartedActivity())
        clickReplacement()
        awaitMessage(R.string.viewer_external_failed)
        compose.onNodeWithTag("file_opening_choices").assertIsDisplayed()
        compose.onNodeWithContentDescription(app.getString(R.string.file_open_built_in)).assertExists()
        assertEquals(0, closed.get())
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertPreferences(baseline)
    }

    private fun showRecovery() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                if (render.value) FilePreviewPane(node, onClose = { closed.incrementAndGet() })
            }
        }
        awaitMessage(R.string.file_open_missing_app)
        shadowOf(app).checkActivities(true)
    }

    private fun assertRecovery() {
        compose.onNodeWithText(app.getString(R.string.file_open_missing_app)).assertIsDisplayed()
        compose.onNodeWithContentDescription("Replacement reader").assertExists()
        compose.onNodeWithContentDescription(app.getString(R.string.file_open_built_in)).assertExists()
        assertEquals(0, closed.get())
        assertNull(shadowOf(app).peekNextStartedActivity())
        assertPreferences(baseline)
    }

    private fun clickReplacement() {
        compose.onNodeWithContentDescription("Replacement reader").performScrollTo().performClick()
    }

    private fun assertLaunchedReplacement() {
        val launched = checkNotNull(shadowOf(app).nextStartedActivity)
        assertEquals(Intent.ACTION_VIEW, launched.action)
        assertEquals(replacement, launched.component)
        assertEquals("application/pdf", launched.type)
        assertEquals(uri, launched.data)
        assertEquals(uri, launched.clipData!!.getItemAt(0).uri)
        assertTrue(launched.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, launched.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        assertEquals(0, launched.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        assertEquals(0, launched.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        assertNull(shadowOf(app).nextStartedActivity)
    }

    private fun assertPreferences(expected: FileOpeningPreferences) {
        assertEquals(expected, repository.settings.value.fileOpening)
        assertEquals(ThemeMode.DARK, repository.settings.value.themeMode)
        assertEquals(false, repository.settings.value.showHiddenFiles)
    }

    private fun awaitMessage(resource: Int) {
        awaitCondition {
            compose.onAllNodesWithText(app.getString(resource)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun awaitCondition(condition: () -> Boolean) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            condition()
        }
    }

    private fun registerReader(
        component: ComponentName,
        label: String,
        mime: String,
    ) {
        val manager = shadowOf(app.packageManager)
        manager.addOrUpdateActivity(
            ActivityInfo().apply {
                packageName = component.packageName
                name = component.className
                nonLocalizedLabel = label
                enabled = true
                exported = true
                applicationInfo =
                    ApplicationInfo().apply {
                        packageName = component.packageName
                        enabled = true
                    }
            },
        )
        manager.addIntentFilterForActivity(
            component,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("content")
                addDataType(mime)
            },
        )
    }
}
