package com.devbehindyou.atomicfilemanager.benchmark

import android.content.Intent
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.util.regex.Pattern

/**
 * Controlled shared-storage listing and scrolling. Missing folders/lists fail setup instead of
 * returning a successful no-op. Fixture preparation and cleanup only own inserted MediaStore IDs.
 */
@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.R)
@RequiresApi(Build.VERSION_CODES.R)
class FolderScrollBenchmark(private val entries: Int) {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun scrollFiles() = withCorpus { corpus, evidence ->
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Disable, warmupIterations = 3),
            setupBlock = {
                openReset(corpus)
                startActivityAndWait(folderIntent(corpus.folderPath))
                awaitFolder(corpus.folderName, entries)
                assertListView()
            },
        ) {
            val before = visibleFileName()
            repeat(FLINGS) {
                fileList().apply {
                    setGestureMargin(device.displayWidth / GESTURE_MARGIN_DIVISOR)
                    fling(Direction.DOWN)
                }
                device.waitForIdle()
            }
            check(before != visibleFileName()) { "Fixture list did not scroll; refusing an empty measurement" }
            iteration?.let { evidence.recordScroll(it) }
        }
    }

    @Test
    fun openFolder() = withCorpus { corpus, evidence ->
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            iterations = ITERATIONS,
            startupMode = StartupMode.WARM,
            compilationMode = CompilationMode.Partial(BaselineProfileMode.Disable, warmupIterations = 3),
            setupBlock = {
                openReset(corpus)
                assertListView()
            },
        ) {
            val started = SystemClock.elapsedRealtimeNanos()
            startActivityAndWait(folderIntent(corpus.folderPath))
            check(device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).text(corpus.folderName)), WAIT_MS)) {
                "Expected fixture folder was not opened"
            }
            check(device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).text(FILE_NAME)), WAIT_MS)) {
                "No usable fixture row appeared"
            }
            val firstRow = SystemClock.elapsedRealtimeNanos()
            awaitFolder(corpus.folderName, entries)
            val complete = SystemClock.elapsedRealtimeNanos()
            fileList()
            iteration?.let { evidence.recordOpen(it, started, firstRow, complete) }
        }
    }

    private fun withCorpus(block: (BenchmarkCorpus, BenchmarkEvidence) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().context
        var evidence: BenchmarkEvidence? = null
        try {
            BenchmarkCorpus.create(context, entries).use { corpus ->
                val recorder = BenchmarkEvidence(context, entries, corpus.imageCount)
                evidence = recorder
                block(corpus, recorder)
            }
        } catch (failure: Throwable) {
            try {
                evidence?.finish(success = false)
            } catch (writeFailure: Exception) {
                failure.addSuppressed(writeFailure)
            }
            throw failure
        }
        evidence?.finish(success = true)
    }

    private fun MacrobenchmarkScope.openReset(corpus: BenchmarkCorpus) {
        pressHome()
        startActivityAndWait(folderIntent(corpus.resetPath))
        awaitFolder(corpus.resetName, 1)
    }

    private fun MacrobenchmarkScope.awaitFolder(name: String, count: Int) {
        check(device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).text(name)), WAIT_MS)) {
            "Expected fixture folder was not opened; check the target app's existing storage access"
        }
        val counter = Pattern.compile("^${Pattern.quote("$count items · ")}.+$")
        check(device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).desc(counter)), WAIT_MS)) {
            "Fixture count did not match $count; clear active search/filters and use one browse pane"
        }
    }

    private fun MacrobenchmarkScope.assertListView() {
        checkNotNull(device.findObject(By.pkg(TARGET_PACKAGE).desc("File options"))) {
            "File options not available; fixture browser must be visible"
        }.click()
        checkNotNull(device.wait(Until.findObject(By.pkg(TARGET_PACKAGE).text("View")), WAIT_MS)) {
            "View options not available"
        }.click()
        val list = checkNotNull(device.wait(Until.findObject(By.pkg(TARGET_PACKAGE).text("List")), WAIT_MS)) {
            "List view option not available"
        }
        // Compose's RadioButton role exports selection as checked on the ancestor row,
        // while the child text node itself is neither selected nor checkable.
        val selected = generateSequence(list) { it.parent }.firstOrNull { it.isCheckable }?.isChecked == true
        checkNotNull(device.findObject(By.pkg(TARGET_PACKAGE).desc("Close file options"))) {
            "File options could not be closed"
        }.click()
        check(device.wait(Until.gone(By.pkg(TARGET_PACKAGE).desc("Close file options")), WAIT_MS)) {
            "File options stayed open"
        }
        device.waitForIdle()
        check(selected) { "Select List view before this benchmark; preferences were not changed" }
    }

    private fun MacrobenchmarkScope.fileList(): UiObject2 {
        val candidates = device.findObjects(By.pkg(TARGET_PACKAGE).scrollable(true)).filter { candidate ->
            candidate.visibleBounds.height() >= device.displayHeight / 3 &&
                candidate.visibleBounds.width() >= device.displayWidth / 2 &&
                candidate.findObject(By.text(FILE_NAME)) != null
        }
        check(candidates.size == 1) { "Expected one vertical fixture list; found ${candidates.size}" }
        return candidates.single()
    }

    private fun MacrobenchmarkScope.visibleFileName(): String =
        checkNotNull(fileList().findObject(By.text(FILE_NAME))?.text) { "Fixture list has no visible file row" }

    companion object {
        const val TARGET_PACKAGE = "com.devbehindyou.atomicfilemanager"
        private const val EXTRA_OPEN = "$TARGET_PACKAGE.extra.OPEN"
        private const val WAIT_MS = 30_000L
        private const val ITERATIONS = 10
        private const val FLINGS = 3
        private const val GESTURE_MARGIN_DIVISOR = 5
        private val FILE_NAME = Pattern.compile("^bench_[0-9]{5}\\.(txt|json|md|png)$")

        @JvmStatic
        @Parameterized.Parameters(name = "{0} entries")
        fun sizes(): List<Array<Int>> = FixturePlan.SIZES.map { arrayOf(it) }

        private fun folderIntent(path: String): Intent =
            Intent(Intent.ACTION_MAIN).apply {
                setClassName(TARGET_PACKAGE, "$TARGET_PACKAGE.MainActivity")
                addCategory(Intent.CATEGORY_LAUNCHER)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(EXTRA_OPEN, "folder:file:$path")
            }
    }
}
