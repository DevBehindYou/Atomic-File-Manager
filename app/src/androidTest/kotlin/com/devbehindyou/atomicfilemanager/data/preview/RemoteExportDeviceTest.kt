package com.devbehindyou.atomicfilemanager.data.preview

import android.content.ContextWrapper
import androidx.core.content.FileProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.BackendType
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
import java.io.File
import java.util.UUID

/** Real Android provider readback in the target UID; no network, external activity or grant changes. */
@RunWith(AndroidJUnit4::class)
class RemoteExportDeviceTest {
    @Test
    fun remoteCopyReportsActualBytesAndRetainsItsReadableContentUriAfterAcknowledgement() =
        runBlocking {
            withContext(Dispatchers.IO) {
                val target = InstrumentationRegistry.getInstrumentation().targetContext
                val standardExports = File(target.cacheDir, "external-preview")
                val originalExports = snapshot(standardExports)
                val ownedCache = File(target.cacheDir, "atomic-remote-export-${UUID.randomUUID()}")
                check(ownedCache.mkdir())
                val source = File(ownedCache, "source.txt")
                val sentinel = File(ownedCache, "preserve-sentinel.bin")
                val exportDirectory = File(ownedCache, "external-preview")
                var ownedCopy: File? = null
                try {
                    val expected = ByteArray(257) { index -> ((index * 17 + 3) % 251).toByte() }
                    val sentinelBytes = byteArrayOf(31, 41, 59)
                    source.writeBytes(expected)
                    sentinel.writeBytes(sentinelBytes)
                    // Prime normal target/provider roots before the pruning-isolation wrapper is used.
                    val originalUri = FileProvider.getUriForFile(target, "${target.packageName}.fileprovider", source)
                    target.contentResolver.openInputStream(originalUri)!!.use { stream ->
                        assertArrayEquals(expected, stream.readBytes())
                    }
                    // Isolate pruning without replacing the real provider or its configured app-cache root.
                    val isolated =
                        object : ContextWrapper(target) {
                            override fun getCacheDir() = ownedCache
                        }
                    val local = FileSystemBackend(target)
                    val sourceId = FileNodeId.file(source.absolutePath)
                    val original = local.getNode(sourceId)
                    assertTrue("owned private source metadata: $original", original is FileResult.Success)
                    val remoteId = FileNodeId.ftp("owned-device-contract", "/${source.name}")
                    val remote =
                        (original as FileResult.Success).value.copy(
                            id = remoteId,
                            mimeType = "text/plain",
                            size = -1,
                        )
                    var acquisitions = 0
                    val proxy =
                        object : StorageBackend by local {
                            override val type = BackendType.FTP

                            override fun canHandle(id: FileNodeId) = id == remoteId

                            override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
                                assertEquals(remoteId, id)
                                acquisitions++
                                return local.openInput(sourceId)
                            }
                        }
                    val reports = mutableListOf<Long>()
                    var published: ExternalPreviewFile? = null
                    val result =
                        ExternalPreviewHelper(isolated) { proxy }.open(
                            remote,
                            onProgress = { progress ->
                                val copy = exportDirectory.listFiles().orEmpty().single()
                                if (ownedCopy == null) ownedCopy = copy
                                assertEquals(ownedCopy, copy)
                                assertEquals(exportDirectory.canonicalFile, copy.parentFile?.canonicalFile)
                                assertTrue(copy.name.startsWith("open-"))
                                assertEquals(copy.length(), progress.bytesCopied)
                                reports += progress.bytesCopied
                            },
                        ) { file ->
                            published = file
                            assertEquals("content", file.uri.scheme)
                            assertEquals("${target.packageName}.fileprovider", file.uri.authority)
                            assertEquals("text/plain", file.mimeType)
                            assertTrue(file.uri.pathSegments.contains(ownedCache.name))
                            assertEquals("text/plain", target.contentResolver.getType(file.uri))
                            val received = target.contentResolver.openInputStream(file.uri)
                            assertTrue("actual Android provider must return a stream", received != null)
                            received!!.use { assertArrayEquals(expected, it.readBytes()) }
                        }
                    assertTrue("real remote preparation and provider readback: $result", result is FileResult.Success)
                    assertEquals(1, acquisitions)
                    assertTrue("zero, first write and forced EOF must all be reported", reports.size >= 3)
                    assertEquals(0L, reports.first())
                    assertEquals(expected.size.toLong(), reports.last())
                    assertTrue(reports.zipWithNext().all { (before, after) -> before <= after })
                    assertTrue(reports.all { it in 0L..expected.size.toLong() })
                    val copy = checkNotNull(ownedCopy)
                    assertTrue("acknowledged export stays available for a later read", copy.isFile)
                    val file = checkNotNull(published)
                    target.contentResolver.openInputStream(file.uri)!!.use { stream ->
                        assertArrayEquals(expected, stream.readBytes())
                    }
                    assertArrayEquals(expected, source.readBytes())
                    assertArrayEquals(sentinelBytes, sentinel.readBytes())
                    assertEquals(originalExports, snapshot(standardExports))
                } finally {
                    withContext(NonCancellable + Dispatchers.IO) {
                        val cleanupFailures = mutableListOf<String>()
                        ownedCopy?.let { copy ->
                            if (copy.parentFile?.canonicalFile != exportDirectory.canonicalFile) {
                                cleanupFailures += "Captured copy escaped its owned export directory"
                            } else if (!copy.delete() && copy.exists()) {
                                cleanupFailures += "Captured export cleanup failed"
                            }
                        }
                        for (fixture in listOf(source, sentinel)) {
                            if (!fixture.delete() && fixture.exists()) cleanupFailures += "Owned fixture cleanup failed"
                        }
                        if (exportDirectory.exists() && !exportDirectory.delete()) {
                            cleanupFailures += "Owned export directory was not empty after captured-copy cleanup"
                        }
                        if (!ownedCache.delete() && ownedCache.exists()) cleanupFailures += "Owned cache cleanup failed"
                        assertEquals(
                            "Unrelated export entries must survive",
                            originalExports,
                            snapshot(standardExports),
                        )
                        assertTrue(cleanupFailures.joinToString(), cleanupFailures.isEmpty())
                    }
                }
            }
        }

    private fun snapshot(directory: File): Map<File, Pair<Long, Long>> =
        directory.listFiles().orEmpty().associate { it.canonicalFile to (it.length() to it.lastModified()) }
}
