package com.devbehindyou.atomicfilemanager.ui.components.preview

import com.devbehindyou.atomicfilemanager.data.preview.PdfDocumentSession
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class PdfRenderedPage<T>(val index: Int, val image: T)

internal data class PdfPageState<T>(
    val requested: Int = 0,
    val rendered: PdfRenderedPage<T>? = null,
    val loading: Boolean = true,
    val failed: Boolean = false,
)

/** Requests and publication run on the caller's UI dispatcher; rendering happens in the session. */
internal class PdfPageController<T> {
    private val mutableState = MutableStateFlow(PdfPageState<T>())
    val state = mutableState.asStateFlow()
    private var generation = 0L

    suspend fun show(
        session: PdfDocumentSession<T>,
        index: Int,
    ) {
        val request = ++generation
        mutableState.value = PdfPageState(requested = index)
        val result = session.renderPage(index)
        currentCoroutineContext().ensureActive()
        if (request != generation) return
        mutableState.value =
            when (result) {
                is FileResult.Success -> PdfPageState(index, PdfRenderedPage(index, result.value), loading = false)
                is FileResult.Failure -> PdfPageState(requested = index, loading = false, failed = true)
            }
    }
}
