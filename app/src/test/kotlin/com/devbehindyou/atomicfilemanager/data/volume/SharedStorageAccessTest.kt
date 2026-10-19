package com.devbehindyou.atomicfilemanager.data.volume

import android.Manifest
import android.app.AppOpsManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 29])
class SharedStorageAccessTest {
    @Test
    fun legacyAccessUsesReadPermission() {
        val context = PermissionContext()

        assertFalse(SharedStorageAccess.isGranted(context))
        context.readPermission = PackageManager.PERMISSION_GRANTED
        assertTrue(SharedStorageAccess.isGranted(context))
        context.readPermission = PackageManager.PERMISSION_DENIED
        assertFalse(SharedStorageAccess.isGranted(context))
        assertEquals(List(3) { Manifest.permission.READ_EXTERNAL_STORAGE }, context.checkedPermissions)
    }

    @Test
    fun permissionCheckFailurePropagatesToTheCaller() {
        val failure = SecurityException("permission service unavailable")
        val context =
            object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
                override fun checkPermission(
                    permission: String,
                    pid: Int,
                    uid: Int,
                ): Int = throw failure
            }

        try {
            SharedStorageAccess.isGranted(context)
            fail("The adapter must leave failure handling to its caller")
        } catch (actual: SecurityException) {
            assertSame(failure, actual)
        }
    }

    @Test
    @Config(sdk = [30, 34])
    fun modernAccessUsesAllFilesAppOpInsteadOfReadPermission() {
        val context = PermissionContext()
        context.readPermission = PackageManager.PERMISSION_GRANTED
        setAllFilesAccess(context, AppOpsManager.MODE_ERRORED)
        assertFalse(SharedStorageAccess.isGranted(context))

        context.readPermission = PackageManager.PERMISSION_DENIED
        setAllFilesAccess(context, AppOpsManager.MODE_ALLOWED)
        assertTrue(SharedStorageAccess.isGranted(context))
        assertTrue(context.checkedPermissions.isEmpty())
    }

    @Test
    fun legacyRequestUsesTheCallerLauncherWithoutOpeningSettings() {
        val context = SettingsContext()
        var legacyRequests = 0

        SharedStorageAccess.request(context) { legacyRequests++ }

        assertEquals(1, legacyRequests)
        assertTrue(context.started.isEmpty())
    }

    @Test
    @Config(sdk = [30, 34])
    fun modernRequestOpensAppSettingsWithoutAssumingAccessWasGranted() {
        val context = SettingsContext()
        setAllFilesAccess(context, AppOpsManager.MODE_ERRORED)
        var legacyRequests = 0

        SharedStorageAccess.request(context) { legacyRequests++ }

        assertEquals(0, legacyRequests)
        assertEquals(1, context.started.size)
        assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, context.started.single().action)
        assertEquals("package:${context.packageName}", context.started.single().dataString)
        assertFalse(SharedStorageAccess.isGranted(context))
    }

    @Test
    @Config(sdk = [30, 34])
    fun unavailableAppSettingsFallsBackToGlobalSettings() {
        val context = SettingsContext(failures = 1)
        var legacyRequests = 0

        SharedStorageAccess.request(context) { legacyRequests++ }

        assertEquals(0, legacyRequests)
        assertEquals(
            listOf(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
            ),
            context.started.map { it.action },
        )
        assertNull(context.started.last().data)
    }

    @Test
    @Config(sdk = [30, 34])
    fun unavailableSettingsFallsBackToTheCallerLauncherOnce() {
        val context = SettingsContext(failures = 2)
        var legacyRequests = 0

        SharedStorageAccess.request(context) { legacyRequests++ }

        assertEquals(1, legacyRequests)
        assertEquals(
            listOf(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
            ),
            context.started.map { it.action },
        )
    }

    @Test
    @Config(sdk = [27, 29, 30, 34])
    fun callerLauncherFailurePropagatesWithoutAnotherAttempt() {
        val context = SettingsContext(failures = 2)
        val failure = IllegalStateException("caller launcher unavailable")
        var requests = 0
        try {
            SharedStorageAccess.request(context) {
                requests++
                throw failure
            }
            fail("The adapter must not swallow a caller launcher failure")
        } catch (actual: IllegalStateException) {
            assertSame(failure, actual)
        }
        assertEquals(1, requests)
    }

    private fun setAllFilesAccess(
        context: Context,
        mode: Int,
    ) {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        shadowOf(appOps).setMode(
            AppOpsManager.OPSTR_MANAGE_EXTERNAL_STORAGE,
            context.applicationInfo.uid,
            context.packageName,
            mode,
        )
    }

    private class PermissionContext : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        var readPermission = PackageManager.PERMISSION_DENIED
        val checkedPermissions = mutableListOf<String>()

        override fun checkPermission(
            permission: String,
            pid: Int,
            uid: Int,
        ): Int {
            checkedPermissions += permission
            return if (permission == Manifest.permission.READ_EXTERNAL_STORAGE) {
                readPermission
            } else {
                super.checkPermission(permission, pid, uid)
            }
        }
    }

    private class SettingsContext(private val failures: Int = 0) :
        ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += intent
            if (started.size <= failures) throw ActivityNotFoundException("Settings activity unavailable")
        }
    }
}
