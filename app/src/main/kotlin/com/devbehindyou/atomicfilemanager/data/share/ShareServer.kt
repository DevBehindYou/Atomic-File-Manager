package com.devbehindyou.atomicfilemanager.data.share

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.file.FileAlreadyExistsException
import java.security.SecureRandom
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/** Why a share stopped, shown to the user. */
enum class ShareStopReason(val message: String) {
    USER("Stopped."),
    IDLE("Stopped after 15 minutes without use."),
    WRONG_PIN("Stopped after too many wrong PINs."),
    FAILED("Stopped: the share couldn't keep running."),
}

/**
 * The guarded Wi-Fi share (ALL_IN_ONE_PLAN.md 3.4): a small HTTP server for one folder, for a
 * browser on the same Wi-Fi. Written without a library so every rule is visible here:
 *
 * - Listens only on [address] (the phone's Wi-Fi address), never on all interfaces.
 * - A one-time [pin] signs a browser in; the session is a random cookie (HttpOnly, SameSite=Strict).
 *   [MAX_WRONG_PINS] wrong tries stop the share.
 * - The Host header must be this address and port, so a web page elsewhere can't reach the
 *   server through DNS rebinding.
 * - Only files under [root] are served; `..`, absolute paths, links out of the folder and hidden
 *   files are refused.
 * - Read-only unless [allowUpload]. Uploads use unique staging and exclusive final creation;
 *   in-progress final files are hidden from share clients (see [ShareUploads]).
 * - Stops after [idleMs] without a request.
 */
