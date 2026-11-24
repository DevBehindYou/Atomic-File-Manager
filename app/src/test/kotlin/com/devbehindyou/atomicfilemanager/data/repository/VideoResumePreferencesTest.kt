package com.devbehindyou.atomicfilemanager.data.repository

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeState
import com.devbehindyou.atomicfilemanager.domain.repository.VideoRevision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
@OptIn(ExperimentalCoroutinesApi::class)
class VideoResumePreferencesTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val prefs get() = app.getSharedPreferences(VideoResumePreferences.FILE_NAME, Context.MODE_PRIVATE)
    private val sftp = VideoRevision("sftp:home-nas/Movies/Trip 2024.mp4", 1_700_000_000_000, 5_000_000)

    private fun store(
        clock: () -> Long = { 1L },
        max: Int = VideoResumePreferences.MAX_ENTRIES,
    ) = VideoResumePreferences(app, io = Dispatchers.Unconfined, clock = clock, maxEntries = max)

    @Test
    fun aSavedStateSurvivesIntoTheNextProcessForTheSameRevision() =
        runTest {
            store().save(sftp, VideoResumeState(61_500, playWhenReady = false, speed = 1.25f))
            assertEquals(VideoResumeState(61_500, false, 1.25f), store().read(sftp))
        }

    @Test
    fun aChangedTimeOrSizeIsAReplacementAndClearsTheStaleEntry() =
        runTest {
            val store = store()
            for (replacement in listOf(sftp.copy(modifiedAt = sftp.modifiedAt + 1), sftp.copy(size = sftp.size - 1))) {
                store.save(sftp, VideoResumeState(30_000, true))
                assertNull(store.read(replacement))
                assertTrue("The replaced revision's entry is removed", prefs.all.isEmpty())
                assertNull(store.read(sftp))
            }
        }

    @Test
    fun sourcesAreKeyedByAnAccountQualifiedIdWithoutStoringIt() =
        runTest {
            val store = store()
            val otherAccount = sftp.copy(source = "sftp:office-nas/Movies/Trip 2024.mp4")
            store.save(sftp, VideoResumeState(10_000, true))
            store.save(otherAccount, VideoResumeState(20_000, false))
            assertEquals(10_000L, store.read(sftp)?.positionMs)
            assertEquals(20_000L, store.read(otherAccount)?.positionMs)
            val dump = prefs.all.entries.joinToString { "${it.key}=${it.value}" }
            assertFalse("No path or account name is stored", dump.contains("nas") || dump.contains("Trip"))
            assertEquals(2, prefs.all.size)
        }

    @Test
    fun theStoreIsBoundedAndEvictsTheLeastRecentlyWritten() =
        runTest {
            var now = 0L
            val store = store(clock = { ++now }, max = 3)
            val revisions = (1..6).map { sftp.copy(source = "file:/clip$it.mp4") }
            revisions.forEach {
                store.save(it, VideoResumeState(5_000, true))
                assertTrue(prefs.all.size <= 3)
            }
            // Rewriting an existing source does not evict anything.
            store.save(revisions[3], VideoResumeState(6_000, true))
            assertEquals(3, prefs.all.size)
            assertNull(store.read(revisions[2]))
            assertEquals(listOf(6_000L, 5_000L, 5_000L), revisions.drop(3).map { store.read(it)?.positionMs })
        }

    @Test
    fun malformedOutOfRangeAndOversizedValuesAreDropped() =
        runTest {
            val key = VideoResumePreferences.keyOf(sftp.source)
            val bad =
                listOf(
                    "not json",
                    """{"v":2,"m":1700000000000,"s":5000000,"p":1000,"w":true,"r":1.0,"t":1}""",
                    """{"v":1,"m":1700000000000,"s":5000000,"p":-5,"w":true,"r":1.0,"t":1}""",
                    """{"v":1,"m":1700000000000,"s":5000000,"p":1000,"w":true,"r":9.0,"t":1}""",
                    """{"v":1,"m":1700000000000,"s":5000000,"p":1000,"w":true,"r":"NaN","t":1}""",
                    """{"v":1,"m":1700000000000,"s":5000000,"p":1000}""",
                    "x".repeat(4_096),
                )
            for (raw in bad) {
                prefs.edit().putString(key, raw).commit()
                assertNull(raw.take(40), store().read(sftp))
                assertFalse(raw.take(40), prefs.contains(key))
            }
            store().save(sftp, VideoResumeState(1_000, true, speed = Float.NaN))
            assertFalse("An invalid state is never written", prefs.contains(key))
        }

    @Test
    fun queuedWritesAreReadBackImmediatelyAndCollapseIntoTheLatest() =
        runTest {
            val io = StandardTestDispatcher(testScheduler)
            val store = VideoResumePreferences(app, io = io)
            repeat(5) { store.save(sftp, VideoResumeState(10_000L + it * 1_000, true)) }
            assertFalse(
                "Nothing has reached SharedPreferences yet",
                prefs.contains(VideoResumePreferences.keyOf(sftp.source)),
            )
            assertEquals(14_000L, store.read(sftp)?.positionMs)
            store.forget(sftp)
            assertNull(store.read(sftp))
            testScheduler.advanceUntilIdle()
            assertTrue(prefs.all.isEmpty())
            store.save(sftp, VideoResumeState(15_000, false))
            testScheduler.advanceUntilIdle()
            assertEquals(VideoResumeState(15_000, false), VideoResumePreferences(app, io = io).read(sftp))
        }

    @Test
    fun revisionsRequireAKnownModificationTime() {
        val node =
            com.devbehindyou.atomicfilemanager.domain.model.FileNode(
                com.devbehindyou.atomicfilemanager.domain.model.FileNodeId.file("/sdcard/a.mp4"), "a.mp4", "a.mp4",
                "video/mp4", 10, 0, false, false, null,
                com.devbehindyou.atomicfilemanager.domain.model.StorageType.INTERNAL_SHARED,
                com.devbehindyou.atomicfilemanager.domain.model.AccessFlags.FULL, null, null,
            )
        assertNull(VideoRevision.of(node))
        assertEquals(VideoRevision("file:/sdcard/a.mp4", 5, 10), VideoRevision.of(node.copy(modifiedAt = 5)))
        assertNull(VideoRevision.of(node.copy(modifiedAt = 5, isDirectory = true)))
    }
}
