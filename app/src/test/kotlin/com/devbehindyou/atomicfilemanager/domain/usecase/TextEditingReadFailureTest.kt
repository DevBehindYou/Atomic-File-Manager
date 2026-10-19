package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.Executors

@OptIn(ExperimentalCoroutinesApi::class)
class TextEditingReadFailureTest {
    private val memory = InMemoryBackend()

    @Test
    fun `factory backend and stream construction exceptions map to errors while typed failures are preserved`() =
        runTest {
            val node = file()
            val io = StandardTestDispatcher(testScheduler)
            assertTrue(TextFileEditor({ throw IOException("factory") }, io = io).open(node) is FileResult.Failure)
            val throwing = input { throw IOException("backend") }
            assertTrue(TextFileEditor({ throwing }, io = io).open(node) is FileResult.Failure)
            val streamThrows = input { FileResult.Success(InputStreamProvider { throw IOException("stream") }) }
            assertTrue(TextFileEditor({ streamThrows }, io = io).open(node) is FileResult.Failure)
            val denied =
                input { FileResult.Success(InputStreamProvider { throw SecurityException("permission revoked") }) }
            assertEquals(
                FileResult.Failure(FileError.AccessDenied(node.name)),
                TextFileEditor({ denied }, io = io).open(node),
            )
            val original = FileResult.Failure(FileError.ProviderUnavailable("provider"))
            assertEquals(original, TextFileEditor({ input { original } }, io = io).open(node))
        }

    @Test
    fun `read and close exceptions return failures and the stream always closes`() =
        runTest {
            val node = file()
            val io = StandardTestDispatcher(testScheduler)
            var closed = false
            val broken =
                object : ByteArrayInputStream("text".toByteArray()) {
                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int = throw IOException("read failed")

                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            val reader = TextFileEditor({ input { FileResult.Success(InputStreamProvider { broken }) } }, io = io)
            assertEquals(FileResult.Failure(FileError.IoFailure(node.name)), reader.open(node))
            assertTrue(closed)
            val closeFails =
                object : ByteArrayInputStream("text".toByteArray()) {
                    override fun close() = throw IOException("close failed")
                }
            val closer = TextFileEditor({ input { FileResult.Success(InputStreamProvider { closeFails }) } }, io = io)
            assertTrue(closer.open(node) is FileResult.Failure)
        }

    @Test
    fun `cancellation inside a final stream read propagates and closes rather than returning text`() =
        runTest {
            val node = file()
            val io = StandardTestDispatcher(testScheduler)
            val owner = CoroutineScope(SupervisorJob() + io)
            var closed = false
            val cancelled =
                object : ByteArrayInputStream("text".toByteArray()) {
                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int {
                        owner.cancel()
                        return super.read(buffer, offset, length)
                    }

                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            val reader = TextFileEditor({ input { FileResult.Success(InputStreamProvider { cancelled }) } }, io = io)
            val result = owner.async { reader.open(node) }
            testScheduler.advanceUntilIdle()
            assertTrue(runCatching { result.await() }.exceptionOrNull() is CancellationException)
            assertTrue(closed)
            val selfCancelled = TextFileEditor({ input { throw CancellationException("provider cancelled") } }, io = io)
            assertTrue(runCatching { selfCancelled.open(node) }.exceptionOrNull() is CancellationException)
        }

    @Test
    fun `unknown size input reads at most one byte above the edit limit and closes`() =
        runTest {
            val node = file().copy(size = -1)
            val io = StandardTestDispatcher(testScheduler)
            var consumed = 0
            var closed = false
            val huge =
                object : ByteArrayInputStream(ByteArray((TextFileEditor.MAX_EDIT_BYTES + 100).toInt()) { 65 }) {
                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int = super.read(buffer, offset, length).also { if (it > 0) consumed += it }

                    override fun close() {
                        closed = true
                        super.close()
                    }
                }
            val reader = TextFileEditor({ input { FileResult.Success(InputStreamProvider { huge }) } }, io = io)
            assertEquals(
                FileResult.Failure(FileError.FileTooLarge(node.name, TextFileEditor.MAX_EDIT_BYTES)),
                reader.open(node),
            )
            assertEquals(TextFileEditor.MAX_EDIT_BYTES + 1, consumed.toLong())
            assertTrue(closed)
        }

    @Test
    fun `zero-byte reads make bounded progress without busy spinning`() =
        runTest {
            val node = file()
            val io = StandardTestDispatcher(testScheduler)
            val zeroRead =
                object : ByteArrayInputStream("text".toByteArray()) {
                    override fun read(
                        buffer: ByteArray,
                        offset: Int,
                        length: Int,
                    ): Int = 0
                }
            val reader = TextFileEditor({ input { FileResult.Success(InputStreamProvider { zeroRead }) } }, io = io)
            assertEquals("text", (reader.open(node) as FileResult.Success).value.text)
        }

    @Test
    fun `all construction stream access reads and close run on the injected worker`() {
        val node = runBlocking { file() }
        val caller = Thread.currentThread()
        var closed = false
        val bytes =
            object : ByteArrayInputStream("text".toByteArray()) {
                override fun read(
                    buffer: ByteArray,
                    offset: Int,
                    length: Int,
                ): Int {
                    assertFalse(Thread.currentThread() === caller)
                    return super.read(buffer, offset, length)
                }

                override fun close() {
                    assertFalse(Thread.currentThread() === caller)
                    closed = true
                    super.close()
                }
            }
        Executors.newSingleThreadExecutor().use { executor ->
            executor.asCoroutineDispatcher().use { io ->
                val reader =
                    TextFileEditor({
                        assertFalse(Thread.currentThread() === caller)
                        input {
                            assertFalse(Thread.currentThread() === caller)
                            FileResult.Success(
                                InputStreamProvider {
                                    assertFalse(Thread.currentThread() === caller)
                                    bytes
                                },
                            )
                        }
                    }, io = io)
                assertEquals("text", (runBlocking { reader.open(node) } as FileResult.Success).value.text)
                assertTrue(closed)
            }
        }
    }

    private suspend fun file(): FileNode =
        (
            memory.getNode(
                memory.putFile(memory.rootId, "note.txt", "old".toByteArray(), "text/plain"),
            ) as FileResult.Success
        ).value

    private fun input(open: () -> FileResult<InputStreamProvider>): StorageBackend =
        object : StorageBackend by memory {
            override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> = open()
        }
}
