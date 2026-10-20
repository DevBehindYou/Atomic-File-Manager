package com.devbehindyou.atomicfilemanager.data.share

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ShareTransferDirection { UPLOAD, DOWNLOAD }

data class ShareTransfer(
    val id: String,
    val direction: ShareTransferDirection,
    val name: String,
    val bytesDone: Long,
    val bytesTotal: Long,
)

/** Process-wide live HTTP transfers. Entries live until their worker exits, including on failure. */
class ShareTransfers(private val clock: () -> Long = System::nanoTime) {
    private val _active = MutableStateFlow<List<ShareTransfer>>(emptyList())
    val active = _active.asStateFlow()
    private val cancellations = mutableMapOf<String, () -> Unit>()
    private val publishedAt = mutableMapOf<String, Long>()

    @Synchronized
    fun start(
        direction: ShareTransferDirection,
        name: String,
        total: Long,
        cancel: () -> Unit,
    ): String {
        val id = UUID.randomUUID().toString()
        cancellations[id] = cancel
        publishedAt[id] = clock()
        _active.value += ShareTransfer(id, direction, name, 0, total.coerceAtLeast(0))
        return id
    }

    @Synchronized
    fun progress(
        id: String,
        bytes: Long,
    ) {
        val task = _active.value.firstOrNull { it.id == id } ?: return
        val now = clock()
        if (bytes < task.bytesTotal && now - (publishedAt[id] ?: now) < UPDATE_NANOS) return
        publishedAt[id] = now
        _active.value =
            _active.value.map {
                if (it.id == id) it.copy(bytesDone = bytes.coerceIn(it.bytesDone, it.bytesTotal)) else it
            }
    }

    @Synchronized
    fun finish(id: String) {
        cancellations.remove(id)
        publishedAt.remove(id)
        _active.value = _active.value.filterNot { it.id == id }
    }

    fun cancel(id: String) {
        // Close only this request's socket. Never hold the monitor across external work.
        val action = synchronized(this) { cancellations.remove(id) }
        runCatching { action?.invoke() }
    }

    private companion object {
        const val UPDATE_NANOS = 100_000_000L
    }
}
