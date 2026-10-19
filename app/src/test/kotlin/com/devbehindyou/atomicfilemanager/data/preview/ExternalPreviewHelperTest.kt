package com.devbehindyou.atomicfilemanager.data.preview

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 34], application = Application::class)
class ExternalPreviewHelperTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun remoteExportIsBoundedAndFailedLaunchRemovesItsCopy() =
        runBlocking {
            val backend = InMemoryBackend()
            val id = backend.putFile(backend.rootId, "report.docx", ByteArray(9))
            val original = (backend.getNode(id) as FileResult.Success).value
            val remote = original.copy(id = FileNodeId.ftp("server", "/report.docx"))
            val adapter =
                object : StorageBackend by backend {
                    override suspend fun openInput(id: FileNodeId) = backend.openInput(original.id)
                }
            val before = exports()
            var launched = false
            val bounded = ExternalPreviewHelper(context, maxExportBytes = 8) { adapter }
            assertTrue(bounded.open(remote) { launched = true } is FileResult.Failure)
            assertFalse(launched)
            assertEquals(before, exports())
            val helper = ExternalPreviewHelper(context) { adapter }
            assertTrue(helper.open(remote) { throw IOException("No external reader") } is FileResult.Failure)
            assertEquals(before, exports())
        }

    @Test
    fun safAndMediaItemsAreGrantedAsContentUrisWithoutCopies() =
        runBlocking {
            val backend = InMemoryBackend()
            val id = backend.putFile(backend.rootId, "picture.jpg", byteArrayOf(1))
            val node = (backend.getNode(id) as FileResult.Success).value
            val helper = ExternalPreviewHelper(context) { error("Content sources should not be copied") }
            val saf = "content://provider/tree/root/document/photo"
            assertTrue(
                helper.open(node.copy(id = FileNodeId.saf(saf))) {
                    assertEquals(saf, it.uri.toString())
                } is FileResult.Success,
            )
            assertTrue(
                helper.open(node.copy(id = FileNodeId.media("external", "images", 42))) {
                    assertEquals("content://media/external/images/media/42", it.uri.toString())
                } is FileResult.Success,
            )
        }

    private fun exports() = File(context.cacheDir, "external-preview").listFiles().orEmpty().toSet()
}
