package com.devbehindyou.atomicfilemanager.ui.screens

import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileError
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.ui.components.BreadcrumbItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BrowseBreadcrumbsTest {
    @Test
    fun filesystemPathKeepsExistingBreadcrumbs() = runTest {
        val folder = folder(FileNodeId.file("/storage/emulated/0/Download"), "Downloads")
        val crumbs = resolve(folder)
        assertEquals(listOf("storage", "emulated", "0", "Download"), crumbs.map { it.name })
        assertEquals("file:/storage/emulated/0", crumbs[2].path)
        assertEquals(folder.id.raw, crumbs.last().path)
    }

    @Test
    fun unknownSafParentNeverSplitsOrDecodesDocumentIdentity() = runTest {
        val folder = folder(FileNodeId.saf("content://provider/tree/primary%3ARoot/document/opaque/a%2Fb"), "Reports")
        assertEquals(listOf(BreadcrumbItem("Reports", folder.id.raw)), resolve(folder))
    }

    @Test
    fun visitedSafTrailRetainsOpaqueParentAndRefreshesLabel() = runTest {
        val root = folder(FileNodeId.saf("content://provider/tree/root%2Fid"), "Cloud")
        val child = folder(FileNodeId.saf("content://provider/document/unrelated%2Ftoken"), "Renamed")
        val visited = listOf(BreadcrumbItem(root.displayName, root.id.raw), BreadcrumbItem("Old", child.id.raw))
        val crumbs = resolveBrowseBreadcrumbs(child.id, child, visited) { error("Visited ancestry must not need a parent lookup") }
        assertEquals(listOf(root.id.raw, child.id.raw), crumbs.map { it.path })
        assertEquals("Renamed", crumbs.last().name)
        val back = resolveBrowseBreadcrumbs(root.id, root, crumbs) { error("No parent lookup") }
        assertEquals(listOf(crumbs.first()), back)
    }

    @Test
    fun networkBreadcrumbsUseActualResolvedParentsForEveryProtocol() = runTest {
        val factories: List<(String, String) -> FileNodeId> = listOf(
            { server, path -> FileNodeId.sftp(server, path) },
            { server, path -> FileNodeId.ftp(server, path) },
            { server, path -> FileNodeId.ftps(server, path) },
            { server, path -> FileNodeId.smb(server, path) },
            { server, path -> FileNodeId.webdav(server, path) },
        )
        for (factory in factories) {
            val root = folder(factory("server-id", "/"), "Server")
            val parent = folder(factory("server-id", "/a%2Fb"), "Parent with slash /", root.id)
            val child = folder(factory("server-id", "/a%2Fb/c:d"), "Child", parent.id)
            val crumbs = resolve(child, mapOf(root.id to root, parent.id to parent))
            assertEquals(listOf(root.id.raw, parent.id.raw, child.id.raw), crumbs.map { it.path })
            assertEquals(listOf("Server", "Parent with slash /", "Child"), crumbs.map { it.name })
        }
    }

    @Test
    fun unknownNetworkParentShowsCurrentFolderOnly() = runTest {
        val folder = folder(FileNodeId.webdav("server", "/deep/path/with%2Fencoding"), "Direct link")
        assertEquals(listOf(BreadcrumbItem(folder.displayName, folder.id.raw)), resolve(folder))
    }

    @Test
    fun unavailableParentNeverCreatesAnUnresolvedCrumb() = runTest {
        val folder = folder(FileNodeId.sftp("server", "/a/b"), "B", FileNodeId.sftp("server", "/a"))
        assertEquals(listOf(BreadcrumbItem(folder.displayName, folder.id.raw)), resolve(folder))
    }

    @Test
    fun cyclicParentsStopWithoutDuplicatingCurrentFolder() = runTest {
        val firstId = FileNodeId.webdav("server", "/one")
        val secondId = FileNodeId.webdav("server", "/two")
        val first = folder(firstId, "One", secondId)
        val second = folder(secondId, "Two", firstId)
        assertEquals(listOf(secondId.raw, firstId.raw), resolve(first, mapOf(secondId to second)).map { it.path })
    }

    @Test
    fun cancellationWhileResolvingParentPropagates() {
        assertThrows(CancellationException::class.java) {
            runTest {
                val folder = folder(FileNodeId.ftp("server", "/a"), "A", FileNodeId.ftp("server", "/"))
                resolveBrowseBreadcrumbs(folder.id, folder, emptyList()) { throw CancellationException("Cancelled lookup") }
            }
        }
    }

    private suspend fun resolve(folder: FileNode, parents: Map<FileNodeId, FileNode> = emptyMap()): List<BreadcrumbItem> =
        resolveBrowseBreadcrumbs(folder.id, folder, emptyList()) { id ->
            parents[id]?.let { FileResult.Success(it) } ?: FileResult.Failure(FileError.FileNotFound(id.raw))
        }
}

internal fun folder(id: FileNodeId, name: String, parentId: FileNodeId? = null): FileNode =
    FileNode(id, name, name, null, -1, 0, true, false, parentId, StorageType.VIRTUAL, AccessFlags.FULL, null, null)
