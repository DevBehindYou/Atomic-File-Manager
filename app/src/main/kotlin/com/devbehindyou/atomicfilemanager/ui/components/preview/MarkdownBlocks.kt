package com.devbehindyou.atomicfilemanager.ui.components.preview

import org.commonmark.node.BlockQuote
import org.commonmark.node.BulletList
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.Heading
import org.commonmark.node.HtmlBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.ListBlock
import org.commonmark.node.ListItem
import org.commonmark.node.Node
import org.commonmark.node.OrderedList
import org.commonmark.node.Paragraph
import org.commonmark.node.Text
import org.commonmark.node.ThematicBreak
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.parser.InlineParser
import org.commonmark.parser.InlineParserFactory
import org.commonmark.parser.Parser

/** Block families the native renderer draws. Anything else is disclosed as [Raw] source text. */
internal enum class MarkdownBlockKind { Heading, Paragraph, Code, Rule, Raw }

/**
 * One flat, render-ready block. Containers (quotes, list items) are flattened into depths so the lazy
 * list never nests. Inline markup (emphasis, code spans, links, images, inline HTML) stays literal.
 */
internal data class MarkdownBlock(
    val kind: MarkdownBlockKind,
    val text: String = "",
    val level: Int = 0,
    val quoteDepth: Int = 0,
    val listDepth: Int = 0,
    val marker: String? = null,
)

/** Containers beyond this many open blocks are not started; their markers remain literal text. */
internal const val MARKDOWN_MAX_OPEN_BLOCKS = 32

/**
 * CommonMark block structure through commonmark-java, parsed once before lazy composition so scrolling
 * cannot change parsing state. The library's inline parser is replaced by a literal pass, so no link,
 * image or HTML node is ever produced: nothing can be navigated, fetched or executed from this view.
 */
internal fun markdownBlocks(lines: List<String>): List<MarkdownBlock> {
    val document = markdownParser.parse(lines.joinToString("\n"))
    return MarkdownBlockCollector(lines).collect(document)
}

private val literalInlines =
    InlineParserFactory { _ -> InlineParser { content, node -> node.appendChild(Text(content.content)) } }

// Parser instances are immutable and safe to share; the literal inline parser keeps no state.
private val markdownParser: Parser =
    Parser
        .builder()
        .inlineParserFactory(literalInlines)
        .includeSourceSpans(IncludeSourceSpans.BLOCKS)
        .maxOpenBlockParsers(MARKDOWN_MAX_OPEN_BLOCKS)
        .build()

private class MarkdownBlockCollector(
    private val source: List<String>,
) {
    private val blocks = mutableListOf<MarkdownBlock>()

    fun collect(document: Node): List<MarkdownBlock> {
        children(document, Depth(0, 0), firstMarker = null)
        return blocks
    }

    private fun children(
        parent: Node,
        depth: Depth,
        firstMarker: String?,
    ) {
        var marker = firstMarker
        var child = parent.firstChild
        while (child != null) {
            visit(child, depth, marker)
            marker = null
            child = child.next
        }
        // An empty list item still shows its marker.
        if (marker != null) add(MarkdownBlockKind.Paragraph, "", 0, depth, marker)
    }

    private fun visit(
        node: Node,
        depth: Depth,
        marker: String?,
    ) {
        when (node) {
            is Heading -> add(MarkdownBlockKind.Heading, literal(node), node.level, depth, marker)
            is Paragraph -> add(MarkdownBlockKind.Paragraph, literal(node), 0, depth, marker)
            is FencedCodeBlock -> add(MarkdownBlockKind.Code, node.literal.removeSuffix("\n"), 0, depth, marker)
            is IndentedCodeBlock -> add(MarkdownBlockKind.Code, node.literal.removeSuffix("\n"), 0, depth, marker)
            is ThematicBreak -> add(MarkdownBlockKind.Rule, "", 0, depth, marker)
            is HtmlBlock -> add(MarkdownBlockKind.Raw, node.literal.removeSuffix("\n"), 0, depth, marker)
            is BlockQuote -> children(node, depth.copy(quote = depth.quote + 1), marker)
            is ListBlock -> list(node, depth, marker)
            // Link reference definitions and any other block: disclose the original source text.
            else -> add(MarkdownBlockKind.Raw, sourceText(node), 0, depth, marker)
        }
    }

    private fun list(
        list: ListBlock,
        depth: Depth,
        outerMarker: String?,
    ) {
        var number = (list as? OrderedList)?.markerStartNumber ?: 1
        val delimiter = (list as? OrderedList)?.markerDelimiter ?: "."
        // A list that opens its parent item keeps the parent's marker on a line of its own.
        if (outerMarker != null) add(MarkdownBlockKind.Paragraph, "", 0, depth, outerMarker)
        var item = list.firstChild
        while (item != null) {
            if (item is ListItem) {
                val marker = if (list is BulletList) BULLET else "$number$delimiter"
                children(item, depth.copy(list = depth.list + 1), marker)
                number++
            }
            item = item.next
        }
    }

    private fun add(
        kind: MarkdownBlockKind,
        text: String,
        level: Int,
        depth: Depth,
        marker: String?,
    ) {
        blocks += MarkdownBlock(kind, text, level, depth.quote, depth.list, marker)
    }

    /** Raw inline content as the literal parser received it, one trimmed source line per line. */
    private fun literal(node: Node): String =
        (node.firstChild as? Text)
            ?.literal
            .orEmpty()
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()

    private fun sourceText(node: Node): String =
        node.sourceSpans.joinToString("\n") { span ->
            val line = source.getOrNull(span.lineIndex).orEmpty()
            val start = span.columnIndex.coerceIn(0, line.length)
            line.substring(start, (start + span.length).coerceIn(start, line.length))
        }

    private data class Depth(
        val quote: Int,
        val list: Int,
    )

    private companion object {
        const val BULLET = "•"
    }
}
