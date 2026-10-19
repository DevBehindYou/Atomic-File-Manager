package com.devbehindyou.atomicfilemanager.ui.components.preview

import com.devbehindyou.atomicfilemanager.data.preview.PdfDocumentSession
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PdfPageControllerTest {
    @Test
    fun `an older slow result never replaces the latest page or its label`() =
        runTest {
            val oldPage = CompletableDeferred<FileResult<String>>()
            val session = session { if (it == 0) oldPage.await() else FileResult.Success("page $it") }
            val controller = PdfPageController<String>()
            val first = launch(start = CoroutineStart.UNDISPATCHED) { controller.show(session, 0) }
            controller.show(session, 2)
            oldPage.complete(FileResult.Success("page 0"))
            first.join()
            assertEquals(PdfRenderedPage(2, "page 2"), controller.state.value.rendered)
            assertEquals(2, controller.state.value.requested)
        }

    @Test
    fun `requesting another page hides the previous bitmap until matching completion`() =
        runTest {
            val next = CompletableDeferred<FileResult<String>>()
            val session = session { if (it == 0) FileResult.Success("first") else next.await() }
            val controller = PdfPageController<String>()
            controller.show(session, 0)
            val pending = launch(start = CoroutineStart.UNDISPATCHED) { controller.show(session, 1) }
            assertTrue(controller.state.value.loading)
            assertNull(controller.state.value.rendered)
            next.complete(FileResult.Success("second"))
            pending.join()
            assertEquals(PdfRenderedPage(1, "second"), controller.state.value.rendered)
        }

    @Test
    fun `a successful retry clears the page failure`() =
        runTest {
            var fail = true
            val session =
                session {
                    if (fail) {
                        FileResult.Failure(
                            FileError.IoFailure("unavailable"),
                        )
                    } else {
                        FileResult.Success("ok")
                    }
                }
            val controller = PdfPageController<String>()
            controller.show(session, 1)
            assertTrue(controller.state.value.failed)
            fail = false
            controller.show(session, 1)
            assertFalse(controller.state.value.failed)
            assertEquals(PdfRenderedPage(1, "ok"), controller.state.value.rendered)
        }

    @Test
    fun `a cancelled request returning from an uncooperative renderer cannot publish`() =
        runTest {
            val controller = PdfPageController<String>()
            val session =
                session {
                    currentCoroutineContext().cancel()
                    FileResult.Success("late")
                }
            launch { controller.show(session, 0) }
            advanceUntilIdle()
            assertNull(controller.state.value.rendered)
        }

    private fun session(render: suspend (Int) -> FileResult<String>) =
        object : PdfDocumentSession<String> {
            override val pageCount = 3

            override suspend fun renderPage(
                index: Int,
                maxDimension: Int,
            ) = render(index)
        }
}
