package com.devbehindyou.atomicfilemanager.data.database.room

import android.app.Application
import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** File-backed Room checks: a fake DAO cannot establish transaction rollback or reopen durability. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 34], application = Application::class)
class SearchIndexReconciliationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "search-reconciliation-test.db"
    private lateinit var database: AtomicDatabase

    @Before
    fun setUp() {
        context.deleteDatabase(name)
        database = open()
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(name)
    }

    private fun open(): AtomicDatabase =
        Room.databaseBuilder(context, AtomicDatabase::class.java, name).allowMainThreadQueries().build()

    private fun reopen() {
        database.close()
        database = open()
    }

    @Test
    fun completedFolderSurvivesReopenAndPreservesSiblingRoots() =
        runBlocking {
            val parent = "file:/root/docs"
            val old = row("$parent/old", parent, directory = true)
            val descendant = row("${old.id}/deep.txt", old.id)
            val keep = row("$parent/keep.txt", parent)
            val sibling = row("file:/root/docs-other/other.txt", "file:/root/docs-other")
            val otherRoot = row("file:/sd/other.txt", "file:/sd")
            database.searchIndex().upsert(listOf(old, descendant, keep, sibling, otherRoot))
            val added = row("$parent/new.txt", parent)
            val updated = keep.copy(size = 99, generation = 2)

            database.searchIndex().reconcileFolder(parent, listOf(updated, added))
            reopen()

            assertEquals(setOf(updated, added, sibling, otherRoot), database.searchIndex().search("%", 100).toSet())
            database.searchIndex().reconcileFolder(parent, emptyList())
            reopen()
            assertEquals(setOf(sibling, otherRoot), database.searchIndex().search("%", 100).toSet())
        }

    @Test
    fun deleteFailureRollsBackEarlierUpsertAndRetainsOldFolderAfterReopen() =
        runBlocking {
            val parent = "file:/root/docs"
            val old = row("$parent/old.txt", parent)
            val keep = row("$parent/keep.txt", parent)
            database.searchIndex().upsert(listOf(old, keep))
            database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER fail_reconcile_delete BEFORE DELETE ON search_index " +
                    "WHEN OLD.id = '${old.id}' BEGIN SELECT RAISE(ABORT, 'injected delete failure'); END",
            )
            val added = row("$parent/new.txt", parent)
            val updated = keep.copy(size = 99, generation = 2)

            val failure =
                assertThrows(RuntimeException::class.java) {
                    runBlocking { database.searchIndex().reconcileFolder(parent, listOf(updated, added)) }
                }
            assertTrue(
                generateSequence<Throwable>(failure) { it.cause }.any {
                    it.message?.contains("injected delete failure") == true
                },
            )
            reopen()

            assertEquals(setOf(old, keep), database.searchIndex().search("%", 100).toSet())
        }

    @Test
    fun cancelledEnclosingTransactionRollsBackReconciliationAfterReopen() =
        runBlocking {
            val parent = "file:/root/docs"
            val old = row("$parent/old.txt", parent)
            database.searchIndex().upsert(listOf(old))
            val changed = CompletableDeferred<Unit>()
            val update =
                launch {
                    database.withTransaction {
                        database.searchIndex().reconcileFolder(parent, listOf(row("$parent/new.txt", parent)))
                        changed.complete(Unit)
                        awaitCancellation()
                    }
                }
            changed.await()
            update.cancelAndJoin()
            assertTrue(update.isCancelled)
            reopen()

            assertEquals(listOf(old), database.searchIndex().search("%", 100))
        }

    @Test
    fun descendantDeletionEscapesWildcardsAndRejectsRowsFromAnotherParent() =
        runBlocking {
            val parent = "file:/root/docs"
            val old = row("$parent/a_%", parent, directory = true)
            val descendant = row("${old.id}/deep.txt", old.id)
            val similar = row("$parent/abc/kept.txt", "$parent/abc")
            database.searchIndex().upsert(listOf(old, descendant, similar))
            assertThrows(IllegalArgumentException::class.java) {
                runBlocking { database.searchIndex().reconcileFolder(parent, listOf(row("file:/sd/new", "file:/sd"))) }
            }
            assertEquals(setOf(old, descendant, similar), database.searchIndex().search("%", 100).toSet())

            database.searchIndex().reconcileFolder(parent, emptyList())
            reopen()

            val remaining = database.searchIndex().search("%", 100)
            assertEquals(listOf(similar), remaining)
            assertFalse(remaining.any { it.id == old.id || it.id == descendant.id })
        }

    private fun row(
        id: String,
        parentId: String,
        directory: Boolean = false,
    ) = SearchIndexEntity(
        id = id,
        name = id.substringAfterLast('/'),
        nameLower = id.substringAfterLast('/').lowercase(),
        parentId = parentId,
        isDirectory = directory,
        size = if (directory) -1 else 1,
        modifiedAt = 0,
        mimeType = null,
        generation = 1,
    )
}
