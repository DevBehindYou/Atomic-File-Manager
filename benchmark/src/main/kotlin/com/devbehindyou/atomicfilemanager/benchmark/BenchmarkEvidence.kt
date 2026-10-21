package com.devbehindyou.atomicfilemanager.benchmark

import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import androidx.annotation.RequiresApi
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/** Raw observations complement frame metrics without claiming first-paint or pure-listing precision. */
@RequiresApi(Build.VERSION_CODES.Q)
internal class BenchmarkEvidence(context: Context, entries: Int, images: Int) {
    private val manager = context.getSystemService(PowerManager::class.java)
    private val output = File(
        File(context.getExternalFilesDir(null) ?: context.filesDir, "benchmark-results").apply {
            check(isDirectory || mkdirs()) { "Could not create benchmark-owned result directory" }
        },
        "corpus-$entries-${UUID.randomUUID()}.jsonl",
    )
    private var samples = 0
    private val records = mutableListOf<JSONObject>()

    init {
        val info = context.packageManager.getPackageInfo(FolderScrollBenchmark.TARGET_PACKAGE, 0)
        val source = checkNotNull(context.packageManager.getApplicationInfo(FolderScrollBenchmark.TARGET_PACKAGE, 0).sourceDir)
        append(
            JSONObject()
                .put("kind", "environment")
                .put("schema", 1)
                .put("entries", entries)
                .put("images", images)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("model", Build.MODEL)
                .put("buildFingerprint", Build.FINGERPRINT)
                .put("versionName", info.versionName)
                .put("versionCode", info.longVersionCode)
                .put("baseApkSha256", hash(File(source)))
                .put("startupMode", "WARM")
                .put("compilationMode", "Partial(baselineProfileMode=Disable,warmupIterations=3)")
                .put("viewMode", "List (asserted)")
                .put("cacheState", "OS/app caches retained; reset folder opened before each sample")
                .put("thermalStatusBefore", manager.currentThermalStatus)
                .put("observedClock", "elapsedRealtimeNanos")
                .put("timingCaveat", "Includes activity launch handshake, frame wait and accessibility polling; not decoder or first-paint timing"),
        )
    }

    fun recordOpen(iteration: Int, started: Long, firstRow: Long, complete: Long) {
        samples++
        append(
            JSONObject()
                .put("kind", "observedFolderOpen")
                .put("sample", samples)
                .put("macrobenchmarkIteration", iteration)
                .put("observedFirstRowMs", (firstRow - started) / NANOS_PER_MILLI)
                .put("observedCompleteCountMs", (complete - started) / NANOS_PER_MILLI),
        )
    }

    fun recordScroll(iteration: Int) {
        samples++
        append(JSONObject().put("kind", "scrollVerified").put("sample", samples).put("macrobenchmarkIteration", iteration))
    }

    fun finish(success: Boolean) {
        append(
            JSONObject()
                .put("kind", "result")
                .put("success", success)
                .put("samples", samples)
                .put("thermalStatusAfter", manager.currentThermalStatus)
                .put("finishedElapsedMs", SystemClock.elapsedRealtime()),
        )
        // Only write after measureRepeated and corpus cleanup, never on the measured path.
        output.bufferedWriter(Charsets.UTF_8).use { writer ->
            records.forEach { writer.append(it.toString()).append('\n') }
        }
    }

    private fun append(value: JSONObject) {
        records += value
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val NANOS_PER_MILLI = 1_000_000.0
    }
}
