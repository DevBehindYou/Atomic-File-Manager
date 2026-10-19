package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.components.ViewerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun PdfPreviewContent(
    node: FileNode,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    key(node.id) { PdfReader(node, container, modifier) }
}

private data class PdfPageRequest(val index: Int, val retry: Int = 0)

@Composable
private fun PdfReader(
    node: FileNode,
    container: AppContainer,
    modifier: Modifier,
) {
    var page by rememberSaveable(node.id.raw) { mutableIntStateOf(0) }
    var reopen by remember { mutableIntStateOf(0) }
    var pageCount by remember { mutableIntStateOf(0) }
    var openError by remember { mutableStateOf<FileError?>(null) }
    var opening by remember { mutableStateOf(true) }
    var jump by rememberSaveable(node.id.raw) { mutableStateOf("") }
    val requests = remember { MutableStateFlow(PdfPageRequest(page)) }
    val pages = remember(reopen) { PdfPageController<Bitmap>() }
    val state by pages.state.collectAsState()

    LaunchedEffect(node.id, reopen) {
        opening = true
        openError = null
        pageCount = 0
        val result =
            container.pdfPreviewHelper.withDocument(node.id) { document ->
                pageCount = document.pageCount
                page = page.coerceIn(0, pageCount - 1)
                requests.value = PdfPageRequest(page)
                opening = false
                requests.collectLatest { pages.show(document, it.index) }
            }
        if (result is FileResult.Failure) {
            openError = result.error
            opening = false
        }
    }

    fun request(index: Int) {
        page = index.coerceIn(0, pageCount - 1)
        requests.value = PdfPageRequest(page, requests.value.retry + 1)
    }

    Column(modifier.fillMaxSize().testTag("pdf_reader")) {
        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds(), contentAlignment = Alignment.Center) {
            when {
                opening -> ViewerState(loading = true, error = null)
                openError != null ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AtomicText(
                            stringResource(
                                if (openError is FileError.FileTooLarge) {
                                    R.string.pdf_too_large
                                } else {
                                    R.string.pdf_open_error
                                },
                            ),
                            AtomicTextRole.Body,
                        )
                        AtomicButton(stringResource(R.string.pdf_retry), onClick = { reopen++ })
                    }
                state.loading -> ViewerState(loading = true, error = null)
                state.failed ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AtomicText(stringResource(R.string.pdf_page_error), AtomicTextRole.Body)
                        AtomicButton(stringResource(R.string.pdf_retry), onClick = { request(page) })
                    }
                else ->
                    state.rendered?.let { rendered ->
                        key(rendered.index, reopen) { PdfZoomablePage(rendered.image, rendered.index) }
                    }
            }
        }
        if (pageCount > 0 && openError == null) {
            // The label belongs to the displayed bitmap, never to an uncompleted request.
            val displayedIndex = state.rendered?.index ?: state.requested
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtomicIconButton(
                    AtomicIcons.Back,
                    stringResource(R.string.pdf_previous),
                    { request(page - 1) },
                    enabled = page > 0,
                )
                AtomicText(
                    stringResource(R.string.pdf_page_label, displayedIndex + 1, pageCount),
                    AtomicTextRole.MonoLabel,
                )
                AtomicIconButton(AtomicIcons.Forward, stringResource(R.string.pdf_next), {
                    request(page + 1)
                }, enabled = page < pageCount - 1)
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = AtomicSpacing.s16, vertical = AtomicSpacing.s4),
                horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AtomicTextField(jump, {
                    jump = it.filter(Char::isDigit).take(9)
                }, label = stringResource(R.string.pdf_go_to_page), modifier = Modifier.weight(1f))
                val target = jump.toIntOrNull()?.takeIf { it in 1..pageCount }
                AtomicButton(
                    stringResource(R.string.pdf_go),
                    onClick = { target?.let { request(it - 1) } },
                    enabled = target != null,
                    variant = AtomicButtonVariant.Text,
                )
            }
        }
    }
}

@Composable
private fun PdfZoomablePage(
    bitmap: Bitmap,
    index: Int,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val description = stringResource(R.string.pdf_page_description, index + 1)
    Image(
        bitmap.asImageBitmap(),
        contentDescription = description,
        modifier =
            Modifier.fillMaxSize().testTag("pdf_page")
                .pointerInput(index) {
                    detectTapGestures(onDoubleTap = {
                        zoom = if (zoom > 1f) 1f else 2.5f
                        offset = Offset.Zero
                    })
                }
                .pointerInput(index) {
                    detectTransformGestures { _, pan, scale, _ ->
                        zoom = (zoom * scale).coerceIn(1f, 6f)
                        val maxX = (zoom - 1f) * size.width / 2
                        val maxY = (zoom - 1f) * size.height / 2
                        offset =
                            Offset(
                                (offset.x + pan.x).coerceIn(-maxX, maxX),
                                (offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                    }
                }
                .graphicsLayer(scaleX = zoom, scaleY = zoom, translationX = offset.x, translationY = offset.y),
    )
}
