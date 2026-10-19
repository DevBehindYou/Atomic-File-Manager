package com.devbehindyou.atomicfilemanager.ui.components

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.testTag
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult

private const val MAX_ZOOM = 6f
private const val DOUBLE_TAP_ZOOM = 2.5f

/**
 * Swipes through the images of one folder (ALL_IN_ONE_PLAN.md 1.5). Each page pinches and
 * double-taps to zoom; while a page is zoomed, one-finger drags pan it instead of turning the page.
 */
@Composable
internal fun ImageGallery(
    images: List<FileNode>,
    startIndex: Int,
    container: AppContainer,
    onPageChange: (FileNode) -> Unit,
) {
    val pager = rememberPagerState(initialPage = startIndex) { images.size }
    var zoomed by remember { mutableStateOf(false) }
    LaunchedEffect(pager, images) {
        snapshotFlow { pager.currentPage }.collect { page -> images.getOrNull(page)?.let(onPageChange) }
    }
    HorizontalPager(
        state = pager,
        userScrollEnabled = !zoomed,
        beyondViewportPageCount = 1,
        key = { images[it].id.raw },
        modifier = Modifier.fillMaxSize().testTag("image_gallery"),
    ) { page ->
        ZoomableImage(
            node = images[page],
            container = container,
            onZoomChange = { if (page == pager.currentPage) zoomed = it },
        )
    }
}

/** One image with pinch, pan and double-tap zoom. Used alone, or as a page of [ImageGallery]. */
@Composable
internal fun ZoomableImage(
    node: FileNode,
    container: AppContainer,
    onZoomChange: (Boolean) -> Unit = {},
) {
    val decoded by produceState<Pair<Boolean, Bitmap?>>(false to null, node.id) {
        value = true to (container.imagePreviewHelper.decodeImage(node.id) as? FileResult.Success)?.value
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val (loaded, bitmap) = decoded
    if (!loaded || bitmap == null) {
        ViewerState(
            loading = !loaded,
            error = "The image may be damaged or in a format this phone can't decode.".takeIf { loaded },
        )
        return
    }

    fun zoomTo(value: Float) {
        scale = value.coerceIn(1f, MAX_ZOOM)
        if (scale == 1f) offset = Offset.Zero
        onZoomChange(scale > 1f)
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .pointerInput(node.id) {
                    detectTapGestures(onDoubleTap = { zoomTo(if (scale > 1f) 1f else DOUBLE_TAP_ZOOM) })
                }
                .pointerInput(node.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            // One finger on an unzoomed image is left to the pager, so it turns the page.
                            if (fingers > 1 || scale > 1f) {
                                zoomTo(scale * event.calculateZoom())
                                val pan = event.calculatePan()
                                offset =
                                    Offset(
                                        Gallery.clampPan(offset.x + pan.x, scale, size.width.toFloat()),
                                        Gallery.clampPan(offset.y + pan.y, scale, size.height.toFloat()),
                                    )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = node.name,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        )
    }
}

/** Gallery rules; pure so they are unit-tested. */
internal object Gallery {
    private val imageExtensions = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif", "avif")

    fun isImage(node: FileNode): Boolean =
        !node.isDirectory &&
            (
                node.mimeType.orEmpty().lowercase().startsWith("image/") ||
                    node.name.substringAfterLast('.', "").lowercase() in imageExtensions
            )

    /**
     * The images to swipe through and where [opened] sits among them, in the order given.
     * [opened] is always included, even when [siblings] doesn't list it.
     */
    fun of(
        siblings: List<FileNode>,
        opened: FileNode,
    ): Pair<List<FileNode>, Int> {
        val images = siblings.filter(::isImage)
        val index = images.indexOfFirst { it.id == opened.id }
        return if (index >= 0) images to index else listOf(opened) to 0
    }

    /** Keeps a zoomed image's edge from being dragged inside the viewport. */
    fun clampPan(
        value: Float,
        scale: Float,
        extent: Float,
    ): Float {
        val limit = (scale - 1f).coerceAtLeast(0f) * extent / 2f
        return value.coerceIn(-limit, limit)
    }

    fun position(
        index: Int,
        count: Int,
    ): String = "${index + 1} of $count"
}
