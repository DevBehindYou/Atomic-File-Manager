package com.devbehindyou.atomicfilemanager.ui.components.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicTypography
import com.devbehindyou.atomicfilemanager.data.preview.DocxDocument
import com.devbehindyou.atomicfilemanager.data.preview.DocxParagraph
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult

@Composable
fun DocxPreviewContent(
    node: FileNode,
    readDocument: suspend (FileNodeId) -> FileResult<DocxDocument>,
    modifier: Modifier = Modifier,
) {
    // Reset stale text, query and failures before drawing a replacement source/helper session.
    key(node.id, node.modifiedAt, node.size, readDocument) { DocxSourceSession(node, readDocument, modifier) }
}

@Composable
private fun DocxSourceSession(
    node: FileNode,
    readDocument: suspend (FileNodeId) -> FileResult<DocxDocument>,
    modifier: Modifier = Modifier,
) {
    var result by remember(node.id) { mutableStateOf<FileResult<DocxDocument>?>(null) }
    var attempt by remember(node.id) { mutableIntStateOf(0) }
    LaunchedEffect(node.id, attempt) {
        result = null
        result = readDocument(node.id)
    }
    when (val loaded = result) {
        null ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AtomicLoading(stringResource(R.string.docx_loading))
            }
        is FileResult.Failure ->
            Column(modifier.padding(AtomicSpacing.s16)) {
                AtomicText(stringResource(R.string.docx_unavailable), AtomicTextRole.Body)
                AtomicButton(stringResource(R.string.docx_retry), onClick = { attempt++ })
            }
        is FileResult.Success -> DocxDocumentContent(loaded.value, modifier)
    }
}

@Composable
internal fun DocxDocumentContent(
    document: DocxDocument,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable(document) { mutableStateOf("") }
    var current by rememberSaveable(document) { mutableIntStateOf(0) }
    var fontStep by rememberSaveable(document) { mutableIntStateOf(0) }
    val matches = remember(document, query) { findDocxMatches(document, query) }
    val matchesByParagraph = remember(matches) { matches.groupBy { it.paragraph } }
    val selected = matches.getOrNull(current)
    val listState = rememberLazyListState()
    LaunchedEffect(selected) { if (selected != null) listState.animateScrollToItem(selected.paragraph) }
    Column(
        modifier.fillMaxSize().padding(AtomicSpacing.s12),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
    ) {
        AtomicText(stringResource(R.string.docx_text_layout_note), AtomicTextRole.BodySecondary)
        AtomicTextField(
            value = query,
            onValueChange = {
                query = it.take(MAX_QUERY_LENGTH)
                current = 0
            },
            label = stringResource(R.string.docx_find),
            modifier = Modifier.testTag("docx_find"),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
            AtomicButton(
                stringResource(R.string.docx_previous),
                onClick = { current = (current - 1 + matches.size) % matches.size },
                enabled = matches.isNotEmpty(),
                variant = AtomicButtonVariant.Text,
                modifier = Modifier.weight(1f).testTag("docx_previous"),
            )
            AtomicButton(
                stringResource(R.string.docx_next),
                onClick = { current = (current + 1) % matches.size },
                enabled = matches.isNotEmpty(),
                variant = AtomicButtonVariant.Text,
                modifier = Modifier.weight(1f).testTag("docx_next"),
            )
        }
        if (query.isNotEmpty()) {
            AtomicText(
                stringResource(R.string.docx_match_count, if (matches.isEmpty()) 0 else current + 1, matches.size),
                AtomicTextRole.BodySecondary,
                modifier = Modifier.testTag("docx_match_count"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
            AtomicButton(
                stringResource(R.string.docx_smaller),
                onClick = { fontStep-- },
                enabled = fontStep > 0,
                variant = AtomicButtonVariant.Text,
                modifier = Modifier.weight(1f),
            )
            AtomicButton(
                stringResource(R.string.docx_larger),
                onClick = { fontStep++ },
                enabled = fontStep < 4,
                variant = AtomicButtonVariant.Text,
                modifier = Modifier.weight(1f),
            )
        }
        if (document.paragraphs.none { it.text.isNotBlank() }) {
            AtomicText(stringResource(R.string.docx_empty), AtomicTextRole.Body)
        }
        SelectionContainer(Modifier.weight(1f)) {
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s12)) {
                itemsIndexed(document.paragraphs) { index, paragraph ->
                    DocxParagraphContent(
                        paragraph,
                        matchesByParagraph[index].orEmpty(),
                        selected,
                        1f + fontStep * .2f,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocxParagraphContent(
    paragraph: DocxParagraph,
    matches: List<DocxMatch>,
    selected: DocxMatch?,
    fontScale: Float,
) {
    val colors = Atomic.colors
    val prefix = if (paragraph.listItem) "• " else ""
    val annotated =
        buildAnnotatedString {
            append(prefix)
            paragraph.runs.forEach { run ->
                val start = length
                append(run.text)
                addStyle(
                    SpanStyle(
                        fontWeight = if (run.bold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (run.italic) FontStyle.Italic else FontStyle.Normal,
                    ),
                    start,
                    length,
                )
            }
            matches.forEach { match ->
                addStyle(
                    SpanStyle(
                        background = if (match == selected) colors.accent else colors.surfaceCard,
                        color = if (match == selected) colors.onAccent else colors.content,
                        fontWeight = FontWeight.Bold,
                    ),
                    match.start + prefix.length,
                    match.end + prefix.length,
                )
            }
        }
    val base = if (paragraph.headingLevel > 0) AtomicTypography.nameLarge else AtomicTypography.body
    Text(
        annotated,
        style = base.copy(fontSize = base.fontSize * fontScale, lineHeight = base.lineHeight * fontScale),
        color = colors.content,
        modifier = Modifier.fillMaxWidth().semantics { if (paragraph.headingLevel > 0) heading() },
    )
}

internal data class DocxMatch(val paragraph: Int, val start: Int, val end: Int)

/** Bounded, case-insensitive, non-overlapping matches; offsets are UTF-16 indices used by Compose. */
internal fun findDocxMatches(
    document: DocxDocument,
    query: String,
): List<DocxMatch> {
    if (query.isEmpty() || query.length > MAX_QUERY_LENGTH) return emptyList()
    val matches = mutableListOf<DocxMatch>()
    document.paragraphs.forEachIndexed { index, paragraph ->
        val text = paragraph.text
        var offset = 0
        while (offset <= text.length - query.length) {
            val found = text.indexOf(query, offset, ignoreCase = true)
            if (found < 0) break
            matches.add(DocxMatch(index, found, found + query.length))
            if (matches.size == MAX_MATCHES) return matches.toList()
            offset = found + query.length
        }
    }
    return matches.toList()
}

private const val MAX_MATCHES = 1_000
private const val MAX_QUERY_LENGTH = 200
