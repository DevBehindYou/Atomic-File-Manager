package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Real archive headers and the actual inspector; no extraction or user filesystem mutation. */
class ArchiveInspectionSafetyTest {
    private val backend = InMemoryBackend()

    @Test
    fun `repeated TAR file identities are rejected instead of returning duplicate browse keys`() =
        runTest {
            val closes = AtomicInteger()
            val source = source("repeated.tar", tar(listOf("same.txt", "same.txt")), closes)
            val result = source.inspect(source.id)

            assertTrue((result as FileResult.Failure).error is FileError.SuspiciousArchive)
            assertEquals(1, closes.get())
        }

    @Test
    fun `ZIP slash aliases cannot become the same normalized browse identity`() =
        runTest {
            val source = source("aliases.zip", zip(listOf("docs/readme.txt", "docs\\readme.txt")))
            val result = source.inspect(source.id)

            assertTrue((result as FileResult.Failure).error is FileError.SuspiciousArchive)
        }

    @Test
    fun `different paths retain equal basenames and original archive order`() =
        runTest {
            val paths = listOf("a/readme.txt", "b/readme.txt", "empty/")
            val source = source("distinct.tar", tar(paths))
            val result = source.inspect(source.id) as FileResult.Success

            assertEquals(paths, result.value.map { it.path })
            assertEquals(listOf("readme.txt", "readme.txt", "empty"), result.value.map { it.name })
            assertTrue(result.value.last().isDirectory)
        }

    @Test
    fun `stream cancellation is rethrown and the owned stream is closed`() =
        runTest {
            val cancelled = CancellationException("owned read cancelled")
            val closed = AtomicBoolean()
            val id = backend.putFile(backend.rootId, "cancelled.tar", byteArrayOf())
            val failing =
                object : StorageBackend by backend {
                    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                        FileResult.Success(
                            InputStreamProvider {
                                object : InputStream() {
                                    override fun read(): Int = throw cancelled

                                    override fun close() {
                                        closed.set(true)
                                    }
                                }
                            },
                        )
                }
            val thrown =
                try {
                    InspectArchiveUseCase { failing }(id)
                    null
                } catch (caught: CancellationException) {
                    caught
                }

            assertSame(cancelled, thrown)
            assertTrue(closed.get())
        }

    @Test
    fun `cancelled inspection stops before scanning remaining archive headers`() =
        runTest {
            val bytes = tar((0 until 1_000).map { "entry-$it.txt" })
            val owner = Job()
            val reads = AtomicInteger()
            val closes = AtomicInteger()
            val source = source("many.tar", bytes, closes) { count ->
                reads.addAndGet(count)
                owner.cancel(CancellationException("owner closed"))
            }
            var cancelled = false
            try {
                withContext(owner) { source.inspect(source.id) }
            } catch (_: CancellationException) {
                cancelled = true
            } finally {
                owner.cancel()
            }

            assertTrue(cancelled)
            assertTrue("Cancellation must stop header scanning, not consume the entire archive", reads.get() < bytes.size)
            assertEquals(1, closes.get())
        }

    @Test
    fun `denied acquisition is preserved without manufacturing archive corruption`() =
        runTest {
            val denied = FileError.AccessDenied("fixture archive")
            val id = backend.putFile(backend.rootId, "denied.tar", byteArrayOf())
            val blocked =
                object : StorageBackend by backend {
                    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                        FileResult.Failure(denied)
                }

            assertEquals(FileResult.Failure(denied), InspectArchiveUseCase { blocked }(id))
        }

    private data class Source(val id: FileNodeId, val inspect: InspectArchiveUseCase)

    private suspend fun source(
        name: String,
        bytes: ByteArray,
        closes: AtomicInteger = AtomicInteger(),
        onRead: (Int) -> Unit = {},
    ): Source {
        val id = backend.putFile(backend.rootId, name, bytes)
        val tracked =
            object : StorageBackend by backend {
                override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                    FileResult.Success(
                        InputStreamProvider {
                            object : ByteArrayInputStream(bytes) {
                                override fun read(): Int = super.read().also { if (it >= 0) onRead(1) }

                                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                                    super.read(buffer, offset, length).also { if (it > 0) onRead(it) }

                                override fun close() {
                                    closes.incrementAndGet()
                                    super.close()
                                }
                            }
                        },
                    )
            }
        return Source(id, InspectArchiveUseCase { tracked })
    }

    private fun tar(paths: List<String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        TarArchiveOutputStream(bytes).use { archive ->
            paths.forEach { path ->
                val entry = TarArchiveEntry(path)
                val content = if (entry.isDirectory) byteArrayOf() else "fixture".toByteArray()
                entry.size = content.size.toLong()
                archive.putArchiveEntry(entry)
                archive.write(content)
                archive.closeArchiveEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun zip(paths: List<String>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { archive ->
            paths.forEach { path ->
                archive.putNextEntry(ZipEntry(path))
                archive.write("fixture".toByteArray())
                archive.closeEntry()
            }
        }
        return bytes.toByteArray()
    }
}