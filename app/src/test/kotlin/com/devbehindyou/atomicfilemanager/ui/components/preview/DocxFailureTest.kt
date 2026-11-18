package com.devbehindyou.atomicfilemanager.ui.components.preview

import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.data.preview.DOCX_LEGACY_OR_PROTECTED
import com.devbehindyou.atomicfilemanager.data.preview.DOCX_NOT_A_PACKAGE
import com.devbehindyou.atomicfilemanager.data.preview.DOCX_OVER_PREVIEW_LIMITS
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DocxFailureTest {
    @Test
    fun `files that are not previewable DOCX say what they are and offer no pointless Retry`() {
        assertEquals(
            DocxFailure(R.string.docx_legacy_or_protected, canRetry = false),
            docxFailure(FileError.UnsupportedFormat(DOCX_LEGACY_OR_PROTECTED)),
        )
        assertEquals(
            DocxFailure(R.string.docx_not_a_package, canRetry = false),
            docxFailure(FileError.UnsupportedFormat(DOCX_NOT_A_PACKAGE)),
        )
        assertEquals(
            DocxFailure(R.string.docx_over_limits, canRetry = false),
            docxFailure(FileError.UnsupportedFormat(DOCX_OVER_PREVIEW_LIMITS)),
        )
    }

    @Test
    fun `read failures keep Retry`() {
        for (error in listOf(
            FileError.IoFailure("x"),
            FileError.ProviderUnavailable("p"),
            FileError.UnsupportedFormat(null),
        )) {
            assertEquals(DocxFailure(R.string.docx_unavailable, canRetry = true), docxFailure(error))
        }
    }
}
