package com.devbehindyou.atomicfilemanager.data.backend.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import com.devbehindyou.atomicfilemanager.domain.model.NetworkServerConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.KeyStore
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory

/** Real loopback sockets and protocol replies; no public server or user credentials. */
class FtpProtocolIntegrationTest {
    @Test
    fun `login accepts multiline greeting and correct credentials`() {
        conversation(false, listOf("USER test" to "331 Password", "PASS fixture" to "230 Logged in")) { client ->
            client.connect()
            client.login("test", "fixture")
        }
    }

    @Test
    fun `authentication rejection is surfaced`() {
        conversation(false, listOf("USER test" to "331 Password", "PASS fixture" to "530 Rejected")) { client ->
            client.connect()
            assertThrows(FtpProtocolException::class.java) { client.login("test", "fixture") }
        }
    }

    @Test
    fun `FTPS refuses plaintext fallback when server rejects TLS`() {
        conversation(true, listOf("AUTH TLS" to "500 TLS unavailable")) { client ->
            assertThrows(Exception::class.java) { client.connect() }
        }
    }

    @Test
    fun `FTPS protects listing download and upload after preliminary replies`() {
        transferSession(tls = true) { client ->
            assertEquals(listOf("fixture.txt"), client.list("/"))
            val downloaded =
                client.openDataTransfer("RETR /fixture.txt", binary = true).use {
                    it.getInputStream().readBytes()
                }
            client.completeTransfer()
            assertArrayEquals(PAYLOAD, downloaded)
            FtpUploadStream(client, client.openDataTransfer("STOR /upload.txt", binary = true)).use {
                it.write(PAYLOAD)
            }
        }
    }

    @Test
    fun `plain FTP still transfers listing download and upload`() {
        transferSession(tls = false) { client ->
            assertEquals(listOf("fixture.txt"), client.list("/"))
            val downloaded =
                client.openDataTransfer("RETR /fixture.txt", binary = true).use {
                    it.getInputStream().readBytes()
                }
            client.completeTransfer()
            assertArrayEquals(PAYLOAD, downloaded)
            FtpUploadStream(client, client.openDataTransfer("STOR /upload.txt", binary = true)).use {
                it.write(PAYLOAD)
            }
        }
    }

    @Test
    fun `multiline passive reply uses endpoint from terminal line`() {
        transferSession(tls = false, multilinePassiveReply = true) { client ->
            assertEquals(listOf("fixture.txt"), client.list("/"))
            val downloaded =
                client.openDataTransfer("RETR /fixture.txt", binary = true).use {
                    it.getInputStream().readBytes()
                }
            client.completeTransfer()
            assertArrayEquals(PAYLOAD, downloaded)
            FtpUploadStream(client, client.openDataTransfer("STOR /upload.txt", binary = true)).use {
                it.write(PAYLOAD)
            }
        }
    }

    @Test
    fun `oversized reply line is rejected`() {
        conversation(false, listOf("USER test" to "230 ${"x".repeat(9000)}"), allowReplyDisconnect = true) { client ->
            client.connect()
            val failure = assertThrows(IOException::class.java) { client.login("test", "fixture") }
            assertEquals("FTP reply line exceeds limit", failure.message)
        }
    }

    @Test
    fun `multiline reply has a total size bound`() {
        val reply = "230-Starting\r\n" + ("x".repeat(7000) + "\r\n").repeat(10) + "230 Complete"
        conversation(false, listOf("USER test" to reply), allowReplyDisconnect = true) { client ->
            client.connect()
            val failure = assertThrows(IOException::class.java) { client.login("test", "fixture") }
            assertEquals("FTP multiline reply exceeds limit", failure.message)
        }
    }

    @Test
    fun `oversized listing line fails instead of returning partial success`() {
        rejectedListing(9000, 1, "FTP listing line exceeds limit")
    }

    @Test
    fun `too many listing entries fail instead of truncating`() {
        rejectedListing(1, 10_001, "FTP listing exceeds limit")
    }

    @Test
    fun `listing total character count is bounded`() {
        rejectedListing(7000, 600, "FTP listing exceeds limit")
    }

