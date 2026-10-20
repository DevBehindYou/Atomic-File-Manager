# Implementation progress

21 October addition: the expanded [feature upgrade plan](FEATURE_UPGRADE_PLAN.md) is complete as a planning deliverable. Its U01–U13 ledger is separate from the modernization percentage below. The bundled plugins inventory is implemented; see [increment validation](../testing/BUILT_IN_PLUGINS_VALIDATION.md). This does not mark the new cloud/viewer/performance requirements complete.

20 October 2026; baseline `7782660`. Weights/acceptance: [OPTIMIZATION_ROADMAP](OPTIMIZATION_ROADMAP.md).

| Task | State | Evidence |
|---|---|---|
| M01 | Complete | Source audits and seven source-linked planning documents created before production edits; independently reviewed findings |
| M02 | Implemented; host regressions pass | Staged archive output, preserved collisions, propagated failures and opaque child IDs; subtree/cycle guards and 18 regression tests |
| M03/M06/M07 | Implemented; host regressions pass | Durable admission, terminal-state cancellation repair, IO dispatch and actual progress throttling |
| M04 | Implemented; host regressions pass | Exclusive upload staging/publication and bounded socket lifetime |
| M05 | Implemented; host regressions and phone surface pass | Secondary pane lifetime and compact-layout loading; primary tab ownership deferred |
| M08/M10 | Implemented; host regressions pass | Cancellation-safe trash reconciliation and filtered search before result cap |
| M09 | Implemented; host/provider fixtures pass | SAF ancestry and destination guard, actual-ID breadcrumbs/history, terminal network parents; actual provider acceptance remains |
| M11 | Implemented; protocol fixtures pass | FTPS control/data TLS, bounded replies/listings, complete bounded DAV XML, conditional mutations and canonical paths; live-server acceptance remains |
| M12 | Partial | Verified wrapper checksum and dependency/privacy facts; screen, startup and comparison preference IO changes implemented; duplicate Kotlin-plugin loading and shared-storage permission adapter changes integrated. 917 app host cases and 13 selected phone cases pass; debug/release Lint and release/benchmark test-APK packaging pass |
| M13 | Partial; owner and broader device gates remain | API34 manual checks and all 13 selected automated surface cases pass; retained Android resource diagnostic, full provider/file/recovery/performance acceptance and production signing remain |

Implemented scope: M01–M11, 47/60 weighted units (about **78% of the full modernization roadmap**); M01–M07 is 26/26 first-batch units implemented. M12 is partial and M13 device/release acceptance is outstanding. This is implementation coverage, not release readiness.

Acceptance is tracked separately from the 78% implementation figure. Passing host tests and one phone surface run do not complete provider, process-death, performance, human-review or owner-signing acceptance. No release-readiness percentage is claimed.

Source `4792db8` passes **917 app JVM/Robolectric tests**, **28 custom Lint-rule tests**, ktlint/detekt, debug/instrumentation packaging and debug Android Lint. Debug/release Android Lint, R8 release and benchmark test-APK packaging passed in the complete 14m36s Gradle run (exit 0). No dependency version was upgraded.

Current source `4792db8` includes comparison preference IO, thumbnails/folder miniatures, contextual actions, DOCX/PDF/media/image/editor improvements, provider video thumbnails, the live Tasks popup, Files storage/view/options controls, content-sized top popups, bottom icon-card menus and typed file icons. All 13 selected API34 cases pass; manual row actions, real video thumbnails/playback frame, popup placement/dismissal, Grid/List and Internal/SD switching also pass. See [menu/icon validation](../testing/MENU_REFINEMENT_VALIDATION.md), [Files validation](../testing/FILES_WORKSPACE_VALIDATION.md), [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md), [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md) and [thumbnail scope](../testing/FILE_THUMBNAILS_VALIDATION.md).

The Android14/API34 phone passed four automated navigation/recreation tests at the earlier checkpoint. After the startup fix, manual Home → Files → Storage → Settings → Home checks recorded zero StrictMode disk warnings, fatal exceptions or ANRs. The current 13-case suite also covers native PDF/image/folder display, DOCX find, notification recreation, Tasks and Files controls. The current selected log contains no StrictMode or fatal error; the preceding Files run retains one nonfatal InsetsSourceControl/SurfaceControl resource diagnostic. The [storage-card layout fix](../testing/STORAGE_CARD_LAYOUT_2026-10-19.md), `8ee61d0`, passed its own packaging, formatting and four phone tests. [Original device report](../testing/DEVICE_SURFACE_TEST_2026-10-19.md) preserves initial findings. No user-file mutation test was run. Seventeen draft PRs are open, [#44–60](REVIEW_BRANCHES.md); none is merged. The Code Review connector cannot inspect remote CI without connecting its GitHub account, so no remote green-check claim or automatic merge is made.

Independent review and full-suite failures produced fixes for canonical DAV names, multiline FTP replies, incomplete Android XML parsing, network root metadata, correctly attached provider fixtures, paused Android cancellation callbacks, and Windows storage fixtures. Assertions were retained and the complete app suite now passes. Approval-service usage failures recovered after reset. A later publication rejection was resolved by showing the explicit user PR instructions and verifying the existing repository destination; no approval block remains at this checkpoint.
