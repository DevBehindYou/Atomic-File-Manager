package com.devbehindyou.atomicfilemanager.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.devbehindyou.atomicfilemanager.R
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButton
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicButtonVariant
import com.devbehindyou.atomicfilemanager.core.designsystem.atoms.AtomicLoading
import com.devbehindyou.atomicfilemanager.core.designsystem.foundation.AtomicSpacing
import com.devbehindyou.atomicfilemanager.data.preview.ExternalExportProgress
import com.devbehindyou.atomicfilemanager.ui.util.FileUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal data class ExternalPreparationState(
    val busy: Boolean = false,
    val cancelling: Boolean = false,
    val bytesCopied: Long = 0,
)

/** One displayed source owns one export attempt; completion follows the helper's cleanup. */
internal class ExternalPreparationRequest(private val scope: CoroutineScope) {
    private data class Active(val ticket: Any, val job: Job)

    private val lock = Any()
    private val mutable = MutableStateFlow(ExternalPreparationState())
    val state = mutable.asStateFlow()
    private var active: Active? = null
    private var disposed = false

    fun start(work: suspend ((ExternalExportProgress) -> Unit) -> Unit): Boolean =
        synchronized(lock) {
            if (disposed || active != null) return@synchronized false
            val ticket = Any()
            // Admission is synchronous: another click cannot enqueue a second export before launch runs.
            mutable.value = ExternalPreparationState(busy = true)
            val job =
                scope.launch(start = CoroutineStart.LAZY) {
                    work { progress ->
                        synchronized(lock) {
                            if (active?.ticket === ticket && !disposed && !mutable.value.cancelling) {
                                mutable.value = mutable.value.copy(bytesCopied = progress.bytesCopied)
                            }
                        }
                    }
                }
            active = Active(ticket, job)
            // Also handles cancellation before a lazy body starts, when a coroutine finally would not run.
            job.invokeOnCompletion {
                synchronized(lock) {
                    if (active?.ticket === ticket) {
                        active = null
                        mutable.value = ExternalPreparationState()
                    }
                }
            }
            job.start()
            true
        }

    fun cancel() = requestCancellation(dispose = false)

    fun dispose() = requestCancellation(dispose = true)

    private fun requestCancellation(dispose: Boolean) {
        val job =
            synchronized(lock) {
                if (dispose) disposed = true
                active?.job?.also {
                    // Busy remains true until the copy and its NonCancellable cleanup actually finish.
                    mutable.value = mutable.value.copy(cancelling = true)
                }
            }
        job?.cancel()
    }
}

@Composable
internal fun ExternalPreparationStatus(
    state: ExternalPreparationState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(AtomicSpacing.s8)) {
        AtomicLoading(
            stringResource(
                if (state.cancelling) R.string.external_preparation_cancelling else R.string.external_preparation_bytes,
                FileUtils.formatBytes(state.bytesCopied),
            ),
            Modifier.testTag("external_export_progress"),
        )
        AtomicButton(
            stringResource(R.string.external_preparation_cancel),
            onClick = onCancel,
            variant = AtomicButtonVariant.Ghost,
            enabled = !state.cancelling,
        )
    }
}
