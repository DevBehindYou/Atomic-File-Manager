package com.devbehindyou.atomicfilemanager.data.preview

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Actual Android FILE stream boundary; no external activity, permission change or provider substitution. */
@RunWith(AndroidJUnit4::class)
class SelectedContentTypeDeviceTest {
    @Test
    fun opaquePrivatePngReturnsOnlyEightCallerBytesAndKeepsItsRawMetadataAndFiles() =
        runBlocking {
            withContext(Dispatchers.IO) {
                val target = InstrumentationRegistry.getInstrumentation().targetContext
                val exports = File(target.cacheDir, "external-preview")
                val originalExports = snapshot(exports)
                val exportsExisted = exports.exists()
                val owned = File(target.cacheDir, "atomic-selected-type-${UUID.randomUUID()}")
                check(owned.mkdir())
                check(owned.canonicalFile.parentFile == target.cacheDir.canonicalFile)
                val source = File(owned, "opaque.atomic_unknown")
                val sentinel = File(owned, "preserve-sentinel.bin")
                val ownedInput = AtomicReference<InputStream?>()
                val inputClosed = AtomicBoolean()
                try {
                    val bytes = png()
                    val sentinelBytes = byteArrayOf(31, 41, 59)
                    source.writeBytes(bytes)
                    sentinel.writeBytes(sentinelBytes)
                    val local = FileSystemBackend(target)
                    val sourceId = FileNodeId.file(source.absolutePath)
                    val original = local.getNode(sourceId)
                    assertTrue("Actual private FILE metadata: $original", original is FileResult.Success)
                    val raw = (original as FileResult.Success).value.copy(mimeType = "application/octet-stream")
                    val acquisitions = AtomicInteger()
                    val created = AtomicInteger()
                    val returned = AtomicInteger()
                    val closed = AtomicInteger()
                    val recorded =
                        object : StorageBackend by local {
                            override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                                assertEquals(sourceId, id)
                                acquisitions.incrementAndGet()
                                return when (val opened = local.openInput(id)) {
                                    is FileResult.Failure -> opened
                                    is FileResult.Success ->
                                        FileResult.Success(
                                            InputStreamProvider {
                                                created.incrementAndGet()
                                                RecordedInput(
                                                    opened.value.stream(),
                                                    returned,
                                                    closed,
                                                ) { inputClosed.set(true) }
                                                    .also { ownedInput.set(it) }
                                            },
                                        )
                                }
                            }
                        }
                    val result =
                        SelectedContentMimeResolver { id ->
                            assertEquals(sourceId, id)
                            recorded
                        }.resolve(raw)
                    assertTrue(
                        "Actual FILE source should recognize its signature: $result",
                        result is SelectedContentType.Ready,
                    )
                    assertEquals(raw.copy(mimeType = "image/png"), result.node)
                    assertEquals("Original raw metadata is immutable", "application/octet-stream", raw.mimeType)
                    assertEquals(1, acquisitions.get())
                    assertEquals(1, created.get())
                    assertEquals("Only caller-returned bytes are bounded, not FILE buffering", 8, returned.get())
                    assertEquals("Real delegated FILE stream closes before resolution returns", 1, closed.get())
                    val reopened = local.openInput(sourceId)
                    assertTrue("Source remains readable: $reopened", reopened is FileResult.Success)
                    (reopened as FileResult.Success).value.stream().use {
                            input ->
                        assertArrayEquals(bytes, input.readBytes())
                    }
                    assertArrayEquals(bytes, source.readBytes())
                    assertArrayEquals(sentinelBytes, sentinel.readBytes())
                    assertEquals(
                        setOf(source.canonicalFile, sentinel.canonicalFile),
                        owned.listFiles().orEmpty().map {
                            it.canonicalFile
                        }.toSet(),
                    )
                    assertEquals(exportsExisted, exports.exists())
                    assertEquals(originalExports, snapshot(exports))
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) {
                        val failures = mutableListOf<String>()
                        if (!inputClosed.get()) {
                            try {
                                ownedInput.get()?.close()
                            } catch (_: Exception) {
                                failures += "Captured FILE stream cleanup failed"
                            }
                        }
                        for (fixture in listOf(source, sentinel)) {
                            if (!fixture.delete() && fixture.exists()) {
                                failures += "Owned private fixture cleanup failed"
                            }
                        }
                        if (!owned.delete() && owned.exists()) failures += "Owned private directory was not empty"
                        assertEquals(exportsExisted, exports.exists())
                        assertEquals("Unrelated exports must survive detection", originalExports, snapshot(exports))
                        assertTrue(failures.joinToString(), failures.isEmpty())
                    }
                }
            }
        }

    private fun png(): ByteArray {
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(Color.GREEN)
            return ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /** Observes bytes returned by the actual buffered FILE stream, without replacing its reads or closure. */
    private class RecordedInput(
        input: InputStream,
        private val returned: AtomicInteger,
        private val closed: AtomicInteger,
        private val onClose: () -> Unit,
    ) : FilterInputStream(input) {
        override fun read(): Int = `in`.read().also { if (it >= 0) returned.incrementAndGet() }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int = `in`.read(buffer, offset, length).also { if (it > 0) returned.addAndGet(it) }

        override fun close() {
            try {
                super.close()
            } finally {
                closed.incrementAndGet()
                onClose()
            }
        }
    }

    private fun snapshot(directory: File): Map<File, Pair<Long, Long>> =
        directory.listFiles().orEmpty().associate { it.canonicalFile to (it.length() to it.lastModified()) }
}
