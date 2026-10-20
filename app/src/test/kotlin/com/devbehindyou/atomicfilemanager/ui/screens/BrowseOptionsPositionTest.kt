package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class BrowseOptionsPositionTest {
    private val position = BrowseOptionsPositionProvider(gap = 8, margin = 16)

    @Test
    fun `popup aligns to anchor end and flips above near the bottom`() {
        assertEquals(
            IntOffset(384, 104),
            position.calculatePosition(
                IntRect(600, 48, 648, 96),
                IntSize(800, 600),
                LayoutDirection.Ltr,
                IntSize(264, 300),
            ),
        )
        assertEquals(
            IntOffset(384, 192),
            position.calculatePosition(
                IntRect(600, 500, 648, 548),
                IntSize(800, 600),
                LayoutDirection.Ltr,
                IntSize(264, 300),
            ),
        )
    }

    @Test
    fun `rtl and narrow windows keep the popup in visible bounds`() {
        assertEquals(
            IntOffset(48, 104),
            position.calculatePosition(
                IntRect(48, 48, 96, 96),
                IntSize(800, 600),
                LayoutDirection.Rtl,
                IntSize(264, 300),
            ),
        )
        assertEquals(
            IntOffset(0, 0),
            position.calculatePosition(
                IntRect(10, 10, 58, 58),
                IntSize(100, 100),
                LayoutDirection.Ltr,
                IntSize(100, 100),
            ),
        )
    }
}
