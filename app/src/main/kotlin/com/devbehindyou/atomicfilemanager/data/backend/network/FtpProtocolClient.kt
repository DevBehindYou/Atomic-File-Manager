package com.devbehindyou.atomicfilemanager.data.backend.network

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

internal class FtpProtocolException(val code: Int?, operation: String) : IOException("FTP $operation rejected ($code)")

/** Explicit FTPS (RFC 4217); both channels use platform certificate trust and hostname checks. */
internal class FtpProtocolClient(
    private val host: String,
    private val port: Int,
    private val isFtps: Boolean,
    private val sslFactory: SSLSocketFactory = SSLSocketFactory.getDefault() as SSLSocketFactory,
) : Closeable {
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null

    fun connect() {
        val plain = Socket()
        socket = plain
        try {
            plain.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT)
            plain.soTimeout = READ_TIMEOUT
            bindStreams(plain)
            requireResponse(readResponse(), "greeting", 220)
            if (isFtps) {
                requireResponse(sendCommand("AUTH TLS"), "AUTH TLS", 234)
                val secured = secure(plain)
                socket = secured
                bindStreams(secured)
            }
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    fun login(
        user: String,
        pass: String,
    ) {
        try {
            val userResponse = sendCommand("USER $user")
            if (userResponse.startsWith("331")) {
                requireResponse(sendCommand("PASS $pass"), "login", 230)
            } else {
                requireResponse(userResponse, "login", 230)
            }
            if (isFtps) {
                requireResponse(sendCommand("PBSZ 0"), "PBSZ", 200)
                requireResponse(sendCommand("PROT P"), "PROT", 200)
            }
        } catch (e: Exception) {
            close()
            throw e
        }
    }

    /** TCP first, command/preliminary reply second, TLS handshake last (RFC 4217 section 12.7). */
    fun openDataTransfer(
        command: String,
        binary: Boolean,
    ): Socket {
        requireResponse(sendCommand(if (binary) "TYPE I" else "TYPE A"), "TYPE", 200)
        val data = openPassiveDataSocket()
        try {
            requireResponse(sendCommand(command), command.substringBefore(' '), 125, 150)
            return if (isFtps) secure(data) else data
        } catch (e: Exception) {
            data.close()
            throw e
        }
    }

    fun completeTransfer() {
        requireResponse(readResponse(), "transfer completion", 226, 250)
    }

    fun list(path: String): List<String> {
        val lines =
            openDataTransfer("LIST $path", binary = false).use { data ->
                data.getInputStream().bufferedReader(StandardCharsets.UTF_8).use { it.readLines() }
            }
        completeTransfer()
        return lines
    }

    fun sendCommand(command: String): String {
        require('\r' !in command && '\n' !in command) { "FTP command contains a line break" }
        val output = writer ?: throw IOException("FTP is not connected")
        output.write(command + "\r\n")
        output.flush()
        return readResponse()
    }

    fun readResponse(): String {
        val input = reader ?: throw IOException("FTP is not connected")
        val first = input.readLine() ?: throw IOException("FTP connection closed before reply")
        val code = first.take(3).toIntOrNull()
        if (first.length < 4 || code == null || first[3] !in charArrayOf(' ', '-')) {
            throw IOException("Invalid FTP reply")
        }
        if (first[3] == '-') {
            var lines = 0
            while (true) {
                val line = input.readLine() ?: throw IOException("FTP multiline reply was truncated")
                if (line.startsWith("${first.take(3)} ")) break
                if (++lines > 1024) throw IOException("FTP multiline reply exceeds limit")
            }
        }
        return first
    }

    private fun openPassiveDataSocket(): Socket {
        val response = sendCommand("PASV")
        requireResponse(response, "PASV", 227)
        val fields =
            PASV.find(response)?.groupValues?.drop(1)?.map { it.toIntOrNull() }
                ?: throw IOException("Invalid FTP passive endpoint")
        if (fields.any { it == null || it !in 0..255 }) throw IOException("Invalid FTP passive endpoint")
        val dataPort = fields[4]!! * 256 + fields[5]!!
        if (dataPort == 0) throw IOException("Invalid FTP passive port")
        val control = socket ?: throw IOException("FTP is not connected")
        val data = Socket()
        try {
            // Do not let a PASV response redirect this connection to an unrelated host.
            data.connect(InetSocketAddress(control.inetAddress, dataPort), CONNECT_TIMEOUT)
            data.soTimeout = READ_TIMEOUT
            return data
        } catch (e: Exception) {
            data.close()
            throw e
        }
    }

    private fun secure(plain: Socket): SSLSocket {
        var secured: SSLSocket? = null
        try {
            secured = sslFactory.createSocket(plain, host, plain.port, true) as SSLSocket
            secured.useClientMode = true
            secured.soTimeout = READ_TIMEOUT
            secured.sslParameters = secured.sslParameters.apply { endpointIdentificationAlgorithm = "HTTPS" }
            secured.startHandshake()
            return secured
        } catch (e: Exception) {
            runCatching { secured?.close() }
            runCatching { plain.close() }
            throw e
        }
    }

    private fun bindStreams(connection: Socket) {
        reader = connection.getInputStream().bufferedReader(StandardCharsets.UTF_8)
        writer = connection.getOutputStream().bufferedWriter(StandardCharsets.UTF_8)
    }

    override fun close() {
        // Close without waiting for a QUIT reply, including after a partial TLS handshake.
        runCatching { socket?.close() }
        socket = null
        reader = null
        writer = null
    }

    private fun requireResponse(
        response: String,
        operation: String,
        vararg expected: Int,
    ) {
        val code = response.take(3).toIntOrNull()
        if (code == null || code !in expected) throw FtpProtocolException(code, operation)
    }

    private companion object {
        const val CONNECT_TIMEOUT = 10_000
        const val READ_TIMEOUT = 15_000
        val PASV = Regex("(\\d+),(\\d+),(\\d+),(\\d+),(\\d+),(\\d+)")
    }
}

/** Closing a transfer checks the final control reply, even when callers close before toNode(). */
internal class FtpUploadStream(private val client: FtpProtocolClient, private val data: Socket) : OutputStream() {
    private val output = data.getOutputStream()
    private var closed = false
    private var failure: Exception? = null

    override fun write(value: Int) = output.write(value)

    override fun write(
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ) = output.write(bytes, offset, length)

    override fun flush() {
        failure?.let { throw it }
        if (!closed) output.flush()
    }

    override fun close() {
        failure?.let { throw it }
        if (closed) return
        closed = true
        try {
            output.close()
            client.completeTransfer()
        } catch (e: Exception) {
            failure = e
            throw e
        } finally {
            runCatching { data.close() }
            client.close()
        }
    }

    fun abort() {
        closed = true
        runCatching { data.close() }
        client.close()
    }
}
