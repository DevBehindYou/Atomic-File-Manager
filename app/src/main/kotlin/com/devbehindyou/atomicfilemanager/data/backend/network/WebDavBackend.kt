package com.devbehindyou.atomicfilemanager.data.backend.network

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
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
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * High-performance WebDAV storage backend conforming to [StorageBackend].
 * Communicates with Nextcloud, ownCloud, Apache WebDAV, and standard HTTP/HTTPS DAV shares.
 */
class WebDavBackend(
    private val credentialsStore: NetworkCredentialsStore,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : StorageBackend {
    override val type: BackendType = BackendType.WEBDAV

    override fun canHandle(id: FileNodeId): Boolean = id.prefix == FileNodeId.Prefix.WEBDAV

    private data class ParsedWebDavId(val serverId: String, val path: String)

    private fun parseId(id: FileNodeId): ParsedWebDavId {
        val body = id.raw.removePrefix(FileNodeId.Prefix.WEBDAV.scheme)
        val firstColon = body.indexOf(':')
        val serverId = if (firstColon >= 0) body.substring(0, firstColon) else body
        val rawPath = if (firstColon >= 0) body.substring(firstColon + 1) else "/"
        val path = if (rawPath.startsWith("/")) rawPath else "/$rawPath"
        return ParsedWebDavId(serverId, path)
    }

    private fun makeNodeId(
        serverId: String,
        path: String,
    ): FileNodeId {
        val norm = if (path.startsWith("/")) path else "/$path"
        return FileNodeId.webdav(serverId, norm)
    }

    private fun buildUrl(
        serverId: String,
        path: String,
    ): URL {
        val server =
            credentialsStore.getServerById(serverId)
                ?: error("Server configuration not found for id: $serverId")
        val protocol = if (server.port == 443 || server.host.startsWith("https://")) "https" else "http"
        val cleanHost = server.host.removePrefix("http://").removePrefix("https://").trimEnd('/')
        val portPart =
            if ((protocol == "http" && server.port == 80) || (protocol == "https" && server.port == 443)) {
                ""
            } else {
                ":${server.port}"
            }
        val cleanPath = if (path.startsWith("/")) path else "/$path"
        return URL("$protocol://$cleanHost$portPart${WebDavPaths.encode(cleanPath)}")
    }

    private fun openConnection(
        url: URL,
        serverId: String,
        method: String,
    ): HttpURLConnection {
        val conn = connectionFactory(url)
        conn.requestMethod = method
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 10_000
        conn.readTimeout = 20_000

        val server = credentialsStore.getServerById(serverId)
        if (server != null && !server.anonymous && server.username.isNotBlank()) {
            val pass = credentialsStore.getPassword(serverId) ?: ""
            val auth = "${server.username}:$pass"
            val encodedAuth = android.util.Base64.encodeToString(auth.toByteArray(), android.util.Base64.NO_WRAP)
            conn.setRequestProperty("Authorization", "Basic $encodedAuth")
        }
        return conn
    }

    override suspend fun getNode(id: FileNodeId): FileResult<FileNode> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            if (parsed.path == "/" || parsed.path.isEmpty()) {
                val server = credentialsStore.getServerById(parsed.serverId)
                val name = server?.name ?: "WebDAV Server"
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

            when (val result = queryProperties(parsed, "0")) {
                is FileResult.Failure -> result
                is FileResult.Success -> {
                    val node = result.value.firstOrNull { WebDavPaths.sameResource(parseId(it.id).path, parsed.path) }
                    node?.let { FileResult.Success(it) } ?: FileResult.Failure(FileError.FileNotFound(parsed.path))
                }
            }
        }

    override fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>> =
        flow {
            val parsed = parseId(id)
            val result = queryProperties(parsed, "1")
            emit(
                when (result) {
                    is FileResult.Failure -> result
                    is FileResult.Success ->
                        FileResult.Success(
                            result.value.filterNot { WebDavPaths.sameResource(parseId(it.id).path, parsed.path) },
                        )
                },
            )
        }.flowOn(Dispatchers.IO)

    private fun queryProperties(
        parsed: ParsedWebDavId,
        depth: String,
    ): FileResult<List<FileNode>> {
        var connection: HttpURLConnection? = null
        return try {
            val conn = openConnection(buildUrl(parsed.serverId, parsed.path), parsed.serverId, "PROPFIND")
            connection = conn
            conn.setRequestProperty("Depth", depth)
            when (val code = conn.responseCode) {
                404 -> FileResult.Failure(FileError.FileNotFound(parsed.path))
                401, 403 -> FileResult.Failure(FileError.AccessDenied("WebDAV unauthorized"))
                in 200..299 -> FileResult.Success(conn.inputStream.use { parseMultiStatusXml(it, parsed.serverId) })
                else -> FileResult.Failure(FileError.StorageUnavailable("WebDAV response: $code"))
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            FileResult.Failure(mapException(error))
        } finally {
            connection?.disconnect()
        }
    }

    override suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            try {
                val url = buildUrl(parsed.serverId, parsed.path)
                val conn = openConnection(url, parsed.serverId, "GET")
                conn.connect()

                val responseCode = conn.responseCode
                if (responseCode == 404) {
                    return@withContext FileResult.Failure(FileError.FileNotFound(parsed.path))
                } else if (responseCode == 401 || responseCode == 403) {
                    return@withContext FileResult.Failure(FileError.AccessDenied("WebDAV unauthorized"))
                } else if (responseCode !in 200..299) {
                    return@withContext FileResult.Failure(
                        FileError.StorageUnavailable("WebDAV response: $responseCode"),
                    )
                }

                val rawStream = conn.inputStream
                FileResult.Success(
                    InputStreamProvider {
                        object : InputStream() {
                            override fun read(): Int = rawStream.read()

                            override fun read(
                                b: ByteArray,
                                off: Int,
                                len: Int,
                            ): Int = rawStream.read(b, off, len)

                            override fun close() {
                                try {
                                    rawStream.close()
                                    conn.disconnect()
                                } catch (_: Exception) {
                                }
                            }
                        }
                    },
                )
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
            val targetNodeId = makeNodeId(parsed.serverId, targetPath)
            var pendingConnection: HttpURLConnection? = null
            try {
                val url = buildUrl(parsed.serverId, targetPath)
                val conn = openConnection(url, parsed.serverId, "PUT")
                pendingConnection = conn
                // Production callers stage new content; explicit replacement first moves the old file aside.
                conn.setRequestProperty("If-None-Match", "*")
                conn.doOutput = true
                conn.setChunkedStreamingMode(64 * 1024)
                if (mime != null) conn.setRequestProperty("Content-Type", mime)
                conn.connect()

                val target =
                    WebDavOutputTarget(
                        connection = conn,
                        name = name,
                        node = { size ->
                            NetworkNodeHelper.createNode(
                                id = targetNodeId,
                                parentId = parent,
                                name = name,
                                size = size,
                                modifiedAt = System.currentTimeMillis(),
                                isDirectory = false,
                                mimeType = mime,
                            )
                        },
                        deleteCreated = { tag ->
                            val cleanup = openConnection(url, parsed.serverId, "DELETE")
                            try {
                                cleanup.setRequestProperty("If-Match", tag)
                                cleanup.responseCode
                            } finally {
                                cleanup.disconnect()
                            }
                        },
                    )
                pendingConnection = null // OutputTarget now owns the connection.
                FileResult.Success(target)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileResult.Failure(mapException(e))
            } finally {
                pendingConnection?.disconnect()
            }
        }

    override suspend fun createDirectory(
        parent: FileNodeId,
        name: String,
    ): FileResult<FileNode> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(parent)
            val newPath = if (parsed.path.endsWith("/")) "${parsed.path}$name/" else "${parsed.path}/$name/"
            val newNodeId = makeNodeId(parsed.serverId, newPath)

            try {
                val url = buildUrl(parsed.serverId, newPath)
                val conn = openConnection(url, parsed.serverId, "MKCOL")
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()

                if (code == 201) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = parent,
                            name = name,
                            isDirectory = true,
                        ),
                    )
                } else if (code == 405) {
                    FileResult.Failure(FileError.FileAlreadyExists(name))
                } else {
                    FileResult.Failure(FileError.AccessDenied("MKCOL failed ($code)"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun delete(id: FileNodeId): FileResult<Unit> =
        withContext(Dispatchers.IO) {
            val parsed = parseId(id)
            try {
                val url = buildUrl(parsed.serverId, parsed.path)
                val conn = openConnection(url, parsed.serverId, "DELETE")
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()

                if (code in 200..299 || code == 204) {
                    FileResult.Success(Unit)
                } else {
                    FileResult.Failure(FileError.AccessDenied("DELETE failed ($code)"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
            val newNodeId = makeNodeId(parsed.serverId, newPath)

            try {
                val url = buildUrl(parsed.serverId, parsed.path)
                val destUrl = buildUrl(parsed.serverId, newPath)
                val conn = openConnection(url, parsed.serverId, "MOVE")
                conn.setRequestProperty("Destination", destUrl.toString())
                conn.setRequestProperty("Overwrite", "F")
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()

                if (code in 200..299 || code == 201 || code == 204) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = makeNodeId(parsed.serverId, parentPath),
                            name = newName,
                            isDirectory = false,
                        ),
                    )
                } else if (code == 412) {
                    FileResult.Failure(FileError.FileAlreadyExists(getFileName(newPath)))
                } else {
                    FileResult.Failure(FileError.AccessDenied("MOVE failed ($code)"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
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
            if (parsed.serverId != parsedParent.serverId) {
                return@withContext FileResult.Failure(FileError.InvalidDestination(newParent.raw))
            }
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
            val newNodeId = makeNodeId(parsed.serverId, newPath)

            try {
                val url = buildUrl(parsed.serverId, parsed.path)
                val destUrl = buildUrl(parsed.serverId, newPath)
                val conn = openConnection(url, parsed.serverId, "MOVE")
                conn.setRequestProperty("Destination", destUrl.toString())
                conn.setRequestProperty("Overwrite", "F")
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()

                if (code in 200..299 || code == 201 || code == 204) {
                    FileResult.Success(
                        NetworkNodeHelper.createNode(
                            id = newNodeId,
                            parentId = newParent,
                            name = fileName,
                            isDirectory = false,
                        ),
                    )
                } else if (code == 412) {
                    FileResult.Failure(FileError.FileAlreadyExists(getFileName(newPath)))
                } else {
                    FileResult.Failure(FileError.AccessDenied("MOVE failed ($code)"))
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                FileResult.Failure(mapException(e))
            }
        }

    override suspend fun exists(
        parent: FileNodeId,
        name: String,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val parsed = parseId(parent)
            val targetPath = if (parsed.path.endsWith("/")) "${parsed.path}$name" else "${parsed.path}/$name"
            try {
                val url = buildUrl(parsed.serverId, targetPath)
                val conn = openConnection(url, parsed.serverId, "PROPFIND")
                conn.setRequestProperty("Depth", "0")
                conn.connect()
                val code = conn.responseCode
                conn.disconnect()
                code in 200..299
            } catch (_: Exception) {
                false
            }
        }

    override suspend fun freeSpace(id: FileNodeId): Long = 0L

    private fun parseMultiStatusXml(
        input: InputStream,
        serverId: String,
    ): List<FileNode> {
        val dateFormat = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        return WebDavXml.parse(input).map { response ->
            val hrefPath =
                URL(
                    if (response.href.startsWith("http")) response.href else "http://dummy${response.href}",
                ).path
            val cleanPath = hrefPath.trimEnd('/')
            NetworkNodeHelper.createNode(
                id = makeNodeId(serverId, hrefPath),
                parentId = makeNodeId(serverId, getParentPath(cleanPath)),
                name = getFileName(cleanPath),
                size = response.properties["getcontentlength"]?.toLongOrNull() ?: 0L,
                modifiedAt =
                    response.properties["getlastmodified"]?.let {
                        runCatching { dateFormat.parse(it)?.time }.getOrNull()
                    } ?: 0L,
                isDirectory = response.directory,
                mimeType = response.properties["getcontenttype"],
            )
        }
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
        val msg = e.message.orEmpty()
        return when {
            msg.contains("401", ignoreCase = true) || msg.contains("403", ignoreCase = true) ->
                FileError.AccessDenied("WebDAV authentication failed")
            msg.contains("404", ignoreCase = true) ->
                FileError.FileNotFound(msg)
            else -> FileError.StorageUnavailable("WebDAV error: $msg")
        }
    }
}
