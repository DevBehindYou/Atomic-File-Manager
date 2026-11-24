package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicIconButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextField
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicShape
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.data.preview.PDF_PASSWORD_PROTECTED
import com.devbehindyou.atomicfilemanager.data.preview.PdfPreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.ui.components.ViewerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

@Composable
fun PdfPreviewContent(
    node: FileNode,
    container: AppContainer,
    modifier: Modifier = Modifier,
) {
    val helper = container.pdfPreviewHelper
    // A known revision/helper owns pages, failures and retries before any old raster can draw.
    key(node.id, node.modifiedAt, node.size, helper) { PdfReader(node, helper, modifier) }
}

/** Retrying cannot unlock a password, so only that open failure omits Retry. */
internal fun FileError.isPdfPasswordProtected(): Boolean =
    this is FileError.UnsupportedFormat && mimeType == PDF_PASSWORD_PROTECTED

internal fun pdfOpenErrorText(error: FileError): Int =
    when {
        error is FileError.FileTooLarge -> R.string.pdf_too_large
        error.isPdfPasswordProtected() -> R.string.pdf_password_protected
        else -> R.string.pdf_open_error
    }

private data class PdfPageRequest(val index: Int, val retry: Int = 0)

@Composable
private fun PdfReader(
    node: FileNode,
    helper: PdfPreviewHelper,
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
    val rendered = state.rendered
    val transform = remember(rendered?.index, reopen) { PdfPageTransform() }

    LaunchedEffect(node.id, reopen) {
        opening = true
        openError = null
        pageCount = 0
        val result =
            helper.withDocument(node.id) { document ->
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

    Column(
        modifier.fillMaxSize().padding(horizontal = AtomicSpacing.s16).testTag("pdf_reader"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.weight(1f).widthIn(max = 720.dp).fillMaxWidth()
                .clip(AtomicShape.lg)
                .background(Atomic.colors.surfaceInset)
                .padding(AtomicSpacing.s12)
                .clipToBounds()
                .testTag("pdf_reading_stage"),
            contentAlignment = Alignment.Center,
        ) {
            when {
                opening -> ViewerState(loading = true, error = null)
                openError != null ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val error = checkNotNull(openError)
                        AtomicText(stringResource(pdfOpenErrorText(error)), AtomicTextRole.Body)
                        if (!error.isPdfPasswordProtected()) {
                            AtomicButton(stringResource(R.string.pdf_retry), onClick = { reopen++ })
                        }
                    }
                state.loading -> ViewerState(loading = true, error = null)
                state.failed ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AtomicText(stringResource(R.string.pdf_page_error), AtomicTextRole.Body)
                        AtomicButton(stringResource(R.string.pdf_retry), onClick = { request(page) })
                    }
                else ->
                    rendered?.let { page ->
                        key(page.index, reopen) { PdfZoomablePage(page.image, page.index, transform) }
                    }
            }
        }
        if (pageCount > 0 && openError == null) {
            // The label belongs to the displayed bitmap, never to an uncompleted request.
            val displayedIndex = state.rendered?.index ?: state.requested
            Row(
                Modifier.widthIn(max = 520.dp).fillMaxWidth(),
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
                Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(vertical = AtomicSpacing.s4),
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
            PdfZoomToolbar(transform, enabled = rendered != null)
        }
    }
}

private const val PDF_MIN_ZOOM = 1f
private const val PDF_MAX_ZOOM = 6f
private const val PDF_ZOOM_STEP = 0.25f

private class PdfPageTransform {
    var zoom by mutableFloatStateOf(PDF_MIN_ZOOM)
    var offset by mutableStateOf(Offset.Zero)

    fun zoomTo(value: Float) {
        zoom = value.coerceIn(PDF_MIN_ZOOM, PDF_MAX_ZOOM)
        offset = Offset.Zero
    }
}

@Composable
private fun PdfZoomToolbar(
    transform: PdfPageTransform,
    enabled: Boolean,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = AtomicSpacing.s4).testTag("pdf_zoom_controls"),
        horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8, Alignment.CenterHorizontally),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        AtomicButton(
            stringResource(R.string.pdf_zoom_out),
            onClick = { transform.zoomTo(transform.zoom - PDF_ZOOM_STEP) },
            variant = AtomicButtonVariant.Ghost,
            enabled = enabled && transform.zoom > PDF_MIN_ZOOM,
        )
        AtomicText(
            stringResource(R.string.pdf_zoom_percent, (transform.zoom * 100).roundToInt()),
            AtomicTextRole.MonoMeta,
        )
        AtomicButton(
            stringResource(R.string.pdf_zoom_in),
            onClick = { transform.zoomTo(transform.zoom + PDF_ZOOM_STEP) },
            variant = AtomicButtonVariant.Ghost,
            enabled = enabled && transform.zoom < PDF_MAX_ZOOM,
        )
    }
}

@Composable
private fun PdfZoomablePage(
    bitmap: Bitmap,
    index: Int,
    transform: PdfPageTransform,
) {
    val description = stringResource(R.string.pdf_page_description, index + 1)
    Image(
        bitmap.asImageBitmap(),
        contentDescription = description,
        modifier =
            Modifier.widthIn(max = 520.dp)
                .aspectRatio(bitmap.width.toFloat() / bitmap.height)
                .testTag("pdf_page")
                .pointerInput(index) {
                    detectTapGestures(onDoubleTap = {
                        transform.zoomTo(if (transform.zoom > PDF_MIN_ZOOM) PDF_MIN_ZOOM else 2.5f)
                    })
                }
                .pointerInput(index) {
                    detectTransformGestures { _, pan, scale, _ ->
                        transform.zoom = (transform.zoom * scale).coerceIn(PDF_MIN_ZOOM, PDF_MAX_ZOOM)
                        val maxX = (transform.zoom - PDF_MIN_ZOOM) * size.width / 2
                        val maxY = (transform.zoom - PDF_MIN_ZOOM) * size.height / 2
                        transform.offset =
                            Offset(
                                (transform.offset.x + pan.x).coerceIn(-maxX, maxX),
                                (transform.offset.y + pan.y).coerceIn(-maxY, maxY),
                            )
                    }
                }
                .graphicsLayer {
                    scaleX = transform.zoom
                    scaleY = transform.zoom
                    translationX = transform.offset.x
                    translationY = transform.offset.y
                    shape = AtomicShape.xs
                    clip = true
                }.background(Color.White),
    )
}
