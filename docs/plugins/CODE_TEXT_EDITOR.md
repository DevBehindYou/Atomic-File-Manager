# Code and text editor — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). It is separate from grammar-engine and app release versions.

Source inspected: `ecf1dc5834c125c9612be74128fc4ac34e65a046` (24 October 2026). The version-only increment changes inventory metadata, not the linked viewer engines.

## Implementation, features and limits

[TextEditorDialog.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/TextEditorDialog.kt) provides monospaced editing, find/replace, wrapping, line numbers while unwrapped, save state and unsaved-close confirmation; editing is locked while saving. [TextEditorSession.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/TextEditorSession.kt) owns in-memory drafts/selection and asynchronous loading/saving with owner-safe acknowledgement/replacement IDs. [TextEditing.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/domain/usecase/TextEditing.kt) bounds loading, preserves detected encoding/BOM and dominant line-ending style, and stages backend publication.

Drafts survive Activity recreation in memory, **not process death**. This is not a paged hundreds-of-MB editor. Mixed line endings are normalized to detected dominant style; arbitrary mixed bytes are not preserved. IDE/LSP, execution, maintained grammar highlighting, explicit undo/redo history, multi-cursor and complete regex tooling are not established. Staging does not prove universal provider atomicity, durable drafts or conflict recovery.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| EDT-T01 | P0 / open design/acceptance | Persistent recovery policy, external revisions, write denial/low space/disconnection/interrupted saves and Save as fallback preserving originals |
| EDT-T02 | P0 / deferred | Explicit undo/redo and robust find/replace options with Unicode/whole-word/regex/long-line tests |
| EDT-T03 | P0 / deferred audit | Bounded large-file read-only/paged fallback and honest truncation/search before raising limits |
| EDT-T04 | P1 / deferred research | Grammar highlighting, indentation/brackets/go-to-line and encoding controls with native fit, latency and license evidence |

## Bugs and risks

Historical recreation/late-acknowledgement/replacement-ID fixes are in the [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md); avoid duplicating them. No new editor bug is reproduced here; assign `EDT-Bnn` to future confirmed failures with evidence. Memory-only drafts/absent advanced tools are limits. External revisions, publication and crash recovery remain open checks. This version/documentation change performs no real user-file save.

## Validation and continuation

Historical `4792db8` [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) covers prior editor work. `EditorTextTest`, `TextEditorSessionTest`, `TextEditorSessionDialogTest`, `TextEditorRetentionTest` and `TextPreviewBoundsTest` cover encoding/edit/session/bounds. They do not establish real-provider process-kill safety or large-file speed. U11 and the [advanced intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) remain separate/incomplete; record exact source/provider fixtures for new acceptance.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained features/tasks/bugs/risks/evidence context. No persistent recovery, grammar engine or advanced editor delivery is implied.