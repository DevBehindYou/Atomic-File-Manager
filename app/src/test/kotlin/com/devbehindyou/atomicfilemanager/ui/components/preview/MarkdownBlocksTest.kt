package com.devbehindyou.atomicfilemanager.ui.components.preview

import com.devbehindyou.atomicfilemanager.ui.components.preview.MarkdownBlockKind.Code
import com.devbehindyou.atomicfilemanager.ui.components.preview.MarkdownBlockKind.Heading
import com.devbehindyou.atomicfilemanager.ui.components.preview.MarkdownBlockKind.Paragraph
import com.devbehindyou.atomicfilemanager.ui.components.preview.MarkdownBlockKind.Raw
import com.devbehindyou.atomicfilemanager.ui.components.preview.MarkdownBlockKind.Rule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Block corpus for the C031 CommonMark adapter. Section names refer to CommonMark 0.31.2. Inline markup
 * is literal by design, and GFM tables, task items, footnotes and strikethrough are explicitly
 * unsupported: they must stay visible as literal text rather than be dropped or partly interpreted.
 */
class MarkdownBlocksTest {
    // Original pre-parse guarantees (fences are stable, whitespace preserved, unclosed fences kept).

    @Test
    fun `fences emit a single stable block with original whitespace and repeated lines`() {
        val lines = listOf("# title", "```kotlin", "  val x = 1", "", "  val x = 1", "```", "after")
        val expected = listOf(h(1, "title"), code("  val x = 1\n\n  val x = 1"), p("after"))
        assertEquals(expected, markdownBlocks(lines))
        assertEquals(expected, markdownBlocks(lines))
        // Reading items out of composition order is harmless.
        assertEquals(expected.reversed(), markdownBlocks(lines).reversed())
    }

    @Test
    fun `unclosed and empty fences retain their content`() {
        assertEquals(listOf(code("unfinished")), markdownBlocks(listOf("```", "unfinished")))
        assertEquals(listOf(code("")), markdownBlocks(listOf("```", "```")))
    }

    @Test
    fun `a different or shorter fence is code and tilde fences work`() {
        assertEquals(listOf(code("```\n~~~"), p("end")), markdownBlocks(listOf("````", "```", "~~~", "````", "end")))
        assertEquals(listOf(code("code")), markdownBlocks(listOf("~~~text", "code", "~~~")))
    }

    // 4.1 Thematic breaks

    @Test
    fun `thematic breaks need only marker characters and spaces`() {
        assertEquals(List(5) { rule() }, parse("***\n\n---\n\n___\n\n - - -\n\n* * *"))
        assertEquals(listOf(p("***bold italic***")), parse("***bold italic***"))
        assertEquals(listOf(p("---not a rule")), parse("---not a rule"))
        assertEquals(listOf(p("+++"), p("--")), parse("+++\n\n--"))
        assertEquals(listOf(code("***")), parse("    ***"))
    }

    // 4.2 ATX headings and 4.3 setext headings

    @Test
    fun `atx headings cover six levels and strip optional closing sequences`() {
        assertEquals(
            (1..6).map { h(it, "level $it") },
            parse((1..6).joinToString("\n") { "#".repeat(it) + " level $it" }),
        )
        assertEquals(listOf(p("####### seven"), p("#5 bolt")), parse("####### seven\n\n#5 bolt"))
        assertEquals(
            listOf(h(1, "foo"), h(3, "foo ### b"), h(2, "indented"), h(1, "")),
            parse("# foo ##\n### foo ### b\n  ## indented\n#"),
        )
    }

    @Test
    fun `setext underlines make headings and a blank line makes a rule instead`() {
        assertEquals(listOf(h(1, "Title"), h(2, "Multi\nline")), parse("Title\n=====\n\nMulti\nline\n---"))
        assertEquals(listOf(p("Foo"), rule()), parse("Foo\n\n---"))
    }

    // 4.4 Indented code and 4.5 fenced code

