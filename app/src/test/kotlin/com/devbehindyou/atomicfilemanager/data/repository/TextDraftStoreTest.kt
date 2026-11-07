package com.devbehindyou.atomicfilemanager.data.repository

import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.repository.DraftLookup
import com.devbehindyou.atomicfilemanager.domain.repository.DraftWrite
import com.devbehindyou.atomicfilemanager.domain.repository.EditorDraft
import com.devbehindyou.atomicfilemanager.domain.usecase.LineEnding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextEncoding
import com.devbehindyou.atomicfilemanager.domain.usecase.TextFormat
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import java.io.File
import java.io.IOException
import java.security.MessageDigest

class TextDraftStoreTest {
    @TempDir lateinit var root: File

    private val dir get() = File(root, "no_backup/editor-drafts")
    private val id = FileNodeId("file:/storage/emulated/0/Documents/secret plans.txt")
    private val format = TextFormat(TextEncoding.UTF16LE, LineEnding.CRLF)

    private fun store(
        clock: () -> Long = { 1_000L },
        maxDrafts: Int = TextDraftStore.MAX_DRAFTS,
        maxTotalBytes: Long = TextDraftStore.MAX_TOTAL_BYTES,
        maxDraftBytes: Long = 2L * 1024 * 1024,
        fault: (DraftWritePhase, File) -> Unit = { _, _ -> },
    ) = TextDraftStore({ dir }, clock, maxDrafts, maxTotalBytes, maxDraftBytes, fault = fault)

    private fun draft(
        text: String,
        at: Long = 1_000L,
    ) = EditorDraft(text, format, "rev-$text", at)

    @Test
    fun `a new store instance over the same directory reads the complete draft back under an opaque name`() {
        assertEquals(DraftWrite.Written, store().write(id, draft("héllo\nwörld ✓")))
        val restarted = store()
        assertEquals(DraftLookup.Found(draft("héllo\nwörld ✓")), restarted.read(id))
        val names = dir.listFiles().orEmpty().map { it.name }
        assertEquals(listOf(TextDraftStore.keyOf(id) + TextDraftStore.DRAFT_SUFFIX), names)
        assertFalse(names.single().contains("secret"))
        assertEquals(DraftLookup.None, restarted.read(FileNodeId("file:/other.txt")))
    }

    @Test
    fun `rewrites replace the draft and discard removes it`() {
        val store = store()
        store.write(id, draft("one"))
        store.write(id, draft("two"))
        assertEquals(DraftLookup.Found(draft("two")), store().read(id))
        store.discard(id)
        assertEquals(DraftLookup.None, store().read(id))
        assertEquals(0, store().publishedCount())
    }

