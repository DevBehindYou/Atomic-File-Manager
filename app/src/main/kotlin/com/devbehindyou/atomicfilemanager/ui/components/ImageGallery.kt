package com.devbehindyou.atomicfilemanager.ui.components

import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.toSize
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.Atomic
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicChip
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicText
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicTextRole
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.data.preview.ImagePreviewHelper
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.normalizedFileMime

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
            isActive = page == pager.currentPage,
        )
    }
}

/** One image with pinch, pan and double-tap zoom. Used alone, or as a page of [ImageGallery]. */
@Composable
internal fun ZoomableImage(
    node: FileNode,
    container: AppContainer,
    onZoomChange: (Boolean) -> Unit = {},
    isActive: Boolean = true,
) {
    val helper = container.imagePreviewHelper
    // A source revision owns its loading state and transforms before its first frame is drawn.
    // Restarting only produceState's producer would leave the previous bitmap in its remembered State.
    key(node.id, node.modifiedAt, node.size, helper) {
        var attempt by remember { mutableIntStateOf(0) }
        key(attempt) {
            LaunchedEffect(Unit) { onZoomChange(false) }
            if (Gallery.isGif(node)) {
                AnimatedGifImage(node, helper, isActive, onZoomChange, onRetry = { attempt++ })
            } else {
                ZoomableImageAttempt(node, helper, onZoomChange, isActive, onRetry = { attempt++ })
            }
        }
    }
}

@Composable
private fun ZoomableImageAttempt(
    node: FileNode,
    helper: ImagePreviewHelper,
    onZoomChange: (Boolean) -> Unit,
    isActive: Boolean,
    onRetry: () -> Unit,
) {
    val decoded by produceState<FileResult<Bitmap>?>(null) {
        value = helper.decodeImage(node.id)
    }

    val bitmap =
        when (val result = decoded) {
            null -> {
                ViewerState(loading = true, error = null)
                return
            }
            is FileResult.Failure -> {
                ImageLoadError(result.error, onRetry)
                return
            }
            is FileResult.Success -> result.value
        }

    ZoomableStillImageSurface(
        node,
        Size(bitmap.width.toFloat(), bitmap.height.toFloat()),
        onZoomChange,
        isActive,
    ) { modifier ->
        Image(bitmap.asImageBitmap(), node.name, modifier = modifier, contentScale = ContentScale.Fit)
    }
}

