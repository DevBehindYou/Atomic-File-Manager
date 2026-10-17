package com.devbehindyou.atomicfilemanager.data.backend.network

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream

/** Bounded DAV metadata only; never builds a DOM or resolves a document type declaration. */
internal object WebDavXml {
    const val MAX_BYTES = 4 * 1024 * 1024
    private const val MAX_RESPONSES = 10_000
    private const val MAX_DEPTH = 32
    private const val MAX_VALUE = 64 * 1024
    private const val DAV = "DAV:"

    data class Response(val href: String, val properties: Map<String, String>, val directory: Boolean)

    fun parse(input: InputStream): List<Response> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        // Do not silently continue if a platform parser cannot disable DTD processing.
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL, false)
        check(!parser.getFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL))
        parser.setInput(BoundedInput(input), null)
        val responses = mutableListOf<Response>()
        var response: ResponseBuilder? = null
        var rootSeen = false
        var field: String? = null
        var fieldDepth = 0
        val value = StringBuilder()
        while (true) {
            when (parser.nextToken()) {
                XmlPullParser.END_DOCUMENT -> break
                XmlPullParser.DOCDECL -> throw IOException("WebDAV DTD is forbidden")
                XmlPullParser.START_TAG -> {
                    if (parser.depth > MAX_DEPTH) throw IOException("WebDAV XML nesting limit exceeded")
                    if (parser.depth == 1) {
                        if (parser.namespace != DAV || parser.name != "multistatus") {
                            throw IOException("Expected DAV multistatus")
                        }
                        rootSeen = true
                    }
                    if (parser.namespace == DAV) {
                        when {
                            parser.name == "response" && parser.depth == 2 -> {
                                if (responses.size >= MAX_RESPONSES) throw IOException("Too many DAV responses")
                                response = ResponseBuilder()
                            }
                            parser.name == "propstat" && parser.depth == 3 -> response?.beginProperties()
                            parser.name == "collection" && parser.depth == 6 -> response?.collection = true
                            response != null && isField(parser.name, parser.depth) -> {
                                field = parser.name
                                fieldDepth = parser.depth
                                value.setLength(0)
                            }
                        }
                    }
                }
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> {
                    val text = parser.text ?: throw IOException("Unresolved WebDAV XML entity")
                    if (field != null) {
                        if (value.length + text.length > MAX_VALUE) throw IOException("DAV property is too large")
                        value.append(text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (field != null && parser.depth == fieldDepth) {
                        response?.field(field, value.toString().trim(), fieldDepth)
                        field = null
                    }
                    if (parser.namespace == DAV) {
                        when {
                            parser.name == "propstat" && parser.depth == 3 -> response?.endProperties()
                            parser.name == "response" && parser.depth == 2 -> {
                                responses += requireNotNull(response).build()
                                response = null
                            }
                        }
                    }
                }
            }
        }
        if (!rootSeen) throw IOException("Missing DAV multistatus")
        return responses
    }

    private fun isField(name: String, depth: Int): Boolean =
        (name == "href" && depth == 3) ||
            (name == "status" && depth in 3..4) ||
            (depth == 5 && name in setOf("getcontentlength", "getlastmodified", "getcontenttype"))

    private class ResponseBuilder {
        var collection = false
        private var href: String? = null
        private var status: String? = null
        private var responseStatus: String? = null
        private var directory = false
        private var hasProperties = false
        private val pending = mutableMapOf<String, String>()
        private val properties = mutableMapOf<String, String>()

        fun beginProperties() {
            pending.clear()
            collection = false
            status = null
        }

        fun field(name: String, value: String, depth: Int) {
            when (name) {
                "href" -> href = value
                "status" -> if (depth == 3) responseStatus = value else status = value
                else -> pending[name] = value
            }
        }

        fun endProperties() {
            if ((status?.split(' ')?.getOrNull(1)?.toIntOrNull() ?: 0) in 200..299) {
                hasProperties = true
                properties.putAll(pending)
                directory = directory || collection
            }
        }

        fun build(): Response {
            if (!hasProperties || responseStatus?.split(' ')?.getOrNull(1)?.let { it != "200" } == true) {
                throw IOException("DAV resource metadata unavailable")
            }
            return Response(href?.takeIf { it.isNotEmpty() } ?: throw IOException("Missing DAV href"), properties, directory)
        }
    }

    private class BoundedInput(input: InputStream) : FilterInputStream(input) {
        private var count = 0

        override fun read(): Int = super.read().also { if (it >= 0) consumed(1) }

        override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
            val read = `in`.read(bytes, offset, minOf(length, MAX_BYTES - count + 1))
            if (read > 0) consumed(read)
            return read
        }

        private fun consumed(bytes: Int) {
            count += bytes
            if (count > MAX_BYTES) throw IOException("WebDAV XML exceeds $MAX_BYTES bytes")
        }
    }
}
