package com.devbehindyou.atomicfilemanager.data.repository

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.devbehindyou.atomicfilemanager.data.backend.network.NetworkCredentialsStore
import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import com.devbehindyou.atomicfilemanager.domain.model.NetworkServerConfig
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncMode
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPair
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34], application = Application::class)
class PreferenceMutationConcurrencyTest {
    @Test
    fun syncPairWriters() {
        val context = BlockingReadContext(RuntimeEnvironment.getApplication())
        val first = SyncPairStore(context)
        val second = SyncPairStore(context)
        val removed = SyncPair("left:a", "right:a", SyncMode.COPY_NEW)
        val kept = SyncPair("left:b", "right:b", SyncMode.COPY_NEW)
        first.save(removed)
        first.save(kept)
        val updated = kept.copy(mode = SyncMode.MIRROR)
        context.race({ first.remove(removed) }, { second.save(updated) })
        assertEquals(listOf(updated), first.all())
    }

    @Test
    fun networkServerWriters() {
        val context = BlockingReadContext(RuntimeEnvironment.getApplication())
        val first = NetworkCredentialsStore(context)
        val second = NetworkCredentialsStore(context)
        val removed = server("a")
        val kept = server("b")
        val added = server("c")
        first.saveServer(removed, null)
        first.saveServer(kept, null)
        context.race({ first.deleteServer(removed.id) }, { second.saveServer(added, null) })
        assertEquals(listOf(kept, added), first.getAllServers())
    }

    private fun server(id: String) =
        NetworkServerConfig(id = id, name = id, protocol = NetworkProtocol.FTP, host = "localhost", port = 21)

    /** Holds a writer after its snapshot read, where an unprotected second writer loses the first update. */
    private class BlockingReadContext(base: Context) : ContextWrapper(base) {
        private val armed = AtomicBoolean()
        private val snapshotRead = CountDownLatch(1)
        private val releaseSnapshot = CountDownLatch(1)
        private val secondRead = CountDownLatch(1)

        override fun getApplicationContext(): Context = this

        override fun getSharedPreferences(
            name: String,
            mode: Int,
        ): SharedPreferences {
            val delegate = super.getSharedPreferences(name, mode)
            return object : SharedPreferences by delegate {
                override fun getString(
                    key: String?,
                    defValue: String?,
                ): String? {
                    val snapshot = delegate.getString(key, defValue)
                    if (armed.compareAndSet(true, false)) {
                        snapshotRead.countDown()
                        check(releaseSnapshot.await(5, TimeUnit.SECONDS))
                    } else if (snapshotRead.count == 0L && releaseSnapshot.count != 0L) {
                        secondRead.countDown()
                    }
                    return snapshot
                }
            }
        }

        fun race(
            first: () -> Unit,
            second: () -> Unit,
        ) {
            val executor = Executors.newFixedThreadPool(2)
            val secondStarted = CountDownLatch(1)
            armed.set(true)
            try {
                val firstWrite = executor.submit { first() }
                assertTrue(snapshotRead.await(5, TimeUnit.SECONDS))
                val secondWrite =
                    executor.submit {
                        secondStarted.countDown()
                        second()
                    }
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
                assertFalse(
                    "a second store must wait for the first writer",
                    secondRead.await(100, TimeUnit.MILLISECONDS),
                )
                releaseSnapshot.countDown()
                firstWrite.get(5, TimeUnit.SECONDS)
                secondWrite.get(5, TimeUnit.SECONDS)
            } finally {
                releaseSnapshot.countDown()
                executor.shutdownNow()
            }
        }
    }
}
