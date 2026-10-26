# Editor bounded history — source candidate, no executed acceptance

26 October 2026. Isolated branch codex/editor-bounded-history, base combined-media source 3aa7538. Test-only baseline 515c92ca0aa9986b8e386cdc8973fda52038cfa4 is preserved. Its three actual-dialog cases specify explicit accessible Undo/Redo, save locking/baseline behavior and existing native Ctrl+Z/Ctrl+Shift+Z compatibility. Baseline file SHA-256: B06AAB6740344F54AF6FCD6F0557D4CD7A6D6C1C26EE12F8DD2CEDD31D44DA83. No baseline execution is claimed here.

Production scope: TextEditorHistory, existing session/UI, two semantic AtomicIcons and two localized strings. No dependency, engine, storage permission, loading limit, saving/publication, source router, plugin version or benchmark changes. Full snapshots avoid new manual O(N) prefix/suffix diffing. Existing dirty/text-change equality and UI rendering still have their existing string costs. The 20-entry/16 MiB UTF-16 retained text-payload cap excludes objects, current/saved text and framework/IME memory. No process-wide memory or latency budget is proven.

New tests, all unexecuted at author handoff:

- TextEditorHistoryTest: six pure cases, including Unicode selection/IME clearing, twenty combined entries in both directions, exact UTF-16 budget across both stacks, branch invalidation, an oversized predecessor barrier and release.
- TextEditorHistorySessionTest: three actual-session cases for selection-only transitions, load/save/closed admission with saved replacement identity, failed-save history/redo invalidation/format preservation.
- TextEditorHistoryIntegrationComposeTest: two actual-dialog cases for shared toolbar/Ctrl+Y history, Find's native Ctrl+Z scope, branch clearing and dialog-remount session retention/release.
- Existing TextEditorUndoRedoComposeTest: three preserved baseline consumers, unchanged.

Parent validation must run these 14 cases plus affected existing TextEditorSessionTest, TextEditorSessionDialogTest, TextEditorRetentionTest, EditorTextTest and TextPreviewBoundsTest; source loading/codec/publication tests remain applicable. Configured ktlint/Detekt, APK packaging and Lint must pass on the current source. Physical API34 editor Unicode/selection/Undo/Redo/find/save/reopen requires only owned synthetic fixtures; broader providers/accessibility/rotation/IME/low-space/interruption/process-death and performance remain distinct open gates. Actual session remount is not full native Activity recreation or process death.

Scoped Kotlin formatter passed at author handoff. No Gradle, ADB, native mutation, CI or merge was run by this author. No capability credit or release readiness follows from this record.

## Native history consumer proposal — not executed

`EditorHistoryDeviceTest.unicodeSelectionUndoRedoAndStagedSavePreserveFormatAndUnrelatedFile` mounts the actual dialog/session with a local `TextFileEditor` and real `FileSystemBackend`. Its uniquely owned private UTF-8-BOM/CRLF text file is opened, Unicode surrogate-pair selection is replaced, Undo/Redo restores text/selection/dirty state, and the actual staged Save preserves encoding/endings. Undo after Save remains memory-only; Redo returns to the saved baseline. A private unrelated binary sentinel and the owned directory's final entries are checked. No global AppContainer, Recents, TrashManager, preferences, permission or shared-storage document is involved. This semantic instrumentation is not manual IME/touch, Activity recreation or process-death acceptance.

Only the captured source/sentinel and empty captured directory are deleted. Unexpected staged entries remain for diagnosis; an unsettled accepted save retains its private fixtures instead of racing file deletion. Composition/session/scope disposal still runs. The proposal adds no manifest/activity. Scoped formatter and whitespace checks pass; compilation/native execution remain parent-owned and unrun.
