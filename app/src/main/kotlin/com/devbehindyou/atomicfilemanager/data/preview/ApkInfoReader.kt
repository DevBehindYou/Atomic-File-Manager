package com.devbehindyou.atomicfilemanager.data.preview

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What an APK file says about itself (ALL_IN_ONE_PLAN.md 1.5, FR-8.7). */
data class ApkInfo(
    val label: String,
    val packageName: String,
    val versionName: String?,
    val versionCode: Long,
    /** Null on API 23 and lower, where the platform doesn't report it. */
    val minSdk: Int?,
    val targetSdk: Int,
    val permissionCount: Int,
    val icon: Bitmap?,
)

/**
 * Reads an APK on local storage with [PackageManager.getPackageArchiveInfo]. Nothing is installed
 * and the APK's code never runs. Returns null for a damaged or non-APK file.
 */
class ApkInfoReader(
    private val context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun read(path: String): ApkInfo? =
        withContext(io) {
            runCatching {
                val pm = context.packageManager
                val info = archiveInfo(pm, path) ?: return@runCatching null
                val app = info.applicationInfo ?: return@runCatching null
                // Without these the label and icon come back as the package name and a default icon.
                app.sourceDir = path
                app.publicSourceDir = path
                ApkInfo(
                    label = app.loadLabel(pm).toString(),
                    packageName = info.packageName,
                    versionName = info.versionName,
                    versionCode = PackageInfoCompat.getLongVersionCode(info),
                    minSdk = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) app.minSdkVersion else null,
                    targetSdk = app.targetSdkVersion,
                    permissionCount = info.requestedPermissions?.size ?: 0,
                    icon = runCatching { app.loadIcon(pm).render(ICON_PX) }.getOrNull(),
                )
            }.getOrNull()
        }

    /**
     * True when the app this APK installs is already on the phone (smart cleanup, ALL_IN_ONE_PLAN.md
     * 2.6). Only apps visible through the manifest's launcher `<queries>` can be seen, so a hidden
     * app is never suggested: the safe direction.
     */
    suspend fun isInstalled(path: String): Boolean =
        withContext(io) {
            runCatching {
                val pm = context.packageManager
                val name = archiveInfo(pm, path)?.packageName ?: return@runCatching false
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(name, PackageManager.PackageInfoFlags.of(0))
                } else {
                    pm.getPackageInfo(name, 0)
                }
                true
            }.getOrDefault(false)
        }

    private fun archiveInfo(
        pm: PackageManager,
        path: String,
    ): PackageInfo? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageArchiveInfo(path, PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageArchiveInfo(path, PackageManager.GET_PERMISSIONS)
        }

    private fun Drawable.render(size: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        setBounds(0, 0, size, size)
        draw(Canvas(bitmap))
        return bitmap
    }

    private companion object {
        const val ICON_PX = 144
    }
}

/** "Android 8.1 (API 27)" for the API levels this app can meet; plain "API n" otherwise. */
fun androidVersionName(api: Int): String {
    val name =
        when (api) {
            in 1..20 -> null
            21 -> "5.0"
            22 -> "5.1"
            23 -> "6"
            24 -> "7.0"
            25 -> "7.1"
            26 -> "8.0"
            27 -> "8.1"
            28 -> "9"
            29 -> "10"
            30 -> "11"
            31 -> "12"
            32 -> "12L"
            33 -> "13"
            34 -> "14"
            35 -> "15"
            36 -> "16"
            else -> null
        }
    return if (name == null) "API $api" else "Android $name (API $api)"
}
