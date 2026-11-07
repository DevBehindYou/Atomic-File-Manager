package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ImageViewModesTest {
    private val viewport = Size(200f, 200f)

    @Test
    fun `fit width and actual preview pixels use fitted and decoded dimensions respectively`() {
        val image = Size(100f, 400f)
        assertEquals(1f, ImageViewModes.scale(ImageViewMode.FIT_SCREEN, image, viewport))
        assertEquals(4f, ImageViewModes.scale(ImageViewMode.FIT_WIDTH, image, viewport))
        assertEquals(2f, ImageViewModes.scale(ImageViewMode.PREVIEW_PIXELS, image, viewport))
        assertEquals(
            ImageTransform(4f),
            ImageViewModes.transform(ImageViewState(ImageViewMode.FIT_WIDTH), image, viewport),
        )
    }

    @Test
    fun `actual preview pixels shrink small upscaled images and establish a meaningful pinch floor`() {
        val image = Size(100f, 100f)
        assertEquals(0.5f, ImageViewModes.scale(ImageViewMode.PREVIEW_PIXELS, image, viewport))
        assertEquals(0.5f, ImageViewModes.minimumScale(image, viewport))
        val actual = ImageViewModes.transform(ImageViewState(ImageViewMode.PREVIEW_PIXELS), image, viewport)
        assertEquals(ImageTransform(0.5f), actual)
        assertEquals(
            actual,
            ImageTransformGeometry.update(
                ImageTransform(),
                0.1f,
                Offset(140f, 100f),
                Offset(50f, 30f),
                image,
                viewport,
                minimumScale = ImageViewModes.minimumScale(image, viewport),
            ),
        )
    }

    @Test
    fun `oversized modes are unavailable and never silently called a successful fit width`() {
        val image = Size(1f, 2048f)
        assertNull(ImageViewModes.scale(ImageViewMode.FIT_WIDTH, image, viewport))
        assertNull(ImageViewModes.scale(ImageViewMode.PREVIEW_PIXELS, Size(4096f, 4096f), viewport))
        assertEquals(
            ImageTransform(),
            ImageViewModes.transform(ImageViewState(ImageViewMode.FIT_WIDTH), image, viewport),
        )
    }

    @Test
    fun `width mode follows viewport resize and resumes when its zoom ceiling becomes usable again`() {
        val image = Size(100f, 400f)
        val state = ImageViewState(ImageViewMode.FIT_WIDTH)
        assertEquals(ImageTransform(4f), ImageViewModes.transform(state, image, viewport))
        assertEquals(ImageTransform(), ImageViewModes.transform(state, image, Size(400f, 200f)))
        assertEquals(ImageTransform(4f), ImageViewModes.transform(state, image, viewport))
    }

    @Test
    fun `normalized custom focus scales with fitted image dimensions instead of retaining raw viewport pixels`() {
        val image = Size(400f, 400f)
        val source = ImageTransform(3f, Offset(100f, -50f))
        val state = ImageViewModes.custom(source, image, viewport)
        val resized = ImageViewModes.transform(state, image, Size(300f, 300f))
        assertEquals(3f, resized.scale)
        assertEquals(150f, resized.offset.x, 0.0001f)
        assertEquals(-75f, resized.offset.y, 0.0001f)
        val restored = ImageViewModes.transform(state, image, viewport)
        assertEquals(source.scale, restored.scale)
        assertEquals(source.offset.x, restored.offset.x, 0.0001f)
        assertEquals(source.offset.y, restored.offset.y, 0.0001f)
    }

    @Test
    fun `resize clamps visible pan while retaining the requested source focus for the return orientation`() {
        val image = Size(400f, 400f)
        val portrait = Size(200f, 400f)
        val original = ImageTransform(3f, Offset(150f, 0f))
        val state = ImageViewModes.custom(original, image, portrait)
        assertEquals(Offset(100f, 0f), ImageViewModes.transform(state, image, Size(400f, 200f)).offset)
        assertEquals(original, ImageViewModes.transform(state, image, portrait))
    }

    @Test
    fun `invalid restored values and unknown modes cannot introduce nonfinite or unchecked transforms`() {
        for (values in listOf(
            emptyList(),
            listOf("FUTURE", 2f, 0f, 0f),
            listOf("CUSTOM", Float.NaN, 0f, 0f),
            listOf("CUSTOM", 2f, Float.POSITIVE_INFINITY, 0f),
            listOf("CUSTOM", 0f, 0f, 0f),
        )) {
            assertEquals(ImageViewState(), ImageViewModes.restore(values))
        }
        val clamped = ImageViewModes.restore(listOf("CUSTOM", 100f, -90f, 90f))
        assertEquals(ImageViewState(ImageViewMode.CUSTOM, 6f, Offset(-0.5f, 0.5f)), clamped)
    }

    @Test
    fun `empty viewports have no enabled size modes and cannot create a custom invalid focus`() {
        val image = Size(100f, 400f)
        assertNull(ImageViewModes.scale(ImageViewMode.FIT_WIDTH, image, Size.Zero))
        assertEquals(
            ImageTransform(),
            ImageViewModes.transform(ImageViewState(ImageViewMode.CUSTOM, 4f), image, Size.Zero),
        )
        assertEquals(ImageViewState(), ImageViewModes.custom(ImageTransform(3f), image, Size.Zero))
        assertEquals(ImageViewState(), ImageViewModes.custom(ImageTransform(), image, viewport))
    }
}
