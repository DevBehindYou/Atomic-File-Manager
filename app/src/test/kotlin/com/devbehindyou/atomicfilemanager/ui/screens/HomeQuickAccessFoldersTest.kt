package com.devbehindyou.atomicfilemanager.ui.screens

import android.content.Context
import android.content.ContextWrapper
import android.os.Environment
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class HomeQuickAccessFoldersTest {
    @Test
    fun filesDirectoryAndPublicPathsResolveOnIoWithTheSameFolderOrderAndFallback() =
        runBlocking {
            val callerThread = Thread.currentThread()
            val fallback = File("/app/private")
            val context =
                object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
                    override fun getFilesDir(): File {
                        assertNotSame(callerThread, Thread.currentThread())
                        return fallback
                    }
                }
            val resolved = mutableListOf<String>()
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { dispatcher ->
                val folders =
                    loadHomeQuickAccessFolders(context, dispatcher) { type ->
                        assertNotSame(callerThread, Thread.currentThread())
                        resolved += type
                        when (type) {
                            Environment.DIRECTORY_DOWNLOADS -> File("/shared/Download")
                            Environment.DIRECTORY_PICTURES -> throw IOException("public storage unavailable")
                            else -> null
                        }
                    }

                assertEquals(listOf("Download", "DCIM", "Documents", "Pictures", "Music"), resolved)
                assertEquals(resolved, folders.map { it.first })
                assertEquals(FileNodeId.file(File("/shared/Download").absolutePath), folders.first().second)
                assertEquals(List(4) { FileNodeId.file(fallback.absolutePath) }, folders.drop(1).map { it.second })
            }
        }
}
