package com.devbehindyou.atomicfilemanager.data.preview

import android.app.Application
import android.graphics.Bitmap
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.Options
import coil3.request.SuccessResult
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
import kotlin.coroutines.EmptyCoroutineContext

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
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
        assertSame(AtomicIcons.TextFile, iconFor(thumbnailNode(name = "photo.jpg", mime = "text/plain")))
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
        assertSame(AtomicIcons.TextFile, iconFor(video.copy(mimeType = "text/plain")))
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

    @Test
    fun coilCacheIdentityFollowsRevisionSourceAndRowSizeWithoutRemoteRequests() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            val fetched = mutableListOf<Any>()
            val loader =
                ImageLoader.Builder(context)
                    .coroutineContext(EmptyCoroutineContext)
                    .diskCache(null)
                    .components {
                        add(
                            object : Fetcher.Factory<Any> {
                                override fun create(
                                    data: Any,
                                    options: Options,
                                    imageLoader: ImageLoader,
                                ) = object : Fetcher {
                                    override suspend fun fetch(): ImageFetchResult {
                                        fetched += data
                                        val edge = (options.size.width as Dimension.Pixels).px
                                        val bitmap = Bitmap.createBitmap(edge, edge, Bitmap.Config.ARGB_8888)
                                        return ImageFetchResult(bitmap.asImage(), true, DataSource.DISK)
                                    }
                                }
                            },
                            Any::class,
                        )
                    }
                    .build()
            val keys = mutableSetOf<String>()

            suspend fun show(
                node: FileNode,
                pixels: Int = 40,
            ): DataSource {
                val request = buildThumbnailRequest(context, checkNotNull(thumbnailSourceOf(node)), pixels)
                keys += checkNotNull(request.memoryCacheKey)
                return (loader.execute(request) as SuccessResult).dataSource
            }
            try {
                val original = thumbnailNode(FileNodeId.media("external", "images", 7), "photo.jpg")
                assertEquals(DataSource.DISK, show(original))
                // Rapid row reuse: a fresh but equal node is a memory hit, never a second provider read.
                assertEquals(DataSource.MEMORY_CACHE, show(original.copy()))
                assertEquals(1, fetched.size)
                // A replacement with a new revision is never served from the old entry.
                assertEquals(DataSource.DISK, show(original.copy(modifiedAt = 2)))
                assertEquals(DataSource.DISK, show(original.copy(size = 2)))
                assertEquals(DataSource.DISK, show(original, pixels = 80))
                // The same row id on another volume, a same-named document from another provider
                // or user, and the same id read as a video are separate identities.
                val otherVolume = original.copy(id = FileNodeId.media("external_primary", "images", 7))
                assertEquals(DataSource.DISK, show(otherVolume))
                val document = "content://documents.one/document/primary%3ADCIM%2Fphoto.jpg"
                assertEquals(DataSource.DISK, show(thumbnailNode(FileNodeId.saf(document))))
                assertEquals(
                    DataSource.DISK,
                    show(thumbnailNode(FileNodeId.saf(document.replace("documents.one", "documents.two")))),
                )
                assertEquals(
                    DataSource.DISK,
                    show(thumbnailNode(FileNodeId.saf(document.replace("documents.one", "10@documents.one")))),
                )
                assertEquals(DataSource.DISK, show(original.copy(mimeType = "video/mp4")))
                assertEquals(9, fetched.size)
                assertEquals(9, keys.size)
                assertTrue(fetched.last() is ProviderVideoThumbnail)
                // Revisiting an unchanged revision is deterministic and costs no request.
                assertEquals(DataSource.MEMORY_CACHE, show(original))
                assertEquals(DataSource.MEMORY_CACHE, show(original.copy(modifiedAt = 2)))
                assertEquals(9, fetched.size)
                listOf(
                    FileNodeId.sftp("account-a", "/photo.jpg"),
                    FileNodeId.ftps("account-a", "/photo.jpg"),
                    FileNodeId.smb("account-a", "share/photo.jpg"),
                    FileNodeId.webdav("account-a", "/photo.jpg"),
                ).forEach { assertNull(thumbnailSourceOf(thumbnailNode(it))) }
            } finally {
                loader.shutdown()
            }
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
