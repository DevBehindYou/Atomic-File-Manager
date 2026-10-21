package com.devbehindyou.atomicfilemanager.data.preview

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.devbehindyou.atomicfilemanager.MainActivity
import com.devbehindyou.atomicfilemanager.data.backend.FileSystemBackend
import com.devbehindyou.atomicfilemanager.domain.model.FileNodeId
import com.devbehindyou.atomicfilemanager.domain.model.FileResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real external-UID handoff; touches only one generated app-cache PNG and the test APK. */
@RunWith(AndroidJUnit4::class)
class ExternalFileHandoffDeviceTest {
    @Test
    fun realPngHandlerReceivesExactContentAndOnlyTemporaryReadAccess() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val context = instrumentation.targetContext
            val testContext = instrumentation.context
            val component = ComponentName(testContext.packageName, ExternalPngReceiverActivity::class.java.name)
            val expected = generatedPng()
            val fixture =
                withContext(Dispatchers.IO) {
                    File.createTempFile(ExternalPngReceiverActivity.FIXTURE_PREFIX, ".png", context.cacheDir)
                }
            val action = context.packageName + ExternalPngReceiverActivity.RESULT_ACTION_SUFFIX + fixture.name
            val observed = AtomicReference<Intent>()
            val delivered = CountDownLatch(1)
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) {
                        if (intent?.action == action) {
                            observed.set(Intent(intent))
                            delivered.countDown()
                        }
                    }
                }
            var registered = false
            var exported: ExternalPreviewFile? = null
            try {
                withContext(Dispatchers.IO) { fixture.outputStream().use { it.write(expected) } }
                ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_EXPORTED)
                registered = true
                val apps = ExternalFileApps(context)
                val offered = withContext(Dispatchers.IO) { apps.list("image/png") }
                val target = offered.single { it.component == component.flattenToString() }
                assertFalse(
                    withContext(Dispatchers.IO) { apps.list("application/pdf") }
                        .any { it.component == component.flattenToString() },
                )
                val backend = FileSystemBackend(context)
                val nodeResult =
                    withContext(Dispatchers.IO) { backend.getNode(FileNodeId.file(fixture.absolutePath)) }
                assertTrue("generated PNG node: $nodeResult", nodeResult is FileResult.Success)
                val node = (nodeResult as FileResult.Success).value
                ActivityScenario.launch(MainActivity::class.java).use {
                    val result =
                        ExternalPreviewHelper(context) { backend }.open(node) { file ->
                            exported = file
                            instrumentation.runOnMainSync {
                                apps.launch(file, fixture.name, target.target(), "Open with")
                            }
                        }
                    assertTrue("production URI handoff: $result", result is FileResult.Success)
                    assertTrue("real test-APK receiver did not report", delivered.await(10, TimeUnit.SECONDS))
                    val report = checkNotNull(observed.get())
                    val file = checkNotNull(exported)
                    assertEquals("content", file.uri.scheme)
                    assertEquals("${context.packageName}.fileprovider", file.uri.authority)
                    assertEquals(testContext.packageName, report.getStringExtra("receiverPackage"))
                    assertEquals(component.flattenToString(), report.getStringExtra("receiverComponent"))
                    assertEquals(testContext.applicationInfo.uid, report.getIntExtra("receiverUid", -1))
                    assertNotEquals(context.applicationInfo.uid, report.getIntExtra("receiverUid", -1))
                    assertEquals(Intent.ACTION_VIEW, report.getStringExtra("action"))
                    assertEquals("image/png", report.getStringExtra("mime"))
                    assertEquals(file.uri.toString(), report.getStringExtra("uri"))
                    assertEquals(1, report.getIntExtra("clipCount", -1))
                    assertEquals(file.uri.toString(), report.getStringExtra("clipUri"))
                    val flags = report.getIntExtra("flags", 0)
                    assertTrue(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                    assertEquals(0, flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    assertEquals(0, flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                    assertEquals(0, flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                    assertEquals(PackageManager.PERMISSION_GRANTED, report.getIntExtra("readPermission", -1))
                    assertEquals(PackageManager.PERMISSION_DENIED, report.getIntExtra("writePermission", 0))
                    assertNull("external read failure", report.getStringExtra("failure"))
                    assertArrayEquals(expected, report.getByteArrayExtra("bytes"))
                }
            } finally {
                try {
                    exported?.let { file ->
                        context.revokeUriPermission(
                            testContext.packageName,
                            file.uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                } finally {
                    if (registered) context.unregisterReceiver(receiver)
                    withContext(Dispatchers.IO) {
                        check(fixture.parentFile?.canonicalFile == context.cacheDir.canonicalFile)
                        assertTrue("generated fixture cleanup", fixture.delete() || !fixture.exists())
                    }
                }
            }
        }

    private fun generatedPng(): ByteArray {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        try {
            return ByteArrayOutputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
                it.toByteArray().also { bytes -> check(bytes.size <= 512) }
            }
        } finally {
            bitmap.recycle()
        }
    }
}
