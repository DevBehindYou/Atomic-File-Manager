package com.devbehindyou.atomicfilemanager.data.share

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.nio.file.Files
import java.util.concurrent.CopyOnWriteArrayList

class ShareServerTest {
    @TempDir
    lateinit var tmp: File

    private val loopback = InetAddress.getLoopbackAddress()
    private val stops = CopyOnWriteArrayList<ShareStopReason>()
    private var server: ShareServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop()
    }

    private fun start(
        allowUpload: Boolean = false,
        idleMs: Long = ShareServer.IDLE_MS,
    ): ShareServer {
        val root = File(tmp, "shared").apply { mkdirs() }
        File(root, "notes.txt").writeText("hello")
        File(root, ".secret").writeText("hidden")
        File(root, "photos").mkdirs()
        File(tmp, "outside.txt").writeText("not shared")
        return ShareServer(
            root,
            loopback,
            pin = "123456",
            allowUpload = allowUpload,
            idleMs = idleMs,
            onStop = { stops += it },
        )
            .also { server = it }
    }

    private class Response(val status: Int, val headers: Map<String, String>, val body: String)

    private fun ShareServer.call(
        method: String,
        target: String,
        cookie: String? = null,
        body: String = "",
        host: String = "127.0.0.1:$port",
        type: String = "application/x-www-form-urlencoded",
    ): Response {
        Socket(loopback, port).use { socket ->
            socket.soTimeout = 5_000
            val bytes = body.toByteArray()
            val head =
                "$method $target HTTP/1.1\r\nHost: $host\r\n" +
                    (cookie?.let { "Cookie: $it\r\n" } ?: "") +
                    (if (method != "GET") "Content-Type: $type\r\nContent-Length: ${bytes.size}\r\n" else "") +
                    "\r\n"
            socket.getOutputStream().apply {
                write(head.toByteArray())
                write(bytes)
                flush()
            }
            val raw = socket.getInputStream().readBytes().toString(Charsets.UTF_8)
            val (top, rest) = raw.split("\r\n\r\n", limit = 2).let { it[0] to it.getOrElse(1) { "" } }
            val lines = top.split("\r\n")
            val headers =
                lines.drop(
                    1,
                ).associate { it.substringBefore(':').lowercase() to it.substringAfter(':').trim() }
            return Response(lines.first().split(' ')[1].toInt(), headers, rest)
        }
    }

    private fun ShareServer.signIn(): String {
        val r = call("POST", "/login", body = "pin=123456")
        assertEquals(303, r.status)
        return r.headers.getValue("set-cookie").substringBefore(';')
    }

    @Test
    fun `live upload cancellation closes only its request and cleans its owned staging`() =
        runBlocking<Unit> {
            val share = start(allowUpload = true)
            val cookie = share.signIn()
            Socket(loopback, share.port).use { client ->
                client.getOutputStream().apply {
                    write(
                        (
                            "PUT /up?p=&n=cancelled.bin HTTP/1.1\r\nHost: 127.0.0.1:${share.port}\r\n" +
                                "Cookie: $cookie\r\nContent-Length: 1048576\r\n\r\n"
                        ).toByteArray(),
                    )
                    write(ByteArray(65536))
                    flush()
                }
                val active = withTimeout(5000) { share.transfers.active.first { it.isNotEmpty() }.single() }
                assertEquals(ShareTransferDirection.UPLOAD, active.direction)
                assertEquals("cancelled.bin", active.name)
                assertEquals(1048576L, active.bytesTotal)
                share.transfers.cancel(active.id)
                withTimeout(5000) { share.transfers.active.first { it.isEmpty() } }
            }
            assertTrue(share.isRunning)
            assertFalse(File(tmp, "shared/cancelled.bin").exists())
            assertTrue(File(tmp, "shared").listFiles().orEmpty().none { it.name.endsWith(".part") })
            assertEquals("hello", share.call("GET", "/dl?p=notes.txt", cookie).body)
            assertTrue(share.transfers.active.value.isEmpty())
        }

    @Test
    fun `nothing is shown before the PIN`() {
        val s = start()
        val page = s.call("GET", "/")
        assertEquals(200, page.status)
        assertTrue("Enter the PIN" in page.body)
        assertFalse("notes.txt" in page.body)
        assertEquals(200, s.call("GET", "/dl?p=notes.txt").status) // the login page again, not the file
        assertFalse("hello" in s.call("GET", "/dl?p=notes.txt").body)
    }

    @Test
    fun `the right PIN opens the folder and downloads work`() {
        val s = start()
        val cookie = s.signIn()
        assertTrue("HttpOnly" in s.call("POST", "/login", body = "pin=123456").headers.getValue("set-cookie"))
        val listing = s.call("GET", "/", cookie)
        assertTrue("notes.txt" in listing.body)
        assertTrue("photos/" in listing.body)
        assertFalse(".secret" in listing.body)
        val file = s.call("GET", "/dl?p=notes.txt", cookie)
        assertEquals(200, file.status)
        assertEquals("hello", file.body)
        assertEquals(1, s.downloads.get())
        assertEquals("no-store", file.headers["cache-control"])
    }

    @Test
    fun `nothing outside the folder or hidden is served`() {
        val s = start()
        val cookie = s.signIn()
        assertEquals(404, s.call("GET", "/dl?p=../outside.txt", cookie).status)
        assertEquals(404, s.call("GET", "/dl?p=photos/../../outside.txt", cookie).status)
        assertEquals(404, s.call("GET", "/dl?p=.secret", cookie).status)
        Files.createSymbolicLink(File(tmp, "shared/link.txt").toPath(), File(tmp, "outside.txt").toPath())
        assertEquals(404, s.call("GET", "/dl?p=link.txt", cookie).status)
        assertNull(s.resolve("a/../.."))
    }

    @Test
    fun `another host name is refused`() {
        val s = start()
        assertEquals(421, s.call("GET", "/", host = "evil.example:${s.port}").status)
    }

    @Test
    fun `five wrong PINs stop the share`() {
        val s = start()
        repeat(ShareServer.MAX_WRONG_PINS) {
            val r = s.call("POST", "/login", body = "pin=000000")
            assertTrue("That PIN is wrong" in r.body)
        }
        awaitCondition { stops.isNotEmpty() }
        assertFalse(s.isRunning)
        assertEquals(listOf(ShareStopReason.WRONG_PIN), stops)
    }

    @Test
    fun `uploads are off unless allowed`() {
        val s = start()
        val cookie = s.signIn()
        assertEquals(403, s.call("PUT", "/up?p=&n=new.txt", cookie, body = "data", type = "text/plain").status)
        assertFalse(File(tmp, "shared/new.txt").exists())
    }

    @Test
    fun `allowed uploads arrive whole and never replace a file`() {
        val s = start(allowUpload = true)
        val cookie = s.signIn()
        assertEquals(
            201,
            s.call("PUT", "/up?p=photos&n=new%20one.txt", cookie, body = "data", type = "text/plain").status,
        )
        assertEquals("data", File(tmp, "shared/photos/new one.txt").readText())
        assertFalse(File(tmp, "shared/photos/.new one.txt.atomic-part").exists())
        assertEquals(409, s.call("PUT", "/up?p=&n=notes.txt", cookie, body = "x", type = "text/plain").status)
        assertEquals("hello", File(tmp, "shared/notes.txt").readText())
        assertEquals(400, s.call("PUT", "/up?p=&n=..%2Fescape.txt", cookie, body = "x", type = "text/plain").status)
        assertEquals(400, s.call("PUT", "/up?p=&n=.hidden", cookie, body = "x", type = "text/plain").status)
        assertEquals(1, s.uploads.get())
    }

    @Test
    fun `an idle share stops by itself`() {
        val s = start(idleMs = 100)
        val deadline = System.currentTimeMillis() + 5_000
        while (s.isRunning && System.currentTimeMillis() < deadline) Thread.sleep(20)
        awaitCondition { stops.isNotEmpty() }
        assertFalse(s.isRunning)
        assertEquals(listOf(ShareStopReason.IDLE), stops)
    }

    @Test
    fun `stopping twice reports once`() {
        val s = start()
        s.stop()
        s.stop()
        assertEquals(listOf(ShareStopReason.USER), stops)
    }

    @Test
    fun `stop closes active and queued clients and excess admissions are rejected`() {
        val s = start()
        // None sends a request: four occupy workers, eight wait, and the rest must be closed.
        val clients = mutableListOf<Socket>()
        try {
            repeat(20) { clients += connectForAdmissionTest(s.port) }
            val rejected = clients.count { it.isClosedByPeer() }
            assertTrue(rejected >= 8, "Accepted connections must be bounded to twelve")
            s.stop()
            clients.forEach {
                it.soTimeout = 2_000
                assertTrue(it.isClosedByPeer(), "Stop must close active and queued sockets")
            }
        } finally {
            clients.forEach(Socket::close)
        }
    }

    private fun connectForAdmissionTest(port: Int): Socket {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (true) {
            val client = Socket()
            try {
                val remaining = ((deadline - System.nanoTime()) / 1_000_000).coerceIn(1, 2_000).toInt()
                client.connect(InetSocketAddress(loopback, port), remaining)
                client.soTimeout = 100
                return client
            } catch (error: ConnectException) {
                client.close()
                if (System.nanoTime() >= deadline) throw error
                // The kernel's small listen backlog can refuse before application admission.
                // Keep the original two-second deadline and still obtain all twenty clients
                // before asserting that the application rejects at least eight of them.
                Thread.sleep(10)
            } catch (error: Exception) {
                client.close()
                throw error
            }
        }
    }

    @Test
    fun `stop during an incomplete upload closes its socket and removes staging`() {
        val s = start(allowUpload = true)
        val cookie = s.signIn()
        Socket(loopback, s.port).use { client ->
            client.soTimeout = 2_000
            client.getOutputStream().apply {
                write(
                    (
                        "PUT /up?p=&n=unfinished.txt HTTP/1.1\r\nHost: 127.0.0.1:${s.port}\r\n" +
                            "Cookie: $cookie\r\nContent-Length: 1000000\r\n\r\nx"
                    ).toByteArray(),
                )
                flush()
            }
            val shared = File(tmp, "shared")
            awaitCondition { shared.list()!!.any { it.startsWith(".atomic-upload-") } }
            s.stop()
            assertTrue(client.isClosedByPeer())
            awaitCondition { shared.list()!!.none { it.startsWith(".atomic-upload-") } }
            assertFalse(File(shared, "unfinished.txt").exists())
        }
    }

    private fun Socket.isClosedByPeer(): Boolean =
        try {
            getInputStream().read() == -1
        } catch (_: SocketTimeoutException) {
            false
        } catch (_: IOException) {
            true // A reset is also a closed connection.
        }

    private fun awaitCondition(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        assertTrue(condition())
    }

    @Test
    fun `pins are six digits`() {
        repeat(20) { assertTrue(Regex("\\d{6}").matches(ShareServer.newPin())) }
    }
}
