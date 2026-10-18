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
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msdtyp.FileTime
import com.hierynomus.mserref.NtStatus
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileBasicInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.mssmb2.SMBApiException
import com.hierynomus.protocol.commons.EnumWithValue
import com.hierynomus.protocol.transport.TransportException
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.SmbConfig
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.hierynomus.smbj.share.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.IOException
import java.io.OutputStream
import java.util.EnumSet
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * SMB 2/3 for Windows shares and Samba, with smbj (ALL_IN_ONE_PLAN.md 3.1). smbj never speaks
 * SMB1, so the old protocol behind WannaCry-era attacks can't be negotiated.
 *
 * Paths are `/<share>/<folder>/<file>`: the first part is the share. A server saved without a
 * share in its path says so instead of guessing, because listing shares needs RPC that SMB
 * itself doesn't offer.
 *
 * As with SFTP, one connection per server is reused, and uploads go to a hidden
 * `.<name>.atomic-part` file that takes the real name only when complete.
 */
class SmbBackend(
    private val serverFor: (String) -> NetworkServerConfig?,
    private val passwordFor: (String) -> String?,
) : StorageBackend {
    constructor(store: NetworkCredentialsStore) : this(store::getServerById, store::getPassword)

    override val type: BackendType = BackendType.SMB

    override fun canHandle(id: FileNodeId): Boolean = id.prefix == FileNodeId.Prefix.SMB

    private val client by lazy {
        SMBClient(
            SmbConfig.builder()
                .withTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .withSoTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
                .build(),
        )
    }

    private class Link(val connection: Connection, val session: Session) {
        val shares = ConcurrentHashMap<String, DiskShare>()
        val alive: Boolean get() = connection.isConnected
    }

    private val links = ConcurrentHashMap<String, Link>()

    // --- Connection pool -------------------------------------------------------------------

    private fun link(serverId: String): Link {
        links[serverId]?.takeIf { it.alive }?.let { return it }
        synchronized(this) {
            links[serverId]?.takeIf { it.alive }?.let { return it }
            links.remove(serverId)?.let { closeQuietly(it) }
            val server = serverFor(serverId) ?: throw NoServer(serverId)
            val connection = client.connect(server.host, server.port)
            try {
                val session = connection.authenticate(SmbPaths.auth(server, passwordFor(serverId)))
                return Link(connection, session).also { links[serverId] = it }
            } catch (e: IOException) {
                runCatching { connection.close() }
                throw e
            } catch (e: SMBApiException) {
                runCatching { connection.close() }
                throw e
            }
        }
    }

    private fun share(
        serverId: String,
        name: String,
    ): DiskShare {
        val link = link(serverId)
        link.shares[name]?.takeIf { it.isConnected }?.let { return it }
        val share = link.session.connectShare(name) as? DiskShare ?: throw NotADiskShare(name)
        link.shares[name] = share
        return share
    }

    /** Runs [block] on the share holding [p], reconnecting once if the pooled connection broke. */
    private suspend fun <T> withShare(
        p: SmbPaths.Path,
        block: (DiskShare) -> T,
    ): FileResult<T> =
        withContext(Dispatchers.IO) {
            val shareName = p.share ?: return@withContext FileResult.Failure(noShare(p.serverId))
            try {
                FileResult.Success(block(share(p.serverId, shareName)))
            } catch (e: TransportException) {
                links.remove(p.serverId)?.let { closeQuietly(it) }
                retryOnce(p, shareName, block)
            } catch (e: IOException) {
                FileResult.Failure(errorFor(e, p.serverId))
            } catch (e: SMBApiException) {
                FileResult.Failure(errorFor(e, p.serverId))
            }
        }

    private fun <T> retryOnce(
        p: SmbPaths.Path,
        shareName: String,
        block: (DiskShare) -> T,
    ): FileResult<T> =
        try {
            FileResult.Success(block(share(p.serverId, shareName)))
        } catch (e: IOException) {
            FileResult.Failure(errorFor(e, p.serverId))
        } catch (e: SMBApiException) {
            FileResult.Failure(errorFor(e, p.serverId))
        }

    /** Closes the server's connection, for example when its settings change. */
    fun disconnect(serverId: String) {
        links.remove(serverId)?.let { closeQuietly(it) }
    }

    // --- StorageBackend --------------------------------------------------------------------

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> {
        val p = SmbPaths.parse(id)
        if (p.inner.isEmpty()) {
            val server = serverFor(p.serverId) ?: return FileResult.Failure(FileError.StorageUnavailable("SMB"))
            return FileResult.Success(
                NetworkNodeHelper.createNode(id, null, p.share ?: server.name, isDirectory = true, isVirtual = true),
            )
        }
        return withShare(p) { share -> stat(share, p) }
    }

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
        flow {
            val p = SmbPaths.parse(id)
            if (serverFor(p.serverId) == null) {
                emit(FileResult.Failure(FileError.StorageUnavailable("SMB")))
                return@flow
            }
            emit(
                withShare(p) { share ->
                    share.list(p.inner)
                        .filter { it.fileName != "." && it.fileName != ".." && !it.fileName.endsWith(PART_SUFFIX) }
                        .map {
                            val isDir = it.fileAttributes.isDirectory()
                            node(
                                p.child(it.fileName),
                                isDir,
                                if (isDir) -1L else it.endOfFile,
                                it.lastWriteTime.toEpochMillis(),
                            )
                        }
                },
            )
        }.flowOn(Dispatchers.IO)

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> {
        val p = SmbPaths.parse(id)
        return withShare(p) { share ->
            if (!share.fileExists(p.inner)) throw Missing(p.name)
            InputStreamProvider {
                val file = share(p.serverId, p.share!!).openFile(p.inner, READ, null, SMB2ShareAccess.ALL, OPEN, null)
                ClosingInput(file)
            }
        }
    }

    override suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget> {
        val p = SmbPaths.parse(parent)
        val final = p.child(name)
        val part = p.child(".$name$PART_SUFFIX")
        return withShare(p) { share ->
            val file =
                share.openFile(
                    part.inner,
                    WRITE,
                    null,
                    SMB2ShareAccess.ALL,
                    SMB2CreateDisposition.FILE_OVERWRITE_IF,
                    null,
                )
            PartFileTarget(file, part, final)
        }
    }

    override suspend fun createDirectory(
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNode> {
        val child = SmbPaths.parse(parent).child(name)
        return withShare(child) { share ->
            if (share.folderExists(child.inner) || share.fileExists(child.inner)) throw AlreadyThere(name)
            share.mkdir(child.inner)
            stat(share, child)
        }
    }

    override suspend fun delete(id: FileNodeId): FileResult<Unit> {
        val p = SmbPaths.parse(id)
        if (p.inner.isEmpty()) return FileResult.Failure(FileError.AccessDenied("The share itself can't be deleted"))
        return withShare(p) { share ->
            if (share.folderExists(p.inner)) share.rmdir(p.inner, true) else share.rm(p.inner)
        }
    }

    override suspend fun rename(
        id: FileNodeId,
        newName: String,
    ): FileResult<FileNode> {
        val p = SmbPaths.parse(id)
        return moveTo(p, p.parent().child(newName))
    }

    override suspend fun moveWithin(
        id: FileNodeId,
        newParent: FileNodeId,
    ): FileResult<FileNode> {
        val p = SmbPaths.parse(id)
        val target = SmbPaths.parse(newParent).child(p.name)
        if (target.share != p.share) return FileResult.Failure(FileError.InvalidDestination(p.name))
        return moveTo(p, target)
    }

    private suspend fun moveTo(
        from: SmbPaths.Path,
        to: SmbPaths.Path,
    ): FileResult<FileNode> =
        withShare(from) { share ->
            if (share.fileExists(to.inner) || share.folderExists(to.inner)) throw AlreadyThere(to.name)
            share.open(from.inner, MOVE, null, SMB2ShareAccess.ALL, OPEN, null).use { it.rename(to.inner, false) }
            stat(share, to)
        }

    override suspend fun exists(
        parent: FileNodeId,
        name: String,
    ): Boolean {
        val child = SmbPaths.parse(parent).child(name)
        return (withShare(child) { it.fileExists(child.inner) || it.folderExists(child.inner) } as? FileResult.Success)
            ?.value ?: false
    }

    override suspend fun freeSpace(id: FileNodeId): Long {
        val p = SmbPaths.parse(id)
        return (withShare(p) { it.shareInformation.freeSpace } as? FileResult.Success)?.value ?: 0L
    }

    // --- Helpers ---------------------------------------------------------------------------

    private fun stat(
        share: DiskShare,
        p: SmbPaths.Path,
    ): FileNode {
        val info = share.getFileInformation(p.inner)
        val isDir = info.standardInformation.isDirectory
        return node(
            p,
            isDir,
            if (isDir) -1L else info.standardInformation.endOfFile,
            info.basicInformation.lastWriteTime.toEpochMillis(),
        )
    }

    private fun node(
        p: SmbPaths.Path,
        isDirectory: Boolean,
        size: Long,
        modifiedAt: Long,
    ): FileNode =
        NetworkNodeHelper.createNode(
            id = p.id(),
            parentId = p.parentId,
            name = p.name,
            size = size,
            modifiedAt = modifiedAt.coerceAtLeast(0),
            isDirectory = isDirectory,
        )

    private fun Long.isDirectory(): Boolean =
        EnumWithValue.EnumUtils.isSet(
            this,
            FileAttributes.FILE_ATTRIBUTE_DIRECTORY,
        )

    private fun noShare(serverId: String): FileError =
        FileError.AccessDenied(
            "${serverFor(serverId)?.name ?: "SMB"}: add the share to this server's path, for example /Photos",
        )

    private fun errorFor(
        e: Exception,
        serverId: String,
    ): FileError {
        val server = serverFor(serverId)?.name ?: "SMB"
        return when (e) {
            is NoServer -> FileError.StorageUnavailable("SMB")
            is AlreadyThere -> FileError.FileAlreadyExists(e.name)
            is Missing -> FileError.FileNotFound(e.name)
            is NotADiskShare -> FileError.AccessDenied("$server: ${e.name} is not a file share")
            is SMBApiException -> SmbPaths.errorFor(e.status, server, e.message)
            is java.net.ConnectException, is java.net.UnknownHostException, is java.net.SocketTimeoutException ->
                FileError.StorageUnavailable(server)
            else -> FileError.IoFailure(server)
        }
    }

    private fun closeQuietly(link: Link) {
        runCatching { link.session.close() }
        runCatching { link.connection.close() }
    }

    /** Writes to the hidden part file; [toNode] gives it the real name once it is complete. */
    private inner class PartFileTarget(
        private val file: File,
        private val part: SmbPaths.Path,
        private val final: SmbPaths.Path,
    ) : OutputTarget {
        private val out: OutputStream = file.outputStream
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

                // The caller closes after writing; the handle stays open until toNode/discard.
                override fun close() = out.flush()
            }

        override fun setLastModified(epochMillis: Long) {
            lastModified = epochMillis
        }

        override fun discard() {
            runCatching { out.close() }
            runCatching { file.close() }
            runCatching { share(part.serverId, part.share!!).rm(part.inner) }
        }

        override fun sync() {
            out.flush()
        }

        override suspend fun toNode(): FileResult<FileNode> =
            withShare(part) { share ->
                out.close()
                if (lastModified > 0) {
                    val time = FileTime.ofEpochMillis(lastModified)
                    file.setFileInformation(
                        FileBasicInformation(
                            FileBasicInformation.DONT_SET,
                            FileBasicInformation.DONT_SET,
                            time,
                            FileBasicInformation.DONT_SET,
                            0L,
                        ),
                    )
                }
                file.close()
                share.open(part.inner, MOVE, null, SMB2ShareAccess.ALL, OPEN, null).use {
                    it.rename(final.inner, true)
                }
                stat(share, final)
            }
    }

    /** Closes the remote handle together with the stream. */
    private class ClosingInput(
        private val file: File,
    ) : FilterInputStream(file.inputStream) {
        override fun close() {
            try {
                super.close()
            } finally {
                file.close()
            }
        }
    }

    private class NoServer(serverId: String) : IOException("No saved server $serverId")

    private class AlreadyThere(val name: String) : IOException("$name already exists")

    private class Missing(val name: String) : IOException("$name not found")

    private class NotADiskShare(val name: String) : IOException("$name is not a disk share")

    private companion object {
        const val PART_SUFFIX = ".atomic-part"
        const val READ_TIMEOUT_S = 30L
        val READ: Set<AccessMask> = EnumSet.of(AccessMask.GENERIC_READ)
        val WRITE: Set<AccessMask> = EnumSet.of(AccessMask.GENERIC_WRITE)
        val MOVE: Set<AccessMask> = EnumSet.of(AccessMask.DELETE, AccessMask.GENERIC_READ)
        val OPEN = SMB2CreateDisposition.FILE_OPEN
    }
}