    private fun rejectedListing(
        lineLength: Int,
        count: Int,
        expected: String,
    ) {
        serverSession { server, execute ->
            execute {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val wire = Wire(socket)
                    wire.reply("220 Ready")
                    wire.expect("USER test", "230 Logged in")
                    try {
                        dataTransfer(wire, false, tlsContext(), "LIST /", false) { data ->
                            val line = ("x".repeat(lineLength) + "\r\n").toByteArray()
                            repeat(count) { data.getOutputStream().write(line) }
                        }
                    } catch (_: IOException) {
                        // The client closes the overflowing stream before the server finishes.
                    }
                }
            }
            FtpProtocolClient("localhost", server.localPort, false).use { client ->
                client.connect()
                client.login("test", "fixture")
                val failure = assertThrows(IOException::class.java) { client.list("/") }
                assertEquals(expected, failure.message)
            }
        }
    }

    @Test
    fun `LIST denial is surfaced instead of empty success`() {
        transferSession(tls = false, deniedList = true) { client ->
            assertEquals(550, assertThrows(FtpProtocolException::class.java) { client.list("/") }.code)
        }
    }

    @Test
    fun `upload failure in final reply stays failed on repeated close`() {
        transferSession(tls = true, failedUpload = true) { client ->
            assertEquals(listOf("fixture.txt"), client.list("/"))
            client.openDataTransfer("RETR /fixture.txt", binary = true).use { it.getInputStream().readBytes() }
            client.completeTransfer()
            val upload = FtpUploadStream(client, client.openDataTransfer("STOR /upload.txt", binary = true))
            upload.write(PAYLOAD)
            assertEquals(552, assertThrows(FtpProtocolException::class.java) { upload.close() }.code)
            assertEquals(552, assertThrows(FtpProtocolException::class.java) { upload.close() }.code)
        }
    }

    @Test
    fun `FTPS rejects certificate not trusted by default client`() {
        rejectedTls("localhost", SSLSocketFactory.getDefault() as SSLSocketFactory)
    }

    @Test
    fun `FTPS rejects trusted certificate for different configured host`() {
        // The fixture is trusted only by this test factory and has only a localhost DNS SAN.
        rejectedTls("127.0.0.1", tlsContext().socketFactory)
    }

    private fun rejectedTls(
        host: String,
        factory: SSLSocketFactory,
    ) {
        val context = tlsContext()
        serverSession { server, execute ->
            execute {
                server.accept().use { socket ->
                    val wire = Wire(socket)
                    wire.reply("220 Ready")
                    wire.expect("AUTH TLS", "234 Secure")
                    // A rejecting client may send a TLS alert or reset the TCP connection.
                    // The client assertion below still requires certificate/hostname rejection.
                    assertThrows(IOException::class.java) { secureServer(socket, context).close() }
                }
            }
            FtpProtocolClient(host, server.localPort, true, factory).use { client ->
                assertThrows(SSLException::class.java) { client.connect() }
            }
        }
    }

    internal fun deniedListServer(action: (Int) -> Unit) {
        serverSession { server, execute ->
            execute {
                server.accept().use { socket ->
                    socket.soTimeout = 5000
                    val wire = Wire(socket)
                    wire.reply("220 Ready")
                    wire.expect("USER anonymous", "230 Logged in")
                    dataTransfer(wire, false, tlsContext(), "LIST /", false, denied = true) { }
                }
            }
            action(server.localPort)
        }
    }

    private fun transferSession(
        tls: Boolean,
        deniedList: Boolean = false,
        failedUpload: Boolean = false,
        multilinePassiveReply: Boolean = false,
        action: (FtpProtocolClient) -> Unit,
    ) {
        val context = tlsContext()
        serverSession { server, execute ->
            execute {
                server.accept().use { plain ->
                    var wire = Wire(plain)
                    wire.reply("220 Ready")
                    val control =
                        if (tls) {
                            wire.expect("AUTH TLS", "234 Secure")
                            secureServer(plain, context)
                        } else {
                            plain
                        }
                    control.use {
                        wire = Wire(control)
                        wire.expect("USER test", "331 Password")
                        wire.expect("PASS fixture", "230 Logged in")
                        if (tls) {
                            wire.expect("PBSZ 0", "200 OK")
                            wire.expect("PROT P", "200 OK")
                        }
                        dataTransfer(
                            wire,
                            tls,
                            context,
                            "LIST /",
                            false,
                            deniedList,
                            multilinePassiveReply = multilinePassiveReply,
                        ) { data ->
                            data.getOutputStream().write("fixture.txt\r\n".toByteArray())
                        }
                        if (!deniedList) {
                            dataTransfer(
                                wire,
                                tls,
                                context,
                                "RETR /fixture.txt",
                                true,
                                multilinePassiveReply = multilinePassiveReply,
                            ) { data ->
                                data.getOutputStream().write(PAYLOAD)
                            }
                            dataTransfer(
                                wire,
                                tls,
                                context,
                                "STOR /upload.txt",
                                true,
                                failedFinal = failedUpload,
                                multilinePassiveReply = multilinePassiveReply,
                            ) { data ->
                                assertArrayEquals(PAYLOAD, data.getInputStream().readBytes())
                            }
                        }
                    }
                }
            }
            FtpProtocolClient("localhost", server.localPort, tls, context.socketFactory).use { client ->
                client.connect()
                client.login("test", "fixture")
                action(client)
            }
        }
    }

    private fun dataTransfer(
        wire: Wire,
        tls: Boolean,
        context: SSLContext,
        command: String,
        binary: Boolean,
        denied: Boolean = false,
        failedFinal: Boolean = false,
        multilinePassiveReply: Boolean = false,
        exchange: (Socket) -> Unit,
    ) {
        wire.expect(if (binary) "TYPE I" else "TYPE A", "200 OK")
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { passive ->
            passive.soTimeout = 5000
            val port = passive.localPort
            // A hostile PASV host must not redirect away from the control connection peer.
            val prefix = if (multilinePassiveReply) "227-Preparing endpoint\r\n" else ""
            wire.expect("PASV", "${prefix}227 Passive (203,0,113,1,${port / 256},${port % 256})")
            passive.accept().use { plain ->
                plain.soTimeout = 5000
                wire.expect(command, if (denied) "550 Denied" else "150 Opening")
                if (!denied) {
                    // Handshake deliberately starts only after 150; early clients time out.
                    val data = if (tls) secureServer(plain, context) else plain
                    data.use(exchange)
                    wire.reply(if (failedFinal) "552 No space" else "226 Complete")
                }
            }
        }
    }

    private fun secureServer(
        socket: Socket,
        context: SSLContext,
    ): SSLSocket {
        val secured = context.socketFactory.createSocket(socket, "localhost", socket.port, true) as SSLSocket
        secured.useClientMode = false
        secured.soTimeout = 5000
        try {
            secured.startHandshake()
            return secured
        } catch (e: Exception) {
            secured.close()
            throw e
        }
    }

    private fun tlsContext(): SSLContext {
        val keys = KeyStore.getInstance("PKCS12")
        requireNotNull(javaClass.getResourceAsStream("/ftp/localhost.p12")).use {
            keys.load(it, "fixture".toCharArray())
        }
        val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        managers.init(keys, "fixture".toCharArray())
        val trust = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        trust.init(keys)
        return SSLContext.getInstance("TLS").apply { init(managers.keyManagers, trust.trustManagers, null) }
    }

    private fun serverSession(action: (ServerSocket, (() -> Unit) -> Unit) -> Unit) {
        val executor = Executors.newSingleThreadExecutor()
        try {
            ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
                server.soTimeout = 5000
                var future: java.util.concurrent.Future<*>? = null
                action(server) { script -> future = executor.submit { script() } }
                requireNotNull(future).get(20, TimeUnit.SECONDS)
            }
        } finally {
            executor.shutdownNow()
            executor.awaitTermination(10, TimeUnit.SECONDS)
        }
    }

    private class Wire(socket: Socket) {
        private val reader = socket.getInputStream().bufferedReader()
        private val writer = socket.getOutputStream().bufferedWriter()

        fun reply(response: String) {
            writer.write("$response\r\n")
            writer.flush()
        }

        fun expect(
            command: String,
            response: String,
        ) {
            assertEquals(command, reader.readLine())
            reply(response)
        }
    }

    private companion object {
        val PAYLOAD = "TLS protected fixture content\n".toByteArray()
    }

    private fun conversation(
        tls: Boolean,
        replies: List<Pair<String, String>>,
        allowReplyDisconnect: Boolean = false,
        action: (FtpProtocolClient) -> Unit,
    ) {
        val executor = Executors.newSingleThreadExecutor()
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 5000
            val future =
                executor.submit {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val reader = socket.getInputStream().bufferedReader()
                        val writer = socket.getOutputStream().bufferedWriter()
                        writer.write("220-Fixture\r\n220 Ready\r\n")
                        writer.flush()
                        for ((expected, response) in replies) {
                            assertEquals(expected, reader.readLine())
                            try {
                                writer.write("$response\r\n")
                                writer.flush()
                            } catch (e: IOException) {
                                if (!allowReplyDisconnect) throw e
                            }
                        }
                    }
                }
            val client = FtpProtocolClient("127.0.0.1", server.localPort, tls)
            try {
                action(client)
                future.get(10, TimeUnit.SECONDS)
            } finally {
                client.close()
                executor.shutdownNow()
            }
        }
    }
}

