package com.devbehindyou.atomicfilemanager.data.backend.network

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import com.devbehindyou.atomicfilemanager.domain.model.NetworkServerConfig
import com.devbehindyou.atomicfilemanager.domain.usecase.ArchiveOperationsHelper
import com.devbehindyou.atomicfilemanager.domain.usecase.FileOperationsEngine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipFile

/** Real SFTP against an in-process Apache MINA SSHD server rooted in a temporary folder. */
class SftpBackendTest {
    @TempDir
    lateinit var root: File

    @TempDir
    lateinit var keys: Path

    private val servers = mutableListOf<SshServer>()
    private val knownHosts = InMemoryKnownHosts()
    private lateinit var backend: SftpBackend
    private var port = 0

    private fun startServer(keyFile: Path): SshServer =
        SshServer.setUpDefaultServer().apply {
            host = "127.0.0.1"
            port = 0
            keyPairProvider = SimpleGeneratorHostKeyProvider(keyFile)
            subsystemFactories = listOf(SftpSubsystemFactory())
            fileSystemFactory = VirtualFileSystemFactory(root.toPath())
            setPasswordAuthenticator { user, password, _ -> user == "alice" && password == "pw" }
            start()
            servers += this
        }

    @BeforeEach
    fun setUp() {
        port = startServer(keys.resolve("a.ser")).port
        backend = backendFor(password = "pw")
    }

    private fun backendFor(password: String): SftpBackend {
        val config = NetworkServerConfig("srv", "Home NAS", NetworkProtocol.SFTP, "127.0.0.1", port, "alice", "/")
        return SftpBackend({ id -> config.takeIf { id == "srv" } }, { password }, knownHosts)
    }

    @AfterEach
    fun stop() {
        servers.forEach { it.stop(true) }
    }

    private fun id(path: String) = FileNodeId.sftp("srv", path)

    @Test
    fun `root metadata ends ancestry and children retain the root parent`() =
        runBlocking {
            File(root, "docs").mkdirs()
            val rootNode = (backend.getNode(id("/")) as FileResult.Success).value
            val child = (backend.getNode(id("/docs")) as FileResult.Success).value
            assertTrue(rootNode.isDirectory)
            assertNull(rootNode.parentId)
            assertEquals(rootNode.id, child.parentId)
        }

    @Test
    fun `compression into an unrelated SFTP directory traverses the root safely`() =
        runBlocking {
            File(root, "source").mkdirs()
            File(root, "source/a.txt").writeText("alpha")
            File(root, "destination").mkdirs()
            val result =
                ArchiveOperationsHelper.compressItems(
                    sources = listOf(id("/source")),
                    destParentId = id("/destination"),
                    zipName = "backup.zip",
                    compressionLevel = null,
                    destBackend = backend,
                    backendSelector = { backend },
                    onBytesCopied = {},
                    emitProgress = {},
                )
            assertEquals(FileOperationsEngine.ItemResult.Success, result)
            ZipFile(File(root, "destination/backup.zip")).use { zip ->
                assertEquals(setOf("source/", "source/a.txt"), zip.entries().toList().map { it.name }.toSet())
                assertEquals(
                    "alpha",
                    zip.getInputStream(zip.getEntry("source/a.txt")).bufferedReader().use { it.readText() },
                )
            }
            assertEquals("alpha", File(root, "source/a.txt").readText())
            assertEquals(listOf("backup.zip"), File(root, "destination").list()!!.toList())
        }

    @Test
    fun `lists, uploads, downloads, renames and deletes`() =
        runBlocking {
            File(root, "docs").mkdirs()
            File(root, "docs/a.txt").writeText("alpha")

            val listed = (backend.listChildren(id("/docs")).first() as FileResult.Success).value
            assertEquals(listOf("a.txt"), listed.map { it.name })
            assertEquals(5L, listed.single().size)

            val target = (backend.openOutput(id("/docs"), "b.txt", null) as FileResult.Success).value
            target.stream().use { it.write("bravo".toByteArray()) }
            target.setLastModified(1_700_000_000_000L)
            val uploaded = (target.toNode() as FileResult.Success).value
            assertEquals("b.txt", uploaded.name)
            assertEquals("bravo", File(root, "docs/b.txt").readText())
            assertEquals(1_700_000_000_000L, File(root, "docs/b.txt").lastModified())
            assertFalse(File(root, "docs/.b.txt.atomic-part").exists())

            val read = (backend.openInput(id("/docs/a.txt")) as FileResult.Success).value
            assertEquals("alpha", read.stream().use { it.readBytes().decodeToString() })

            val renamed = (backend.rename(id("/docs/a.txt"), "c.txt") as FileResult.Success).value
            assertEquals(id("/docs/c.txt"), renamed.id)
            assertTrue(backend.rename(id("/docs/c.txt"), "b.txt") is FileResult.Failure)

            val folder = (backend.createDirectory(id("/"), "new") as FileResult.Success).value
            assertTrue(folder.isDirectory)
            assertTrue(backend.exists(id("/"), "new"))
            assertTrue(backend.delete(id("/docs")) is FileResult.Success)
            assertFalse(File(root, "docs").exists())
        }

    @Test
    fun `a discarded upload leaves nothing behind`() =
        runBlocking {
            val target = (backend.openOutput(id("/"), "half.bin", null) as FileResult.Success).value
            target.stream().write(ByteArray(1024))
            target.discard()

            assertEquals(emptyList<String>(), root.list()!!.toList())
        }

    @Test
    fun `a wrong password and a changed host key are refused clearly`() =
        runBlocking {
            val refused = backendFor(password = "nope").listChildren(id("/")).first()
            assertTrue((refused as FileResult.Failure).error is FileError.AccessDenied)

            backend.listChildren(id("/")).first()
            servers.forEach { it.stop(true) }
            // Same address and port, different host key: an impostor.
            val impostor =
                SshServer.setUpDefaultServer().apply {
                    host = "127.0.0.1"
                    this.port = this@SftpBackendTest.port
                    keyPairProvider = SimpleGeneratorHostKeyProvider(keys.resolve("b.ser"))
                    subsystemFactories = listOf(SftpSubsystemFactory())
                    fileSystemFactory = VirtualFileSystemFactory(root.toPath())
                    setPasswordAuthenticator { _, _, _ -> true }
                    start()
                }
            servers += impostor
            backend.disconnect("srv")

            val result = backend.listChildren(id("/")).first()

            val error = (result as FileResult.Failure).error
            assertTrue(error is FileError.AccessDenied && error.name.orEmpty().contains("host key"), "was $error")
        }

    @Test
    fun `missing files and unknown servers fail without inventing anything`() =
        runBlocking {
            assertTrue((backend.getNode(id("/ghost.txt")) as FileResult.Failure).error is FileError.FileNotFound)
            val unknown = SftpBackend({ null }, { null }, knownHosts)
            val result = unknown.listChildren(FileNodeId.sftp("nope", "/")).first()
            assertTrue((result as FileResult.Failure).error is FileError.StorageUnavailable)
        }
}
