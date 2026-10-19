package com.devbehindyou.atomicfilemanager.ui.components.preview

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreviewPlayerLifecycleTest {
    @Test
    fun `autoplay requires both user policy and a started owner`() {
        val owner = Owner()
        for (state in listOf(Lifecycle.State.INITIALIZED, Lifecycle.State.CREATED, Lifecycle.State.STARTED)) {
            owner.lifecycle.currentState = state
            assertEquals(state == Lifecycle.State.STARTED, previewAutoplayAllowed(true, owner.lifecycle))
            assertFalse(previewAutoplayAllowed(false, owner.lifecycle))
        }
    }

    @Test
    fun `preparation completing after stop is paused immediately and does not resume automatically`() {
        val owner = Owner()
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        var playing = true
        PreviewPlayerLifecycleBinding(owner.lifecycle) { playing = false }.use {
            assertFalse(playing)
            assertFalse(previewAutoplayAllowed(true, owner.lifecycle))
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            assertFalse(playing)
        }
    }

    @Test
    fun `stop pauses once and foregrounding preserves pause`() {
        val owner = Owner()
        owner.lifecycle.currentState = Lifecycle.State.STARTED
        var pauses = 0
        PreviewPlayerLifecycleBinding(owner.lifecycle) { pauses++ }.use {
            assertEquals(0, pauses)
            owner.lifecycle.currentState = Lifecycle.State.CREATED
            assertEquals(1, pauses)
            owner.lifecycle.currentState = Lifecycle.State.STARTED
            assertEquals(1, pauses)
        }
    }

    @Test
    fun `replacement owner synchronizes immediately and disposed owner no longer pauses`() {
        val first = Owner()
        first.lifecycle.currentState = Lifecycle.State.STARTED
        val replacement = Owner()
        replacement.lifecycle.currentState = Lifecycle.State.CREATED
        var playing = true
        val firstBinding = PreviewPlayerLifecycleBinding(first.lifecycle) { playing = false }
        firstBinding.close()
        first.lifecycle.currentState = Lifecycle.State.CREATED
        assertTrue(playing)
        PreviewPlayerLifecycleBinding(replacement.lifecycle) { playing = false }.use {
            assertFalse(playing)
        }
    }

    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry.createUnsafe(this)
    }
}
