package com.devbehindyou.atomicfilemanager.benchmark

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Deterministic corpus and cleanup-identity contracts; these tests do not mutate provider files. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
class BenchmarkFixtureContractTest {
    @Test
    fun corpusNamesAndThumbnailBudgetAreDeterministic() {
        FixturePlan.SIZES.forEach { size ->
            val plan = FixturePlan(size)
            val names = (0 until size).map(plan::name)
            assertEquals(size, names.toSet().size)
            assertEquals(minOf(size / 10, 100), names.count { it.endsWith(".png") })
            assertTrue(names.none { '/' in it || '\\' in it || it.startsWith('.') })
        }
        assertThrows(IllegalArgumentException::class.java) { FixturePlan(100_000) }
        assertThrows(IllegalArgumentException::class.java) { FixturePlan(100).name(-1) }
        assertThrows(IllegalArgumentException::class.java) { FixturePlan(100).name(100) }
    }

    @Test
    fun explicitCorpusSelectionCannotFallBackToTheWholeSuite() {
        assertEquals(listOf(100, 1_000, 10_000), FixturePlan.selectedSizes(null, supplied = false))
        FixturePlan.SIZES.forEach { size ->
            assertEquals(listOf(size), FixturePlan.selectedSizes(size.toString(), supplied = true))
        }
        listOf(null, "", " ", " 100", "100 ", "+100", "0100", "100.0", "100,1000", "0", "-100", "100000")
            .forEach { invalid ->
                assertThrows(IllegalArgumentException::class.java) {
                    FixturePlan.selectedSizes(invalid, supplied = true)
                }
            }
        assertThrows(IllegalArgumentException::class.java) {
            FixturePlan.selectedSizes("100", supplied = false)
        }
    }

    @Test
    fun cleanupRequiresMatchingOwnerPathAndGeneratedName() {
        val owner = "benchmark.package"
        val path = "Download/AtomicBenchmark-owned/"
        val name = "bench_00001.txt"
        assertTrue(BenchmarkCorpus.ownsRow(owner, path, name, owner, path, name))
        assertFalse(BenchmarkCorpus.ownsRow(owner, path, name, "another.package", path, name))
        assertFalse(BenchmarkCorpus.ownsRow(owner, path, name, owner, "Download/user/", name))
        assertFalse(BenchmarkCorpus.ownsRow(owner, path, name, owner, path, "user.txt"))
        assertFalse(BenchmarkCorpus.ownsRow(owner, path, name, null, path, name))
    }
}
