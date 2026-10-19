package com.devbehindyou.atomicfilemanager.data.backend.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27])
class WebDavXmlTest {
    @Test
    fun `parses namespaces and successful properties on minimum supported API`() {
        val result =
            parse(
                """
                <d:multistatus xmlns:d="DAV:"><d:response><d:href>/files/a%20b/</d:href>
                <d:propstat><d:prop><d:getcontentlength>9</d:getcontentlength>
                <d:resourcetype><d:collection/></d:resourcetype></d:prop>
                <d:status>HTTP/1.1 200 OK</d:status></d:propstat>
                <d:propstat><d:prop><d:getcontentlength>999</d:getcontentlength></d:prop>
                <d:status>HTTP/1.1 404 Not Found</d:status></d:propstat>
                </d:response></d:multistatus>
                """.trimIndent(),
            ).single()
        assertEquals("/files/a%20b/", result.href)
        assertEquals("9", result.properties["getcontentlength"])
        assertTrue(result.directory)
    }

    @Test
    fun `rejects DTD with external and internal entities`() {
        for (declaration in listOf("<!ENTITY x SYSTEM 'file:///unread-fixture'>", "<!ENTITY x 'expanded'>")) {
            assertThrows(Exception::class.java) {
                parse("<!DOCTYPE multistatus [$declaration]><multistatus xmlns='DAV:'>&x;</multistatus>")
            }
        }
    }

    @Test
    fun `rejects oversized body including ignored XML content`() {
        assertThrows(Exception::class.java) {
            parse("<multistatus xmlns='DAV:'><!--${"x".repeat(WebDavXml.MAX_BYTES)}--></multistatus>")
        }
    }

    @Test
    fun `rejects excessive depth and property size`() {
        assertThrows(Exception::class.java) {
            parse("<multistatus xmlns='DAV:'>${"<x>".repeat(33)}${"</x>".repeat(33)}</multistatus>")
        }
        assertThrows(Exception::class.java) {
            parse("<multistatus xmlns='DAV:'><response><href>${"x".repeat(65_537)}</href></response></multistatus>")
        }
    }

    @Test
    fun `rejects malformed metadata and failed resource responses`() {
        for (xml in listOf(
            "<html/>",
            "<multistatus xmlns='DAV:'><response><href>/lost</href>" +
                "<status>HTTP/1.1 403 Forbidden</status></response></multistatus>",
            "<multistatus xmlns='DAV:'><response>",
        )) {
            assertThrows(Exception::class.java) { parse(xml) }
        }
    }

    @Test
    fun `requires root closing tag even after a complete valid response`() {
        val response =
            "<response><href>/file</href><propstat><prop><getcontentlength>1</getcontentlength></prop>" +
                "<status>HTTP/1.1 200 OK</status></propstat></response>"
        for (tail in listOf("", "<response>", "<response><href>/partial", response)) {
            assertThrows(Exception::class.java) { parse("<multistatus xmlns='DAV:'>$tail") }
        }
    }

    @Test
    fun `accepts complete empty listing and rejects content after its root`() {
        val emptyListing = "<multistatus xmlns='DAV:'/>"
        assertTrue(parse(emptyListing).isEmpty())
        assertThrows(Exception::class.java) { parse("$emptyListing$emptyListing") }
        assertThrows(Exception::class.java) { parse("${emptyListing}trailing data") }
    }

    private fun parse(xml: String) = WebDavXml.parse(xml.byteInputStream())
}
