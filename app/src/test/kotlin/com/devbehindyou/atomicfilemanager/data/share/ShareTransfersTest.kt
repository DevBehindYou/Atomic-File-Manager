package com.devbehindyou.atomicfilemanager.data.share

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ShareTransfersTest {
    @Test
    fun `progress is bounded and rate limited while final bytes always publish`() {
        var now = 0L
        val tasks = ShareTransfers { now }
        val id = tasks.start(ShareTransferDirection.UPLOAD, "photo.jpg", 100) {}
        tasks.progress(id, 20)
        assertEquals(0L, tasks.active.value.single().bytesDone)
        now = 100_000_000L
        tasks.progress(id, 40)
        assertEquals(40L, tasks.active.value.single().bytesDone)
        tasks.progress(id, 200)
        assertEquals(100L, tasks.active.value.single().bytesDone)
        now += 100_000_000L
        tasks.progress(id, 10)
        assertEquals(100L, tasks.active.value.single().bytesDone)
        tasks.finish(id)
        tasks.progress(id, 100)
        assertTrue(tasks.active.value.isEmpty())
    }

    @Test
    fun `cancelling one request is idempotent and leaves other transfers running`() {
        val tasks = ShareTransfers()
        var firstCancelled = 0
        var secondCancelled = 0
        val first = tasks.start(ShareTransferDirection.DOWNLOAD, "a", 10) { firstCancelled++ }
        val second = tasks.start(ShareTransferDirection.UPLOAD, "b", 20) { secondCancelled++ }
        tasks.cancel(first)
        tasks.cancel(first)
        assertEquals(1, firstCancelled)
        assertEquals(0, secondCancelled)
        assertEquals(2, tasks.active.value.size)
        tasks.finish(first)
        tasks.cancel(first)
        assertEquals(second, tasks.active.value.single().id)
        tasks.finish(second)
        assertTrue(tasks.active.value.isEmpty())
    }
}
