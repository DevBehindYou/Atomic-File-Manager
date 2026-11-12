package com.devbehindyou.atomicfilemanager.domain.usecase

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.testing.InMemoryBackend
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.zip.CRC32

/**
 * Real RAR bytes. RAR5 files come from RAR 7.23 (`rar a -m3 [-p|-hp]`, one 19-byte `hello.txt`),
 * since current WinRAR writes nothing older. RAR 4 cases are built here in the stored layout of the
 * RAR 2.9-4.x format, the variant the bundled junrar reader supports.
 */
class ArchiveRarVariantsTest {
    private val backend = InMemoryBackend()

    @ParameterizedTest(name = "RAR5 fixture {index}")
    @ValueSource(strings = [RAR5, RAR5_ENCRYPTED, RAR5_HEADERS_ENCRYPTED])
    fun `rar5 files say they are rar5 instead of damaged, and extract nothing`(fixture: String) =
        runTest {
            val bytes = Base64.getDecoder().decode(fixture)
            val id = backend.putFile(backend.rootId, "modern.rar", bytes)

            val listed = InspectArchiveUseCase { backend }(id)
            val extracted = extractInto(id, "out")

            assertEquals(FileError.UnsupportedFormat(RAR5_UNSUPPORTED), (listed as FileResult.Failure).error)
            assertEquals(
                FileError.UnsupportedFormat(RAR5_UNSUPPORTED),
                (extracted as FileOperationsEngine.ItemResult.Failure).error,
            )
            assertEquals(emptyList<String>(), names(child(backend.rootId, "out")))
            assertArrayEquals(bytes, read(id))
        }

    @Test
    fun `a stored rar 4 archive lists and extracts its files with folders`() =
        runTest {
            val notes = "first line\nsecond line\n".toByteArray()
            val bytes = storedRar4("hello.txt" to "Atomic RAR fixture\n".toByteArray(), "docs/notes.txt" to notes)
            val id = backend.putFile(backend.rootId, "classic.rar", bytes)

            val inspect = InspectArchiveUseCase { backend }
            val listed = (inspect(id) as FileResult.Success).value.filterNot { it.isDirectory }
            val extracted = extractInto(id, "out")

            assertEquals(
                listOf("hello.txt" to 19L, "docs/notes.txt" to notes.size.toLong()),
                listed.map { it.path to it.uncompressedSize },
            )
            assertEquals(FileOperationsEngine.ItemResult.Success, extracted)
            val out = child(backend.rootId, "out")
            assertEquals("Atomic RAR fixture\n", text(child(out, "hello.txt")))
            assertArrayEquals(notes, read(child(child(out, "docs"), "notes.txt")))
            assertArrayEquals(bytes, read(id))
        }

    @Test
    fun `a rar 4 entry whose data fails its checksum publishes nothing`() =
        runTest {
            val good = storedRar4("hello.txt" to "Atomic RAR fixture\n".toByteArray())
            // Flip one stored data byte after the file header; the header CRC stays valid.
            val bytes = good.copyOf().also { it[it.size - 8] = (it[it.size - 8].toInt() xor 0x01).toByte() }
            val id = backend.putFile(backend.rootId, "flipped.rar", bytes)

            val extracted = extractInto(id, "out")

            assertTrue((extracted as FileOperationsEngine.ItemResult.Failure).error is FileError.CorruptedArchive)
            assertEquals(emptyList<String>(), names(child(backend.rootId, "out")))
        }

    private suspend fun extractInto(
        archive: FileNodeId,
        folder: String,
    ): FileOperationsEngine.ItemResult {
        val target = (backend.createDirectory(backend.rootId, folder) as FileResult.Success).value.id
        return ArchiveOperationsHelper.extractArchive(archive, target, backend, backend, {}, {})
    }

    private suspend fun names(folder: FileNodeId): List<String> =
        (backend.listChildren(folder).first() as FileResult.Success).value.map { it.name }

    private suspend fun child(
        parent: FileNodeId,
        name: String,
    ): FileNodeId = (backend.listChildren(parent).first() as FileResult.Success).value.single { it.name == name }.id

