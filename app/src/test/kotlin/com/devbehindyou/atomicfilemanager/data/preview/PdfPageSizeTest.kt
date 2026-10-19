package com.devbehindyou.atomicfilemanager.data.preview

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PdfPageSizeTest {
    @Test
    fun `huge square thin and ordinary pages stay inside both budgets`() {
        for ((width, height) in listOf(100_000 to 100_000, Int.MAX_VALUE to 1, 1 to Int.MAX_VALUE, 595 to 842)) {
            for (dimension in listOf(1, 1600, Int.MAX_VALUE)) {
                val size = pdfPageSize(width, height, dimension)
                assertTrue(size.width in 1..minOf(dimension, 2048))
                assertTrue(size.height in 1..minOf(dimension, 2048))
                assertTrue(size.width.toLong() * size.height <= 4_000_000)
            }
        }
    }
}
