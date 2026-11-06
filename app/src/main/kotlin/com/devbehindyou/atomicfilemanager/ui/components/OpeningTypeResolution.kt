package com.devbehindyou.atomicfilemanager.ui.components

import com.devbehindyou.atomicfilemanager.data.preview.SelectedContentType
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

internal data class OpeningTypeResolutionState(
    val key: PreviewSourceKey? = null,
    val result: SelectedContentType? = null,
    val resolving: Boolean = false,
    val cancelling: Boolean = false,
    val disposed: Boolean = false,
)

/** One live pane drains its old probe before admitting only its latest selected source. */
internal class OpeningTypeResolution(
    private val scope: CoroutineScope,
    private val resolve: suspend (FileNode) -> SelectedContentType,
) {
    private data class Pending(
        val node: FileNode,
        val key: PreviewSourceKey,
        val work: suspend (FileNode) -> SelectedContentType,
    )

    private data class Active(val ticket: Any, val job: Job)

    private val lock = Any()
    private val mutable = MutableStateFlow(OpeningTypeResolutionState())
    val state = mutable.asStateFlow()
    private var active: Active? = null
    private var pending: Pending? = null
    private var disposed = false

    fun select(
        node: FileNode,
        refresh: Boolean = false,
        work: suspend (FileNode) -> SelectedContentType = resolve,
    ): Boolean {
        val key = node.previewSourceKey()
        val cancel: Job?
        synchronized(lock) {
            if (disposed || !scope.isActive || (!refresh && mutable.value.key == key)) return false
            if (refresh && (active != null || mutable.value.key != key)) return false
            pending = Pending(node, key, work)
            mutable.value = OpeningTypeResolutionState(key = key, resolving = true, cancelling = active != null)
            cancel = active?.job
            if (cancel == null) startPending()
        }
        cancel?.cancel()
        return true
    }

    /** Retire an obsolete selection without starting a probe or replacing this owner. */
    fun invalidateSelection() {
        val cancel =
            synchronized(lock) {
                if (disposed) return
                pending = null
                mutable.value =
                    OpeningTypeResolutionState(
                        resolving = active != null,
                        cancelling = active != null,
                    )
                active?.job
            }
        cancel?.cancel()
    }

    fun dispose() {
        val cancel =
            synchronized(lock) {
                disposed = true
                pending = null
                mutable.value =
                    OpeningTypeResolutionState(
                        key = mutable.value.key,
                        resolving = active != null,
                        cancelling = active != null,
                        disposed = true,
                    )
                active?.job
            }
        cancel?.cancel()
    }

    /** Called under lock; a lazy job makes admission synchronous even before dispatch starts. */
    private fun startPending() {
        val next = pending ?: return
        pending = null
        if (disposed || !scope.isActive) return
        val ticket = Any()
        var result: SelectedContentType? = null
        val job =
            scope.launch(start = CoroutineStart.LAZY) {
                val found =
                    try {
                        next.work(next.node)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        SelectedContentType.Failure(next.node, FileError.IoFailure(next.node.name))
                    }
                currentCoroutineContext().ensureActive()
                synchronized(lock) {
                    if (!disposed && active?.ticket === ticket && mutable.value.key == next.key) result = found
                }
            }
        active = Active(ticket, job)
        job.invokeOnCompletion { cause ->
            synchronized(lock) {
                if (active?.ticket === ticket) {
                    active = null
                    when {
                        disposed -> mutable.value = OpeningTypeResolutionState(disposed = true)
                        pending != null -> startPending()
                        mutable.value.key == null -> mutable.value = OpeningTypeResolutionState()
                        mutable.value.key == next.key ->
                            mutable.value =
                                OpeningTypeResolutionState(
                                    key = next.key,
                                    result = result.takeIf { cause == null },
                                )
                    }
                }
            }
        }
        // The selected source remains resolving until this job, including helper cleanup, completes.
        mutable.value = mutable.value.copy(cancelling = false)
        job.start()
    }
}