/** Path, sign-in and error rules for [SmbBackend]; free of the network so they are unit-tested. */
object SmbPaths {
    /** `/<share>/<inner…>`; [inner] uses backslashes as SMB expects and is empty at the share root. */
    data class Path(val serverId: String, val share: String?, val inner: String) {
        val name: String get() = inner.substringAfterLast('\\').ifEmpty { share.orEmpty() }

        /** A share is the navigable storage root; it must not point to itself as its parent. */
        val parentId: FileNodeId? get() = if (inner.isEmpty() || share == null) null else parent().id()

        fun child(name: String): Path = copy(inner = if (inner.isEmpty()) name else "$inner\\$name")

        fun parent(): Path = copy(inner = inner.substringBeforeLast('\\', ""))

        fun id(): FileNodeId {
            val tail = if (inner.isEmpty()) "" else "/" + inner.replace('\\', '/')
            return FileNodeId.smb(serverId, share?.let { "/$it$tail" } ?: "/")
        }
    }

    fun parse(id: FileNodeId): Path {
        val body = id.raw.removePrefix(FileNodeId.Prefix.SMB.scheme)
        val colon = body.indexOf(':')
        val serverId = if (colon >= 0) body.substring(0, colon) else body
        val parts =
            (if (colon >= 0) body.substring(colon + 1) else "")
                .replace('\\', '/')
                .split('/')
                .filter { it.isNotEmpty() && it != "." }
        return Path(serverId, parts.firstOrNull(), parts.drop(1).joinToString("\\"))
    }

