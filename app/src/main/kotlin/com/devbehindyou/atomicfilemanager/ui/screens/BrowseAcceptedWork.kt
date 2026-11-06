package com.devbehindyou.atomicfilemanager.ui.screens

import androidx.annotation.MainThread
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/** Main-owned bookkeeping; accepted mutations keep their existing model scope and cancellation. */
internal class BrowseAcceptedWork(private val scope: () -> CoroutineScope) {
    var retired = false
        private set

    private var count = 0
    private var retirementCallback: (() -> Unit)? = null

    @MainThread
    fun retire(
        stopObservation: () -> Unit,
        onDrained: () -> Unit,
    ) {
        if (retired) return
        // Seal re-entry before hiding, which can synchronously complete cancelled observers.
        retired = true
        retirementCallback = onDrained
        stopObservation()
        completeRetirementIfIdle()
    }

    /** Count admission before launch, including work cancelled before its first dispatch. */
    @MainThread
    fun launch(
        start: CoroutineStart = CoroutineStart.DEFAULT,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        if (retired) return
        count++
        val job =
            try {
                scope().launch(start = start, block = block)
            } catch (failure: Exception) {
                count--
                completeRetirementIfIdle()
                throw failure
            }
        job.invokeOnCompletion {
            count--
            completeRetirementIfIdle()
        }
    }

    private fun completeRetirementIfIdle() {
        if (!retired || count != 0) return
        val callback = retirementCallback ?: return
        retirementCallback = null
        callback()
    }
}
