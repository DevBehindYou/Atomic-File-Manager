# Preview tools and row actions — 20 October 2026

Preview checkpoint: `f04ab8f` on `codex/architecture-audit`; current integration
is `4792db8` with Files controls, adaptive menus and typed icons. This report covers the
thumbnail/viewer/contextual-action increment and its integrated dependencies. It
does not establish provider mutation, process-death recovery, performance or
production-signing acceptance.

## Implemented behavior

- Local, SAF and MediaStore image/video thumbnail requests share revision-aware
  cache keys and retain file-type icons on failure. Local folder icons can show
  four direct media children after bounded IO and hidden/symlink/`.nomedia` checks.
- Per-row More boxes are removed. Long press selects items; contextual overflow
  and accessibility actions retain single-item operations. Tablet action ownership
  follows the selected pane. Pane clipboards remain independent.
- Images apply all eight EXIF orientations with bounded metadata/decode ownership.
  PDF sessions own their descriptor/renderer/private spool, bound raster size and
  publish only the latest requested page, with retry and page jump.
- DOCX uses bounded, read-only document text with find, selection and font size.
  It does not reproduce Office pagination or edit documents.
- Media uses direct readable local/content URIs or bounded owned remote staging,
  lifecycle pause, visible errors and retry. Codec support remains device-specific.
- Text/code drafts and selection survive activity recreation in memory; accepted
  saves acknowledge the current owner and follow replacement provider IDs.
  Drafts do not survive process death.

See [viewer audit](BUILT_IN_VIEWER_AUDIT.md) and
[thumbnail privacy/ownership scope](FILE_THUMBNAILS_VALIDATION.md).

## Validation ledger

| Gate | Current result |
| --- | --- |
| Formatting / detekt | Passed on current integration |
| Full app JVM/Robolectric suite | 917 tests passed, zero failures/errors/skips; preceding Tasks checkpoint passed 882 |
| Custom Lint-rule suite | 28 tests passed (unchanged/cached) |
| Debug / instrumentation APKs | Current app and test APKs packaged and installed as updates |
| Native API34 surface suite | All 13 selected cases passed, including Files controls, Tasks navigation, notification recreation, decoded PNG/folder pixels and DOCX search |
| Physical row actions and media | No row More boxes; folder long press, correct Rename target and cancellation verified; folder miniatures and real video thumbnails/frame visible. Current Files Grid/List and SD switching also verified |
| Android Lint | Current debug/release reports each passed with zero errors, 153 warnings and one hint |
| Release / benchmark APKs | R8 release and benchmark test APK assembled; complete 14m36s Gradle run exited 0 |

Host: Windows, JDK21, Gradle9.3.1, one worker and a local 3 GiB Gradle heap. Gradle
runs are serialized. No dependency version was edited for this increment.

Current APK hashes and clean selected-phone log are in [menu/icon validation](MENU_REFINEMENT_VALIDATION.md). Earlier Files evidence and the retained nonfatal Android
`InsetsSourceControl` StrictMode diagnostic are recorded in
[Files workspace validation](FILES_WORKSPACE_VALIDATION.md). That preceding Files
run was not warning-free. The latest menu/icon selected run captured no
StrictMode or fatal error.

## Historical failures and corrections

The initial resumed online build stalled on repository metadata and was cancelled
after a thread diagnostic confirmed an HTTP wait. The offline retry failed because
runtime metadata was uncached; neither attempt is a test pass. The current retry
uses the configured official repositories with bounded HTTP timeouts. Its log and
eventual exit marker are retained under ignored
`build/audit-tools/preview-tools-validation-online-retry.*`.

Earlier integrated runs exposed lifecycle-fixture timeouts, platform URI assertions
and Compose action selectors. Corrections preserve the behavioral assertions and
do not disable tests or extend timeouts. The later complete host and selected
phone suites above confirm these corrections.

The `c09ec88` run passed the five browse-lifecycle cases and the real tablet media
long-press/action-ownership case. Its four failures were two SDK variants of the
broken-image reader fixture (the legacy Robolectric bitmap shadow fabricated a
decode result) and two thumbnail lifecycle waits. The reader fixture now requests
native graphics, and the thumbnail waits synchronize pending composition before
checking cancellation/reload. These retain the original failure/closure assertions.

