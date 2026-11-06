package com.devbehindyou.atomicfilemanager.data.backend

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.repository.BackendType
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase 3 AC2: "Instrumented listing succeeds on API 28, 29, 30, 33, 36 — with the correct
 * backend chosen automatically on each." Cannot be run in the sandbox this project was
 * built in (no device/emulator access at all — see `PHASE_1_NOTES.md` §0) — structurally
 * complete, not evidence it passes anywhere.
 *
 * **Honestly scoped down from the full AC:** this only exercises `file:` ids against the
 * app's own private storage, on whichever single device/emulator actually runs it — it
 * doesn't run across the specific five API levels the AC names (that needs the real
 * `testing/DEVICE_MATRIX.md` emulator matrix, at merge-to-main, per
 * `testing/TEST_STRATEGY.md` §11), and it doesn't exercise SAF/MediaStore selection based on
 * live permission state (that decision belongs to Phase 4, which doesn't exist yet — see
 * `StorageBackendSelector`'s KDoc). What this *does* prove, once someone can actually run
 * it: `StorageBackendSelector` correctly routes a `file:` id to a working
 * `FileSystemBackend` under a real Android runtime, not just under Robolectric's shadow
 * layer.
 */
@RunWith(AndroidJUnit4::class)
class StorageBackendSelectorInstrumentedTest {
    @Test
    fun fileIdRoutesToAWorkingFileSystemBackendOnARealDevice() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<android.content.Context>()
            val backends: Map<BackendType, StorageBackend> =
                mapOf(
                    BackendType.FILE to FileSystemBackend(context),
                )
            val selector = StorageBackendSelector(backends)

            val testDir = File(context.filesDir, "instrumented-test-${System.currentTimeMillis()}").apply { mkdir() }
            try {
                val rootId = FileNodeId.file(testDir.absolutePath)
                val backend = selector.forNode(rootId)

                assertEquals(BackendType.FILE, backend.type)

                val created = backend.createDirectory(rootId, "child")
                assertTrue(created is FileResult.Success)

                val listed = backend.listChildren(rootId).first()
                assertTrue(listed is FileResult.Success)
                assertTrue((listed as FileResult.Success).value.any { it.name == "child" })
            } finally {
                testDir.deleteRecursively()
            }
        }
}
