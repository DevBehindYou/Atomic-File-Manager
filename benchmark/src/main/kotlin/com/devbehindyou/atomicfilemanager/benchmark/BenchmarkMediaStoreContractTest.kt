package com.devbehindyou.atomicfilemanager.benchmark

import android.os.Build
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Creates only the reviewed 100-entry corpus plus its one-entry reset; no target UI/measurement. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class BenchmarkMediaStoreContractTest {
    @Test
    fun providerPreservesRequestedNamesMimeAndOwnershipAndDeletesOnlyRecordedUris() {
        val context = InstrumentationRegistry.getInstrumentation().context
        val resolver = context.contentResolver
        val plan = FixturePlan(100)
        val corpus = BenchmarkCorpus.create(context, 100)
        val rows = corpus.providerRows
        try {
            assertEquals(101, rows.size)
            assertEquals(101, rows.map { it.uri }.toSet().size)
            rows.forEachIndexed { index, row ->
                val name = if (index < 100) plan.name(index) else "reset.txt"
                val mime = if (index < 100) plan.mime(index) else "text/plain"
                assertEquals(name, row.name)
                assertEquals(mime, row.mime)
                val projection = arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.RELATIVE_PATH,
                    MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
                    MediaStore.MediaColumns.IS_PENDING,
                )
                resolver.query(row.uri, projection, null, null, null).use { cursor ->
                    checkNotNull(cursor)
                    assertTrue("recorded fixture URI must exist", cursor.moveToFirst())
                    assertEquals(name, cursor.getString(0))
                    assertEquals(mime, cursor.getString(1))
                    assertEquals(row.relativePath, cursor.getString(2))
                    assertEquals(context.packageName, cursor.getString(3))
                    assertEquals(0, cursor.getInt(4))
                    assertEquals(1, cursor.count)
                }
            }
            assertTrue("bounded recovery manifest", corpus.recoveryFile.length() in 1L..512_000L)
            val records = corpus.recoveryFile.readLines(Charsets.UTF_8).map(::JSONObject)
            rows.forEach { row ->
                val events = records.filter { it.optString("uri") == row.uri.toString() }
                assertEquals(listOf("inserted", "captured", "published"), events.map { it.getString("event") })
                val captured = events[1]
                assertEquals(row.name, captured.getString("actualName"))
                assertEquals(row.mime, captured.getString("actualMime"))
                assertEquals(row.relativePath, captured.getString("actualPath"))
                assertEquals(context.packageName, captured.getString("actualOwner"))
            }
        } finally {
            corpus.close()
        }
        rows.forEach { row ->
            resolver.query(row.uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null).use { cursor ->
                checkNotNull(cursor)
                assertEquals("only this recorded fixture URI was removed", 0, cursor.count)
            }
        }
        val terminal = corpus.recoveryFile.useLines { it.last() }
        assertEquals("cleanupComplete", JSONObject(terminal).getString("event"))
    }
}
