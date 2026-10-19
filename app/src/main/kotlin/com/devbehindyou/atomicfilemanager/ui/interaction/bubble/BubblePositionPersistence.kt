package com.devbehindyou.atomicfilemanager.ui.interaction.bubble

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal data class BubblePosition(val x: Float = 1f, val y: Float = 0.5f)

internal interface BubblePositionStore {
    fun read(): BubblePosition

    fun write(position: BubblePosition)
}

internal class BubblePositionWrites {
    val mutex = Mutex()
    val pending = AtomicReference(BubblePositionWrite())
}

internal data class BubblePositionWrite(val revision: Long = 0, val position: BubblePosition? = null)

/** Defers opening preferences to IO and keeps older drag completions from replacing newer ones. */
internal class BubblePositionPersistence(
    private val openStore: () -> BubblePositionStore,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val writes: BubblePositionWrites = BubblePositionWrites(),
) {
    constructor(context: Context, dispatcher: CoroutineDispatcher = Dispatchers.IO) :
        this({ PreferencesBubblePositionStore(context) }, dispatcher, sharedWrites)

    private val store by lazy(openStore)
    private val interactions = AtomicLong()

    /** Called on the UI thread before a drag can change its first coordinate. */
    fun markInteraction() {
        interactions.incrementAndGet()
    }

    /** Resumes on the caller's dispatcher; a user gesture always wins over the initial disk read. */
    suspend fun loadInitial(onLoaded: (BubblePosition) -> Unit) {
        val position =
            withContext(dispatcher) {
                writes.mutex.withLock { writes.pending.get().position ?: store.read() }
            }
        if (interactions.get() == 0L) onLoaded(position)
    }

    fun save(
        scope: CoroutineScope,
        position: BubblePosition,
    ): Deferred<Result<Unit>> {
        markInteraction()
        val request = writes.pending.updateAndGet { BubblePositionWrite(it.revision + 1, position) }
        // Enter before returning so a screen dismissal cannot cancel an already requested save.
        // Only this finite preference update survives disposal; no UI work runs in this section.
        return scope.async(start = CoroutineStart.UNDISPATCHED) {
            withContext(NonCancellable + dispatcher) {
                writes.mutex.withLock {
                    if (writes.pending.get() !== request) {
                        Result.success(Unit)
                    } else {
                        try {
                            store.write(position)
                            Result.success(Unit)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            // Position persistence is best effort; keep immediate UI movement usable.
                            Result.failure(failure)
                        } finally {
                            // Never discard a newer request, and never cache disk values after a flush.
                            writes.pending.compareAndSet(request, request.copy(position = null))
                        }
                    }
                }
            }
        }
    }

    private companion object {
        // A disposed rail's finite save and a replacement rail must use the same ordering.
        val sharedWrites = BubblePositionWrites()
    }
}

private class PreferencesBubblePositionStore(context: Context) : BubblePositionStore {
    private val preferences = context.getSharedPreferences("bubble_position", Context.MODE_PRIVATE)

    override fun read(): BubblePosition =
        BubblePosition(
            x = if (preferences.getFloat("x", 1f) < 0.5f) 0f else 1f,
            y = preferences.getFloat("y", 0.5f).coerceIn(0f, 1f),
        )

    override fun write(position: BubblePosition) {
        preferences.edit().putFloat("x", position.x).putFloat("y", position.y).apply()
    }
}
