package com.devbehindyou.atomicfilemanager.ui.components.preview

import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.data.preview.PDF_PASSWORD_PROTECTED
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PdfOpenErrorTest {
    @Test
    fun `password protection has its own message and no retry while corruption stays retryable`() {
        val password = FileError.UnsupportedFormat(PDF_PASSWORD_PROTECTED)
        assertEquals(R.string.pdf_password_protected, pdfOpenErrorText(password))
        assertTrue(password.isPdfPasswordProtected())
        val retryable =
            listOf(
                FileError.IoFailure("PDF preview unavailable"),
                FileError.UnsupportedFormat("application/pdf"),
                FileError.FileNotFound("document.pdf"),
            )
        for (other in retryable) {
            assertEquals(R.string.pdf_open_error, pdfOpenErrorText(other))
            assertFalse(other.isPdfPasswordProtected())
        }
        assertEquals(R.string.pdf_too_large, pdfOpenErrorText(FileError.FileTooLarge("PDF preview", 1)))
    }
}
