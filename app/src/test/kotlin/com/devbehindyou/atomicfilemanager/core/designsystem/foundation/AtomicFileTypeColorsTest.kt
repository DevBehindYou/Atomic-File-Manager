package com.devbehindyou.atomicfilemanager.core.designsystem.foundation

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AtomicFileTypeColorsTest {
    @Test
    fun everyFileTypeRemainsReadableOnAllLightAndDarkSurfaces() {
        listOf(AtomicColorFactory.light(), AtomicColorFactory.dark()).forEach { theme ->
            val surfaces = listOf(theme.background, theme.surfaceCard, theme.surfaceInset, theme.surfaceRaised)
            AtomicFileType.entries.forEach { type ->
                val icon = AtomicFileTypeColors.icon(type, theme.isDark)
                surfaces.forEach { surface ->
                    val ratio = ColorMath.contrast(icon, surface)
                    assertTrue(ratio >= 4.5, "$type dark=${theme.isDark} contrast=$ratio")
                }
            }
        }
    }

    @Test
    fun typeColoursAreDistinctAndUseSeparateTonesForEachTheme() {
        listOf(false, true).forEach { dark ->
            val tones = AtomicFileType.entries.map { AtomicFileTypeColors.icon(it, dark) }
            assertEquals(AtomicFileType.entries.size, tones.distinct().size)
            tones.forEach { assertEquals(255, it ushr 24) }
        }
        AtomicFileType.entries.forEach { type ->
            assertNotEquals(AtomicFileTypeColors.icon(type, false), AtomicFileTypeColors.icon(type, true))
        }
    }
}
