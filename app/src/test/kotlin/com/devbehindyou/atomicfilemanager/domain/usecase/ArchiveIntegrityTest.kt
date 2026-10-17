package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.OutputTarget
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveIntegrityTest {
    private val backend = InMemoryBackend()

    @Test
    fun `extract refuses collision without modifying original`() =
        runTest {
            val original = backend.putFile(backend.rootId, "keep.txt", "original".toByteArray())
            val source = archive("keep.txt")

            val result = extract(source)

            assertEquals(FileOperationsEngine.ItemResult.Failure(FileError.FileAlreadyExists("keep.txt")), result)
            assertEquals("original", contents(original))
            assertNoStaging()
        }

    @Test
    fun `corrupt entry discards staging and preserves unrelated files`() =
        runTest {
            val original = backend.putFile(backend.rootId, "keep.txt", "original".toByteArray())
            val bytes = zip("broken.txt")
            // STORED entry data follows its 30-byte local header and UTF-8 filename.
            bytes[30 + "broken.txt".length] = 0
            val source = backend.putFile(backend.rootId, "source.zip", bytes)

            val result = extract(source)

            assertTrue((result as FileOperationsEngine.ItemResult.Failure).error is FileError.CorruptedArchive)
            assertFalse(backend.exists(backend.rootId, "broken.txt"))
            assertEquals("original", contents(original))
            assertNoStaging()
        }

    @Test
    fun `cancelled extraction removes owned output and rethrows`() =
        runTest {
            val source = archive("new.txt")
            var cancelled = false
            try {
                extract(source, onBytes = { throw CancellationException("cancel") })
            } catch (_: CancellationException) {
                cancelled = true
            }
            assertTrue(cancelled)
            assertFalse(backend.exists(backend.rootId, "new.txt"))
            assertNoStaging()
        }

    @Test
    fun `nested extraction resolves existing opaque IDs and created IDs`() =
        runTest {
            val existing = (backend.createDirectory(backend.rootId, "existing") as FileResult.Success).value
            val source = archive("existing/new/deep.txt")

            assertEquals(FileOperationsEngine.ItemResult.Success, extract(source))

            val created = children(existing.id).single()
            assertEquals("new", created.name)
            assertEquals("payload", contents(children(created.id).single().id))
        }

    @Test
    fun `extraction propagates denied output and directory listing`() =
        runTest {
            val source = archive("new.txt")
            val denied = FileError.AccessDenied("destination")
            val outputDenied =
                object : StorageBackend by backend {
                    override suspend fun openOutput(
                        parent: FileNodeId,
                        name: String,
                        mime: String?,
                    ): FileResult<OutputTarget> = FileResult.Failure(denied)
                }
            assertEquals(FileOperationsEngine.ItemResult.Failure(denied), extract(source, outputDenied))

            val nested = archive("folder/new.txt")
            val listingDenied =
                object : StorageBackend by backend {
                    override fun listChildren(id: FileNodeId) = flowOf(FileResult.Failure(denied))
                }
            assertEquals(FileOperationsEngine.ItemResult.Failure(denied), extract(nested, listingDenied))
            assertFalse(backend.exists(backend.rootId, "folder"))
        }

    @Test
    fun `compression propagates unreadable source and removes staging`() =
        runTest {
            val source = backend.putFile(backend.rootId, "private.txt", "payload".toByteArray())
            val denied = FileError.AccessDenied("private.txt")
            val sourceBackend =
                object : StorageBackend by backend {
                    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                        FileResult.Failure(denied)
                }

            assertEquals(FileOperationsEngine.ItemResult.Failure(denied), compress(source, sourceBackend))
            assertFalse(backend.exists(backend.rootId, "result.zip"))
            assertNoStaging()
        }

    @Test
    fun `compression propagates failed child listing`() =
        runTest {
            val folder = (backend.createDirectory(backend.rootId, "private") as FileResult.Success).value
            val denied = FileError.AccessDenied("private")
            val sourceBackend =
                object : StorageBackend by backend {
                    override fun listChildren(id: FileNodeId) = flowOf(FileResult.Failure(denied))
                }

            assertEquals(FileOperationsEngine.ItemResult.Failure(denied), compress(folder.id, sourceBackend))
            assertFalse(backend.exists(backend.rootId, "result.zip"))
            assertNoStaging()
        }

    @Test
    fun `compression refuses existing archive`() =
        runTest {
            val original = backend.putFile(backend.rootId, "result.zip", "original".toByteArray())
            val source = backend.putFile(backend.rootId, "source.txt", "payload".toByteArray())

            assertEquals(
                FileOperationsEngine.ItemResult.Failure(FileError.FileAlreadyExists("result.zip")),
                compress(source),
            )
            assertEquals("original", contents(original))
            assertNoStaging()
        }

    @Test
    fun `failed extraction publication removes staging and propagates error`() =
        runTest {
            val source = archive("new.txt")
            val denied = FileError.AccessDenied("new.txt")
            val destination =
                object : StorageBackend by backend {
                    override suspend fun rename(
                        id: FileNodeId,
                        newName: String,
                    ): FileResult<FileNode> = FileResult.Failure(denied)
                }

            assertEquals(FileOperationsEngine.ItemResult.Failure(denied), extract(source, destination))
            assertFalse(backend.exists(backend.rootId, "new.txt"))
            assertNoStaging()
        }

    @Test
    fun `compression rejects truncated source and removes staging`() =
        runTest {
            val source = backend.putFile(backend.rootId, "source.txt", "payload".toByteArray())
            val sourceBackend =
                object : StorageBackend by backend {
                    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
                        FileResult.Success(InputStreamProvider { "cut".byteInputStream() })
                }

            assertEquals(
                FileOperationsEngine.ItemResult.Failure(FileError.IncompleteWrite("source.txt", 3L, 7L)),
                compress(source, sourceBackend),
            )
            assertFalse(backend.exists(backend.rootId, "result.zip"))
            assertNoStaging()
        }

    private suspend fun extract(
        source: FileNodeId,
        destination: StorageBackend = backend,
        onBytes: (Long) -> Unit = {},
    ) = ArchiveOperationsHelper.extractArchive(source, backend.rootId, backend, destination, onBytes, {})

    private suspend fun compress(
        source: FileNodeId,
        sourceBackend: StorageBackend = backend,
    ) = ArchiveOperationsHelper.compressItems(listOf(source), backend.rootId, "result.zip", null, backend, {
        sourceBackend
    }, {}, {})

    private suspend fun archive(path: String) = backend.putFile(backend.rootId, "source.zip", zip(path))

    private fun zip(path: String): ByteArray {
        val bytes = "payload".toByteArray()
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            val entry = ZipEntry(path)
            entry.method = ZipEntry.STORED
            entry.size = bytes.size.toLong()
            entry.crc = CRC32().apply { update(bytes) }.value
            zip.putNextEntry(entry)
            zip.write(bytes)
            zip.closeEntry()
        }
        return output.toByteArray()
    }

    private suspend fun contents(id: FileNodeId): String =
        (backend.openInput(id) as FileResult.Success).value.stream().bufferedReader().use { it.readText() }

    private suspend fun children(id: FileNodeId): List<FileNode> {
        val result = mutableListOf<FileNode>()
        backend.listChildren(id).collect { result += (it as FileResult.Success).value }
        return result
    }

    private suspend fun assertNoStaging() {
        assertFalse(children(backend.rootId).any { InterruptedTransferCleanup.isStaging(it.name) })
    }
}
