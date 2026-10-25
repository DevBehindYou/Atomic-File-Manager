package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import kotlin.math.min

internal data class ImageTransform(val scale: Float = 1f, val offset: Offset = Offset.Zero)

/** Geometry for a centered ContentScale.Fit image, in viewport pixels. */
internal object ImageTransformGeometry {
    fun fittedSize(
        image: Size,
        viewport: Size,
    ): Size {
        if (image.width <= 0f || image.height <= 0f || viewport.width <= 0f || viewport.height <= 0f) return Size.Zero
        val ratio = min(viewport.width / image.width, viewport.height / image.height)
        return Size(image.width * ratio, image.height * ratio)
    }

    fun clamp(
        offset: Offset,
        scale: Float,
        image: Size,
        viewport: Size,
    ): Offset {
        val fitted = fittedSize(image, viewport)
        val xLimit = ((fitted.width * scale - viewport.width) / 2f).coerceAtLeast(0f)
        val yLimit = ((fitted.height * scale - viewport.height) / 2f).coerceAtLeast(0f)
        return Offset(offset.x.coerceIn(-xLimit, xLimit), offset.y.coerceIn(-yLimit, yLimit))
    }

    /** Keep the image point under the previous centroid, then apply its viewport-space pan. */
    fun update(
        current: ImageTransform,
        targetScale: Float,
        centroid: Offset,
        pan: Offset,
        image: Size,
        viewport: Size,
    ): ImageTransform {
        // Pointer replacement can leave no pointer pressed in both event samples.
        // calculateCentroid then returns Unspecified; wait for a stable sample.
        if (centroid == Offset.Unspecified || !centroid.x.isFinite() || !centroid.y.isFinite()) return current
        val scale = targetScale.coerceIn(1f, 6f)
        if (scale == 1f) return ImageTransform()
        val focus = centroid - Offset(viewport.width / 2f, viewport.height / 2f)
        val ratio = scale / current.scale
        val offset = focus - (focus - current.offset) * ratio + pan
        return ImageTransform(scale, clamp(offset, scale, image, viewport))
    }
}
