package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.data.preview.SelectedContentType
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OpeningTypeResolutionTest {
    private val first = node("first")
    private val second = node("second")
    private val third = node("third")

    @Test
    fun synchronousSelectionRejectsDuplicatesAndRunsOnlyTheLatestBeforeDispatch() =
        runTest {
            val starts = mutableListOf<FileNodeId>()
            val request =
                OpeningTypeResolution(this) {
                    starts += it.id
                    SelectedContentType.Unresolved(it)
                }
            assertTrue(request.select(first))
            assertFalse(request.select(first))
            assertTrue(request.state.value.resolving)
            assertTrue(starts.isEmpty())
            assertTrue(request.select(second))
            assertTrue(request.select(third))
            runCurrent()
            assertEquals(listOf(third.id), starts)
            assertEquals(
                OpeningTypeResolutionState(third.previewSourceKey(), SelectedContentType.Unresolved(third)),
                request.state.value,
            )
        }

    @Test
    fun sourceChangesDrainOwnedCleanupAndDropIntermediateSelections() =
        runTest {
            val starts = mutableListOf<FileNodeId>()
            val cleanupStarted = CompletableDeferred<Unit>()
            val releaseCleanup = CompletableDeferred<Unit>()
            val request =
                OpeningTypeResolution(this) { source ->
                    starts += source.id
                    if (source.id == first.id) {
                        try {
                            awaitCancellation()
                        } finally {
                            withContext(NonCancellable) {
                                cleanupStarted.complete(Unit)
                                releaseCleanup.await()
                            }
                        }
                    }
                    SelectedContentType.Ready(source.copy(mimeType = "image/png"))
                }
            request.select(first)
            runCurrent()
            request.select(second)
            runCurrent()
            assertTrue(cleanupStarted.isCompleted)
            request.select(third)
            runCurrent()
            assertEquals(listOf(first.id), starts)
            assertEquals(third.previewSourceKey(), request.state.value.key)
            assertTrue(request.state.value.resolving)
            assertTrue(request.state.value.cancelling)
            assertEquals(null, request.state.value.result)
            releaseCleanup.complete(Unit)
            runCurrent()
            assertEquals(listOf(first.id, third.id), starts)
            assertEquals(SelectedContentType.Ready(third.copy(mimeType = "image/png")), request.state.value.result)
            assertFalse(request.state.value.resolving)
        }

    @Test
    fun lateResultsFromAnUncooperativeCancelledSourceCannotReplaceTheCurrentSource() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val starts = mutableListOf<FileNodeId>()
            val request =
                OpeningTypeResolution(this) { source ->
                    starts += source.id
                    if (source.id == first.id) withContext(NonCancellable) { release.await() }
                    SelectedContentType.Ready(source.copy(mimeType = "image/png"))
                }
            request.select(first)
            runCurrent()
            request.select(second)
            runCurrent()
            assertEquals(second.previewSourceKey(), request.state.value.key)
            assertEquals(null, request.state.value.result)
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf(first.id, second.id), starts)
            assertEquals(second.id, request.state.value.result!!.node.id)
        }

    @Test
    fun disposalImmediatelyInvalidatesPendingWorkAndNeverPublishesAfterCleanup() =
        runTest {
            val release = CompletableDeferred<Unit>()
            val starts = mutableListOf<FileNodeId>()
            val request =
                OpeningTypeResolution(this) { source ->
                    starts += source.id
                    withContext(NonCancellable) { release.await() }
                    SelectedContentType.Ready(source.copy(mimeType = "image/png"))
                }
            request.select(first)
            runCurrent()
            request.select(second)
            request.dispose()
            assertTrue(request.state.value.disposed)
            assertEquals(null, request.state.value.result)
            assertFalse(request.select(third))
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf(first.id), starts)
            assertEquals(OpeningTypeResolutionState(disposed = true), request.state.value)
            assertFalse(request.select(first.copy(name = "same-id-update")))
        }

    @Test
    fun disposalBeforeDispatchDoesNotOpenTheSource() =
        runTest {
            val request = OpeningTypeResolution(this) { error("Disposed owner must never open content") }
            request.select(first)
            request.dispose()
            runCurrent()
            assertEquals(OpeningTypeResolutionState(disposed = true), request.state.value)
        }

    @Test
    fun typedFailureAndUnexpectedFailureStayRecoverableWithoutCancellingThePane() =
        runTest {
            val denied = FileError.AccessDenied(first.name)
            val request =
                OpeningTypeResolution(this) { source ->
                    if (source.id == first.id) {
                        SelectedContentType.Failure(
                            source,
                            denied,
                        )
                    } else {
                        throw IOException("read")
                    }
                }
            request.select(first)
            runCurrent()
            assertEquals(SelectedContentType.Failure(first, denied), request.state.value.result)
            assertTrue(request.select(second))
            runCurrent()
            assertEquals(
                SelectedContentType.Failure(second, FileError.IoFailure(second.name)),
                request.state.value.result,
            )
            assertFalse(request.state.value.resolving)
        }

    private fun node(name: String) =
        FileNode(
            FileNodeId.file("/owned-prefix/$name"), name, name, "application/octet-stream", 100, 1000,
            false, false, null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
        )
}
