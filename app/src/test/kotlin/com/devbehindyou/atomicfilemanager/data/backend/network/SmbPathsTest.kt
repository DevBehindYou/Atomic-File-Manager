package com.devbehindyou.atomicfilemanager.data.backend.network

import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.NetworkProtocol
import com.devbehindyou.atomicfilemanager.domain.model.NetworkServerConfig
import com.hierynomus.mserref.NtStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SmbPathsTest {
    @Test
    fun `the first part of the path is the share`() {
        val p = SmbPaths.parse(FileNodeId.smb("nas", "/Photos/2024/trip.jpg"))
        assertEquals("nas", p.serverId)
        assertEquals("Photos", p.share)
        assertEquals("2024\\trip.jpg", p.inner)
        assertEquals("trip.jpg", p.name)
    }

    @Test
    fun `share roots and server roots`() {
        val share = SmbPaths.parse(FileNodeId.smb("nas", "/Photos/"))
        assertEquals("Photos", share.share)
        assertEquals("", share.inner)
        assertEquals("Photos", share.name)

        val server = SmbPaths.parse(FileNodeId.smb("nas", "/"))
        assertNull(server.share)
    }

    @Test
    fun `metadata parents terminate at the share root and preserve child identities`() {
        val share = SmbPaths.parse(FileNodeId.smb("nas", "/Photos/"))
        assertNull(share.parentId)
        assertNull(SmbPaths.parse(FileNodeId.smb("nas", "/")).parentId)
        val album = share.child("Summer 2026")
        assertEquals(share.id(), album.parentId)
        assertEquals(album.id(), album.child("photo.jpg").parentId)
    }

    @Test
    fun `ids survive child, parent and back`() {
        val share = SmbPaths.parse(FileNodeId.smb("nas", "/Docs"))
        val file = share.child("a").child("b.txt")
        assertEquals(FileNodeId.smb("nas", "/Docs/a/b.txt"), file.id())
        assertEquals(file, SmbPaths.parse(file.id()))
        assertEquals(FileNodeId.smb("nas", "/Docs/a"), file.parent().id())
        assertEquals(FileNodeId.smb("nas", "/Docs"), file.parent().parent().id())
    }

    @Test
    fun `backslashes and dot parts are tidied`() {
        val p = SmbPaths.parse(FileNodeId.smb("nas", "/Docs\\a/./b"))
        assertEquals("Docs", p.share)
        assertEquals("a\\b", p.inner)
    }

    @Test
    fun `domain can be written either way`() {
        assertEquals("ana" to "OFFICE", SmbPaths.splitUser("OFFICE\\ana"))
        assertEquals("ana" to "office.local", SmbPaths.splitUser("ana@office.local"))
        assertEquals("ana" to null, SmbPaths.splitUser("ana"))
    }

    @Test
    fun `anonymous servers sign in as guest`() {
        val server = NetworkServerConfig("s", "NAS", NetworkProtocol.SMB, "nas.local", anonymous = true)
        assertEquals("Guest", SmbPaths.auth(server, null).username)
        val named = server.copy(anonymous = false, username = "HOME\\ana")
        val ctx = SmbPaths.auth(named, "pw")
        assertEquals("ana", ctx.username)
        assertEquals("HOME", ctx.domain)
    }

    @Test
    fun `server statuses become clear errors`() {
        assertTrue(SmbPaths.errorFor(NtStatus.STATUS_OBJECT_NAME_NOT_FOUND, "NAS", "x") is FileError.FileNotFound)
        assertTrue(SmbPaths.errorFor(NtStatus.STATUS_LOGON_FAILURE, "NAS", null) is FileError.AccessDenied)
        assertTrue(SmbPaths.errorFor(NtStatus.STATUS_OBJECT_NAME_COLLISION, "NAS", "x") is FileError.FileAlreadyExists)
        assertTrue(SmbPaths.errorFor(NtStatus.STATUS_BAD_NETWORK_NAME, "NAS", null) is FileError.AccessDenied)
        assertTrue(SmbPaths.errorFor(NtStatus.STATUS_INTERNAL_ERROR, "NAS", null) is FileError.IoFailure)
    }
}
