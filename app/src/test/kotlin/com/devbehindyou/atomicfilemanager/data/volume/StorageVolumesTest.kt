package com.devbehindyou.atomicfilemanager.data.volume

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.storage.StorageManager
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowEnvironment
import org.robolectric.shadows.ShadowStatFs
import org.robolectric.shadows.StorageVolumeBuilder
import java.io.File

/** Real platform StorageManager enumeration with API-specific permission and directory discovery. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 34])
class StorageVolumesTest {
    @get:Rule val temporary = TemporaryFolder()
    private lateinit var context: VolumeContext
    private lateinit var manager: StorageManager

    @Before
    fun setUp() {
        context = VolumeContext()
        manager = checkNotNull(context.getSystemService(StorageManager::class.java))
        shadowOf(manager).resetStorageVolumeList()
        if (Build.VERSION.SDK_INT >= 30) ShadowEnvironment.addExternalDir("volume-access-fixture")
        setGranted(true)
    }

    @Test
    fun readWriteAndReadOnlyVolumesAreMountedAndOnlyThoseContributeToUsage() =
        runBlocking(Dispatchers.IO) {
            val readWrite = temporary.newFolder("read-write").canonicalFile
            val readOnly = temporary.newFolder("read-only").canonicalFile
            val removed = temporary.newFolder("removed").canonicalFile
            addVolume("rw", readWrite, Environment.MEDIA_MOUNTED, primary = true, blocks = 1_000, available = 200)
            addVolume("ro", readOnly, Environment.MEDIA_MOUNTED_READ_ONLY, blocks = 2_000, available = 300)
            addVolume("removed", removed, Environment.MEDIA_UNMOUNTED, blocks = 500, available = 50)
            // Deliberately reverse the platform's volume order to check API 27's real manager mapping.
            context.externalDirectories = listOf(appDirectory(readOnly), appDirectory(removed), appDirectory(readWrite))

            val volumes = StorageVolumes.enumerate(context).associateBy { it.id }
            assertTrue(checkNotNull(volumes["rw"]).isMounted)
            val card = checkNotNull(volumes["ro"])
            assertTrue(card.isMounted)
            assertFalse(card.requiresGrant)
            assertEquals(readOnly, rootFile(checkNotNull(card.rootNodeId).raw))
            assertEquals(2_000L * ShadowStatFs.BLOCK_SIZE, card.totalBytes)
            assertEquals(300L * ShadowStatFs.BLOCK_SIZE, card.freeBytes)
            assertFalse(checkNotNull(volumes["removed"]).isMounted)

            val plan =
                LocalStorageUsageSource(primaryPath = { context.filesDir.absolutePath })
                    .plan(volumes.values.toList())
            assertEquals(2, plan.capacity.mountedVolumes)
            assertEquals(3_000L * ShadowStatFs.BLOCK_SIZE, plan.capacity.totalBytes)
            assertEquals(500L * ShadowStatFs.BLOCK_SIZE, plan.capacity.freeBytes)
            assertEquals(setOf(readWrite.path, readOnly.path), plan.roots.toSet())
        }

    @Test
    fun readOnlyMountDoesNotBypassTheExistingSharedStorageGrant() =
        runBlocking(Dispatchers.IO) {
            val root = temporary.newFolder("read-only-denied").canonicalFile
            addVolume("ro", root, Environment.MEDIA_MOUNTED_READ_ONLY, blocks = 2_000, available = 300)
            context.externalDirectories = listOf(appDirectory(root))
            setGranted(false)

            val card = StorageVolumes.enumerate(context).single()
            assertTrue(card.isMounted)
            assertTrue(card.requiresGrant)
            assertNull(card.rootNodeId)
            // Native capacity may be known even when listing access has not been granted.
            assertEquals(2_000L * ShadowStatFs.BLOCK_SIZE, card.totalBytes)
            val plan = LocalStorageUsageSource(primaryPath = { context.filesDir.absolutePath }).plan(listOf(card))
            assertEquals(1, plan.capacity.mountedVolumes)
            assertEquals(0, plan.capacity.unknownVolumes)
            assertEquals(1, plan.unavailableRoots)
            assertTrue(plan.roots.isEmpty())
        }

    @Test
    fun legacyMissingDirectoryMappingIsUnavailableWhileModernDirectoryDiscoveryRemainsSupported() {
        val root = temporary.newFolder("not-in-app-directory-list").canonicalFile
        addVolume("ro", root, Environment.MEDIA_MOUNTED_READ_ONLY, blocks = 2_000, available = 300)
        context.externalDirectories = emptyList()

        val card = StorageVolumes.enumerate(context).single()
        assertTrue(card.isMounted)
        if (Build.VERSION.SDK_INT < 30) {
            assertTrue(card.requiresGrant)
            assertNull(card.rootNodeId)
            assertEquals(0L, card.totalBytes)
            assertEquals(0L, card.freeBytes)
        } else {
            assertFalse(card.requiresGrant)
            assertEquals(root, rootFile(checkNotNull(card.rootNodeId).raw))
            assertEquals(2_000L * ShadowStatFs.BLOCK_SIZE, card.totalBytes)
        }
    }

    @Test
    fun mountedReadOnlyVolumeWithAnUnreadableDirectoryRemainsExplicitlyUnavailable() {
        val missing = File(temporary.root, "missing").canonicalFile
        addVolume("ro", missing, Environment.MEDIA_MOUNTED_READ_ONLY, blocks = 2_000, available = 300)
        context.externalDirectories = listOf(appDirectory(missing))

        val card = StorageVolumes.enumerate(context).single()
        assertTrue(card.isMounted)
        assertTrue(card.requiresGrant)
        assertNull(card.rootNodeId)
        assertEquals(0L, card.totalBytes)
        assertEquals(0L, card.freeBytes)
    }

    private fun addVolume(
        id: String,
        root: File,
        state: String,
        primary: Boolean = false,
        blocks: Int,
        available: Int,
    ) {
        val volume =
            StorageVolumeBuilder(id, root, id, Process.myUserHandle(), state)
                .setFsUuid(id)
                .setIsPrimary(primary)
                .setIsRemovable(!primary)
                .setIsEmulated(primary)
                .build()
        shadowOf(manager).addStorageVolume(volume)
        ShadowStatFs.registerStats(root, blocks, available, available)
    }

    private fun setGranted(granted: Boolean) {
        context.readPermission = if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        if (Build.VERSION.SDK_INT >= 30) {
            shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(
                "android:manage_external_storage",
                context.applicationInfo.uid,
                context.packageName,
                if (granted) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_ERRORED,
            )
        }
        assertEquals(granted, SharedStorageAccess.isGranted(context))
    }

    private fun appDirectory(root: File): File =
        AndroidPathFile(File(root, "Android/data/${context.packageName}/files"))

    private fun rootFile(raw: String): File = File(raw.removePrefix("file:")).canonicalFile

    private class VolumeContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        var readPermission = PackageManager.PERMISSION_DENIED
        var externalDirectories = emptyList<File>()

        override fun getExternalFilesDirs(type: String?): Array<File> = externalDirectories.toTypedArray()

        override fun checkPermission(
            permission: String,
            pid: Int,
            uid: Int,
        ): Int =
            if (permission == Manifest.permission.READ_EXTERNAL_STORAGE) {
                readPermission
            } else {
                super.checkPermission(permission, pid, uid)
            }
    }

    /** Keep Android's suffix separators on Windows while the real manager still sees native absolute paths. */
    private class AndroidPathFile(private val native: File) : File(native.path) {
        override fun getPath(): String = native.path.replace('\\', '/')

        override fun getAbsolutePath(): String = native.absolutePath
    }
}
