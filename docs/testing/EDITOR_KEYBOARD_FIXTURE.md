# Scoped Editor keyboard fixture correction

29 October 2026. Test-only preparation; no passing execution is claimed here.

Frozen source `515c92ca0aa9986b8e386cdc8973fda52038cfa4` remains unchanged. Its retained three-case result has two missing-Undo-control assertions and one failed intended keyboard control: Ctrl+Z inserts `z`, leaving `keyboard draftz`. Do not describe that third result as a valid passing control or proven production keyboard defect.

## Cause checked against pinned artifacts

The cached Compose UI-test 1.11.0 `AndroidInputDispatcher.constructMetaState` includes both CTRL_ON and CTRL_LEFT_ON (12288) for CtrlLeft. Modifier injection is correct. Robolectric 4.14.1 `ShadowKeyCharacterMap.nativeGetCharacter(long, int, int)` checks only SHIFT_ON and returns printable `z` for Ctrl+Z. Compose Foundation 1.11.0 `TextFieldKeyInput.process` attempts typed input before key-command mapping; its Android typed-event check accepts non-control Unicode. The printable result therefore bypasses framework Undo handling.

## Minimal correction and limits

`ControlKeyCharacterMapShadow` subclasses the pinned shadow and maps Ctrl+A–Z to U+0001–U+001A when Alt and Meta are absent. All other mappings and inherited keyboard behavior delegate to the existing implementation. This shadow is selected only by the Editor Undo/Redo fixture and its direct mapping contract; it changes no application source, build dependency, global Robolectric configuration, assertion or frozen baseline.

The mapping contract loads the real Android KeyCharacterMap facade and checks plain/Shift/Ctrl/Ctrl+Shift Z, Ctrl+A, numeric/punctuation delegation, Alt/Meta fallback, display label and number. The original Editor behavior assertions remain intact. Run this corrected test-only baseline separately; preserve its results alongside the original failures before comparing the candidate. A local commit alone is not validation.

The prepared native Editor save fixture exercises toolbar controls and exact staged-save bytes, not hardware shortcuts. Native Ctrl+Z/Redo acceptance requires separate actual key-event assertions. The scoped host correction is not that device evidence.
