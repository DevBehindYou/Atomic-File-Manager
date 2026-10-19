package com.devbehindyou.atomicfilemanager.ui.components.preview

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.devbehindyou.atomicfilemanager.data.preview.DocxDocument
import com.devbehindyou.atomicfilemanager.data.preview.DocxParagraph
import com.devbehindyou.atomicfilemanager.data.preview.DocxTextRun
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DocxPreviewContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun searchCountsAndMovesBetweenCaseInsensitiveMatchesAndDisablesNavigationWhenNone() {
        compose.setContent { DocxDocumentContent(document("First needle", "Second NEEDLE")) }
        compose.onNodeWithTag("docx_next").assertIsNotEnabled()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("docx_find"))).performTextInput("needle")
        compose.onNodeWithText("1 of 2 matches (first 1,000)").assertIsDisplayed()
        compose.onNodeWithTag("docx_next").performClick()
        compose.onNodeWithText("2 of 2 matches (first 1,000)").assertIsDisplayed()
        compose.onNodeWithTag("docx_previous").performClick()
        compose.onNodeWithText("1 of 2 matches (first 1,000)").assertIsDisplayed()
        compose.onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag("docx_find"))).performTextReplacement("absent")
        compose.onNodeWithText("0 of 0 matches (first 1,000)").assertIsDisplayed()
        compose.onNodeWithTag("docx_next").assertIsNotEnabled()
    }

    @Test
    fun searchSpansRunsKeepsUtf16OffsetsAndCapsMatches() {
        val styled = DocxDocument(listOf(DocxParagraph(listOf(DocxTextRun("😀nee"), DocxTextRun("dle", bold = true)))))
        assertEquals(listOf(DocxMatch(0, 2, 8)), findDocxMatches(styled, "NEEDLE"))
        assertEquals(1_000, findDocxMatches(document("a".repeat(20_000)), "a").size)
        assertEquals(emptyList<DocxMatch>(), findDocxMatches(styled, ""))
        assertEquals(emptyList<DocxMatch>(), findDocxMatches(styled, "a".repeat(201)))
    }

    @Test
    fun failedReadOffersRetryAndDoesNotLeaveAnOldDocumentVisible() {
        var reads = 0
        compose.setContent {
            DocxPreviewContent(
                node = node(),
                readDocument = {
                    reads++
                    if (reads == 1) {
                        FileResult.Failure(FileError.IoFailure("unavailable"))
                    } else {
                        FileResult.Success(document("Recovered paragraph"))
                    }
                },
                modifier = Modifier.testTag("docx_content"),
            )
        }
        compose.onNodeWithText("Retry", ignoreCase = true).performClick()
        compose.onNodeWithText("Recovered paragraph").assertIsDisplayed()
        assertEquals(2, reads)
    }

    private fun document(vararg values: String): DocxDocument =
        DocxDocument(values.map { DocxParagraph(listOf(DocxTextRun(it))) })

    private fun node(): FileNode =
        FileNode(
            id = FileNodeId("file:/example.docx"),
            name = "example.docx",
            displayName = "example.docx",
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            size = 0,
            modifiedAt = 0,
            isDirectory = false,
            isHidden = false,
            parentId = null,
            storageType = StorageType.INTERNAL_SHARED,
            access = AccessFlags.READ_ONLY,
            childCount = null,
            extras = null,
        )
}
