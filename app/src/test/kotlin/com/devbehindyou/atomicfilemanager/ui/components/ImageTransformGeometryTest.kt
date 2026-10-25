package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImageTransformGeometryTest {
    private val viewport = Size(200f, 200f)
    private val square = Size(100f, 100f)

    @Test
    fun `wide image is fitted without inventing pixels in its letterbox`() {
        assertEquals(Size(200f, 50f), ImageTransformGeometry.fittedSize(Size(400f, 100f), viewport))
        assertEquals(
            Offset(150f, 0f),
            ImageTransformGeometry.clamp(Offset(900f, 900f), 2.5f, Size(400f, 100f), viewport),
        )
    }

    @Test
    fun `tall image cannot pan along its letterboxed short axis`() {
        assertEquals(Size(50f, 200f), ImageTransformGeometry.fittedSize(Size(100f, 400f), viewport))
        assertEquals(
            Offset(0f, -150f),
            ImageTransformGeometry.clamp(Offset(-900f, -900f), 2.5f, Size(100f, 400f), viewport),
        )
    }

    @Test
    fun `double tap keeps an off center image point at the tap`() {
        val result =
            ImageTransformGeometry.update(
                ImageTransform(),
                2.5f,
                Offset(140f, 100f),
                Offset.Zero,
                square,
                viewport,
            )
        assertEquals(ImageTransform(2.5f, Offset(-60f, 0f)), result)
        assertEquals(40f, (140f - 100f - result.offset.x) / result.scale)
    }

    @Test
    fun `pinch from existing zoom preserves the image point under a moving centroid`() {
        val result =
            ImageTransformGeometry.update(
                ImageTransform(2f, Offset(-20f, 10f)),
                3f,
                Offset(130f, 120f),
                Offset(5f, -4f),
                square,
                viewport,
            )
        assertEquals(25f, (135f - 100f - result.offset.x) / result.scale)
        assertEquals(5f, (116f - 100f - result.offset.y) / result.scale)
    }

    @Test
    fun `zoom out to fit resets pan completely`() {
        assertEquals(
            ImageTransform(),
            ImageTransformGeometry.update(
                ImageTransform(4f, Offset(100f, -80f)),
                0.5f,
                Offset(170f, 120f),
                Offset(20f, 5f),
                square,
                viewport,
            ),
        )
    }

    @Test
    fun `zoom ceiling applies before focal point calculation`() {
        val result =
            ImageTransformGeometry.update(
                ImageTransform(),
                20f,
                Offset(120f, 100f),
                Offset.Zero,
                square,
                viewport,
            )
        assertEquals(ImageTransform(6f, Offset(-100f, 0f)), result)
    }

    @Test
    fun `viewport aspect change removes newly invalid translation`() {
        val offset = ImageTransformGeometry.clamp(Offset(100f, 0f), 2f, square, Size(200f, 400f))
        assertEquals(Offset(100f, 0f), offset)
        assertEquals(Offset.Zero, ImageTransformGeometry.clamp(offset, 2f, square, Size(400f, 200f)))
    }

    @Test
    fun `short axis becomes pannable only when drawn pixels exceed viewport`() {
        assertEquals(Offset(0f, 0f), ImageTransformGeometry.clamp(Offset(0f, 100f), 4f, Size(400f, 100f), viewport))
        assertEquals(Offset(0f, 25f), ImageTransformGeometry.clamp(Offset(0f, 100f), 5f, Size(400f, 100f), viewport))
    }

    @Test
    fun `pointer replacement without a continuing centroid preserves transform until stable input`() {
        val current = ImageTransform(2f, Offset(-20f, 10f))
        val ignored = ImageTransformGeometry.update(current, 2f, Offset.Unspecified, Offset.Zero, square, viewport)
        assertEquals(current, ignored)
        val continued = ImageTransformGeometry.update(ignored, 3f, Offset(140f, 100f), Offset.Zero, square, viewport)
        assertEquals(ImageTransform(3f, Offset(-50f, 15f)), continued)
    }

    @Test
    fun `nonfinite centroid cannot poison subsequent translations`() {
        val current = ImageTransform(2f, Offset(-20f, 10f))
        for (centroid in listOf(Offset(Float.POSITIVE_INFINITY, 100f), Offset(100f, Float.NaN))) {
            assertEquals(current, ImageTransformGeometry.update(current, 2f, centroid, Offset.Zero, square, viewport))
        }
    }
}
