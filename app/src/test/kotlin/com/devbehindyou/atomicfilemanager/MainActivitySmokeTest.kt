package com.devbehindyou.atomicfilemanager

import androidx.test.core.app.ActivityScenario
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Host startup coverage at the owner-requested API 28 minimum and API 34.
 * This does not replace native emulator acceptance or the wider release matrix
 * (testing/TEST_STRATEGY.md §11).
 *
 * Uses JUnit4 + RobolectricTestRunner rather than JUnit5, deliberately, for this one
 * file — see PHASE_1_NOTES.md for the reasoning (it's run through the same JUnit
 * Platform test task as everything else, via the vintage engine).
 *
 * Parameterised across minSdk (28) and the API 34 host fixture via
 * @Config(sdk = ...) rather than two separate test classes.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
class MainActivitySmokeTest {
    @Test
    fun mainActivityLaunchesWithoutCrashing() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assert(!activity.isFinishing) {
                    "MainActivity finished immediately instead of launching cleanly"
                }
            }
        }
    }
}
