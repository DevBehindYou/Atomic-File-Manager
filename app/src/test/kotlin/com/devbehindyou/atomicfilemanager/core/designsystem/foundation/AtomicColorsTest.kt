package com.devbehindyou.atomicfilemanager.core.designsystem.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AtomicColorsTest {
    private fun assertContrast(
        label: String,
        foreground: Int,
        background: Int,
        minimum: Double,
    ) {
        val ratio = ColorMath.contrast(foreground, background)
        assertTrue(ratio >= minimum, "$label: contrast %.2f is below %.1f".format(ratio, minimum))
    }

    @Test
    fun brandOrangeUsesInkForFilledControls() {
        assertEquals(7.19, ColorMath.contrast(AtomicPalette.BLACK, AtomicPalette.SIGNAL), 0.05)
        listOf(AtomicColorFactory.light(), AtomicColorFactory.dark()).forEach { theme ->
            assertEquals(0xFFFE6902.toInt(), theme.accent.fill)
            assertContrast("pressed accent", theme.accent.onFill, theme.accent.pressed, 4.5)
        }
    }

    @Test
    fun lightRolesMeetContrastRules() {
        val c = AtomicColorFactory.light()
        listOf(c.background, c.surfaceCard, c.surfaceInset, c.surfaceRaised).forEach { bg ->
            assertContrast("content", c.content, bg, 4.5)
            assertContrast("secondary", c.contentSecondary, bg, 4.5)
            assertContrast("accent text", c.accent.text, bg, 4.5)
            assertContrast("strong border", c.borderStrong, bg, 3.0)
        }
        assertContrast("on accent", c.accent.onFill, c.accent.fill, 4.5)
        assertContrast("error", c.error.error, c.background, 4.5)
        assertContrast("error container", c.error.onContainer, c.error.container, 4.5)
    }

    @Test
    fun darkRolesMeetContrastRules() {
        val c = AtomicColorFactory.dark()
        listOf(c.background, c.surfaceCard, c.surfaceInset, c.surfaceRaised).forEach { bg ->
            assertContrast("content", c.content, bg, 4.5)
            assertContrast("secondary", c.contentSecondary, bg, 4.5)
            assertContrast("muted", c.contentMuted, bg, 4.5)
            assertContrast("accent text", c.accent.text, bg, 4.5)
            assertContrast("strong border", c.borderStrong, bg, 3.0)
        }
        assertContrast("on accent", c.accent.onFill, c.accent.fill, 4.5)
        assertContrast("error", c.error.error, c.background, 4.5)
        assertContrast("error container", c.error.onContainer, c.error.container, 4.5)
    }
}
