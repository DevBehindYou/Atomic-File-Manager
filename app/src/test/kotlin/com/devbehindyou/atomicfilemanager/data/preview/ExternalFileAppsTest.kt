package com.devbehindyou.atomicfilemanager.data.preview

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 34], application = Application::class)
class ExternalFileAppsTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Test
    fun onlyEnabledExportedDistinctExternalComponentsAreOffered() {
        val valid = handler("reader.app", "Reader")
        val manager = shadowOf(app.packageManager)
        // Robolectric 4.14's override-intent comparator ignores MIME. Installed packages with
        // actual filters exercise type matching instead of making a PDF override match video.
        listOf(
            valid,
            handler(app.packageName, "Atomic"),
            handler("private.app", "Private", exported = false),
            handler("disabled.app", "Disabled", enabled = false),
        ).forEach { activity ->
            manager.installPackage(
                PackageInfo().apply {
                    packageName = activity.packageName
                    applicationInfo = activity.applicationInfo
                    activities = arrayOf(activity)
                },
            )
            manager.addIntentFilterForActivity(ComponentName(activity.packageName, activity.name), pdfFilter())
        }
        // Two matching manifest filters must still offer this component only once.
        manager.addIntentFilterForActivity(ComponentName(valid.packageName, valid.name), pdfFilter())
        assertEquals(
            listOf(ExternalFileApp("reader.app/reader.app.View", "Reader")),
            ExternalFileApps(app).list("application/pdf"),
        )
        assertTrue(ExternalFileApps(app).list("video/mp4").isEmpty())
    }

    @Test
    fun explicitExternalLaunchUsesOnlyTemporaryReadAccessAndTheSelectedComponent() {
        val uri = Uri.parse("content://fixture/document/report")
        ExternalFileApps(app).launch(
            ExternalPreviewFile(uri, "application/pdf"),
            "report.pdf",
            FileOpenTarget(FileOpenMode.EXTERNAL, "reader.app/reader.app.View", "Reader"),
            "Open with",
        )
        val launched = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, launched.action)
        assertEquals(uri, launched.data)
        assertEquals(uri, launched.clipData!!.getItemAt(0).uri)
        assertEquals(ComponentName("reader.app", "reader.app.View"), launched.component)
        assertTrue(launched.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, launched.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @Test
    fun genericExternalPreferenceUsesARealSystemChooserWithoutInventingAnAppIdentity() {
        val uri = Uri.parse("content://fixture/document/report")
        ExternalFileApps(app).launch(
            ExternalPreviewFile(uri, "application/pdf"),
            "report.pdf",
            FileOpenTarget(FileOpenMode.EXTERNAL),
            "Open with",
        )
        val chooser = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION") // Typed getParcelableExtra needs API33; this fixture also runs API27.
        val actual = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(null, actual.component)
        assertEquals(uri, actual.data)
        assertEquals(0, actual.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    private fun handler(
        packageName: String,
        label: String,
        enabled: Boolean = true,
        exported: Boolean = true,
    ) = ActivityInfo().apply {
        nonLocalizedLabel = label
        this.packageName = packageName
        name = "$packageName.View"
        this.enabled = enabled
        this.exported = exported
        applicationInfo =
            ApplicationInfo().apply {
                this.packageName = packageName
                this.enabled = true
            }
    }

    private fun pdfFilter() =
        IntentFilter(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addDataScheme("content")
            addDataType("application/pdf")
        }
}