class ShareServer(
    private val root: File,
    private val address: InetAddress,
    val pin: String = newPin(),
    private val allowUpload: Boolean = false,
    private val idleMs: Long = IDLE_MS,
    private val clock: () -> Long = System::currentTimeMillis,
    private val onStop: (ShareStopReason) -> Unit = {},
) {
    private val rootCanonical: File = root.toPath().toRealPath().toFile()
    private val socket = ServerSocket(0, BACKLOG, address)
    private val workers =
        ThreadPoolExecutor(WORKERS, WORKERS, 0L, TimeUnit.MILLISECONDS, ArrayBlockingQueue(QUEUED_CLIENTS))
    private val clients = ConcurrentHashMap.newKeySet<Socket>()
    private val running = AtomicBoolean(true)
    private val lastActivity = AtomicLong(clock())
    private val wrongPins = AtomicInteger(0)
    private val sessions = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val uploadFiles = ShareUploads { if (!running.get()) throw IOException("Share stopped") }
    private val idleThread = Thread(::idleWatch, "wifi-share-idle").apply { isDaemon = true }

    val port: Int get() = socket.localPort
    val url: String get() = "http://${hostOf(address)}:$port/"

    /** Files sent and received, for the screen. */
    val downloads = AtomicInteger(0)
    val uploads = AtomicInteger(0)

    init {
        Thread(::acceptLoop, "wifi-share-accept").apply { isDaemon = true }.start()
        idleThread.start()
    }

    val isRunning: Boolean get() = running.get()

    fun stop(reason: ShareStopReason = ShareStopReason.USER) {
        if (!running.compareAndSet(true, false)) return
        runCatching { socket.close() }
        // Interrupting a worker does not unblock a socket read. Include queued clients too.
        synchronized(clients) {
            clients.forEach { runCatching { it.close() } }
            clients.clear()
        }
        workers.shutdownNow()
        idleThread.interrupt()
        sessions.clear()
        onStop(reason)
    }

    private fun acceptLoop() {
        while (running.get()) {
            val client = accept() ?: break
            synchronized(clients) {
                if (running.get()) clients.add(client) else runCatching { client.close() }
            }
            if (client.isClosed) continue
            runCatching {
                workers.execute {
                    try {
                        client.use(::serve)
                    } catch (_: IOException) {
                        // Disconnected, timed out, or closed by stop(); use closes the socket.
                    } finally {
                        clients.remove(client)
                    }
                }
            }.onFailure {
                clients.remove(client)
                runCatching { client.close() }
            }
        }
    }

    /** The next browser connection, or null once the socket is closed. */
    private fun accept(): Socket? =
        try {
            socket.accept()
        } catch (_: SocketException) {
            null // closed by stop()
        } catch (_: IOException) {
            stop(ShareStopReason.FAILED)
            null
        }

    private fun idleWatch() {
        while (running.get()) {
            try {
                Thread.sleep(minOf(IDLE_CHECK_MS, idleMs))
            } catch (_: InterruptedException) {
                return
            }
            if (clock() - lastActivity.get() >= idleMs) stop(ShareStopReason.IDLE)
        }
    }

    // --- Requests ----------------------------------------------------------------------------

    private class Request(
        val method: String,
        val path: String,
        val query: Map<String, String>,
        val headers: Map<String, String>,
        val body: InputStream,
    ) {
        val cookie: String? get() =
            headers["cookie"]?.split(';')?.map { it.trim() }
                ?.firstOrNull { it.startsWith("$COOKIE=") }?.substringAfter('=')
    }

    private fun serve(client: Socket) {
        client.soTimeout = SOCKET_TIMEOUT_MS
        val input = BufferedInputStream(client.getInputStream())
        val out = client.getOutputStream()
        val request = runCatching { readRequest(input) }.getOrNull() ?: return respond(out, 400, "Bad request")
        if (!running.get()) return
        lastActivity.set(clock())
        if (request.headers["host"] != "${hostOf(address)}:$port") return respond(out, 421, "Wrong address")
        val signedIn = request.cookie?.let { it in sessions } == true
        when {
            request.method == "POST" && request.path == "/login" -> login(request, out)
            !signedIn -> page(out, ShareHtml.login(wrong = false))
            request.method == "GET" && request.path == "/" -> listing(request, out)
            request.method == "GET" && request.path == "/dl" -> download(request, out)
            request.method == "PUT" && request.path == "/up" -> upload(request, out)
            else -> respond(out, 404, "Not found")
        }
    }

    private fun login(
        request: Request,
        out: OutputStream,
    ) {
        val form =
            runCatching {
                parseQuery(String(readAtMost(request.body, request.contentLength(), MAX_FORM), Charsets.UTF_8))
            }.getOrDefault(emptyMap())
        if (form["pin"] == pin) {
            val token = newToken()
            sessions += token
            respond(
                out,
                303,
                "",
                extra =
                    mapOf(
                        "Location" to "/",
                        "Set-Cookie" to "$COOKIE=$token; Path=/; HttpOnly; SameSite=Strict",
                    ),
            )
        } else {
            page(out, ShareHtml.login(wrong = true))
            if (wrongPins.incrementAndGet() >= MAX_WRONG_PINS) stop(ShareStopReason.WRONG_PIN)
        }
    }

    private fun listing(
        request: Request,
        out: OutputStream,
    ) {
        val rel = request.query["p"].orEmpty()
        val dir = resolve(rel)?.takeIf { it.isDirectory } ?: return respond(out, 404, "Not found")
        val entries =
            dir.listFiles().orEmpty()
                .filter { !it.name.startsWith(".") && inside(it) && !uploadFiles.isPublishing(it) }
                .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
                .map { ShareHtml.Entry(it.name, ShareHtml.join(rel, it.name), it.isDirectory, it.length()) }
        page(out, ShareHtml.listing(root.name, rel, entries, allowUpload))
    }

    private fun download(
        request: Request,
        out: OutputStream,
    ) {
        val file = resolve(request.query["p"].orEmpty())?.takeIf { it.isFile } ?: return respond(out, 404, "Not found")
        if (uploadFiles.isPublishing(file)) return respond(out, 404, "Not found")
        val headers =
            header(200, "application/octet-stream", file.length()) +
                "Content-Disposition: attachment; filename*=UTF-8''${encode(file.name)}\r\n\r\n"
        out.write(headers.toByteArray(Charsets.UTF_8))
        file.inputStream().use { it.copyTo(out, BUFFER) }
        out.flush()
        downloads.incrementAndGet()
    }

    private fun upload(
        request: Request,
        out: OutputStream,
    ) {
        if (!allowUpload) return respond(out, 403, "Uploads are off")
        val dir =
            resolve(request.query["p"].orEmpty())?.takeIf { it.isDirectory }
                ?: return respond(out, 404, "Not found")
        val name = request.query["n"].orEmpty()
        if (!ShareHtml.isSafeName(name)) return respond(out, 400, "Bad name")
        val length = request.contentLength()
        if (length < 0) return respond(out, 411, "Length required")
        try {
            uploadFiles.receive(request.body, dir, name, length) { lastActivity.set(clock()) }
        } catch (_: FileAlreadyExistsException) {
            return respond(out, 409, "A file with that name is already there")
        } catch (e: IOException) {
            return respond(out, 500, "Upload failed")
        }
        uploads.incrementAndGet()
        respond(out, 201, "Saved")
    }

    // --- Paths -------------------------------------------------------------------------------

    /** The file at [rel] under the root, or null when it would leave the folder or is hidden. */
    internal fun resolve(rel: String): File? {
        val parts = rel.split('/').filter { it.isNotEmpty() }
        if (parts.any { it == ".." || it == "." || it.startsWith(".") || '\\' in it || '\u0000' in it }) return null
        val file = parts.fold(rootCanonical) { dir, part -> File(dir, part) }
        return file.takeIf { inside(it) }
    }

    private fun inside(file: File): Boolean {
        // Resolve actual links on every supported host; File.canonicalFile does not follow
        // Windows symbolic links. Path.startsWith also compares complete path components.
        val real = runCatching { file.toPath().toRealPath() }.getOrNull() ?: return false
        return real.startsWith(rootCanonical.toPath())
    }

    // --- HTTP plumbing -----------------------------------------------------------------------

    private fun readRequest(input: InputStream): Request? {
        val head = readHead(input) ?: return null
        val lines = head.split("\r\n")
        val (method, target) = lines.first().split(' ').takeIf { it.size == 3 }?.let { it[0] to it[1] } ?: return null
        val headers =
            lines.drop(1).filter { ':' in it }.associate {
                it.substringBefore(':').trim().lowercase() to it.substringAfter(':').trim()
            }
        return Request(
            method = method,
            path = target.substringBefore('?'),
            query = parseQuery(target.substringAfter('?', "")),
            headers = headers,
            body = input,
        )
    }

    /** Reads up to the blank line ending the headers; null when it is too long or cut off. */
    private fun readHead(input: InputStream): String? {
        val buffer = ByteArrayOutputStream()
        var lastFour = 0
        while (buffer.size() < MAX_HEAD) {
            val b = input.read()
            if (b < 0) return null
            buffer.write(b)
            lastFour = (lastFour shl Byte.SIZE_BITS) or b
            if (lastFour == CRLF_CRLF) return buffer.toString(Charsets.ISO_8859_1.name()).removeSuffix(END)
        }
        return null
    }

    private fun Request.contentLength(): Long = headers["content-length"]?.toLongOrNull() ?: -1

    private fun page(
        out: OutputStream,
        html: String,
    ) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        out.write((header(200, "text/html; charset=utf-8", bytes.size.toLong()) + "\r\n").toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun respond(
        out: OutputStream,
        status: Int,
        text: String,
        extra: Map<String, String> = emptyMap(),
    ) {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val head =
            header(status, "text/plain; charset=utf-8", bytes.size.toLong()) +
                extra.entries.joinToString("") { "${it.key}: ${it.value}\r\n" } + "\r\n"
        runCatching {
            out.write(head.toByteArray(Charsets.UTF_8))
            out.write(bytes)
            out.flush()
        }
    }

    private fun header(
        status: Int,
        type: String,
        length: Long,
    ): String =
        "HTTP/1.1 $status ${REASONS[status] ?: "OK"}\r\n" +
            "Content-Type: $type\r\n" +
            "Content-Length: $length\r\n" +
            "Connection: close\r\n" +
            "Cache-Control: no-store\r\n" +
            "X-Content-Type-Options: nosniff\r\n" +
            "X-Frame-Options: DENY\r\n" +
            "Referrer-Policy: no-referrer\r\n" +
            "Content-Security-Policy: default-src 'none'; style-src 'unsafe-inline'; " +
            "script-src 'unsafe-inline'; connect-src 'self'; form-action 'self'\r\n"

    private fun readAtMost(
        input: InputStream,
        length: Long,
        limit: Int,
    ): ByteArray {
        if (length !in 0..limit) return ByteArray(0)
        val bytes = ByteArray(length.toInt())
        var read = 0
        while (read < bytes.size) {
            val n = input.read(bytes, read, bytes.size - read)
            if (n < 0) break
            read += n
        }
        return bytes.copyOf(read)
    }

    companion object {
        const val IDLE_MS = 15 * 60 * 1000L
        const val MAX_WRONG_PINS = 5
        private const val PIN_DIGITS = 6
        private const val TOKEN_BYTES = 16
        private const val IDLE_CHECK_MS = 30_000L
        private const val SOCKET_TIMEOUT_MS = 30_000
        private const val BACKLOG = 8
        private const val WORKERS = 4
        private const val QUEUED_CLIENTS = 8
        private const val BUFFER = 64 * 1024
        private const val MAX_HEAD = 16 * 1024
        private const val MAX_FORM = 1024
        private const val COOKIE = "atomic_share"
        private const val END = "\r\n\r\n"
        private const val CRLF_CRLF = 0x0D0A0D0A
        private val REASONS =
            mapOf(
                200 to "OK", 201 to "Created", 303 to "See Other", 400 to "Bad Request", 403 to "Forbidden",
                404 to "Not Found", 409 to "Conflict", 411 to "Length Required", 421 to "Misdirected Request",
                500 to "Internal Server Error",
            )
        private val random = SecureRandom()

        fun newPin(): String = (1..PIN_DIGITS).joinToString("") { random.nextInt(10).toString() }

        private fun newToken(): String =
            ByteArray(TOKEN_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }

        fun hostOf(address: InetAddress): String =
            address.hostAddress.orEmpty().substringBefore('%').let { if (':' in it) "[$it]" else it }

        private fun encode(text: String): String = URLEncoder.encode(text, "UTF-8").replace("+", "%20")

        internal fun parseQuery(query: String): Map<String, String> =
            query.split('&').filter { '=' in it }.associate {
                decode(it.substringBefore('=')) to decode(it.substringAfter('='))
            }

        private fun decode(text: String): String = URLDecoder.decode(text, "UTF-8")
    }
}
