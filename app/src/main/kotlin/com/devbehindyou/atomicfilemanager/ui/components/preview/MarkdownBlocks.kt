package com.devbehindyou.atomicfilemanager.ui.components.preview

internal data class MarkdownBlock(val text: String, val code: Boolean = false)

/** Parse before lazy composition: scrolling must not change fence state or consume a buffer. */
internal fun markdownBlocks(lines: List<String>): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    var fence: Char? = null
    var fenceLength = 0
    val code = mutableListOf<String>()
    for (line in lines) {
        val trimmed = line.trimStart()
        val marker = trimmed.firstOrNull()?.takeIf { it == '`' || it == '~' }
        val length = if (marker == null) 0 else trimmed.takeWhile { it == marker }.length
        when {
            fence == null && marker != null && length >= 3 -> {
                fence = marker
                fenceLength = length
            }
            fence != null && marker == fence && length >= fenceLength && trimmed.drop(length).isBlank() -> {
                blocks += MarkdownBlock(code.joinToString("\n"), code = true)
                code.clear()
                fence = null
            }
            fence != null -> code += line
            else -> blocks += MarkdownBlock(line)
        }
    }
    if (fence != null) blocks += MarkdownBlock(code.joinToString("\n"), code = true)
    return blocks
}
