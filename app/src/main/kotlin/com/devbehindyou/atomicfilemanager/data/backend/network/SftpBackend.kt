package com.devbehindyou.atomicfilemanager.data.backend.network

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.NetworkServerConfig
import com.devbehindyou.atomicfilemanager.domain.repository.BackendType
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.OutputTarget
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import net.schmizz.sshj.DefaultConfig
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.DisconnectReason
import net.schmizz.sshj.common.SecurityUtils
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.RemoteFile
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.transport.TransportException
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.UserAuthException
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.PublicKey
import java.security.Security
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap

/**
 * SFTP over SSH with sshj (ALL_IN_ONE_PLAN.md 3.1).
 *
 * - One connection per server, reused between folders and dropped when it breaks, so browsing
 *   doesn't pay a new handshake per folder.
 * - Host keys are trusted on first use and pinned per server in [knownHosts]; a different key
 *   later is refused, because the machine answering may not be the one the user saved.
 * - Uploads go to a hidden `.<name>.atomic-part` file and take the real name only when complete,
 *   so an interrupted transfer never leaves a truncated file under the real name.
 * - Reads use sshj's read-ahead stream for throughput on high-latency links.
 */
class SftpBackend(
    private val serverFor: (String) -> NetworkServerConfig?,
    private val passwordFor: (String) -> String?,
    private val knownHosts: KnownHosts,
) : StorageBackend {
    constructor(
        store: NetworkCredentialsStore,
        knownHosts: KnownHosts = InMemoryKnownHosts(),
    ) : this(store::getServerById, store::getPassword, knownHosts)

    override val type: BackendType = BackendType.SFTP

    override fun canHandle(id: FileNodeId): Boolean = id.prefix == FileNodeId.Prefix.SFTP

    private class Session(val ssh: SSHClient, val sftp: SFTPClient)

    private val sessions = ConcurrentHashMap<String, Session>()

    private data class Path(val serverId: String, val path: String) {
        val name: String get() = path.trimEnd('/').substringAfterLast('/')
        val parent: String
            get() = path.trimEnd('/').substringBeforeLast('/', "").ifEmpty { "/" }

        fun child(name: String): String = if (path.endsWith("/")) "$path$name" else "$path/$name"
    }

    private fun parse(id: FileNodeId): Path {
        val body = id.raw.removePrefix(FileNodeId.Prefix.SFTP.scheme)
        val colon = body.indexOf(':')
        val serverId = if (colon >= 0) body.substring(0, colon) else body
        val raw = if (colon >= 0) body.substring(colon + 1) else "/"
        return Path(serverId, if (raw.startsWith("/")) raw else "/$raw")
    }

    private fun idOf(
        serverId: String,
        path: String,
    ): FileNodeId = FileNodeId.sftp(serverId, path)

    // --- Connection pool -------------------------------------------------------------------

    private fun session(serverId: String): Session {
        sessions[serverId]?.let { if (it.ssh.isConnected && it.ssh.isAuthenticated) return it }
        synchronized(this) {
            sessions[serverId]?.let { if (it.ssh.isConnected && it.ssh.isAuthenticated) return it }
            sessions.remove(serverId)?.let { closeQuietly(it) }
            val server = serverFor(serverId) ?: throw NoServer(serverId)
            ensureBouncyCastle()
            val ssh = SSHClient(DefaultConfig())
            ssh.connectTimeout = CONNECT_TIMEOUT_MS
            ssh.timeout = READ_TIMEOUT_MS
            ssh.addHostKeyVerifier(TrustOnFirstUse(knownHosts, "sftp:${server.host}:${server.port}"))
            try {
                ssh.connect(server.host, server.port)
                ssh.authPassword(server.username, passwordFor(serverId).orEmpty())
                return Session(ssh, ssh.newSFTPClient()).also { sessions[serverId] = it }
            } catch (e: IOException) {
                runCatching { ssh.disconnect() }
                throw e
            }
        }
    }

    /** Runs [block] on the server's session, reconnecting once if the pooled one broke. */
    private suspend fun <T> withSftp(
        serverId: String,
        block: (SFTPClient) -> T,
    ): FileResult<T> =
        withContext(Dispatchers.IO) {
            try {
                FileResult.Success(block(session(serverId).sftp))
            } catch (e: TransportException) {
                sessions.remove(serverId)?.let { closeQuietly(it) }
                if (e.isHostKeyMismatch()) return@withContext FileResult.Failure(hostKeyChanged(serverId))
                retryOnce(serverId, block)
            } catch (e: IOException) {
                FileResult.Failure(errorFor(e, serverId))
            }
        }

    private fun <T> retryOnce(
        serverId: String,
        block: (SFTPClient) -> T,
    ): FileResult<T> =
        try {
            FileResult.Success(block(session(serverId).sftp))
        } catch (e: IOException) {
            FileResult.Failure(errorFor(e, serverId))
        }

    /** Closes the server's connection, for example when its settings change. */
    fun disconnect(serverId: String) {
        sessions.remove(serverId)?.let { closeQuietly(it) }
    }

    // --- StorageBackend --------------------------------------------------------------------

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> {
        val p = parse(id)
        if (p.path == "/") {
            val server = serverFor(p.serverId) ?: return FileResult.Failure(FileError.StorageUnavailable("SFTP"))
            return FileResult.Success(
                NetworkNodeHelper.createNode(id, null, server.name, isDirectory = true, isVirtual = true),
            )
        }
        return withSftp(p.serverId) { sftp -> nodeOf(p, sftp.stat(p.path)) }
    }

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
        flow {
            val p = parse(id)
            if (serverFor(p.serverId) == null) {
                emit(FileResult.Failure(FileError.StorageUnavailable("SFTP")))
                return@flow
            }
            emit(
                withSftp(p.serverId) { sftp ->
                    sftp.ls(p.path)
                        .filter { it.name != "." && it.name != ".." && !it.name.endsWith(PART_SUFFIX) }
                        .map { nodeOf(Path(p.serverId, p.child(it.name)), it.attributes) }
                },
            )
        }.flowOn(Dispatchers.IO)

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
        val p = parse(id)
        return withSftp(p.serverId) { sftp ->
            sftp.stat(p.path) // Fail now, not on first read, when the file is gone.
            InputStreamProvider {
                val remote = session(p.serverId).sftp.open(p.path, EnumSet.of(OpenMode.READ))
                RemoteInput(remote, remote.ReadAheadRemoteFileInputStream(READ_AHEAD_REQUESTS))
            }
        }
    }

    override suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget> {
        val p = parse(parent)
        val finalPath = p.child(name)
        val partPath = p.child(".$name$PART_SUFFIX")
        return withSftp(p.serverId) { sftp ->
            val remote = sftp.open(partPath, EnumSet.of(OpenMode.WRITE, OpenMode.CREAT, OpenMode.TRUNC))
            PartFileTarget(p.serverId, remote, partPath, finalPath)
        }
    }

    override suspend fun createDirectory(
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNode> {
        val p = parse(parent)
        val path = p.child(name)
        return withSftp(p.serverId) { sftp ->
            if (sftp.statExistence(path) != null) throw AlreadyThere(name)
            sftp.mkdir(path)
            nodeOf(Path(p.serverId, path), sftp.stat(path))
        }
    }

    override suspend fun delete(id: FileNodeId): FileResult<Unit> {
        val p = parse(id)
        return withSftp(p.serverId) { sftp -> deleteRecursively(sftp, p.path) }
    }

    override suspend fun rename(
        id: FileNodeId,
        newName: String,
    ): FileResult<FileNode> {
        val p = parse(id)
        return moveTo(p, Path(p.serverId, p.parent).child(newName))
    }

    override suspend fun moveWithin(
        id: FileNodeId,
        newParent: FileNodeId,
    ): FileResult<FileNode> {
        val p = parse(id)
        return moveTo(p, parse(newParent).child(p.name))
    }

    private suspend fun moveTo(
        from: Path,
        toPath: String,
    ): FileResult<FileNode> =
        withSftp(from.serverId) { sftp ->
            if (sftp.statExistence(toPath) != null) throw AlreadyThere(toPath.substringAfterLast('/'))
            sftp.rename(from.path, toPath)
            nodeOf(Path(from.serverId, toPath), sftp.stat(toPath))
        }

    override suspend fun exists(
        parent: FileNodeId,
        name: String,
    ): Boolean {
        val p = parse(parent)
        return (withSftp(p.serverId) { sftp -> sftp.statExistence(p.child(name)) != null } as? FileResult.Success)
            ?.value ?: false
    }

    /** Unknown for SFTP without server extensions; callers treat 0 as "can't tell". */
    override suspend fun freeSpace(id: FileNodeId): Long = 0L

    // --- Helpers ---------------------------------------------------------------------------

    private fun nodeOf(
        p: Path,
        attrs: FileAttributes,
    ): FileNode {
        val isDir = attrs.type == FileMode.Type.DIRECTORY
        return NetworkNodeHelper.createNode(
            id = idOf(p.serverId, p.path),
            parentId = if (p.path.trimEnd('/').isEmpty()) null else idOf(p.serverId, p.parent),
            name = p.name,
            size = if (isDir) -1L else attrs.size,
            modifiedAt = attrs.mtime * MILLIS,
            isDirectory = isDir,
        )
    }

    private fun deleteRecursively(
        sftp: SFTPClient,
        path: String,
    ) {
        val attrs = sftp.stat(path)
        if (attrs.type == FileMode.Type.DIRECTORY) {
            sftp.ls(path).filter { it.name != "." && it.name != ".." }.forEach { deleteRecursively(sftp, it.path) }
            sftp.rmdir(path)
        } else {
            sftp.rm(path)
        }
    }

    private fun errorFor(
        e: IOException,
        serverId: String,
    ): FileError {
        val server = serverFor(serverId)?.name ?: "SFTP"
        return when {
            e is NoServer -> FileError.StorageUnavailable("SFTP")
            e is AlreadyThere -> FileError.FileAlreadyExists(e.name)
            e is UserAuthException -> FileError.AccessDenied("$server: the user name or password was refused")
            e is SFTPException && e.statusCode == Response.StatusCode.NO_SUCH_FILE ->
                FileError.FileNotFound(e.message)
            e is SFTPException && e.statusCode == Response.StatusCode.PERMISSION_DENIED ->
                FileError.AccessDenied(e.message)
            e is java.net.ConnectException || e is java.net.UnknownHostException ||
                e is java.net.SocketTimeoutException -> FileError.StorageUnavailable(server)
            else -> FileError.IoFailure(server)
        }
    }

    private fun hostKeyChanged(serverId: String): FileError =
        FileError.AccessDenied(
            "${serverFor(serverId)?.name ?: "SFTP"}: the server's identity (host key) changed. " +
                "It may not be the same machine.",
        )

    private fun TransportException.isHostKeyMismatch(): Boolean =
        disconnectReason == DisconnectReason.HOST_KEY_NOT_VERIFIABLE

    private fun closeQuietly(session: Session) {
        runCatching { session.sftp.close() }
        runCatching { session.ssh.disconnect() }
    }

    /** Writes to the hidden part file; [toNode] gives it the real name once it is complete. */
    private inner class PartFileTarget(
        private val serverId: String,
        private val remote: RemoteFile,
        private val partPath: String,
        private val finalPath: String,
    ) : OutputTarget {
        private val out: OutputStream = remote.RemoteFileOutputStream(0, WRITE_AHEAD_REQUESTS)
        private var lastModified = 0L

        override fun stream(): OutputStream =
            object : OutputStream() {
                override fun write(b: Int) = out.write(b)

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) = out.write(b, off, len)

                override fun flush() = out.flush()

                // The caller closes after writing; the remote handle stays open until toNode/discard.
                override fun close() = out.flush()
            }

        override fun setLastModified(epochMillis: Long) {
            lastModified = epochMillis
        }

        override fun discard() {
            runCatching { out.close() }
            runCatching { remote.close() }
            runCatching { session(serverId).sftp.rm(partPath) }
        }

        override fun sync() {
            out.flush()
        }

        override suspend fun toNode(): FileResult<FileNode> =
            withSftp(serverId) { sftp ->
                out.close()
                remote.close()
                if (sftp.statExistence(finalPath) != null) sftp.rm(finalPath)
                sftp.rename(partPath, finalPath)
                if (lastModified > 0) {
                    val seconds = lastModified / MILLIS
                    sftp.setattr(finalPath, FileAttributes.Builder().withAtimeMtime(seconds, seconds).build())
                }
                nodeOf(Path(serverId, finalPath), sftp.stat(finalPath))
            }
    }

    /** Closes the remote handle together with the stream. */
    private class RemoteInput(
        private val remote: RemoteFile,
        input: InputStream,
    ) : FilterInputStream(input) {
        override fun close() {
            try {
                super.close()
            } finally {
                remote.close()
            }
        }
    }

    private class TrustOnFirstUse(
        private val knownHosts: KnownHosts,
        private val serverKey: String,
    ) : HostKeyVerifier {
        override fun verify(
            hostname: String,
            port: Int,
            key: PublicKey,
        ): Boolean {
            val fingerprint = SecurityUtils.getFingerprint(key)
            val known = knownHosts.fingerprint(serverKey)
            if (known == null) {
                knownHosts.remember(serverKey, fingerprint)
                return true
            }
            return known == fingerprint
        }

        override fun findExistingAlgorithms(
            hostname: String,
            port: Int,
        ): List<String> = emptyList()
    }

    private class NoServer(serverId: String) : IOException("No saved server $serverId")

    private class AlreadyThere(val name: String) : IOException("$name already exists")

    private companion object {
        const val PART_SUFFIX = ".atomic-part"
        const val CONNECT_TIMEOUT_MS = 10_000
        const val READ_TIMEOUT_MS = 30_000
        const val READ_AHEAD_REQUESTS = 16
        const val WRITE_AHEAD_REQUESTS = 16
        const val MILLIS = 1000L

        /**
         * Android ships a cut-down "BC" provider without the algorithms SSH needs; sshj expects
         * the full BouncyCastle one, so it replaces the platform's once per process.
         */
        @Synchronized
        fun ensureBouncyCastle() {
            val current = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (current?.javaClass == BouncyCastleProvider::class.java) return
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }
}