    /** "DOMAIN\user" and "user@domain" carry the domain; anonymous servers sign in as guest. */
    fun auth(
        server: NetworkServerConfig,
        password: String?,
    ): AuthenticationContext {
        if (server.anonymous || server.username.isBlank()) return AuthenticationContext.guest()
        val (user, domain) = splitUser(server.username)
        return AuthenticationContext(user, password.orEmpty().toCharArray(), domain)
    }

    fun splitUser(username: String): Pair<String, String?> =
        when {
            '\\' in username -> username.substringAfter('\\') to username.substringBefore('\\')
            '@' in username -> username.substringBefore('@') to username.substringAfter('@')
            else -> username to null
        }

    fun errorFor(
        status: NtStatus,
        server: String,
        message: String?,
    ): FileError =
        when (status) {
            NtStatus.STATUS_OBJECT_NAME_NOT_FOUND,
            NtStatus.STATUS_OBJECT_PATH_NOT_FOUND,
            NtStatus.STATUS_NO_SUCH_FILE,
            ->
                FileError.FileNotFound(message ?: server)
            NtStatus.STATUS_LOGON_FAILURE, NtStatus.STATUS_ACCOUNT_DISABLED, NtStatus.STATUS_PASSWORD_EXPIRED ->
                FileError.AccessDenied("$server: the user name or password was refused")
            NtStatus.STATUS_ACCESS_DENIED -> FileError.AccessDenied(message ?: server)
            NtStatus.STATUS_BAD_NETWORK_NAME -> FileError.AccessDenied("$server: that share doesn't exist")
            NtStatus.STATUS_OBJECT_NAME_COLLISION -> FileError.FileAlreadyExists(message ?: server)
            NtStatus.STATUS_DISK_FULL -> FileError.IoFailure("$server is full")
            else -> FileError.IoFailure(server)
        }
}
