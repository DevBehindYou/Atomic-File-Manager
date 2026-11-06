package com.devbehindyou.atomicfilemanager.ui.components

import android.view.KeyCharacterMap
import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ControlKeyCharacterMapShadow::class])
class ControlKeyCharacterMapShadowTest {
    @Test
    fun controlLettersAreControlCharactersWhilePlainShiftAndOtherKeysKeepTheirMappings() {
        val keyboard = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)
        val control = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        val shift = KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        assertEquals('z'.code, keyboard.get(KeyEvent.KEYCODE_Z, 0))
        assertEquals('Z'.code, keyboard.get(KeyEvent.KEYCODE_Z, shift))
        assertEquals(26, keyboard.get(KeyEvent.KEYCODE_Z, control))
        assertEquals(26, keyboard.get(KeyEvent.KEYCODE_Z, control or shift))
        assertEquals(1, keyboard.get(KeyEvent.KEYCODE_A, control))
        assertEquals('1'.code, keyboard.get(KeyEvent.KEYCODE_1, 0))
        assertEquals('!'.code, keyboard.get(KeyEvent.KEYCODE_1, shift))
        assertEquals('1'.code, keyboard.get(KeyEvent.KEYCODE_1, control))
        assertEquals('z'.code, keyboard.get(KeyEvent.KEYCODE_Z, control or KeyEvent.META_ALT_ON))
        assertEquals('z'.code, keyboard.get(KeyEvent.KEYCODE_Z, control or KeyEvent.META_META_ON))
        assertEquals('Z', keyboard.getDisplayLabel(KeyEvent.KEYCODE_Z))
        assertEquals('1', keyboard.getNumber(KeyEvent.KEYCODE_1))
    }
}
