package com.devbehindyou.atomicfilemanager.data.preview

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.DirectoryStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executors

class FolderThumbnailReaderTest {
    @TempDir
    lateinit var directory: Path

    @Test
    fun `peek is bounded and never visits a subdirectory`() =
        runBlocking {
            val nested = Files.createDirectory(directory.resolve("nested"))
            Files.createFile(nested.resolve("nested.jpg"))
            val entries =
                listOf(nested) + (0..90).map { Files.createFile(directory.resolve("$it.txt")) } +
                    Files.createFile(directory.resolve("late.jpg"))
            var examined = 0
            var closed = false
            val reader =
                FolderThumbnailReader(openDirectory = {
                    stream(entries, onNext = { examined++ }, onClose = { closed = true })
                })
            assertTrue(reader.read(folder()).isEmpty())
            assertEquals(FolderThumbnailReader.MAX_EXAMINED, examined)
            assertTrue(closed)
        }

    @Test
    fun `peek skips hidden files and returns at most four regular media files on IO`() =
        runBlocking {
            val caller = Thread.currentThread()
            val entries =
                listOf(".secret.jpg", "text.txt", "a.JPG", "b.mp4", "c.png", "d.webp", "e.jpg")
                    .map { Files.createFile(directory.resolve(it)) }
            var examined = 0
            var worker: Thread? = null
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
                val reader =
                    FolderThumbnailReader(io) {
                        worker = Thread.currentThread()
                        stream(entries, onNext = { examined++ })
                    }
                val sources = reader.read(folder())
                assertEquals(4, sources.size)
                assertEquals(6, examined)
                assertTrue(worker !== caller)
                assertEquals(
                    listOf("a.JPG", "b.mp4", "c.png", "d.webp"),
                    sources.map { (it.data as java.io.File).name },
                )
                assertEquals(ThumbnailKind.VIDEO, sources[1].kind)
            }
        }

    @Test
    fun `hidden folders nomedia and unreadable directories retain their folder icon`() =
        runBlocking {
            var opened = false
            val reader =
                FolderThumbnailReader(openDirectory = {
                    opened = true
                    stream(emptyList())
                })
            assertTrue(reader.read(folder().copy(isHidden = true)).isEmpty())
            assertTrue(reader.read(folder().copy(id = FileNodeId.ftp("server", "/folder"))).isEmpty())
            assertFalse(opened)
            Files.createFile(directory.resolve(".nomedia"))
            assertTrue(reader.read(folder()).isEmpty())
            assertFalse(opened)
            Files.delete(directory.resolve(".nomedia"))
            assertTrue(FolderThumbnailReader(openDirectory = { throw IOException("denied") }).read(folder()).isEmpty())
        }

    @Test
    fun `symlink folders children and ancestors are skipped`() =
        runBlocking {
            val target = Files.createDirectory(directory.resolve("target"))
            val image = Files.createFile(target.resolve("photo.jpg"))
            val link = directory.resolve("linked.jpg")
            try {
                Files.createSymbolicLink(link, image)
            } catch (failure: IOException) {
                assumeTrue(false, "Host cannot create symlinks: ${failure.javaClass.simpleName}")
            } catch (_: UnsupportedOperationException) {
                assumeTrue(false, "Host filesystem does not support symlinks")
            }
            val reader = FolderThumbnailReader(openDirectory = { stream(listOf(link)) })
            assertTrue(reader.read(folder()).isEmpty())
            val folderLink = Files.createSymbolicLink(directory.resolve("folder-link"), target)
            assertTrue(reader.read(folder().copy(id = FileNodeId.file(folderLink.toString()))).isEmpty())
            val nested = Files.createDirectory(target.resolve("nested"))
            Files.createFile(nested.resolve("private.jpg"))
            var opened = false
            val ancestorReader =
                FolderThumbnailReader(openDirectory = {
                    opened = true
                    stream(emptyList())
                })
            val alias = folderLink.resolve("nested")
            assertTrue(ancestorReader.read(folder().copy(id = FileNodeId.file(alias.toString()))).isEmpty())
            assertFalse(opened)
        }

    @Test
    fun `dot and nomedia ancestors block a nested miniature before listing`() =
        runBlocking {
            val nested = Files.createDirectories(directory.resolve("parent").resolve("nested"))
            Files.createFile(nested.resolve("photo.jpg"))
            Files.createFile(nested.parent.resolve(".nomedia"))
            val hidden = Files.createDirectories(directory.resolve(".hidden").resolve("nested"))
            var opened = false
            val reader =
                FolderThumbnailReader(openDirectory = {
                    opened = true
                    stream(emptyList())
                })
            assertTrue(reader.read(folder().copy(id = FileNodeId.file(nested.toString()))).isEmpty())
            assertTrue(reader.read(folder().copy(id = FileNodeId.file(hidden.toString()))).isEmpty())
            assertFalse(opened)
        }

    @Test
    fun `ancestor preflight has a fixed bound and checks root before descendants`() {
        val normalized = directory.toAbsolutePath().normalize()
        val chain = checkNotNull(boundedThumbnailAncestors(normalized))
        assertEquals(normalized.root, chain.first())
        assertEquals(normalized, chain.last())
        val tooDeep =
            (0..FolderThumbnailReader.MAX_ANCESTORS).fold(normalized) { path, index ->
                path.resolve(index.toString())
            }
        assertEquals(null, boundedThumbnailAncestors(tooDeep))
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `cancellation closes the directory stream without publishing`() =
        runTest {
            val image = Files.createFile(directory.resolve("photo.jpg"))
            var closed = false
            var published = false
            lateinit var job: Job
            val reader =
                FolderThumbnailReader(StandardTestDispatcher(testScheduler)) {
                    job.cancel()
                    stream(listOf(image), onClose = { closed = true })
                }
            job =
                launch(start = CoroutineStart.LAZY) {
                    reader.read(folder())
                    published = true
                }
            job.start()
            advanceUntilIdle()
            assertTrue(job.isCancelled)
            assertTrue(closed)
            assertFalse(published)
        }

    private fun folder() =
        thumbnailNode(
            FileNodeId.file(directory.toString()),
            directory.fileName.toString(),
        ).copy(isDirectory = true)

    private fun stream(
        paths: List<Path>,
        onNext: () -> Unit = {},
        onClose: () -> Unit = {},
    ): DirectoryStream<Path> =
        object : DirectoryStream<Path> {
            override fun iterator(): MutableIterator<Path> =
                object : MutableIterator<Path> {
                    val source = paths.iterator()

                    override fun hasNext() = source.hasNext()

                    override fun next(): Path {
                        onNext()
                        return source.next()
                    }

                    override fun remove(): Unit = error("read-only")
                }

            override fun close() = onClose()
        }
}