    @ParameterizedTest
    @EnumSource(DraftWritePhase::class)
    fun `a failure at each write phase leaves either the previous or the new complete draft`(phase: DraftWritePhase) {
        store().write(id, draft("previous"))
        val failing = store(fault = { at, _ -> if (at == phase) throw IOException("injected at $at") })
        val outcome = failing.write(id, draft("next"))
        val expected = if (phase == DraftWritePhase.PUBLISHED) "next" else "previous"
        assertEquals(if (phase == DraftWritePhase.PUBLISHED) DraftWrite.Written else DraftWrite.Failed, outcome)
        assertEquals(DraftLookup.Found(draft(expected)), store().read(id))
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(TextDraftStore.TEMP_SUFFIX) })
    }

    @ParameterizedTest
    @EnumSource(value = DraftWritePhase::class, names = ["TEMP_WRITTEN", "TEMP_SYNCED"])
    fun `process loss before rename leaves a temp the next process sweeps without touching the old draft`(
        phase: DraftWritePhase,
    ) {
        store().write(id, draft("previous"))
        val leftovers = mutableListOf<Pair<String, ByteArray>>()
        val dying =
            store(fault = { at, temp ->
                if (at == phase) {
                    // Keep what a killed process would leave: the full temp and a torn half of it.
                    val bytes = temp.readBytes()
                    leftovers += temp.name to bytes
                    leftovers += temp.name.replace(".tmp", "-torn.tmp") to bytes.copyOf(bytes.size / 2)
                    throw IOException("process lost")
                }
            })
        dying.write(id, draft("next"))
        leftovers.forEach { (name, bytes) -> File(dir, name).writeBytes(bytes) }
        assertEquals(2, dir.listFiles().orEmpty().count { it.name.endsWith(TextDraftStore.TEMP_SUFFIX) })

        val restarted = store()
        assertEquals(DraftLookup.Found(draft("previous")), restarted.read(id))
        assertTrue(dir.listFiles().orEmpty().none { it.name.endsWith(TextDraftStore.TEMP_SUFFIX) })
        assertEquals(1, restarted.publishedCount())
    }

    @Test
    fun `truncated, altered and misplaced drafts are reported damaged once and removed`() {
        val store = store()
        val file = File(dir, TextDraftStore.keyOf(id) + TextDraftStore.DRAFT_SUFFIX)

        store.write(id, draft("truncate me"))
        file.writeBytes(file.readBytes().copyOf(file.length().toInt() - 5))
        assertEquals(DraftLookup.Damaged, store().read(id))
        assertFalse(file.exists())
        assertEquals(DraftLookup.None, store().read(id))

        store.write(id, draft("flip me"))
        val bytes = file.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
        file.writeBytes(bytes)
        assertEquals(DraftLookup.Damaged, store().read(id))

        val other = FileNodeId("file:/other.txt")
        store.write(other, draft("belongs elsewhere"))
        File(dir, TextDraftStore.keyOf(other) + TextDraftStore.DRAFT_SUFFIX).renameTo(file)
        assertEquals(DraftLookup.Damaged, store().read(id))

        file.writeBytes(ByteArray(0))
        assertEquals(DraftLookup.Damaged, store().read(id))
        file.writeText("not a draft at all, but long enough to pass the size floor")
        assertEquals(DraftLookup.Damaged, store().read(id))
    }

    @Test
    fun `a draft over the size bound is not kept and removes the older draft`() {
        val store = store(maxDraftBytes = 16)
        assertEquals(DraftWrite.Written, store.write(id, draft("small")))
        assertEquals(DraftWrite.TooLarge, store.write(id, draft("x".repeat(17))))
        assertEquals(DraftLookup.None, store().read(id))
        // Multi-byte text is bounded by its encoded size, not its length in chars.
        assertEquals(DraftWrite.TooLarge, store.write(id, draft("é".repeat(9))))
    }

    @Test
    fun `eviction keeps the most recently written drafts within the count bound`() {
        var now = 1_000_000L
        val store = store(clock = { now }, maxDrafts = 3)
        val ids = (1..5).map { FileNodeId("file:/doc$it.txt") }
        ids.forEach { node ->
            now += 10_000
            assertEquals(DraftWrite.Written, store.write(node, draft(node.raw)))
        }
        now += 10_000
        store.write(ids[2], draft("rewritten"))
        now += 10_000
        store.write(FileNodeId("file:/doc6.txt"), draft("six"))
        val restarted = store()
        assertEquals(3, restarted.publishedCount())
        assertEquals(DraftLookup.Found(draft("rewritten")), restarted.read(ids[2]))
        assertEquals(DraftLookup.Found(draft(ids[4].raw)), restarted.read(ids[4]))
        listOf(0, 1, 3).forEach { assertEquals(DraftLookup.None, restarted.read(ids[it])) }
    }

    @Test
    fun `eviction keeps total bytes bounded and never evicts the draft just written`() {
        var now = 1_000_000L
        val store = store(clock = { now }, maxTotalBytes = 600)
        val ids = (1..4).map { FileNodeId("file:/doc$it.txt") }
        ids.forEach { node ->
            now += 10_000
            store.write(node, draft("y".repeat(200)))
        }
        val total = dir.listFiles().orEmpty().sumOf { it.length() }
        assertTrue(total <= 600, "total $total")
        assertTrue(store().read(ids.last()) is DraftLookup.Found)
        assertEquals(DraftLookup.None, store().read(ids.first()))
        val alone = store(clock = { now + 1 }, maxTotalBytes = 10)
        assertEquals(DraftWrite.Written, alone.write(ids.first(), draft("z".repeat(100))))
        assertEquals(1, alone.publishedCount())
    }

    @Test
    fun `no store operation touches files outside its own directory`() {
        val source = File(root, "shared/notes.txt").apply { parentFile.mkdirs() }
        source.writeText("original bytes")
        val before = sha256(source)
        val sourceId = FileNodeId.file(source.absolutePath)
        val store = store()
        store.write(sourceId, draft("edited"))
        store.read(sourceId)
        store.discard(sourceId)
        store.write(sourceId, draft("again"))
        assertEquals(before, sha256(source))
        assertEquals(listOf("notes.txt"), source.parentFile.list().orEmpty().toList())
    }

    private fun sha256(file: File) =
        MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") {
            "%02x".format(it)
        }
}
