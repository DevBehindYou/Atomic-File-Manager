package com.devbehindyou.atomicfilemanager.data.backend.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WebDavPathsTest {
    @Test
    fun `raw spaces and Unicode match encoded metadata without double encoding`() {
        assertTrue(WebDavPaths.sameResource("/a b/日本語", "/a%20b/%E6%97%A5%E6%9C%AC%E8%AA%9E"))
        assertEquals("/a%20b/%E6%97%A5", WebDavPaths.encode("/a%20b/%e6%97%a5"))
    }

    @Test
    fun `encoded separators and percent remain distinct resource identities`() {
        assertFalse(WebDavPaths.sameResource("/a%2Fb", "/a/b"))
        assertFalse(WebDavPaths.sameResource("/a%252Fb", "/a%2Fb"))
        assertEquals("/a%252Fb", WebDavPaths.encode("/a%252Fb"))
    }

    @Test
    fun `unreserved escapes and collection suffix have canonical identity`() {
        assertTrue(WebDavPaths.sameResource("/%7euser/a%62/", "/~user/ab"))
        assertFalse(WebDavPaths.sameResource("/folder", "/folder2"))
    }

    @Test
    fun `invalid percent escapes and URL delimiters are escaped as path content`() {
        assertEquals("/a%25invalid%3Fq%23fragment", WebDavPaths.encode("/a%invalid?q#fragment"))
    }
}
