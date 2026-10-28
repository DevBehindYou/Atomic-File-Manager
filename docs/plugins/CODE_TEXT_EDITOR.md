# Code and text editor — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). It is separate from grammar-engine and app release versions.

Source inspected: `ecf1dc5834c125c9612be74128fc4ac34e65a046` (24 October 2026). The version-only increment changes inventory metadata, not the linked viewer engines.

## Implementation, features and limits

[TextEditorDialog.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/TextEditorDialog.kt) provides monospaced editing, find/replace, wrapping, line numbers while unwrapped, save state and unsaved-close confirmation; editing is locked while saving. [TextEditorSession.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/TextEditorSession.kt) owns in-memory drafts/selection and asynchronous loading/saving with owner-safe acknowledgement/replacement IDs. [TextEditing.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/domain/usecase/TextEditing.kt) bounds loading, preserves detected encoding/BOM and dominant line-ending style, and stages backend publication.

Drafts survive Activity recreation in memory, **not process death**. This is not a paged hundreds-of-MB editor. Mixed line endings are normalized to detected dominant style; arbitrary mixed bytes are not preserved. IDE/LSP, execution, maintained grammar highlighting, multi-cursor and complete regex tooling are not established. A new bounded Undo/Redo source candidate is described below; host/device acceptance remains open. Staging does not prove universal provider atomicity, durable drafts or conflict recovery.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| EDT-T01 | P0 / open design/acceptance | Persistent recovery policy, external revisions, write denial/low space/disconnection/interrupted saves and Save as fallback preserving originals |
| EDT-T02 | P0 / partial candidate, unvalidated | Document-owned Undo/Redo controls/shortcuts implemented with bounded snapshots; actual consumer/static/APK/device gates remain. Case/whole-word/regex/go-to-line and advanced find tooling remain open |
| EDT-T03 | P0 / deferred audit | Bounded large-file read-only/paged fallback and honest truncation/search before raising limits |
| EDT-T04 | P1 / deferred research | Grammar highlighting, indentation/brackets/go-to-line and encoding controls with native fit, latency and license evidence |

## Bugs and risks

Historical recreation/late-acknowledgement/replacement-ID fixes are in the [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md); avoid duplicating them. EDT-B01: source inspection and the preserved test-only candidate `515c92c` identify missing explicit Undo/Redo controls and session-owned history. The baseline has not yet been executed; its missing-control failures are expected, not reported as observed. Existing BasicTextField already provides native Ctrl+Z/Ctrl+Shift+Z; this is not a claim that the framework has no history or unbounded history. Memory-only drafts/absent advanced tools are limits. External revisions, publication and crash recovery remain open checks. This version/documentation change performs no real user-file save.

## Validation and continuation

Historical `4792db8` [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) covers prior editor work. `EditorTextTest`, `TextEditorSessionTest`, `TextEditorSessionDialogTest`, `TextEditorRetentionTest` and `TextPreviewBoundsTest` cover encoding/edit/session/bounds. They do not establish real-provider process-kill safety or large-file speed. U11 and the [advanced intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) remain separate/incomplete; record exact source/provider fixtures for new acceptance.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained features/tasks/bugs/risks/evidence context. No persistent recovery, grammar engine or advanced editor delivery is implied.
## Bounded Undo/Redo candidate — 26 October 2026

`TextEditorHistory` adds full immutable TextFieldValue snapshots owned by the existing memory-only TextEditorSession. Undo and Redo share a cap of 20 retained snapshots and 16 MiB of UTF-16 text payload, conservatively counting two bytes per code unit even when immutable strings share storage. Snapshot objects/annotations, current draft, saved baseline and Compose/IME/framework allocations are additional; this is not a process-heap limit or a measured performance claim. Oldest reachable history is evicted within these budgets. An oversized immediate predecessor becomes a history barrier rather than silently undoing past an omitted edit. The existing 2 MiB source-loading limit is unchanged.

Every accepted text-changing callback is one history step; there is no typing-time coalescing or line-diff scan. Selection-only/composition-only changes do not create steps or clear Redo. Restored text retains caret/selection and drops stale IME composition. New text edits clear Redo. History survives dialog remount and Activity recreation only as part of the existing Activity-owned session; close/owner release clears it. No plaintext SavedState, draft persistence or process-death recovery is added.

The toolbar exposes accessible Undo/Redo icons. Document-field Ctrl+Z, Ctrl+Shift+Z and Ctrl+Y use the same history, are rejected during a save, and do not intercept Find/Replace field shortcuts. Saving retains history and changes only the existing saved baseline/replacement identity after its acknowledgement; Undo after Save may therefore make the document dirty again. Undo/Redo do not themselves write to storage or change encoding/BOM/line-ending/publication policy.

The prior missing-controls baseline commit is preserved independently. Added candidates cover history budget/count/Unicode/IME/branch/release behavior, loading/save/identity/format guards, actual toolbar and keyboard interaction, Find focus and session remount. They are unexecuted until the parent records exact source and results. See [candidate validation](../testing/EDITOR_BOUNDED_HISTORY_CANDIDATE.md). EDT2 and EDT6 remain incomplete; version remains v0.2 and this partial repair earns no complete capability-group credit.

Official Android [keyboard handling documentation](https://developer.android.com/develop/ui/compose/touch-input/keyboard-input/commands) confirms native BasicTextField Undo/Redo shortcuts and preview interception. Context7 is unavailable in this session; the official source is the fallback. No dependency or engine changes are proposed.

- **27 October 2026 preparation:** retained PR #90 CI failure identified an unused test import and a Find input selector targeting AtomicTextField's outer Column. The test-only correction targets its editable descendant and preserves the original behavioral assertions. Corrected execution remains pending; see the candidate validation record above. Plugin version and capability credit remain unchanged.

- **28 October 2026 dependency preparation:** synchronize the preserved history/native/Find-selector candidate with frozen beta source `02605684` (pending PR #97), adopting its requested API28 app/benchmark and retention-test baseline. No editor execution or dependency acceptance follows from the merge. The preserved baseline runs first after beta; the candidate record above lists host/native gates. Undo/Redo bounds and v0.2/N42 status are unchanged.