    private suspend fun read(id: FileNodeId): ByteArray {
        val out = ByteArrayOutputStream()
        (backend.openInput(id) as FileResult.Success).value.stream().use { it.copyTo(out) }
        return out.toByteArray()
    }

    private suspend fun text(id: FileNodeId): String = read(id).toString(Charsets.UTF_8)

    /** RAR 2.9-4.x marker, main header, stored (method 0x30) file blocks and end-of-archive block. */
    private fun storedRar4(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x00))
        out.write(block(type = 0x73, flags = 0x0000, body = ByteArray(6)))
        for ((name, data) in entries) {
            val nameBytes = name.toByteArray(Charsets.US_ASCII)
            val body =
                ByteBuffer.allocate(25 + nameBytes.size).order(ByteOrder.LITTLE_ENDIAN)
                    .putInt(data.size) // Packed size
                    .putInt(data.size) // Unpacked size
                    .put(3) // Host OS: Unix
                    .putInt(CRC32().apply { update(data) }.value.toInt())
                    .putInt(DOS_TIME)
                    .put(20) // Version needed: 2.0
                    .put(0x30) // Method: store
                    .putShort(nameBytes.size.toShort())
                    .putInt(0x81A4) // Unix regular file, mode 0644
                    .put(nameBytes)
                    .array()
            out.write(block(type = 0x74, flags = 0x8000, body = body))
            out.write(data)
        }
        out.write(block(type = 0x7B, flags = 0x4000, body = ByteArray(0)))
        return out.toByteArray()
    }

    private fun block(
        type: Int,
        flags: Int,
        body: ByteArray,
    ): ByteArray {
        val bytes =
            ByteBuffer.allocate(7 + body.size).order(ByteOrder.LITTLE_ENDIAN)
                .putShort(0)
                .put(type.toByte())
                .putShort(flags.toShort())
                .putShort((7 + body.size).toShort())
                .put(body)
                .array()
        val crc = CRC32().apply { update(bytes, 2, bytes.size - 2) }.value.toInt()
        bytes[0] = crc.toByte()
        bytes[1] = (crc shr 8).toByte()
        return bytes
    }

    private companion object {
        // 2024-05-01 13:45:10 in MS-DOS date/time form.
        const val DOS_TIME = (44 shl 25) or (5 shl 21) or (1 shl 16) or (13 shl 11) or (45 shl 5) or 5

        const val RAR5 =
            "UmFyIRoHAQAzkrXlCgEFBgAFAQGAgABoMK7zGQICkwAEkwAgcNsUmIAAAAloZWxsby50eHRBdG9taWMg" +
                "UkFSIGZpeHR1cmUKHXdWUQMFBAA="

        const val RAR5_ENCRYPTED =
            "UmFyIRoHAQAzkrXlCgEFBgAFAQGAgADN/fxBSwIDMaAABJMAIAMiwxKAAwAJaGVsbG8udHh0MAEAAw/t" +
                "J+V0Goy5XjLF+Up8Au1EBKnrFey4Y4mbyQAA5vK+jOt3FnAnru7OL96xzz5Y5KDVYlohBAyxxAJ4P555" +
                "6xR3J7TcNn+Lf6ncO1KAHXdWUQMFBAA="

        const val RAR5_HEADERS_ENCRYPTED =
            "UmFyIRoHAQBJGAR/IQQAAAEPhyP+XMV3Xy/BqIxjeBMM8Jd3bNdxpmJXn64yejTN/sqYnnT1WJMZITFI" +
                "U4KgfFcUFJhJsSRmKGtlWX/wS2ylvGtrJZ+ST69iNcuF1qDp4AA6QoZfhmtO5GcYz6m9WI9GN5z7QeGF" +
                "ploPyQt2wkTZtLEnYr+T6JK5mogFSvWSufIqrM7IoZnCJULkYPNsLaM55iD49VfWOYd61ECKYOPzapBO" +
                "Z7Nhfj6JRYXafKDQ4Kcomrob/wMxpFOK5iwv9RMnqX/uH8KirVRfDIhAdPymUm26EpRU8poTN1DQAg=="
    }
}