@RunWith(RobolectricTestRunner::class)
class FtpBackendProtocolIntegrationTest {
    @org.junit.Test
    fun `denied directory listing returns failure`() {
        withDeniedListing { backend ->
            check(backend.listChildren(FileNodeId.ftp("fixture", "/")).first() is FileResult.Failure)
        }
    }

    @org.junit.Test
    fun `getNode does not fabricate metadata after denied listing`() {
        withDeniedListing { backend ->
            check(backend.getNode(FileNodeId.ftp("fixture", "/missing.txt")) is FileResult.Failure)
        }
    }

    @org.junit.Test
    fun `existence check does not turn denied listing into absence`() {
        withDeniedListing { backend ->
            var failure: Exception? = null
            try {
                backend.exists(FileNodeId.ftp("fixture", "/"), "existing.txt")
            } catch (e: Exception) {
                failure = e
            }
            check(failure is FtpProtocolException)
        }
    }

    private fun withDeniedListing(action: suspend (FtpBackend) -> Unit) {
        FtpProtocolIntegrationTest().deniedListServer { port ->
            val context = ApplicationProvider.getApplicationContext<Context>()
            val store = NetworkCredentialsStore(context)
            check(
                store.saveServer(
                    NetworkServerConfig(
                        id = "fixture",
                        name = "Loopback fixture",
                        protocol = NetworkProtocol.FTP,
                        host = "127.0.0.1",
                        port = port,
                        username = "",
                        anonymous = true,
                    ),
                    password = null,
                ),
            )
            try {
                runBlocking { action(FtpBackend(store)) }
            } finally {
                store.deleteServer("fixture")
            }
        }
    }
}
