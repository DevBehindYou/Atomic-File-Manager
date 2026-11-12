package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

internal enum class ImageViewMode { FIT_SCREEN, FIT_WIDTH, PREVIEW_PIXELS, CUSTOM }

/** Save coordinates/mode only, never the bitmap, source bytes, paths or a persisted preference. */
internal data class ImageViewState(
    val mode: ImageViewMode = ImageViewMode.FIT_SCREEN,
    val scale: Float = 1f,
    val offsetFraction: Offset = Offset.Zero,
)

/** Modes refer to the sampled, EXIF-oriented preview, not the original file's pixel dimensions. */
internal object ImageViewModes {
    const val MAX_SCALE = 6f

    val Saver =
        listSaver<ImageViewState, Any>(
            save = { listOf(it.mode.name, it.scale, it.offsetFraction.x, it.offsetFraction.y) },
            restore = ::restore,
        )

    fun restore(values: List<Any>): ImageViewState {
        val mode = ImageViewMode.entries.firstOrNull { it.name == values.getOrNull(0) } ?: return ImageViewState()
        val scale = values.getOrNull(1) as? Float ?: return ImageViewState()
        val x = values.getOrNull(2) as? Float ?: return ImageViewState()
        val y = values.getOrNull(3) as? Float ?: return ImageViewState()
        if (!scale.isFinite() || scale <= 0 || !x.isFinite() || !y.isFinite()) return ImageViewState()
        return ImageViewState(
            mode,
            scale.coerceAtMost(MAX_SCALE),
            Offset(x.coerceIn(-0.5f, 0.5f), y.coerceIn(-0.5f, 0.5f)),
        )
    }

    fun scale(
        mode: ImageViewMode,
        image: Size,
        viewport: Size,
    ): Float? {
        val fitted = ImageTransformGeometry.fittedSize(image, viewport)
        if (fitted.width <= 0f || fitted.height <= 0f) return null
        val target =
            when (mode) {
                ImageViewMode.FIT_SCREEN -> 1f
                ImageViewMode.FIT_WIDTH -> viewport.width / fitted.width
                ImageViewMode.PREVIEW_PIXELS -> image.width / fitted.width
                ImageViewMode.CUSTOM -> return null
            }
        return target.takeIf { it.isFinite() && it > 0f && it <= MAX_SCALE }
    }

    fun minimumScale(
        image: Size,
        viewport: Size,
    ): Float = scale(ImageViewMode.PREVIEW_PIXELS, image, viewport)?.coerceAtMost(1f) ?: 1f

    fun transform(
        state: ImageViewState,
        image: Size,
        viewport: Size,
    ): ImageTransform {
        val fitted = ImageTransformGeometry.fittedSize(image, viewport)
        if (fitted.width <= 0f || fitted.height <= 0f) return ImageTransform()
        if (state.mode != ImageViewMode.CUSTOM) return ImageTransform(scale(state.mode, image, viewport) ?: 1f)
        val zoom = state.scale.coerceIn(minimumScale(image, viewport), MAX_SCALE)
        val offset = Offset(state.offsetFraction.x * fitted.width * zoom, state.offsetFraction.y * fitted.height * zoom)
        return ImageTransform(zoom, ImageTransformGeometry.clamp(offset, zoom, image, viewport))
    }

    /** A relative image-space translation keeps the requested image centre across viewport changes. */
    fun custom(
        transform: ImageTransform,
        image: Size,
        viewport: Size,
    ): ImageViewState {
        if (transform.scale == 1f) return ImageViewState()
        val fitted = ImageTransformGeometry.fittedSize(image, viewport)
        if (fitted.width <= 0f || fitted.height <= 0f) return ImageViewState()
        return ImageViewState(
            ImageViewMode.CUSTOM,
            transform.scale,
            Offset(
                transform.offset.x / (fitted.width * transform.scale),
                transform.offset.y / (fitted.height * transform.scale),
            ),
        )
    }
}
