package com.devbehindyou.atomicfilemanager.data.preview

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File

/**
 * Test-only URI boundary for owned export contracts on JVM hosts (including Windows).
 * Copying, progress, cleanup and launch construction remain real; Android FileProvider
 * path mapping and URI grants require their separate native/device contracts.
 */
@Implements(FileProvider::class, isInAndroidSdk = false)
class OwnedExportFileProviderShadow {
    companion object {
        @JvmStatic
        @Implementation
        fun getUriForFile(
            context: Context,
            authority: String,
            file: File,
        ): Uri {
            val owned = file.canonicalFile
            require(authority == "${context.packageName}.fileprovider")
            require(owned.parentFile == File(context.cacheDir, "external-preview").canonicalFile)
            require(owned.name.startsWith("open-") && owned.isFile && owned.canRead())
            val uri =
                Uri.Builder().scheme("content").authority(authority)
                    .appendPath("owned-export-fixture").appendPath(owned.name).build()
            shadowOf(context.contentResolver).registerInputStreamSupplier(uri) { owned.inputStream() }
            return uri
        }
    }
}
