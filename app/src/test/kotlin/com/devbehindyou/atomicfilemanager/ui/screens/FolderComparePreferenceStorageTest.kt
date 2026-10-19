package com.devbehindyou.atomicfilemanager.ui.screens

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import com.devbehindyou.atomicfilemanager.data.repository.SyncPairStore
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncMode
import com.devbehindyou.atomicfilemanager.domain.usecase.SyncPair
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.concurrent.Executors

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 34], application = Application::class)
class FolderComparePreferenceStorageTest {
    @Test
    fun legacyFormatAndDispatch() =
        runBlocking {
            val base = RuntimeEnvironment.getApplication()
            SyncPairStore(base).save(SyncPair("left", "right", SyncMode.MIRROR))
            val caller = Thread.currentThread()
            val threads = mutableListOf<Thread>()
            val context =
                object : ContextWrapper(base) {
                    override fun getApplicationContext(): Context = this

                    override fun getSharedPreferences(
                        name: String,
                        mode: Int,
                    ): SharedPreferences {
                        threads += Thread.currentThread()
                        val delegate = super.getSharedPreferences(name, mode)
                        return object : SharedPreferences by delegate {
                            override fun getString(
                                key: String?,
                                defValue: String?,
                            ): String? {
                                threads += Thread.currentThread()
                                return delegate.getString(key, defValue)
                            }

                            override fun edit(): SharedPreferences.Editor {
                                threads += Thread.currentThread()
                                return delegate.edit()
                            }
                        }
                    }
                }
            Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { io ->
                val preferences = comparePairPreferences(context, io)
                assertTrue(threads.isEmpty())
                assertEquals(SyncMode.MIRROR, preferences.access { it.findMode("left", "right") })
                preferences.update { it.save(SyncPair("left", "right", SyncMode.COPY_NEW)) }
                assertTrue(threads.size >= 4)
                assertTrue(threads.all { it !== caller })
                assertEquals(SyncMode.COPY_NEW, SyncPairStore(base).find("left", "right")?.mode)
            }
        }
}