    @Test
    fun `fence indentation and info strings follow the spec`() {
        assertEquals(listOf(code("```\ncode"), p("after")), parse("    ```\n    code\n\nafter"))
        assertEquals(listOf(code("aaa\n  bbb")), parse("  ```\n  aaa\n    bbb\n  ```"))
        assertEquals(listOf(code("aaa\n    ```")), parse("```\naaa\n    ```"))
        // A backtick fence info string cannot contain backticks: this is inline code, kept literal.
        assertEquals(listOf(p("``` aa ```\nfoo")), parse("``` aa ```\nfoo"))
        assertEquals(listOf(code("x")), parse("```&amp; entity info\nx\n```"))
    }

    // 5.1 Block quotes, 5.2/5.3 lists

    @Test
    fun `block quotes nest, need no space and accept lazy continuation`() {
        assertEquals(listOf(p("quoted", quote = 1)), parse(">quoted"))
        assertEquals(listOf(p("deep", quote = 2)), parse("> > deep"))
        assertEquals(listOf(p("a\nb", quote = 1)), parse("> a\nb"))
        assertEquals(
            listOf(h(2, "in quote", quote = 1), code("x", quote = 1)),
            parse("> ## in quote\n> ```\n> x\n> ```"),
        )
    }

    @Test
    fun `bullet and ordered lists keep markers, numbering and nesting`() {
        assertEquals(
            listOf("a", "b", "c").map { p(it, list = 1, marker = "•") },
            parse("- a\n* b\n\n+ c"),
        )
        assertEquals(listOf(p("x", list = 1, marker = "3."), p("y", list = 1, marker = "4.")), parse("3. x\n4. y"))
        assertEquals(listOf(p("x", list = 1, marker = "1)")), parse("1) x"))
        assertEquals(
            listOf(p("outer", list = 1, marker = "•"), p("inner", list = 2, marker = "•")),
            parse("- outer\n  - inner"),
        )
        assertEquals(
            listOf(p("first", list = 1, marker = "1."), p("second paragraph", list = 1)),
            parse("1. first\n\n   second paragraph"),
        )
        assertEquals(listOf(p("", list = 1, marker = "•"), p("x", list = 2, marker = "•")), parse("- - x"))
        assertEquals(listOf(p("", list = 1, marker = "•")), parse("-"))
        assertEquals(listOf(code("fn()", list = 1, marker = "•")), parse("- ```\n  fn()\n  ```"))
        assertEquals(listOf(p("1234567890. too long")), parse("1234567890. too long"))
    }

    // Literal inline content, HTML disclosure and explicit non-support

    @Test
    fun `inline markup links and images stay literal and are never resolved`() {
        val inline =
            "**bold** _em_ `code` [link](https://example.invalid) ![img](https://example.invalid/i.png) <b>x</b> &amp;"
        assertEquals(listOf(p(inline)), parse(inline))
        assertEquals(listOf(p("[ref]")), parse("[ref]"))
    }

    @Test
    fun `html blocks and reference definitions are disclosed as raw source`() {
        assertEquals(listOf(raw("<script>alert(1)</script>")), parse("<script>alert(1)</script>"))
        assertEquals(listOf(raw("<div>\n*x*\n</div>")), parse("<div>\n*x*\n</div>"))
        assertEquals(
            listOf(raw("[foo]: https://example.invalid \"title\""), p("[foo]")),
            parse("[foo]: https://example.invalid \"title\"\n\n[foo]"),
        )
        assertEquals(listOf(raw("[q]: /local", quote = 1)), parse("> [q]: /local"))
    }

