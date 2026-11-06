package com.devbehindyou.atomicfilemanager.ui.components.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.usecase.ReadFileContentUseCase
import com.devbehindyou.atomicfilemanager.domain.usecase.TextContent

@Composable
fun MarkdownPreviewContent(
    node: FileNode,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val reader = container.readFileContentUseCase
    // The actual reader and known revision own loading, errors and explicit retry attempts.
    key(node.id, node.modifiedAt, node.size, reader) {
        var attempt by remember { mutableIntStateOf(0) }
        key(attempt) {
            MarkdownReadAttempt(node, reader, modifier, onRetry = { attempt++ })
        }
    }
}

@Composable
private fun MarkdownReadAttempt(
    node: FileNode,
    reader: ReadFileContentUseCase,
    modifier: Modifier,
    onRetry: () -> Unit,
) {
    var result by remember { mutableStateOf<FileResult<TextContent>?>(null) }
    LaunchedEffect(Unit) { result = reader.readText(node.id) }
    when (val loaded = result) {
        null ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                AtomicLoading(stringResource(R.string.markdown_preview_loading))
            }
        is FileResult.Failure ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(stringResource(R.string.markdown_preview_failed), color = MaterialTheme.colorScheme.error)
                    if (loaded.error.recoverable) AtomicButton(stringResource(R.string.action_retry), onClick = onRetry)
                }
            }
        is FileResult.Success -> MarkdownDocumentContent(loaded.value, modifier)
    }
}

@Composable
private fun MarkdownDocumentContent(
    content: TextContent,
    modifier: Modifier,
) {
    var isRenderedMode by remember { mutableStateOf(true) }
    Column(modifier = modifier.fillMaxSize()) {
        MarkdownHeaderBar(
            lineCount = content.totalLinesCount,
            isRendered = isRenderedMode,
            onToggleMode = { isRenderedMode = it },
        )
        if (content.isTruncated) {
            Text(
                text = stringResource(R.string.markdown_preview_truncated),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (isRenderedMode) {
            RenderedMarkdownView(lines = content.lines, modifier = Modifier.fillMaxSize())
        } else {
            RawMarkdownView(lines = content.lines, modifier = Modifier.fillMaxSize())
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MarkdownHeaderBar(
    lineCount: Int,
    isRendered: Boolean,
    onToggleMode: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = modifier.fillMaxWidth(),
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = "$lineCount lines",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AtomicChip(label = "Rendered", selected = isRendered, onSelectedChange = { onToggleMode(true) })
                AtomicChip(label = "Raw", selected = !isRendered, onSelectedChange = { onToggleMode(false) })
            }
        }
    }
}

@Composable
private fun RenderedMarkdownView(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    val blocks = remember(lines) { markdownBlocks(lines) }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(blocks) { block ->
            val line = block.text.trimEnd()
            when {
                block.code -> MarkdownCodeBlock(block.text)
                line.startsWith("# ") -> {
                    Text(
                        text = line.removePrefix("# "),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                line.startsWith("## ") -> {
                    Text(
                        text = line.removePrefix("## "),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                line.startsWith("### ") -> {
                    Text(
                        text = line.removePrefix("### "),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                line.startsWith("- ") || line.startsWith("* ") -> {
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Text(text = "• ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Text(
                            text = line.substring(2),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
                line.startsWith("> ") -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = line.removePrefix("> "),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
                line.startsWith("---") || line.startsWith("***") -> {
                    Spacer(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant),
                    )
                }
                line.isBlank() -> {
                    Spacer(modifier = Modifier.height(4.dp))
                }
                else -> {
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownCodeBlock(
    code: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface,
            modifier =
                Modifier
                    .padding(12.dp)
                    .horizontalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun RawMarkdownView(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    var wrapLines by remember { mutableStateOf(false) }
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val contentModifier =
        if (wrapLines) {
            modifier.verticalScroll(verticalScroll)
        } else {
            modifier
                .verticalScroll(verticalScroll)
                .horizontalScroll(horizontalScroll)
        }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            AtomicChip(label = "Wrap", selected = wrapLines, onSelectedChange = { wrapLines = it })
        }

        Row(modifier = contentModifier.padding(8.dp)) {
            Column(
                modifier =
                    Modifier
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp),
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp),
            ) {
                lines.indices.forEach { idx ->
                    Text(
                        text = "${idx + 1}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.padding(vertical = 2.dp)) {
                lines.forEach { line ->
                    Text(
                        text = line.ifEmpty { " " },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}
