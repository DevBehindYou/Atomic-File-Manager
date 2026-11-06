package com.devbehindyou.atomicfilemanager

import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

/** Checks the real Activity content observer before Compose's STARTED lifecycle can run. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 34])
@LooperMode(LooperMode.Mode.PAUSED)
class MainActivityFirstFrameTest {
    @Test
    fun `first content draw does not wait for ready composition`() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        try {
            val activity = controller.get()
            assertEquals(Lifecycle.State.CREATED, activity.lifecycle.currentState)
            val content = activity.findViewById<ViewGroup>(android.R.id.content)
            assertTrue("The actual Activity must have installed its Compose content", content.childCount > 0)
            // Both SplashScreen implementations register their keep predicate on this observer.
            // dispatchOnPreDraw returns true when any listener CANCELS the draw.
            assertFalse(
                "Startup must permit a frame before Ready content or its SideEffect can run",
                content.viewTreeObserver.dispatchOnPreDraw(),
            )
        } finally {
            controller.destroy()
        }
    }
}