    @Test
    fun `gfm tables tasks footnotes and strikethrough are unsupported and stay literal`() {
        assertEquals(listOf(p("| a | b |\n| - | - |\n| 1 | 2 |")), parse("| a | b |\n| - | - |\n| 1 | 2 |"))
        assertEquals(
            listOf(p("[ ] todo", list = 1, marker = "•"), p("[x] done", list = 1, marker = "•")),
            parse("- [ ] todo\n- [x] done"),
        )
        // A footnote definition is a valid CommonMark reference definition, so it is disclosed as raw source.
        assertEquals(listOf(p("text[^1]"), raw("[^1]: note")), parse("text[^1]\n\n[^1]: note"))
        assertEquals(listOf(p("~~gone~~")), parse("~~gone~~"))
    }

    // Bounds, malformed input and stability

    @Test
    fun `deep container nesting is capped and the excess stays literal`() {
        val quotes = parse(">".repeat(10_000) + " x")
        assertEquals(1, quotes.size)
        assertTrue(quotes.single().quoteDepth <= MARKDOWN_MAX_OPEN_BLOCKS)
        assertTrue(quotes.single().text.endsWith("> x"))
        assertEquals(10_000, quotes.single().quoteDepth + quotes.single().text.count { it == '>' })

        val lists = parse("- ".repeat(5_000) + "x")
        assertTrue(lists.maxOf { it.listDepth } <= MARKDOWN_MAX_OPEN_BLOCKS)
        assertTrue(lists.last().text.endsWith("- x"))
    }

    @Test
    fun `pathological inline input is not parsed and malformed input never throws`() {
        val brackets = "[".repeat(200_000) + "x" + "](u)".repeat(50_000)
        assertEquals(listOf(p(brackets)), parse(brackets))
        val emphasis = "*a ".repeat(50_000).trim()
        assertEquals(listOf(p(emphasis)), parse(emphasis))
        val malformed = listOf("```", "\u0000", "> ```", "- <div", "[x]: <", "#".repeat(10_000), "|||", "\t- \t>")
        assertTrue(markdownBlocks(malformed).isNotEmpty())
        assertEquals(emptyList<MarkdownBlock>(), markdownBlocks(emptyList()))
        assertEquals(emptyList<MarkdownBlock>(), markdownBlocks(listOf("", "   ")))
    }

    @Test
    fun `a full bounded preview parses within the reader limits`() {
        // ReadFileContentUseCase stops at 5,000 lines / 1 MiB; build roughly that much mixed Markdown.
        val unit =
            listOf(
                "## Section",
                "",
                "- item with some words to fill the line",
                "> quoted text here",
                "",
                "```",
                "code line",
                "```",
                "",
            )
        val lines = mutableListOf<String>()
        var bytes = 0
        while (lines.size < 4_990) {
            unit.forEach { line ->
                val padded = if (line.startsWith("- ")) line + " x".repeat(850) else line
                lines += padded
                bytes += padded.length + 1
            }
        }
        val started = System.nanoTime()
        val blocks = markdownBlocks(lines)
        val millis = (System.nanoTime() - started) / 1_000_000
        println("C031 bounded preview: ${lines.size} lines, $bytes bytes, ${blocks.size} blocks, $millis ms")
        assertEquals(lines.count { it == "## Section" }, blocks.count { it.kind == Heading })
        assertEquals(lines.count { it == "code line" }, blocks.count { it.kind == Code })
        assertEquals(markdownBlocks(lines), blocks)
    }

    private fun parse(markdown: String) = markdownBlocks(markdown.split("\n"))

    private fun p(
        text: String,
        quote: Int = 0,
        list: Int = 0,
        marker: String? = null,
    ) = MarkdownBlock(Paragraph, text, 0, quote, list, marker)

    private fun h(
        level: Int,
        text: String,
        quote: Int = 0,
    ) = MarkdownBlock(Heading, text, level, quote)

    private fun code(
        text: String,
        quote: Int = 0,
        list: Int = 0,
        marker: String? = null,
    ) = MarkdownBlock(Code, text, 0, quote, list, marker)

    private fun raw(
        text: String,
        quote: Int = 0,
    ) = MarkdownBlock(Raw, text, 0, quote)

    private fun rule() = MarkdownBlock(Rule)
}
