package com.devbehindyou.atomicfilemanager.data.backend.network

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import com.devbehindyou.atomicfilemanager.domain.repository.BackendType
import com.devbehindyou.atomicfilemanager.domain.repository.InputStreamProvider
import com.devbehindyou.atomicfilemanager.domain.repository.OutputTarget
import com.devbehindyou.atomicfilemanager.domain.repository.StorageBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.util.regex.Pattern

/**
 * Robust FTP and FTPS storage backend conforming to [StorageBackend].
 * Supports RFC 959 (FTP) and RFC 4217 (FTPS) over passive TCP connections.
 */
class FtpBackend(
    private val credentialsStore: NetworkCredentialsStore,
    override val type: BackendType = BackendType.FTP,
) : StorageBackend {
    override fun canHandle(id: FileNodeId): Boolean {
        return id.prefix == FileNodeId.Prefix.FTP || id.prefix == FileNodeId.Prefix.FTPS
    }

    private data class ParsedFtpId(val isFtps: Boolean, val serverId: String, val path: String)

    private fun parseId(id: FileNodeId): ParsedFtpId {
        val isFtps = id.prefix == FileNodeId.Prefix.FTPS
        val prefix = if (isFtps) FileNodeId.Prefix.FTPS.scheme else FileNodeId.Prefix.FTP.scheme
        val body = id.raw.removePrefix(prefix)
        val firstColon = body.indexOf(':')
        val serverId = if (firstColon >= 0) body.substring(0, firstColon) else body
        val rawPath = if (firstColon >= 0) body.substring(firstColon + 1) else "/"
        val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        return ParsedFtpId(isFtps, serverId, path)
    }

    private fun makeNodeId(
        parsed: ParsedFtpId,
        newPath: String,
    ): FileNodeId {
        val norm = if (newPath.startsWith("/")) newPath else "/$newPath"
        return if (parsed.isFtps) {
            FileNodeId.ftps(parsed.serverId, norm)
        } else {
            FileNodeId.ftp(parsed.serverId, norm)
        }
    }

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            if (parsed.path == "/" || parsed.path.isEmpty()) {
                val server = credentialsStore.getServerById(parsed.serverId)
                val name = server?.name ?: "FTP Server"
                return@withContext FileResult.Success(
                    NetworkNodeHelper.createNode(
                        id = id,
                        parentId = null,
                        name = name,
                        isDirectory = true,
                        isVirtual = true,
                    ),
                )
            }

            val parentPath = getParentPath(parsed.path)
            val name = getFileName(parsed.path)
            try {
                val match = listRemote(parsed, parentPath).firstOrNull { it.name == name }
                if (match == null) FileResult.Failure(FileError.FileNotFound(name)) else FileResult.Success(match)
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
        flow {
            val parsed = parseId(id)
            val server = credentialsStore.getServerById(parsed.serverId)
            if (server == null) {
                emit(FileResult.Failure(FileError.StorageUnavailable("FTP")))
                return@flow
            }

            try {
                val entries = listRemote(parsed, parsed.path)
                emit(FileResult.Success(entries))
            } catch (e: Exception) {
                val error = mapException(e)
                emit(FileResult.Failure(error))
            }
        }.flowOn(Dispatchers.IO)

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            var activeClient: FtpProtocolClient? = null
            var activeData: Socket? = null
            try {
                val client = connectAndLogin(parsed)
                activeClient = client
                val dataSocket = client.openDataTransfer("RETR ${parsed.path}", binary = true)
                activeData = dataSocket
                val rawIn = dataSocket.getInputStream()
                var closed = false
                FileResult.Success(
                    InputStreamProvider {
                        object : InputStream() {
                            override fun read(): Int = rawIn.read()

                            override fun read(
                                b: ByteArray,
                                off: Int,
                                len: Int,
                            ): Int = rawIn.read(b, off, len)

                            override fun close() {
                                if (closed) return
                                closed = true
                                try {
                                    dataSocket.close()
                                    client.completeTransfer()
                                } finally {
                                    client.close()
                                }
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                runCatching { activeData?.close() }
                activeClient?.close()
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun openOutput(
        parent: FileNodeId,
        name: String,
        mime: String?,
    ): FileResult<OutputTarget> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(parent)
            val targetPath = if (parsed.path.endsWith("/")) "${parsed.path}$name" else "${parsed.path}/$name"
            val targetNodeId = makeNodeId(parsed, targetPath)
            var activeClient: FtpProtocolClient? = null
            var activeData: Socket? = null
            try {
                val client = connectAndLogin(parsed)
                activeClient = client
                val dataSocket = client.openDataTransfer("STOR $targetPath", binary = true)
                activeData = dataSocket
                val dataOut = FtpUploadStream(client, dataSocket)
                var discarded = false

                val target =
                    object : OutputTarget {
                        override fun stream(): OutputStream = dataOut

                        override fun setLastModified(epochMillis: Long) {
                            // FTP does not support direct timestamp setting in standard RFC 959 without MFMT extension
                        }

                        override fun discard() {
                            discarded = true
                            dataOut.abort()
                            try {
                                connectAndLogin(parsed).use { it.sendCommand("DELE $targetPath") }
                            } catch (_: Exception) {
                            }
                        }

                        override fun sync() {
                            dataOut.flush()
                        }

                        override suspend fun toNode(): FileResult<FileNode> {
                            if (discarded) {
                                return FileResult.Failure(FileError.OperationCancelled)
                            }
                            try {
                                dataOut.close()
                            } catch (e: Exception) {
                                return FileResult.Failure(mapException(e))
                            }

                            return FileResult.Success(
                                NetworkNodeHelper.createNode(
                                    id = targetNodeId,
                                    parentId = parent,
                                    name = name,
                                    size = 0L,
                                    modifiedAt = System.currentTimeMillis(),
                                    isDirectory = false,
                                    mimeType = mime,
                                ),
                            )
                        }
                    }

                FileResult.Success(target)
            } catch (e: Exception) {
                runCatching { activeData?.close() }
                activeClient?.close()
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun createDirectory(
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNode> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(parent)
            val newPath = if (parsed.path.endsWith("/")) "${parsed.path}$name" else "${parsed.path}/$name"
            val newNodeId = makeNodeId(parsed, newPath)

            try {
                val resp = connectAndLogin(parsed).use { it.sendCommand("MKD $newPath") }
                if (resp.startsWith("257")) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = parent,
                            name = name,
                            isDirectory = true,
                        ),
                    )
                } else if (resp.startsWith("550")) {
                    FileResult.Failure(FileError.FileAlreadyExists(name))
                } else {
                    FileResult.Failure(FileError.AccessDenied("MKD rejected"))
                }
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun delete(id: FileNodeId): FileResult<Unit> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            try {
                val resp = connectAndLogin(parsed).use { client ->
                    val deleted = client.sendCommand("DELE ${parsed.path}")
                    if (deleted.startsWith("250")) deleted else client.sendCommand("RMD ${parsed.path}")
                }
                if (resp.startsWith("250")) {
                    FileResult.Success(Unit)
                } else {
                    FileResult.Failure(FileError.AccessDenied("Delete failed"))
                }
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun rename(
        id: FileNodeId,
        newName: String,
    ): FileResult<FileNode> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            val parentPath = getParentPath(parsed.path)
            val newPath = if (parentPath.endsWith("/")) "$parentPath$newName" else "$parentPath/$newName"
            val newNodeId = makeNodeId(parsed, newPath)

            try {
                val resp = connectAndLogin(parsed).use { client ->
                    val from = client.sendCommand("RNFR ${parsed.path}")
                    if (from.startsWith("350")) client.sendCommand("RNTO $newPath") else from
                }
                if (resp.startsWith("250")) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = makeNodeId(parsed, parentPath),
                            name = newName,
                            isDirectory = false,
                        ),
                    )
                } else {
                    FileResult.Failure(FileError.AccessDenied("Rename failed"))
                }
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun moveWithin(
        id: FileNodeId,
        newParent: FileNodeId,
    ): FileResult<FileNode> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            val parsedParent = parseId(newParent)
            val fileName = getFileName(parsed.path)
            val newPath =
                if (parsedParent.path.endsWith(
                        "/",
                    )
                ) {
                    "${parsedParent.path}$fileName"
                } else {
                    "${parsedParent.path}/$fileName"
                }
            val newNodeId = makeNodeId(parsed, newPath)

            try {
                val resp = connectAndLogin(parsed).use { client ->
                    val from = client.sendCommand("RNFR ${parsed.path}")
                    if (from.startsWith("350")) client.sendCommand("RNTO $newPath") else from
                }
                if (resp.startsWith("250")) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = newParent,
                            name = fileName,
                            isDirectory = false,
                        ),
                    )
                } else {
                    FileResult.Failure(FileError.AccessDenied("Move failed"))
                }
            } catch (e: Exception) {
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun exists(
        parent: FileNodeId,
        name: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val parsed = parseId(parent)
            // Unknown presence must not be reported as absence before a write.
            listRemote(parsed, parsed.path).any { it.name == name }
        }

    override suspend fun freeSpace(id: FileNodeId): Long = 0L

    private fun connectAndLogin(parsed: ParsedFtpId): FtpProtocolClient {
        val server =
            credentialsStore.getServerById(parsed.serverId)
                ?: error("Server configuration not found for id: ${parsed.serverId}")

        val client =
            FtpProtocolClient(
                host = server.host,
                port = server.port,
                isFtps = parsed.isFtps || server.protocol == NetworkProtocol.FTPS,
            )
        try {
            client.connect()
            val pass = credentialsStore.getPassword(server.id) ?: ""
            val username = if (server.anonymous || server.username.isBlank()) "anonymous" else server.username
            val userPass = if (server.anonymous) "atomic@filemanager.local" else pass
            client.login(username, userPass)
            return client
        } catch (e: Exception) {
            client.close()
            throw e
        }
    }

    private fun listRemote(
        parsed: ParsedFtpId,
        remotePath: String,
    ): List<FileNode> {
        return connectAndLogin(parsed).use { client ->
            val lines = client.list(remotePath)
            val parentNodeId = makeNodeId(parsed, remotePath)
            lines.mapNotNull { parseFtpListLine(it, parentNodeId, parsed.copy(path = remotePath)) }
        }
    }

    private fun parseFtpListLine(
        line: String,
        parentId: FileNodeId,
        parsed: ParsedFtpId,
    ): FileNode? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null

        // Unix-style format: drwxr-xr-x 1 owner group 4096 Jan 01 12:00 filename
        if (trimmed[0] in listOf('-', 'd', 'l')) {
            val parts = trimmed.split(Pattern.compile("\\s+"), 9)
            if (parts.size >= 9) {
                val isDir = parts[0].startsWith("d")
                val isLink = parts[0].startsWith("l")
                val size = parts[4].toLongOrNull() ?: 0L
                var name = parts[8]
                if (isLink && name.contains(" -> ")) {
                    name = name.substringBefore(" -> ")
                }
                if (name == "." || name == "..") return null

                val itemPath = if (parsed.path.endsWith("/")) "${parsed.path}$name" else "${parsed.path}/$name"
                return NetworkNodeHelper.createNode(
                    id = makeNodeId(parsed, itemPath),
                    parentId = parentId,
                    name = name,
                    size = size,
                    isDirectory = isDir,
                )
            }
        }

        // MS-DOS style format: 05-12-20 03:45PM <DIR> foldername
        val dosParts = trimmed.split(Pattern.compile("\\s+"), 4)
        if (dosParts.size >= 4) {
            val isDir = dosParts[2].equals("<DIR>", ignoreCase = true)
            val size = if (isDir) 0L else dosParts[2].toLongOrNull() ?: 0L
            val name = dosParts[3]
            if (name == "." || name == "..") return null

            val itemPath = if (parsed.path.endsWith("/")) "${parsed.path}$name" else "${parsed.path}/$name"
            return NetworkNodeHelper.createNode(
                id = makeNodeId(parsed, itemPath),
                parentId = parentId,
                name = name,
                size = size,
                isDirectory = isDir,
            )
        }

        return null
    }

    private fun getParentPath(path: String): String {
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        return if (lastSlash > 0) trimmed.substring(0, lastSlash) else "/"
    }

    private fun getFileName(path: String): String {
        val trimmed = path.trimEnd('/')
        val lastSlash = trimmed.lastIndexOf('/')
        return if (lastSlash >= 0) trimmed.substring(lastSlash + 1) else trimmed
    }

    private fun mapException(e: Throwable): FileError {
        if (e is CancellationException) throw e
        val msg = e.message.orEmpty()
        return when {
            msg.contains("login", ignoreCase = true) || msg.contains("530", ignoreCase = true) ->
                FileError.AccessDenied("Authentication failed")
            msg.contains("timeout", ignoreCase = true) || msg.contains("refused", ignoreCase = true) ||
                msg.contains("unreachable", ignoreCase = true) ->
                FileError.StorageUnavailable("FTP Server Unreachable")
            msg.contains("550", ignoreCase = true) ->
                FileError.FileNotFound(msg)
            else -> FileError.StorageUnavailable("FTP Error: $msg")
        }
    }
}
