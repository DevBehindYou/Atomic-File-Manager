package com.devbehindyou.atomicfilemanager.ui.interaction.bubble

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class BubblePositionPreferencesTest {
    @Test
    fun existingFloatKeysAreLoadedWithTheSameDockingAndClamping() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val preferences = context.getSharedPreferences("bubble_position", Context.MODE_PRIVATE)
            preferences.edit().clear().putFloat("x", 0.4f).putFloat("y", 2f).commit()
            var loaded: BubblePosition? = null

            BubblePositionPersistence(ioOnlyContext(context)).loadInitial { loaded = it }

            assertEquals(BubblePosition(0f, 1f), loaded)
        }

    @Test
    fun savingRetainsTheExistingPreferencesNameAndFloatKeys() =
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val preferences = context.getSharedPreferences("bubble_position", Context.MODE_PRIVATE)
            preferences.edit().clear().commit()

            BubblePositionPersistence(ioOnlyContext(context)).save(this, BubblePosition(1f, 0.25f)).join()

            assertEquals(setOf("x", "y"), preferences.all.keys)
            assertEquals(1f, preferences.getFloat("x", -1f), 0f)
            assertEquals(0.25f, preferences.getFloat("y", -1f), 0f)
        }

    private fun ioOnlyContext(context: Context): Context {
        val callerThread = Thread.currentThread()
        return object : ContextWrapper(context) {
            override fun getSharedPreferences(
                name: String,
                mode: Int,
            ): SharedPreferences {
                assertNotSame(callerThread, Thread.currentThread())
                return super.getSharedPreferences(name, mode)
            }
        }
    }
}
