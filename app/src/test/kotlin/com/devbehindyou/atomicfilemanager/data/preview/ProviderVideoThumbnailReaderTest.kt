package com.devbehindyou.atomicfilemanager.data.preview

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Bitmap
import android.net.Uri
import android.os.CancellationSignal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class ProviderVideoThumbnailReaderTest {
    @Test
    fun providerWorkRunsOnIoAndReturnedBitmapsAreBoundedWithOneOwner() {
        val context = RuntimeEnvironment.getApplication()
        val caller = Thread.currentThread()
        val original = Bitmap.createBitmap(2000, 1000, Bitmap.Config.ARGB_8888)
        var signal: CancellationSignal? = null
        Executors.newSingleThreadExecutor().use { executor ->
            executor.asCoroutineDispatcher().use { io ->
                val reader =
                    ProviderVideoThumbnailReader(context, io) { _, edge, cancellation ->
                        assertFalse(Thread.currentThread() === caller)
                        assertEquals(512, edge)
                        signal = cancellation
                        original
                    }
                val result = runBlocking { reader.read(Any(), 10_000) }
                try {
                    assertEquals(512, result.width)
                    assertEquals(256, result.height)
                    assertTrue(original.isRecycled)
                    assertFalse(result.isRecycled)
                    assertTrue(checkNotNull(signal).isCanceled)
                } finally {
                    result.recycle()
                }
            }
        }
    }

    @Test
    fun cancellationSignalsABlockedProviderBeforeJoiningAndRecyclesItsLateBitmap() =
        runBlocking {
            val entered = CompletableDeferred<Unit>()
            val cancelled = CountDownLatch(1)
            val late = AtomicReference<Bitmap>()
            Executors.newSingleThreadExecutor().use { executor ->
                executor.asCoroutineDispatcher().use { io ->
                    val reader =
                        ProviderVideoThumbnailReader(RuntimeEnvironment.getApplication(), io) { _, _, signal ->
                            signal.setOnCancelListener { cancelled.countDown() }
                            entered.complete(Unit)
                            check(cancelled.await(5, TimeUnit.SECONDS)) { "Provider never received cancellation" }
                            Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888).also(late::set)
                        }
                    val request = async { reader.read(Any(), 40) }
                    entered.await()
                    request.cancelAndJoin()
                    assertTrue(checkNotNull(late.get()).isRecycled)
                }
            }
        }

    @Test
    fun providerFailureRemainsAnErrorAndInvalidSizesDoNotStartWork() {
        var calls = 0
        val reader =
            ProviderVideoThumbnailReader(RuntimeEnvironment.getApplication()) { _, _, _ ->
                calls++
                throw IOException("No thumbnail")
            }
        assertThrows(IllegalArgumentException::class.java) { runBlocking { reader.read(Any(), 0) } }
        assertEquals(0, calls)
        assertThrows(IOException::class.java) { runBlocking { reader.read(Any(), 40) } }
        assertEquals(1, calls)
    }

    @Test
    fun anUnindexedFileClosesItsExactMatchQueryWithoutOpeningTheVideo() {
        var queries = 0
        var closed = false
        val context = RuntimeEnvironment.getApplication()
        val file = File(context.cacheDir, "not-indexed.mp4")
        val provider =
            object : ContentProvider() {
                override fun onCreate() = true

                override fun query(
                    uri: Uri,
                    projection: Array<out String>?,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                    sortOrder: String?,
                ): Cursor {
                    queries++
                    assertEquals("content://media/external/video/media", uri.toString())
                    assertEquals("_data = ?", selection)
                    assertEquals(listOf(file.canonicalPath), selectionArgs?.toList())
                    return object : MatrixCursor(arrayOf("_id")) {
                        override fun close() {
                            closed = true
                            super.close()
                        }
                    }
                }

                override fun getType(uri: Uri): String = error("Must not open an unindexed video")

                override fun insert(
                    uri: Uri,
                    values: ContentValues?,
                ): Uri = error("No mutations")

                override fun delete(
                    uri: Uri,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                ): Int =
                    error(
                        "No mutations",
                    )

                override fun update(
                    uri: Uri,
                    values: ContentValues?,
                    selection: String?,
                    selectionArgs: Array<out String>?,
                ): Int = error("No mutations")
            }
        provider.attachInfo(
            context,
            ProviderInfo().apply {
                authority = "media"
                applicationInfo = context.applicationInfo
                exported = true
            },
        )
        ShadowContentResolver.registerProviderInternal("media", provider)
        assertThrows(IOException::class.java) { runBlocking { ProviderVideoThumbnailReader(context).read(file, 40) } }
        assertEquals(1, queries)
        assertTrue(closed)
    }
}
