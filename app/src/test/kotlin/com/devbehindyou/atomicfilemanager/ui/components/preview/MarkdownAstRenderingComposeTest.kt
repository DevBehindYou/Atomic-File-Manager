package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.app.Application
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.DefaultAppContainer
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import com.devbehindyou.atomicfilemanager.domain.usecase.ReadFileContentUseCase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream

/**
 * C031 consumers: the actual Markdown viewer and bounded reader render CommonMark block structure.
 * Each case names the CommonMark 0.31.2 rule it exercises. Inline markup stays literal by design.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp", application = Application::class)
class MarkdownAstRenderingComposeTest {
    @get:Rule val compose = createComposeRule()
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun aLineStartingWithThreeAsterisksAndTextIsNotSwallowedByARule() {
        // Thematic breaks contain only the marker characters and spaces (spec 4.1).
        show("***bold italic***")
        awaitText("***bold italic***")
    }

    @Test
    fun aDashRunFollowedByTextIsNotSwallowedByARule() {
        show("---not a rule")
        awaitText("---not a rule")
    }

    @Test
    fun levelFourToSixAtxHeadingsDropTheirMarkersAndClosingSequence() {
        // ATX headings have levels 1-6 and an optional closing sequence (spec 4.2).
        show("#### Level four ####\n\n###### Level six")
        awaitText("Level four")
        awaitText("Level six")
        assertAbsent("#### Level four ####")
    }

    @Test
    fun aSetextUnderlineMakesAHeadingInsteadOfLiteralText() {
        // A paragraph followed by = or - underline is a setext heading (spec 4.3).
        show("Title\n=====\n\nSubtitle\n---")
        awaitText("Title")
        awaitText("Subtitle")
        assertAbsent("=====")
    }

    @Test
    fun aFenceIndentedFourSpacesIsIndentedCodeNotAnOpeningFence() {
        // Four spaces of indentation start indented code, so the backticks are literal (spec 4.4/4.5).
        show("    ```\n    code\n\nafter")
        awaitText("```\ncode")
        awaitText("after")
    }

    @Test
    fun aNestedListItemDropsItsMarkerAndIndentation() {
        // List items contain blocks, including nested lists (spec 5.2/5.3).
        show("- outer\n  - inner")
        awaitText("outer")
        awaitText("inner")
        assertAbsent("  - inner")
    }

    @Test
    fun aBlockQuoteMarkerWithoutASpaceIsStillAQuote() {
        // The space after > is optional (spec 5.1).
        show(">quoted")
        awaitText("quoted")
        assertAbsent(">quoted")
    }

    @Test
    fun controlFencedCodeAndLevelOneHeadingStillRender() {
        show("# Title\n\n```kotlin\nval x = 1\n```")
        awaitText("Title")
        awaitText("val x = 1")
        assertAbsent("```kotlin")
    }

    @Test
    fun controlHtmlIsDisclosedAsLiteralTextAndNeverInterpreted() {
        show("<script>alert(1)</script>\n\n<img src=\"https://example.invalid/x.png\">")
        awaitText("<script>alert(1)</script>")
        awaitText("<img src=\"https://example.invalid/x.png\">")
    }

    @Test
    fun controlRawModeStillDisclosesEveryOriginalLine() {
        show("***bold***\n#### four")
        compose.onNodeWithContentDescription("Raw").performClick()
        awaitText("***bold***")
        awaitText("#### four")
    }

    private fun show(markdown: String) {
        val container = container(markdown)
        val node =
            FileNode(
                FileNodeId.file("/markdown-ast/doc.md"), "doc.md", "doc.md", "text/markdown",
                markdown.length.toLong(), 0, false, false, null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
            )
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) { MarkdownPreviewContent(node, container) }
        }
    }

    private fun container(markdown: String): AppContainer {
        val backend =
            object : StorageBackend by InMemoryBackend() {
                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                    FileResult.Success(InputStreamProvider { ByteArrayInputStream(markdown.toByteArray()) })
            }
        return object : AppContainer by DefaultAppContainer(app) {
            override val readFileContentUseCase = ReadFileContentUseCase { backend }
        }
    }

    private fun awaitText(text: String) {
        compose.waitForIdle()
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun assertAbsent(text: String) {
        compose.onNodeWithText(text).assertDoesNotExist()
    }
}
