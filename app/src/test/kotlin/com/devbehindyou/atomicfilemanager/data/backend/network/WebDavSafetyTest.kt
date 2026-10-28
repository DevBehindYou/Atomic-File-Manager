package com.devbehindyou.atomicfilemanager.data.backend.network

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import com.devbehindyou.atomicfilemanager.domain.model.NetworkServerConfig
import com.devbehindyou.atomicfilemanager.domain.repository.OutputTarget
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.URL
import java.util.concurrent.CopyOnWriteArrayList

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WebDavSafetyTest {
    @Test
    fun `collision PUT is conditional and discard never deletes existing bytes`() =
        runBlocking {
            withServer(existing = "original") { fixture ->
                val target = fixture.target()
                target.stream().write("replacement".toByteArray())
                val result = target.toNode()
                assertTrue(result is FileResult.Failure && result.error is FileError.FileAlreadyExists)
                target.discard()
                assertEquals("original", fixture.content)
                assertEquals(listOf("PUT"), fixture.requests)
                assertEquals("*", fixture.putCondition)
            }
        }

    @Test
    fun `discard cleans only created resource matching strong ETag`() =
        runBlocking {
            withServer { fixture ->
                val target = fixture.target()
                target.stream().write("staged".toByteArray())
                target.discard()
                target.discard()
                assertEquals(null, fixture.content)
                assertEquals(listOf("PUT", "DELETE"), fixture.requests)
                assertEquals("\"created\"", fixture.deleteCondition)
            }
        }

    @Test
    fun `concurrent replacement survives conditional discard`() =
        runBlocking {
            withServer { fixture ->
                val target = fixture.target()
                target.stream().write("staged".toByteArray())
                assertTrue(target.toNode() is FileResult.Success)
                fixture.content = "concurrent replacement"
                fixture.etag = "\"replacement\""
                target.discard()
                assertEquals("concurrent replacement", fixture.content)
                assertEquals("\"created\"", fixture.deleteCondition)
            }
        }

    @Test
    fun `missing and weak ETags preserve staged bytes instead of unsafe cleanup`() =
        runBlocking {
            for (tag in listOf(null, "W/\"weak\"")) {
                withServer(tag = tag) { fixture ->
                    val target = fixture.target()
                    target.stream().write("staged".toByteArray())
                    target.discard()
                    assertEquals("staged", fixture.content)
                    assertEquals(listOf("PUT"), fixture.requests)
                }
            }
        }

    @Test
    fun `rename and move forbid replacing destination and map precondition failure`() =
        runBlocking {
            val connections = mutableListOf<MoveConnection>()
            val backend = WebDavBackend(store(80)) { url -> MoveConnection(url).also(connections::add) }
            val source = FileNodeId.webdav("fixture", "/original")
            val renamed = backend.rename(source, "existing")
            val moved = backend.moveWithin(source, FileNodeId.webdav("fixture", "/destination"))
            for (result in listOf(renamed, moved)) {
                assertTrue(result is FileResult.Failure && result.error is FileError.FileAlreadyExists)
            }
            assertEquals(2, connections.size)
            connections.forEach {
                assertEquals("MOVE", it.requestMethod)
                assertEquals("F", it.getRequestProperty("Overwrite"))
                assertTrue(it.disconnected)
            }
        }

    @Test
    fun `malformed listing propagates failure instead of an empty directory`() =
        runBlocking {
            val connection = MoveConnection(URL("http://127.0.0.1/"), 207, "<multistatus xmlns='DAV:'><response>")
            val backend = WebDavBackend(store(80)) { connection }
            val result = backend.listChildren(FileNodeId.webdav("fixture", "/folder")).first()
            assertTrue(result is FileResult.Failure)
            assertEquals("PROPFIND", connection.requestMethod)
            assertEquals("1", connection.getRequestProperty("Depth"))
            assertTrue(connection.disconnected)
        }

    @Test
    fun `cross server move is rejected before connecting`() =
        runBlocking {
            val backend = WebDavBackend(store(80)) { error("Must not connect") }
            val result = backend.moveWithin(FileNodeId.webdav("fixture", "/file"), FileNodeId.webdav("other", "/"))
            assertTrue(result is FileResult.Failure && result.error is FileError.InvalidDestination)
        }

    @Test
    fun `metadata lookup matches raw spaces and Unicode against encoded DAV href`() =
        runBlocking {
            for ((requested, href) in listOf("/a b" to "/a%20b", "/café/文件" to "/caf%C3%A9/%E6%96%87%E4%BB%B6")) {
                var requestUrl: URL? = null
                val backend =
                    WebDavBackend(store(80)) { url ->
                        requestUrl = url
                        MoveConnection(url, 207, properties(href))
                    }
                val result = backend.getNode(FileNodeId.webdav("fixture", requested))
                assertTrue("Expected metadata for $requested, got $result", result is FileResult.Success)
                assertEquals(FileNodeId.webdav("fixture", href), (result as FileResult.Success).value.id)
                assertEquals(href, requestUrl?.path)
            }
        }

    @Test
    fun `directory listing excludes encoded version of requested directory`() =
        runBlocking {
            val backend = WebDavBackend(store(80)) { url -> MoveConnection(url, 207, properties("/a%20b/")) }
            val result = backend.listChildren(FileNodeId.webdav("fixture", "/a b")).first()
            assertTrue(result is FileResult.Success)
            assertTrue((result as FileResult.Success).value.isEmpty())
        }

    @Test
    fun `canonical paths preserve encoded slash and percent identity`() =
        runBlocking {
            assertTrue(WebDavPaths.sameResource("/a%2fb", "/a%2Fb"))
            assertTrue(WebDavPaths.sameResource("/a%25b", "/a%25b"))
            assertTrue(WebDavPaths.sameResource("/%61%20b", "/a b"))
            assertFalse(WebDavPaths.sameResource("/a%2Fb", "/a/b"))
            assertFalse(WebDavPaths.sameResource("/a%252Fb", "/a%2Fb"))
            assertEquals("/a%252Fb", WebDavPaths.encode("/a%252Fb"))
            val backend = WebDavBackend(store(80)) { url -> MoveConnection(url, 207, properties("/a/b")) }
            val result = backend.getNode(FileNodeId.webdav("fixture", "/a%2Fb"))
            assertTrue(result is FileResult.Failure && result.error is FileError.FileNotFound)
        }

    private fun properties(href: String): String =
        """
        <multistatus xmlns="DAV:"><response><href>$href</href>
        <propstat><prop><getcontentlength>3</getcontentlength></prop>
        <status>HTTP/1.1 200 OK</status></propstat></response></multistatus>
        """.trimIndent()

    private suspend fun withServer(
        existing: String? = null,
        tag: String? = "\"created\"",
        action: suspend (Fixture) -> Unit,
    ) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val fixture = Fixture(server, existing, tag)
        server.createContext("/") { request ->
            fixture.requests += request.requestMethod
            val code =
                when (request.requestMethod) {
                    "PUT" -> {
                        fixture.putCondition = request.requestHeaders.getFirst("If-None-Match")
                        val bytes = request.requestBody.use { it.readBytes() }
                        if (fixture.content != null && fixture.putCondition == "*") {
                            412
                        } else {
                            fixture.content = bytes.toString(Charsets.UTF_8)
                            fixture.etag?.let { request.responseHeaders.set("ETag", it) }
                            201
                        }
                    }
                    "DELETE" -> {
                        fixture.deleteCondition = request.requestHeaders.getFirst("If-Match")
                        if (fixture.deleteCondition == fixture.etag) {
                            fixture.content = null
                            204
                        } else {
                            412
                        }
                    }
                    else -> 405
                }
            request.sendResponseHeaders(code, -1)
            request.close()
        }
        server.start()
        try {
            action(fixture)
        } finally {
            server.stop(0)
        }
    }

    private inner class Fixture(
        val server: HttpServer,
        @Volatile var content: String?,
        @Volatile var etag: String?,
    ) {
        val requests = CopyOnWriteArrayList<String>()

        @Volatile var putCondition: String? = null

        @Volatile var deleteCondition: String? = null

        suspend fun target(): OutputTarget {
            val backend = WebDavBackend(store(server.address.port))
            val result = backend.openOutput(FileNodeId.webdav("fixture", "/"), "stage", "text/plain")
            assertTrue("Expected an output target, got $result", result is FileResult.Success)
            return (result as FileResult.Success).value
        }
    }

    private fun store(port: Int): NetworkCredentialsStore {
        val context = ApplicationProvider.getApplicationContext<Context>()
        return NetworkCredentialsStore(context).apply {
            saveServer(
                NetworkServerConfig(
                    id = "fixture",
                    name = "Loopback fixture",
                    protocol = NetworkProtocol.WEBDAV,
                    host = "127.0.0.1",
                    port = port,
                    anonymous = true,
                ),
                password = null,
            )
        }
    }

    private class MoveConnection(
        url: URL,
        private val code: Int = 412,
        private val body: String = "",
    ) : HttpURLConnection(url) {
        var disconnected = false

        override fun setRequestMethod(method: String) {
            this.method = method
        }

        override fun getResponseCode() = code

        override fun getInputStream() = body.byteInputStream()

        override fun connect() = Unit

        override fun disconnect() {
            disconnected = true
        }

        override fun usingProxy() = false
    }
}
