package com.devbehindyou.atomicfilemanager.data.preview

import android.app.Application
import coil3.size.Dimension
import coil3.size.Precision
import com.devbehindyou.atomicfilemanager.core.designsystem.icons.AtomicIcons
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.ui.components.iconFor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 34], application = Application::class)
class ThumbnailSourceTest {
    @Test
    fun contentSources() {
        val document = "content://documents.example/tree/root%3Afolder/document/root%3Afolder%2Fphoto%20%2B.jpg"
        assertEquals(document, thumbnailSourceOf(thumbnailNode(FileNodeId.saf(document)))?.data.toString())
        val single = "content://documents.example/document/opaque%2Fid"
        assertEquals(single, thumbnailSourceOf(thumbnailNode(FileNodeId.saf(single)))?.data.toString())
        assertEquals(
            "content://media/external/images/media/7",
            thumbnailSourceOf(thumbnailNode(FileNodeId.media("external", "images", 7)))?.data.toString(),
        )
        assertEquals(
            "content://media/external_primary/video/media/8",
            thumbnailSourceOf(
                thumbnailNode(FileNodeId.media("external_primary", "video", 8), "clip.mp4"),
            )?.data.toString(),
        )
    }

    @Test
    fun unsupportedAndPrivateSources() {
        assertNull(thumbnailSourceOf(thumbnailNode(FileNodeId.ftp("server", "/photo.jpg"))))
        assertNull(thumbnailSourceOf(thumbnailNode(FileNodeId("saf:broken%uri"))))
        assertNull(thumbnailSourceOf(thumbnailNode(FileNodeId("media:external:images:bad"))))
        assertNull(thumbnailSourceOf(thumbnailNode(FileNodeId.media("external", "images", -1))))
        assertNull(thumbnailSourceOf(thumbnailNode().copy(isDirectory = true)))
        assertNull(thumbnailSourceOf(thumbnailNode().copy(isHidden = true)))
        assertNull(thumbnailSourceOf(thumbnailNode(name = ".private.jpg")))
        assertNull(thumbnailSourceOf(thumbnailNode().copy(access = AccessFlags(false, false, false, false))))
    }

    @Test
    fun genericMimeUsesExtensionWithoutOverridingKnownTypes() {
        assertEquals(ThumbnailKind.IMAGE, thumbnailKind(thumbnailNode(name = "PHOTO.JPG")))
        assertEquals(
            ThumbnailKind.VIDEO,
            thumbnailKind(thumbnailNode(name = "clip.MP4", mime = "application/octet-stream")),
        )
        assertNull(thumbnailKind(thumbnailNode(name = "photo.jpg", mime = "text/plain")))
        assertNull(thumbnailKind(thumbnailNode(name = "unknown.bin", mime = "application/octet-stream")))
        assertSame(AtomicIcons.Image, iconFor(thumbnailNode(name = "PHOTO.JPG")))
        assertSame(AtomicIcons.Video, iconFor(thumbnailNode(name = "clip.mp4", mime = "application/octet-stream")))
        assertSame(AtomicIcons.AudioFile, iconFor(thumbnailNode(name = "song.mp3")))
        assertSame(AtomicIcons.Apk, iconFor(thumbnailNode(name = "package.apk")))
        assertSame(AtomicIcons.Archive, iconFor(thumbnailNode(name = "bundle.7z")))
        assertSame(AtomicIcons.Document, iconFor(thumbnailNode(name = "photo.jpg", mime = "text/plain")))
    }

    @Test
    fun genericMediaUsesItsCollectionWhenTheDisplayNameHasNoExtension() {
        val image = thumbnailNode(FileNodeId.media("external", "images", 42), "42", "application/octet-stream")
        val video = thumbnailNode(FileNodeId.media("external", "video", 43), "opaque", null)
        assertEquals(ThumbnailKind.IMAGE, thumbnailKind(image))
        assertEquals(ThumbnailKind.VIDEO, thumbnailKind(video))
        assertSame(AtomicIcons.Image, iconFor(image))
        assertSame(AtomicIcons.Video, iconFor(video))
        assertEquals("content://media/external/images/media/42", thumbnailSourceOf(image)?.data.toString())
        assertEquals("content://media/external/video/media/43", thumbnailSourceOf(video)?.data.toString())
        // A generic provider MIME cannot make the filename override the known video collection.
        assertEquals(ThumbnailKind.VIDEO, thumbnailKind(video.copy(name = "wrong.jpg")))
        assertNull(thumbnailKind(video.copy(mimeType = "text/plain")))
        assertSame(AtomicIcons.Document, iconFor(video.copy(mimeType = "text/plain")))
        assertNull(thumbnailKind(video.copy(id = FileNodeId.media("external", "video", -1))))
        assertNull(thumbnailKind(video.copy(id = FileNodeId("media:external:video:bad"))))
    }

    @Test
    fun requestSizeAndRevision() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            val node = thumbnailNode()
            val source = checkNotNull(thumbnailSourceOf(node))
            val small = buildThumbnailRequest(context, source, 22)
            val large = buildThumbnailRequest(context, source, 80)
            assertEquals(Precision.EXACT, small.precision)
            assertEquals(Dimension.Pixels(22), small.sizeResolver.size().width)
            assertNotEquals(small.memoryCacheKey, large.memoryCacheKey)
            assertEquals(small.diskCacheKey, large.diskCacheKey)
            assertNotEquals(source.cacheKey, thumbnailSourceOf(node.copy(size = 99))?.cacheKey)
            assertNotEquals(source.cacheKey, thumbnailSourceOf(node.copy(modifiedAt = 99))?.cacheKey)
            val video = checkNotNull(thumbnailSourceOf(node.copy(name = "no-extension", mimeType = "video/mp4")))
            val request = buildThumbnailRequest(context, video, 22)
            assertTrue(request.data is ProviderVideoThumbnail)
            assertNull(request.decoderFactory)
        }
}

internal fun thumbnailNode(
    id: FileNodeId = FileNodeId.file("/fixture/photo.jpg"),
    name: String = "photo.jpg",
    mime: String? = null,
): FileNode =
    FileNode(
        id = id, name = name, displayName = name, mimeType = mime, size = 1, modifiedAt = 1,
        isDirectory = false, isHidden = false, parentId = null, storageType = StorageType.INTERNAL_SHARED,
        access = AccessFlags.FULL, childCount = null, extras = null,
    )
