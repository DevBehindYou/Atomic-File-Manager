package com.devbehindyou.atomicfilemanager.ui.components.preview

import android.app.Application
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.AppContainer
import com.devbehindyou.atomicfilemanager.DefaultAppContainer
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.AtomicTheme
import com.devbehindyou.atomicfilemanager.data.preview.MediaMetadata
import com.devbehindyou.atomicfilemanager.data.preview.MediaPreviewHelper
import com.devbehindyou.atomicfilemanager.data.repository.VideoResumePreferences
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeMemory
import com.devbehindyou.atomicfilemanager.domain.repository.VideoResumeState
import com.devbehindyou.atomicfilemanager.domain.repository.VideoRevision
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

/**
 * Real Media3 players created by the Video preview, with the real resume store. The fixture bytes
 * are not decodable video: these cases cover where a new player starts, its play/pause request and
 * what is written, not decoded playback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], qualifiers = "w411dp-h891dp", application = Application::class)
class VideoResumeComposeTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val render = mutableStateOf(true)
    private val shown = mutableStateOf<FileNode?>(null)
    private val previewInstance = mutableIntStateOf(0)
    private val bytes = ByteArray(64) { it.toByte() }
    private lateinit var clip: File
    private lateinit var memory: CountingMemory

    @Before
    fun createTheOriginalFile() {
        clip = File(app.filesDir, "clip.mp4").apply { writeBytes(bytes) }
        clip.setLastModified(MODIFIED_AT)
        memory = CountingMemory(VideoResumePreferences(app, io = Dispatchers.Unconfined))
    }

    @After
    fun theOriginalFileIsNeverModified() {
        compose.runOnIdle { render.value = false }
        compose.waitForIdle()
        if (clip.exists()) {
            assertArrayEquals(bytes, clip.readBytes())
            assertEquals(MODIFIED_AT, clip.lastModified())
        }
    }

    @Test
    fun recreationRestoresPausedPositionOfTheSameRevision() {
        val restoration = StateRestorationTester(compose)
        val container = container()
        restoration.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                VideoPreviewContent(node(), container, resumeMemory = memory)
            }
        }
        val first = awaitPlayer()
        compose.runOnIdle {
            first.seekTo(42_000)
            first.pause()
        }
        restoration.emulateSavedInstanceStateRestore()
        val second = awaitPlayer { it !== first }
        compose.runOnIdle {
            assertEquals(42_000.0, second.currentPosition.toDouble(), TOLERANCE_MS)
            assertFalse("A paused video must not autoplay after recreation", second.playWhenReady)
            // The pause wrote once; disposing the recreated-from player at the same place adds nothing.
            assertEquals(1, memory.saves)
            assertEquals(0, memory.forgets)
        }
    }

    @Test
    fun reopeningTheSameRevisionResumesWhereItWasClosed() {
        show()
        val first = awaitPlayer()
        compose.runOnIdle { first.seekTo(65_000) }
        reopen()
        val second = awaitPlayer { it !== first }
        compose.runOnIdle {
            assertEquals(65_000.0, second.currentPosition.toDouble(), TOLERANCE_MS)
            assertTrue("A video closed while playing resumes playing", second.playWhenReady)
            assertEquals("Only the close wrote", 1, memory.saves)
        }
    }

    @Test
    fun repeatedNavigationWithoutChangesWritesNothingFurther() {
        memory.seed(revision(), VideoResumeState(30_000, playWhenReady = false))
        show()
        var previous = awaitPlayer()
        repeat(3) {
            compose.runOnIdle {
                assertEquals(30_000.0, previous.currentPosition.toDouble(), TOLERANCE_MS)
                assertFalse(previous.playWhenReady)
            }
            reopen()
            val before = previous
            previous = awaitPlayer { it !== before }
        }
        compose.runOnIdle {
            assertEquals("Restoring and closing at the restored place must not write", 0, memory.saves)
            assertEquals(0, memory.forgets)
        }
    }

    @Test
    fun recomposingWithAnEqualRevisionKeepsThePlayerAndNeverSeeksAgain() {
        memory.seed(revision(), VideoResumeState(20_000, playWhenReady = true))
        show()
        val player = awaitPlayer()
        compose.runOnIdle { player.seekTo(50_000) }
        // A refreshed listing re-emits a new node object for the same revision.
        compose.runOnIdle { shown.value = node().copy(displayName = "Clip (refreshed)") }
        compose.waitForIdle()
        compose.runOnIdle {
            assertSame(player, playerView(compose.activity.window.decorView)?.player)
            assertEquals(
                "Recomposition must not seek back to the saved place",
                50_000.0,
                player.currentPosition.toDouble(),
                TOLERANCE_MS,
            )
            assertEquals(0, memory.saves)
        }
    }

    @Test
    fun aReplacementFileAtTheSameSourceStartsFromTheBeginning() {
        memory.seed(revision(), VideoResumeState(42_000, playWhenReady = false, speed = 1.5f))
        val replacement = node().copy(modifiedAt = MODIFIED_AT + 60_000, size = bytes.size + 1L)
        show(replacement)
        val player = awaitPlayer()
        compose.runOnIdle {
            assertEquals(0L, player.currentPosition)
            assertTrue("A replacement keeps the default autoplay request", player.playWhenReady)
            assertEquals(1f, player.playbackParameters.speed)
        }
        compose.runOnIdle { render.value = false }
        compose.waitForIdle()
        runBlocking {
            assertNull("The stale entry is gone for both revisions", memory.read(revision()))
            assertNull(memory.read(VideoRevision.of(replacement)!!))
        }
        assertEquals(0, memory.saves)
    }

    @Test
    fun aSavedPositionTheFileCannotReachIsDroppedInsteadOfSeeking() {
        memory.seed(revision(), VideoResumeState(DURATION_MS - 500, playWhenReady = false))
        show()
        val player = awaitPlayer()
        compose.runOnIdle {
            assertEquals(0L, player.currentPosition)
            assertTrue(player.playWhenReady)
            assertEquals("The unreachable point is forgotten once", 1, memory.forgets)
        }
        runBlocking { assertNull(memory.read(revision())) }
    }

    @Test
    fun restoredSpeedIsAppliedAndAChangedSpeedIsWrittenOnce() {
        memory.seed(revision(), VideoResumeState(10_000, playWhenReady = false, speed = 1.5f))
        show()
        val player = awaitPlayer()
        compose.runOnIdle {
            assertEquals(1.5f, player.playbackParameters.speed)
            player.setPlaybackSpeed(2f)
        }
        compose.waitForIdle()
        compose.runOnIdle { render.value = false }
        compose.waitForIdle()
        assertEquals(1, memory.saves)
        runBlocking { assertEquals(2f, memory.read(revision())!!.speed) }
    }

    @Test
    fun stopFlushesThePositionSoABackgroundProcessDeathCanResume() {
        show()
        val player = awaitPlayer()
        // Paused first, then sought: no play/pause change remains for STOP to report.
        compose.runOnIdle {
            player.pause()
            player.seekTo(33_000)
        }
        compose.waitForIdle()
        assertEquals("Seeking alone never writes", 0, memory.saves)
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitForIdle()
        assertEquals(1, memory.saves)
        // A new store over the same file stands in for the next process: nothing is held in memory.
        val nextProcess = VideoResumePreferences(app, io = Dispatchers.Unconfined)
        val restored = runBlocking { nextProcess.read(revision()) }
        assertNotNull(restored)
        assertEquals(33_000.0, restored!!.positionMs.toDouble(), TOLERANCE_MS)
        assertFalse("The recovered preview opens paused", restored.playWhenReady)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @Test
    fun sourceLossKeepsTheResumePointForAnExplicitRetry() {
        memory.seed(revision(), VideoResumeState(24_000, playWhenReady = false))
        val moved = File(app.filesDir, "moved.bin")
        assertTrue(clip.renameTo(moved))
        show()
        val retry = awaitRetry()
        compose.runOnIdle {
            assertNull(playerView(compose.activity.window.decorView))
            assertEquals("A failed preparation writes nothing", 0, memory.saves + memory.forgets)
        }
        assertTrue(moved.renameTo(clip))
        retry.performClick()
        val player = awaitPlayer()
        compose.runOnIdle {
            assertEquals(24_000.0, player.currentPosition.toDouble(), TOLERANCE_MS)
            assertFalse(player.playWhenReady)
        }
    }

    @Test
    fun anUnknownRevisionIsNeverRemembered() {
        val unknown = node().copy(modifiedAt = 0)
        show(unknown)
        val player = awaitPlayer()
        compose.runOnIdle {
            player.seekTo(40_000)
            player.pause()
        }
        reopen()
        val second = awaitPlayer { it !== player }
        compose.runOnIdle {
            assertEquals(0L, second.currentPosition)
            assertEquals(0, memory.saves + memory.forgets + memory.reads)
        }
    }

    private fun show(node: FileNode = node()) {
        shown.value = node
        val container = container()
        compose.setContent {
            AtomicTheme(darkTheme = false, wallpaperAccent = false) {
                val current = shown.value
                if (render.value && current != null) {
                    key(previewInstance.intValue) {
                        VideoPreviewContent(current, container, resumeMemory = memory)
                    }
                }
            }
        }
    }

    private fun reopen() {
        // Replace the preview composition in one change, so two visibility writes cannot collapse.
        compose.runOnIdle { previewInstance.intValue++ }
    }

    private fun node() =
        FileNode(
            FileNodeId.file(clip.absolutePath), "clip.mp4", "clip.mp4", "video/mp4", bytes.size.toLong(), MODIFIED_AT,
            false, false, null, StorageType.INTERNAL_PRIVATE, AccessFlags.FULL, null, null,
        )

    private fun revision() = VideoRevision.of(node())!!

    private fun container(): AppContainer =
        object : AppContainer by DefaultAppContainer(app) {
            override val mediaPreviewHelper =
                MediaPreviewHelper(app, io = Dispatchers.Unconfined, metadataReader = {
                    MediaMetadata(null, null, null, DURATION_MS, 0, 0, null, null)
                }) { InMemoryBackend() }
        }

    private fun awaitPlayer(accept: (Player) -> Boolean = { true }): Player {
        var found: Player? = null
        compose.waitUntil(10_000) {
            shadowOf(Looper.getMainLooper()).idle()
            found =
                attachedPlayerViews(compose.activity.window.decorView)
                    .mapNotNull { it.player }
                    .firstOrNull(accept)
            found != null
        }
        return found!!
    }

    private fun awaitRetry() =
        compose.onNodeWithContentDescription(app.getString(R.string.action_retry)).also {
            compose.waitUntil(10_000) {
                shadowOf(Looper.getMainLooper()).idle()
                compose.onAllNodesWithContentDescription(app.getString(R.string.action_retry))
                    .fetchSemanticsNodes().isNotEmpty()
            }
        }

    private fun playerView(view: View): PlayerView? = attachedPlayerViews(view).firstOrNull()

    private fun attachedPlayerViews(view: View): Sequence<PlayerView> =
        when (view) {
            is PlayerView -> if (view.isAttachedToWindow) sequenceOf(view) else emptySequence()
            is ViewGroup -> (0 until view.childCount).asSequence().flatMap { attachedPlayerViews(view.getChildAt(it)) }
            else -> emptySequence()
        }

    /** The real store, counting what the preview asks of it after the fixture was seeded. */
    private class CountingMemory(private val store: VideoResumePreferences) : VideoResumeMemory {
        var reads = 0
        var saves = 0
        var forgets = 0

        fun seed(
            revision: VideoRevision,
            state: VideoResumeState,
        ) = store.save(revision, state)

        override suspend fun read(revision: VideoRevision): VideoResumeState? {
            reads++
            return store.read(revision)
        }

        override fun save(
            revision: VideoRevision,
            state: VideoResumeState,
        ) {
            saves++
            store.save(revision, state)
        }

        override fun forget(revision: VideoRevision) {
            forgets++
            store.forget(revision)
        }
    }

    private companion object {
        const val MODIFIED_AT = 1_700_000_000_000L
        const val DURATION_MS = 120_000L
        const val TOLERANCE_MS = 1_000.0
    }
}
