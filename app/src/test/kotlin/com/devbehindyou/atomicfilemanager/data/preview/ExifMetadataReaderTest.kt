package com.devbehindyou.atomicfilemanager.data.preview

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.coroutines.CoroutineContext

/** Real JPEG and EXIF bytes; the original is never changed and a cancelled copy never lingers. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExifMetadataReaderTest {
    @get:Rule val folder = TemporaryFolder()

    @Test
    fun cleanCopyWithoutLocationKeepsTheCameraAndLeavesTheOriginalUnchanged() =
        runTest {
            val original = photo("trip.jpg")
            val before = original.readBytes()
            val target = File(folder.root, "clean/trip.jpg")

            val reader = ExifMetadataReader(StandardTestDispatcher(testScheduler))
            assertTrue(reader.read(original)!!.hasLocation)

            val copy = reader.cleanCopy(original, target, MetadataStrip.LOCATION)

            assertEquals(target, copy.getOrThrow())
            val clean = reader.read(target)!!
            assertFalse(clean.hasLocation)
            assertEquals("Atomic", clean.make)
            assertArrayEquals(before, original.readBytes())
        }

    @Test
    fun cancellationAfterTheCopyIsWrittenDeletesItAndPropagates() =
        runTest {
            val original = photo("trip.jpg")
            val before = original.readBytes()
            val target = File(folder.root, "clean/trip.jpg")
            val caller = Job(coroutineContext[Job])
            // The blocking copy finishes, then the caller leaves before it can receive the copy.
            val io = CancelAfterRunning(StandardTestDispatcher(testScheduler), caller)

            val copy = async(caller) { ExifMetadataReader(io).cleanCopy(original, target, MetadataStrip.ALL) }
            val outcome = runCatching { copy.await() }

            assertTrue(
                "A cancelled caller must not receive a clean copy",
                outcome.exceptionOrNull() is CancellationException,
            )
            assertTrue(io.ran)
            assertFalse("The unshared copy is left in the cache", target.exists())
            assertArrayEquals(before, original.readBytes())
        }

    @Test
    fun anUnreadableSourceFailsWithoutLeavingATarget() =
        runTest {
            val target = File(folder.root, "clean/missing.jpg")

            val copy =
                ExifMetadataReader(StandardTestDispatcher(testScheduler))
                    .cleanCopy(File(folder.root, "missing.jpg"), target, MetadataStrip.ALL)

            assertTrue(copy.isFailure)
            assertFalse(target.exists())
        }

    @Test
    fun aCorruptImageReadsAsNoMetadata() =
        runTest {
            val corrupt = folder.newFile("corrupt.jpg")
            corrupt.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2))

            val metadata = ExifMetadataReader(StandardTestDispatcher(testScheduler)).read(corrupt)

            assertTrue(metadata == null || metadata.isEmpty)
        }

    @Test
    fun readingAMissingFileReturnsNull() =
        runTest {
            val reader = ExifMetadataReader(StandardTestDispatcher(testScheduler))

            assertNull(reader.read(File(folder.root, "absent.jpg")))
        }

    private fun photo(name: String): File {
        val file = File(folder.root, name)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(file).apply {
            setAttribute(ExifInterface.TAG_MAKE, "Atomic")
            setLatLong(51.5007, -0.12463)
            saveAttributes()
        }
        return file
    }

    /**
     * Runs the IO block later, as a worker pool would while the caller is suspended, then cancels
     * [caller] before its continuation runs, so withContext must discard the finished copy.
     */
    private class CancelAfterRunning(
        private val worker: CoroutineDispatcher,
        private val caller: Job,
    ) : CoroutineDispatcher() {
        var ran = false

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            worker.dispatch(context) {
                block.run()
                ran = true
                caller.cancel()
            }
        }
    }
}
