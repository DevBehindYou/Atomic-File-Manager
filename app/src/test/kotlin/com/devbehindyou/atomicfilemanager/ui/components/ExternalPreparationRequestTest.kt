package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.data.preview.ExternalExportProgress
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExternalPreparationRequestTest {
    @Test
    fun synchronousAdmissionRejectsASecondClickBeforeTheBodyStarts() =
        runTest {
            val request = ExternalPreparationRequest(this)
            var starts = 0
            assertTrue(request.start { report ->
                starts++
                report(ExternalExportProgress(8192))
                awaitCancellation()
            })
            assertTrue(request.state.value.busy)
            assertEquals(0, starts)
            assertFalse(request.start { error("Duplicate export must not run") })
            runCurrent()
            assertEquals(1, starts)
            assertEquals(8192L, request.state.value.bytesCopied)
            request.cancel()
            runCurrent()
            assertEquals(ExternalPreparationState(), request.state.value)
        }

    @Test
    fun cancellationRemainsBusyUntilNonCancellableCleanupCompletes() =
        runTest {
            val request = ExternalPreparationRequest(this)
            val cleanupStarted = CompletableDeferred<Unit>()
            val releaseCleanup = CompletableDeferred<Unit>()
            assertTrue(request.start { report ->
                report(ExternalExportProgress(12))
                try {
                    awaitCancellation()
                } finally {
                    withContext(NonCancellable) {
                        cleanupStarted.complete(Unit)
                        releaseCleanup.await()
                        report(ExternalExportProgress(99))
                    }
                }
            })
            runCurrent()
            request.cancel()
            try {
                runCurrent()
                assertTrue(cleanupStarted.isCompleted)
                assertEquals(
                    ExternalPreparationState(busy = true, cancelling = true, bytesCopied = 12),
                    request.state.value,
                )
                assertFalse(request.start { error("Cannot compete with cleanup") })
            } finally {
                releaseCleanup.complete(Unit)
                runCurrent()
            }
            assertEquals(ExternalPreparationState(), request.state.value)
            assertTrue(request.start { })
            runCurrent()
            assertEquals(ExternalPreparationState(), request.state.value)
        }

    @Test
    fun callbacksFromTheCompletedTicketCannotContaminateANewAttempt() =
        runTest {
            val request = ExternalPreparationRequest(this)
            lateinit var oldCallback: (ExternalExportProgress) -> Unit
            assertTrue(request.start { report -> oldCallback = report })
            runCurrent()
            assertEquals(ExternalPreparationState(), request.state.value)
            assertTrue(request.start { report ->
                report(ExternalExportProgress(7))
                awaitCancellation()
            })
            runCurrent()
            oldCallback(ExternalExportProgress(900))
            assertEquals(ExternalPreparationState(busy = true, bytesCopied = 7), request.state.value)
            request.dispose()
            runCurrent()
        }

    @Test
    fun disposedOwnerIgnoresItsCallbacksAndRejectsNewAttemptsWhileAnotherOwnerWorks() =
        runTest {
            val old = ExternalPreparationRequest(this)
            lateinit var oldCallback: (ExternalExportProgress) -> Unit
            assertTrue(old.start { report ->
                oldCallback = report
                report(ExternalExportProgress(5))
                awaitCancellation()
            })
            runCurrent()
            old.dispose()
            oldCallback(ExternalExportProgress(999))
            assertEquals(ExternalPreparationState(busy = true, cancelling = true, bytesCopied = 5), old.state.value)
            val next = ExternalPreparationRequest(this)
            assertTrue(next.start { report ->
                report(ExternalExportProgress(3))
                awaitCancellation()
            })
            runCurrent()
            assertEquals(ExternalPreparationState(), old.state.value)
            assertFalse(old.start { error("Disposed source must not restart") })
            assertEquals(ExternalPreparationState(busy = true, bytesCopied = 3), next.state.value)
            next.dispose()
            runCurrent()
        }

    @Test
    fun cancellingBeforeDispatchDoesNotRunTheBodyOrLeaveBusyState() =
        runTest {
            val parent = Job()
            val request = ExternalPreparationRequest(CoroutineScope(parent + StandardTestDispatcher(testScheduler)))
            assertTrue(request.start { error("Cancelled lazy body must never run") })
            request.cancel()
            runCurrent()
            assertEquals(ExternalPreparationState(), request.state.value)
            parent.cancel()
        }
}
