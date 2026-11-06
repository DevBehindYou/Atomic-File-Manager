package com.devbehindyou.atomicfilemanager.ui.components.preview

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MarkdownBlocksTest {
    @Test
    fun `fences emit a single stable block with original whitespace and repeated lines`() {
        val lines = listOf("# title", "```kotlin", "  val x = 1", "", "  val x = 1", "```", "after")
        val expected =
            listOf(MarkdownBlock("# title"), MarkdownBlock("  val x = 1\n\n  val x = 1", true), MarkdownBlock("after"))
        assertEquals(expected, markdownBlocks(lines))
        assertEquals(expected, markdownBlocks(lines))
        // Reading items out of composition order is harmless.
        assertEquals(expected.reversed(), markdownBlocks(lines).reversed())
    }

    @Test
    fun `unclosed and empty fences retain their content`() {
        assertEquals(listOf(MarkdownBlock("unfinished", true)), markdownBlocks(listOf("```", "unfinished")))
        assertEquals(listOf(MarkdownBlock("", true)), markdownBlocks(listOf("```", "```")))
    }

    @Test
    fun `a different or shorter fence is code and tilde fences work`() {
        assertEquals(
            listOf(MarkdownBlock("```\n~~~", true), MarkdownBlock("end")),
            markdownBlocks(listOf("````", "```", "~~~", "````", "end")),
        )
        assertEquals(listOf(MarkdownBlock("code", true)), markdownBlocks(listOf("~~~text", "code", "~~~")))
    }
}
