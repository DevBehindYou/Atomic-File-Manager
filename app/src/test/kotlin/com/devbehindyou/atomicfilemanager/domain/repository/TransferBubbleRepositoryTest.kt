package com.devbehindyou.atomicfilemanager.domain.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.data.database.TransferBubbleDatabaseHelper
import com.devbehindyou.atomicfilemanager.data.repository.TransferBubbleRepositoryImpl
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.SQLiteMode
import java.util.UUID
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class TransferBubbleRepositoryTest {
    private lateinit var context: Context
    private lateinit var dbHelper: TransferBubbleDatabaseHelper
    private lateinit var dbName: String
    private lateinit var repository: TransferBubbleRepositoryImpl
    private val testDispatcher = UnconfinedTestDispatcher()

    private fun createTestNode(
        name: String,
        size: Long = 1024L,
    ): FileNode =
        FileNode(
            id = FileNodeId.file("/storage/emulated/0/Documents/$name"),
            name = name,
            displayName = name,
            mimeType = "text/plain",
            size = size,
            modifiedAt = 1000L,
            isDirectory = false,
            isHidden = false,
            parentId = FileNodeId.file("/storage/emulated/0/Documents"),
            storageType = StorageType.INTERNAL_SHARED,
            access = AccessFlags.FULL,
            childCount = null,
            extras = null,
        )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        // Unique database name per test for isolation
        dbName = "test_bubbles_${UUID.randomUUID()}.db"
        dbHelper = TransferBubbleDatabaseHelper(context, dbName)
        repository = TransferBubbleRepositoryImpl(dbHelper, testDispatcher)
    }

    @After
    fun tearDown() {
        dbHelper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun sevenBubbleSlots() =
        runTest(testDispatcher) {
            val names = (1..7).map { "Bubble $it" }
            val results = (1..7).map { repository.createBubble(null) }

            assertTrue("All seven bubbles must be accepted", results.all { it.isSuccess })
            assertEquals(7, repository.bubbles.value.size)
            assertEquals(names, repository.bubbles.value.map { it.displayName })
            assertEquals(7, repository.bubbles.value.map { it.id }.distinct().size)
        }

    @Test
    fun eighthRejected() =
        runTest(testDispatcher) {
            repeat(7) { repository.createBubble("B${it + 1}").getOrThrow() }
            val before = repository.bubbles.value

            val eighth = repository.createBubble("B8")

            assertTrue(eighth.isFailure)
            assertEquals("Maximum of 7 transfer bubbles allowed", eighth.exceptionOrNull()?.message)
            assertEquals(before, repository.bubbles.value)
            assertEquals(7, dbHelper.countBubbles())
        }

    @Test
    fun deletedSlotReusable() =
        runTest(testDispatcher) {
            val created = (1..7).map { repository.createBubble("B$it").getOrThrow() }
            val retainedItem = createTestNode("keep.txt")
            repository.addItemsToBubble(created.first().id, listOf(retainedItem))

            repository.deleteBubble(created[3].id).getOrThrow()
            val replacement = repository.createBubble("Replacement").getOrThrow()

            assertEquals(7, repository.bubbles.value.size)
            assertTrue(repository.bubbles.value.any { it.id == replacement.id })
            assertEquals(listOf(retainedItem.id), repository.getBubble(created.first().id)!!.items.map { it.fileNodeId })
            assertTrue(repository.createBubble("Still full").isFailure)
        }

    @Test
    fun sevenSurviveReopen() =
        runTest(testDispatcher) {
            val created = (1..7).map { repository.createBubble("B$it").getOrThrow() }
            val retainedItem = createTestNode("persist.txt")
            repository.addItemsToBubble(created.last().id, listOf(retainedItem))
            dbHelper.close()

            dbHelper = TransferBubbleDatabaseHelper(context, dbName)
            repository = TransferBubbleRepositoryImpl(dbHelper, testDispatcher)

            assertEquals(created.map { it.id }, repository.bubbles.value.map { it.id })
            assertEquals(listOf(retainedItem.id), repository.getBubble(created.last().id)!!.items.map { it.fileNodeId })
            assertTrue(repository.createBubble("B8").isFailure)
        }

    @Test
    @SQLiteMode(SQLiteMode.Mode.NATIVE)
    fun parallelAdmissionCapsSeven() =
        runTest(testDispatcher) {
            val secondHelper = TransferBubbleDatabaseHelper(context, dbName)
            try {
                Executors.newFixedThreadPool(8).asCoroutineDispatcher().use { dispatcher ->
                    val repositories =
                        listOf(
                            TransferBubbleRepositoryImpl(dbHelper, dispatcher),
                            TransferBubbleRepositoryImpl(secondHelper, dispatcher),
                        )
                    val gate = CyclicBarrier(8)
                    val results =
                        withContext(dispatcher) {
                            (0..7).map { index ->
                                async {
                                    gate.await(10, TimeUnit.SECONDS)
                                    repositories[index % 2].createBubble("Concurrent $index")
                                }
                            }.awaitAll()
                        }

                    assertEquals(7, results.count { it.isSuccess })
                    assertEquals(1, results.count { it.isFailure })
                    assertEquals(7, dbHelper.countBubbles())
                    assertEquals(7, dbHelper.getAllBubbles().map { it.id }.distinct().size)
                }
            } finally {
                secondHelper.close()
            }
        }

    @Test
    fun stagesReferencesWithoutCopying() =
        runTest(testDispatcher) {
            val bubbleRes = repository.createBubble("Staging")
            val bubble = bubbleRes.getOrThrow()

            val fileA = createTestNode("docA.txt", 500L)
            val fileB = createTestNode("docB.txt", 1500L)

            val addRes = repository.addItemsToBubble(bubble.id, listOf(fileA, fileB))
            assertTrue(addRes.isSuccess)

            val updatedBubble = repository.getBubble(bubble.id)
            assertNotNull(updatedBubble)
            assertEquals(2, updatedBubble!!.itemCount)
            assertEquals(2000L, updatedBubble.totalKnownSize)
            assertEquals("docA.txt", updatedBubble.items[0].displayNameSnapshot)
            assertEquals("docB.txt", updatedBubble.items[1].displayNameSnapshot)
        }

    @Test
    fun `can remove single item from bubble`() =
        runTest(testDispatcher) {
            val bubble = repository.createBubble("Temp").getOrThrow()
            val fileA = createTestNode("docA.txt")
            val fileB = createTestNode("docB.txt")
            repository.addItemsToBubble(bubble.id, listOf(fileA, fileB))

            val currentItems = repository.getBubble(bubble.id)!!.items
            val itemIdToRemove = currentItems.first { it.displayNameSnapshot == "docA.txt" }.id

            repository.removeItemFromBubble(bubble.id, itemIdToRemove)

            val updated = repository.getBubble(bubble.id)!!
            assertEquals(1, updated.itemCount)
            assertEquals("docB.txt", updated.items[0].displayNameSnapshot)
        }

    @Test
    fun clearingRetainsContainer() =
        runTest(testDispatcher) {
            val bubble = repository.createBubble("Batch").getOrThrow()
            repository.addItemsToBubble(bubble.id, listOf(createTestNode("file1"), createTestNode("file2")))

            repository.clearBubble(bubble.id)

            val updated = repository.getBubble(bubble.id)!!
            assertEquals(0, updated.itemCount)
            assertEquals(0L, updated.totalKnownSize)
            assertEquals(1, repository.bubbles.value.size)
        }

    @Test
    fun `deleting bubble removes container and all its staged items`() =
        runTest(testDispatcher) {
            val bubble = repository.createBubble("To Delete").getOrThrow()
            repository.addItemsToBubble(bubble.id, listOf(createTestNode("file1")))

            repository.deleteBubble(bubble.id)

            assertEquals(0, repository.bubbles.value.size)
            assertTrue(repository.getBubble(bubble.id) == null)
        }

    @Test
    fun `same file can be staged in two independent bubbles`() =
        runTest(testDispatcher) {
            val b1 = repository.createBubble("Work").getOrThrow()
            val b2 = repository.createBubble("Personal").getOrThrow()

            val sharedFile = createTestNode("resume.pdf", 4096L)
            repository.addItemsToBubble(b1.id, listOf(sharedFile))
            repository.addItemsToBubble(b2.id, listOf(sharedFile))

            val updatedB1 = repository.getBubble(b1.id)!!
            val updatedB2 = repository.getBubble(b2.id)!!

            assertEquals(1, updatedB1.itemCount)
            assertEquals(1, updatedB2.itemCount)
            assertEquals("resume.pdf", updatedB1.items[0].displayNameSnapshot)
            assertEquals("resume.pdf", updatedB2.items[0].displayNameSnapshot)
        }

    // Robolectric includes the method name in SQLite paths; keep native Windows paths short.
    @Test
    fun initialReadUsesDispatcher() =
        runTest(testDispatcher) {
            repository.createBubble("Saved").getOrThrow()
            val loader = StandardTestDispatcher(testScheduler)

            val reopened = TransferBubbleRepositoryImpl(dbHelper, loader, loadScope = this)

            assertTrue(reopened.bubbles.value.isEmpty())
            advanceUntilIdle()
            assertEquals(listOf("Saved"), reopened.bubbles.value.map { it.displayName })
        }
}
