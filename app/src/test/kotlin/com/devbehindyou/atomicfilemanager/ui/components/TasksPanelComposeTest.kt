package com.devbehindyou.atomicfilemanager.ui.components

import android.app.Application
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOperation
import com.devbehindyou.atomicfilemanager.domain.model.OperationId
import com.devbehindyou.atomicfilemanager.domain.model.OperationOptions
import com.devbehindyou.atomicfilemanager.domain.model.OperationProgress
import com.devbehindyou.atomicfilemanager.domain.model.OperationSnapshot
import com.devbehindyou.atomicfilemanager.domain.model.OperationStatus
import com.devbehindyou.atomicfilemanager.domain.model.OperationType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w500dp-h800dp", application = Application::class)
class TasksPanelComposeTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun popupUsesTheSharedCenteredWidth() {
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                TasksPanel(null, emptyList(), emptyList(), {}, {}, {}, {})
            }
        }
        compose.onNodeWithTag("tasks_popup").assertWidthIsEqualTo(420.dp)
        assertCenteredHeader()
    }

    @Test
    fun liveProgressAndCancellationKeepTheirOperationIdentity() {
        val operation =
            FileOperation(
                OperationId("upload"),
                OperationType.COPY,
                listOf(FileNodeId.file("/a.jpg")),
                FileNodeId.sftp("server", "/backup"),
                OperationOptions(),
                0,
            )
        val progress = OperationProgress(0, 1, 25, 100, "a.jpg", 0, null)
        val active = mutableStateOf<OperationSnapshot?>(OperationSnapshot(operation, OperationStatus.Running(progress)))
        var cancelled: FileOperation? = null
        var opened = 0
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                TasksPanel(active.value, emptyList(), emptyList(), { cancelled = it }, {}, {}, { opened++ })
            }
        }
        compose.onNodeWithText("25.0 B / 100.0 B").assertExists()
        compose.runOnIdle {
            active.value = OperationSnapshot(operation, OperationStatus.Running(progress.copy(bytesDone = 75)))
        }
        compose.onNodeWithText("75.0 B / 100.0 B").assertExists()
        compose.onNodeWithContentDescription("Cancel Upload task").performClick()
        compose.runOnIdle {
            assertEquals(operation, cancelled)
            active.value = null
        }
        compose.onNodeWithContentDescription("No tasks running").assertExists()
        compose.onNodeWithContentDescription("Task history").performClick()
        compose.runOnIdle { assertEquals(1, opened) }
    }

    private fun assertCenteredHeader() {
        val leading = compose.onNodeWithTag("tasks_leading").fetchSemanticsNode().boundsInRoot
        val trailing = compose.onNodeWithTag("tasks_trailing").fetchSemanticsNode().boundsInRoot
        val title = compose.onNodeWithTag("tasks_heading").fetchSemanticsNode().boundsInRoot
        assertEquals(leading.width, trailing.width, 0f)
        assertEquals((leading.right + trailing.left) / 2f, title.center.x, 1f)
    }
}
