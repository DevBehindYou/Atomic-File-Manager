package com.devbehindyou.atomicfilemanager.data.preview

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.test.core.app.ApplicationProvider
import com.devbehindyou.atomicfilemanager.domain.model.AccessFlags
import com.devbehindyou.atomicfilemanager.domain.model.FileNode
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenFamily
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenMode
import com.devbehindyou.atomicfilemanager.domain.model.FileOpenTarget
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import com.devbehindyou.atomicfilemanager.domain.model.StorageType
import com.devbehindyou.atomicfilemanager.domain.model.fileOpenFamily
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/** Exercise Android's actual MIME filter matching, not Robolectric's MIME-blind intent overrides. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [27, 34], application = Application::class)
class ExternalMimeDiscoveryTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val uri = Uri.parse("content://fixture/tree/root/document/report")

    @Test
    fun malformedPdfMetadataStillDiscoversAndLaunchesThePdfReader() =
        assertReader("report.pdf", "application/", "application/pdf", FileOpenFamily.PDF, "pdf.reader")

    @Test
    fun wildcardMetadataDoesNotBroadenReaderDiscoveryForAPng() =
        assertReader("picture.png", "*/*", "image/png", FileOpenFamily.IMAGE, "png.reader")

    @Test
    fun oversizedMetadataFallsBackToThePngExtension() =
        assertReader("picture.png", "application/" + "a".repeat(129), "image/png", FileOpenFamily.IMAGE, "png.reader")

    @Test
    fun validConflictingMimeRemainsAuthoritativeAndIsNormalized() =
        assertReader("report.pdf", " IMAGE/PNG ; charset=binary", "image/png", FileOpenFamily.IMAGE, "png.reader")

    @Test
    fun genericMetadataUsesTheKnownExtension() =
        assertReader("report.pdf", "application/octet-stream", "application/pdf", FileOpenFamily.PDF, "pdf.reader")

    @Test
    fun unknownExtensionWithMalformedMetadataKeepsTheGenericSystemChooser() =
        runBlocking {
            registerReaders()
            val node = node("unknown.atomic_unknown", "application/")
            assertEquals(FileOpenFamily.OTHER, fileOpenFamily(node))
            val result =
                helper().open(node) { prepared ->
                    assertTrue(ExternalFileApps(app).list(prepared.mimeType).isEmpty())
                    ExternalFileApps(app).launch(
                        prepared,
                        node.name,
                        FileOpenTarget(FileOpenMode.EXTERNAL),
                        "Open with",
                    )
                }
            assertTrue(result is FileResult.Success)
            val chooser = shadowOf(app).nextStartedActivity
            assertEquals(Intent.ACTION_CHOOSER, chooser.action)
            @Suppress("DEPRECATION") // Typed getParcelableExtra needs API33; this fixture also runs API27.
            val view = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
            assertEquals("application/octet-stream", view.type)
            assertEquals(uri, view.data)
            assertEquals(null, view.component)
            assertEquals(0, view.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }

    private fun assertReader(
        name: String,
        supplied: String,
        expected: String,
        family: FileOpenFamily,
        reader: String,
    ) = runBlocking {
        registerReaders()
        val node = node(name, supplied)
        assertEquals(family, fileOpenFamily(node))
        val result =
            helper().open(node) { prepared ->
                val offered = ExternalFileApps(app).list(prepared.mimeType)
                assertEquals(listOf("$reader/$reader.View"), offered.map { it.component })
                assertEquals(expected, prepared.mimeType)
                ExternalFileApps(app).launch(prepared, name, offered.single().target(), "Open with")
            }
        assertTrue(result is FileResult.Success)
        val view = shadowOf(app).nextStartedActivity
        assertEquals(Intent.ACTION_VIEW, view.action)
        assertEquals(expected, view.type)
        assertEquals(uri, view.data)
        assertEquals(uri, view.clipData!!.getItemAt(0).uri)
        assertEquals(ComponentName(reader, "$reader.View"), view.component)
        assertTrue(view.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, view.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    private fun helper() = ExternalPreviewHelper(app) { error("A SAF handoff must not export backend bytes") }

    private fun node(
        name: String,
        mime: String,
    ) = FileNode(
        FileNodeId.saf(uri.toString()), name, name, mime, 0, 0, false, false,
        null, StorageType.VIRTUAL, AccessFlags.FULL, null, null,
    )

    private fun registerReaders() {
        // Robolectric's MIME-map shadow starts empty; populate the platform mappings used by this fixture.
        shadowOf(MimeTypeMap.getSingleton()).apply {
            addExtensionMimeTypeMapping("pdf", "application/pdf")
            addExtensionMimeTypeMapping("png", "image/png")
        }
        val manager = shadowOf(app.packageManager)
        listOf("pdf.reader" to "application/pdf", "png.reader" to "image/png").forEach { (reader, mime) ->
            val activity =
                ActivityInfo().apply {
                    packageName = reader
                    name = "$reader.View"
                    nonLocalizedLabel = reader
                    enabled = true
                    exported = true
                    applicationInfo =
                        ApplicationInfo().apply {
                            packageName = reader
                            enabled = true
                        }
                }
            manager.addOrUpdateActivity(activity)
            manager.addIntentFilterForActivity(
                ComponentName(reader, activity.name),
                IntentFilter(Intent.ACTION_VIEW).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("content")
                    addDataType(mime)
                },
            )
        }
    }
}
