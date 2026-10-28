package com.devbehindyou.atomicfilemanager.data.repository

import android.app.Application
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
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
@Config(sdk = [28, 34], application = Application::class)
class FolderSortPreferencesTest {
    @Test
    fun dispatchAndReload() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            val calls = mutableListOf<String>()
            val recordingContext =
                object : ContextWrapper(context) {
                    override fun getSharedPreferences(
                        name: String,
                        mode: Int,
                    ): SharedPreferences {
                        calls += Thread.currentThread().name.substringBefore(" @coroutine#")
                        return super.getSharedPreferences(name, mode)
                    }
                }
            Executors.newSingleThreadExecutor { Thread(it, "folder-sort-io") }.asCoroutineDispatcher().use { io ->
                val store = FolderSortPreferences(recordingContext, io)
                assertTrue("construction must not open preferences", calls.isEmpty())
                store.set("opaque:folder", "SIZE")
                assertEquals("SIZE", store.get("opaque:folder"))
                assertEquals(listOf("folder-sort-io"), calls)
                assertEquals("SIZE", FolderSortPreferences(context, io).get("opaque:folder"))
            }
        }

    @Test
    fun existingFormatAndCapacity() =
        runBlocking {
            val context = RuntimeEnvironment.getApplication()
            val prefs = context.getSharedPreferences("atomic_folder_sort", Context.MODE_PRIVATE)
            prefs.edit().clear().putString("existing", "DATE_MODIFIED").commit()
            val store = FolderSortPreferences(context)
            assertEquals("DATE_MODIFIED", store.get("existing"))
            repeat(499) { store.set("folder:$it", "NAME") }
            store.set("existing", "SIZE")
            assertEquals(500, prefs.all.size)
            assertEquals("SIZE", store.get("existing"))
            store.set("new", "DATE_MODIFIED")
            assertEquals(mapOf("new" to "DATE_MODIFIED"), prefs.all)
        }
}