The first phone run was cancelled during recreation. Debugger inspection found
Android's main thread idle and the runner blocked in
`ActivityScenario.recreate → Instrumentation.waitForIdleSync`. The corrected test
keeps advancing Compose frames while recreation waits in a worker. This is test
synchronization, not a reason to skip recreation or accept missing saved state.
Two new expression-bodied fixture methods also needed explicit `runBlocking<Unit>`
so JUnit sees void test methods. The cancelled run has no passing suite result;
its reported process termination was caused by cancelling our stalled run.

Contracts: [Compose test synchronization](https://developer.android.com/develop/ui/compose/testing/synchronization)
and [ActivityScenario recreation](https://developer.android.com/reference/androidx/test/core/app/ActivityScenario).

The focused retry passed all 24 image/thumbnail host cases, custom Lint tests and
ktlint/detekt, and packaged instrumentation. The second phone run passed PDF native
rendering, BMP fallback, all eight JPEG orientation/hash checks, DOCX routing/find,
ordinary recreation and saved-theme rendering. Notification recreation timed out
and its cleanup failed; decoded thumbnail pixels also timed out. These were
acceptance failures at that attempt. New diagnostics assert the actual Coil decode result,
and the notification test preserves the launch intent's filter identity, which
ActivityScenario requires to track lifecycle events after `setIntent`. Production
notification intent flags retain their existing behavior. PNG thumbnail decoding
and displayed pixels passed first; the later complete native run also passed
decoded folder-miniature pixels.

A separate manual browse run crashed with
`MediaMetadataRetriever.finalize() timed out after 10 seconds`. This is a real
native failure, distinct from cancelling the first test run. Decorative video
requests have been changed to provider thumbnails with bounded concurrency and
cancellation; their integrated and phone reruns subsequently passed. Explicit media metadata
and playback acceptance remain separate from decorative thumbnail acceptance.

The authorized phone is Android14/API34. Device fixtures create and remove their
own private cache files. The selected surface run must avoid user-file mutations,
uninstall/data clearing and recovery/performance stress. GitHub CI diagnostics
remain unavailable until the Code Review connector account is connected; local
results must not be presented as remote CI success.

## Tasks popup follow-up

The former header About shortcut is replaced by Tasks. About remains available in
Settings/Commands. The compact anchored popup uses the existing Atomic colours,
borders, typography and hard shadow. It observes the real queue and process-wide
Wi-Fi HTTP transfers, includes pending jobs, determinate/unknown progress, single
request cancellation, conflict review and an Operations link. It dismisses with
Close, outside touch or Back without cancelling work. Network backend copies are
labelled Upload/Download; moves retain their Move label. Decorative sample jobs
are never inserted into the production queue.

Wi-Fi entries are created only after request authorization/path checks and removed
in worker finally blocks. Cancellation closes only the owned request socket;
upload staging/publication checks cancellation and preserves existing-name guards.
Progress emissions are limited to ten per second per transfer plus final bytes.
Live HTTP tasks are process-local and do not claim durable transfer recovery.
Host loopback tests use generated temporary files. Real network hardware acceptance
remains outside the authorized phone surface run.

Implementation reference: [Compose Popup](https://developer.android.com/reference/kotlin/androidx/compose/ui/window/package-summary).

The combined Tasks APKs passed `OK (12 tests)` on API34. A physical capture then
confirmed the anchored popup, active header styling, Close/Back/outside dismissal,
and return to Home. Neither selected nor manual task logs contains a StrictMode
or AndroidRuntime error. Evidence is retained under
`build/audit-tools/device-preview-tools/tasks-only-12-*` and `tasks-*.png`.

Historical Tasks-only debug APK SHA256:
`4a8e4f457a39f06e439fc1abe552f1d32bcb56a4fdbb13d97c82eddeb99002b6`.
Historical Tasks-only instrumentation APK SHA256:
`c451947f519a6ec0d36b0e11e0df0d89d50b60534fafe1dce170ae1c53a6425d`.
Full source hashes and host logs are under ignored `build/audit-tools/tasks-*`.
