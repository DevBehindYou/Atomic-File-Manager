package com.devbehindyou.atomicfilemanager.ui.components;

import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.shadows.ShadowKeyCharacterMap;

/** Scoped JVM keyboard fidelity: retain Robolectric mappings except Ctrl-letter control characters. */
@Implements(KeyCharacterMap.class)
public class ControlKeyCharacterMapShadow extends ShadowKeyCharacterMap {
    @Implementation
    protected static char nativeGetCharacter(long pointer, int keyCode, int metaState) {
        boolean control = (metaState & KeyEvent.META_CTRL_ON) != 0;
        boolean alternativeModifier =
                (metaState & (KeyEvent.META_ALT_ON | KeyEvent.META_META_ON)) != 0;
        if (control && !alternativeModifier
                && keyCode >= KeyEvent.KEYCODE_A && keyCode <= KeyEvent.KEYCODE_Z) {
            return (char) (keyCode - KeyEvent.KEYCODE_A + 1);
        }
        return ShadowKeyCharacterMap.nativeGetCharacter(pointer, keyCode, metaState);
    }
}