@Composable
internal fun ZoomableImageSurface(
    node: FileNode,
    imageSize: Size,
    onZoomChange: (Boolean) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    var transform by remember { mutableStateOf(ImageTransform()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    ImageGestureSurface(
        node,
        transform,
        currentTransform = { transform },
        onViewportChange = { size ->
            viewport = size
            transform =
                transform.copy(
                    offset = ImageTransformGeometry.clamp(transform.offset, transform.scale, imageSize, size.toSize()),
                )
        },
        onTransform = { value, focus, pan ->
            transform = ImageTransformGeometry.update(transform, value, focus, pan, imageSize, viewport.toSize())
            onZoomChange(transform.scale > 1f)
        },
        content = content,
    )
}

@Composable
private fun ZoomableStillImageSurface(
    node: FileNode,
    imageSize: Size,
    onZoomChange: (Boolean) -> Unit,
    isActive: Boolean,
    content: @Composable (Modifier) -> Unit,
) {
    var view by rememberSaveable(stateSaver = ImageViewModes.Saver) { mutableStateOf(ImageViewState()) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    fun currentTransform(): ImageTransform = ImageViewModes.transform(view, imageSize, viewport.toSize())

    val transform = currentTransform()
    LaunchedEffect(isActive, transform.scale) {
        if (isActive) onZoomChange(transform.scale > 1f)
    }
    Box(Modifier.fillMaxSize().clipToBounds()) {
        ImageGestureSurface(
            node,
            transform,
            currentTransform = ::currentTransform,
            onViewportChange = { viewport = it },
            onTransform = { value, focus, pan ->
                val viewportSize = viewport.toSize()
                val updated =
                    ImageTransformGeometry.update(
                        currentTransform(),
                        value,
                        focus,
                        pan,
                        imageSize,
                        viewportSize,
                        minimumScale = ImageViewModes.minimumScale(imageSize, viewportSize),
                    )
                view = ImageViewModes.custom(updated, imageSize, viewportSize)
                onZoomChange(updated.scale > 1f)
            },
            content = content,
        )
        // A sibling above the image pointer target keeps control gestures separate from image pan.
        ImageViewControls(
            view,
            imageSize,
            viewport.toSize(),
            onSelect = { mode ->
                view = ImageViewState(mode)
                onZoomChange((ImageViewModes.scale(mode, imageSize, viewport.toSize()) ?: 1f) > 1f)
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun ImageGestureSurface(
    node: FileNode,
    transform: ImageTransform,
    currentTransform: () -> ImageTransform,
    onViewportChange: (IntSize) -> Unit,
    onTransform: (Float, Offset, Offset) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged(onViewportChange)
                .pointerInput(node.id) {
                    detectTapGestures(onDoubleTap = { tap ->
                        onTransform(if (currentTransform().scale > 1f) 1f else DOUBLE_TAP_ZOOM, tap, Offset.Zero)
                    })
                }
                .pointerInput(node.id) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val fingers = event.changes.count { it.pressed }
                            // Leave one finger on a fitted or smaller image to the pager.
                            val scale = currentTransform().scale
                            if (fingers > 1 || (fingers == 1 && scale > 1f)) {
                                onTransform(
                                    scale * event.calculateZoom(),
                                    event.calculateCentroid(useCurrent = false),
                                    event.calculatePan(),
                                )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        content(
            Modifier.fillMaxSize().graphicsLayer(
                scaleX = transform.scale,
                scaleY = transform.scale,
                translationX = transform.offset.x,
                translationY = transform.offset.y,
            ),
        )
    }
}

@Composable
private fun ImageViewControls(
    state: ImageViewState,
    image: Size,
    viewport: Size,
    onSelect: (ImageViewMode) -> Unit,
    modifier: Modifier,
) {
    val widthAvailable = ImageViewModes.scale(ImageViewMode.FIT_WIDTH, image, viewport) != null
    val pixelsAvailable = ImageViewModes.scale(ImageViewMode.PREVIEW_PIXELS, image, viewport) != null
    val shownMode =
        if (state.mode != ImageViewMode.CUSTOM && ImageViewModes.scale(state.mode, image, viewport) == null) {
            ImageViewMode.FIT_SCREEN
        } else {
            state.mode
        }
    Column(modifier.fillMaxWidth().background(Atomic.colors.background.copy(alpha = 0.94f))) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AtomicSpacing.s8),
            horizontalArrangement = Arrangement.spacedBy(AtomicSpacing.s8),
        ) {
            AtomicChip(
                stringResource(R.string.image_view_fit),
                shownMode == ImageViewMode.FIT_SCREEN,
                onSelectedChange = { onSelect(ImageViewMode.FIT_SCREEN) },
                modifier = Modifier.testTag("image_mode_fit"),
            )
            AtomicChip(
                stringResource(R.string.image_view_width),
                shownMode == ImageViewMode.FIT_WIDTH,
                onSelectedChange = { onSelect(ImageViewMode.FIT_WIDTH) },
                enabled = widthAvailable,
                modifier = Modifier.testTag("image_mode_width"),
            )
            AtomicChip(
                stringResource(R.string.image_view_pixels),
                shownMode == ImageViewMode.PREVIEW_PIXELS,
                onSelectedChange = { onSelect(ImageViewMode.PREVIEW_PIXELS) },
                enabled = pixelsAvailable,
                modifier = Modifier.testTag("image_mode_pixels"),
            )
        }
        if ((!widthAvailable || !pixelsAvailable) && viewport.width > 0f && viewport.height > 0f) {
            AtomicText(
                stringResource(R.string.image_view_zoom_limit),
                AtomicTextRole.BodySecondary,
                modifier = Modifier.padding(AtomicSpacing.s8).testTag("image_mode_limit"),
            )
        }
    }
}

@Composable
internal fun ImageLoadError(
    error: FileError,
    onRetry: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().padding(AtomicSpacing.s16),
        verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s16, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AtomicText(stringResource(imageErrorMessage(error)), AtomicTextRole.BodySecondary)
        if (error.recoverable) AtomicButton(stringResource(R.string.action_retry), onClick = onRetry)
    }
}

@StringRes
private fun imageErrorMessage(error: FileError): Int =
    when (error) {
        is FileError.ProviderUnavailable, is FileError.StorageUnavailable -> R.string.image_load_storage_unavailable
        is FileError.PermissionDenied, is FileError.AccessDenied, FileError.PlatformRestricted ->
            R.string.image_load_access_denied
        is FileError.FileNotFound -> R.string.image_load_missing
        is FileError.UnsupportedFormat -> R.string.image_load_unsupported
        is FileError.FileTooLarge -> R.string.image_load_too_large
        FileError.OutOfMemory -> R.string.image_load_memory
        else -> R.string.image_load_failed
    }

/** Gallery rules; pure so they are unit-tested. */
internal object Gallery {
    fun isGif(node: FileNode): Boolean =
        !node.isDirectory &&
            (node.name.endsWith(".gif", ignoreCase = true) || normalizedFileMime(node.mimeType) == "image/gif")

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
