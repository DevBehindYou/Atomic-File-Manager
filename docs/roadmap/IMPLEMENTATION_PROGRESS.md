# Implementation progress

19 October 2026; baseline `7782660`. Weights/acceptance: [OPTIMIZATION_ROADMAP](OPTIMIZATION_ROADMAP.md).

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
| M12 | Partial | Verified wrapper checksum and corrected dependency/privacy facts; phone confirmed existing main-thread preference reads |
| M13 | Partial; owner and broader device gates remain | API34 surface run and four automated UI tests pass; full provider/file/recovery/performance acceptance and production signing remain |

Implemented scope: M01–M11, 47/60 weighted units (about **78% of the full modernization roadmap**); M01–M07 is 26/26 first-batch units implemented. M12 is partial and M13 device/release acceptance is outstanding. This is implementation coverage, not release readiness.

Acceptance is tracked separately from the 78% implementation figure. Passing host tests and one phone surface run do not complete provider, process-death, performance, human-review or owner-signing acceptance. No release-readiness percentage is claimed.

Current validation: all **663 app JVM/Robolectric tests** and **28 custom Lint-rule tests** pass. Gradle ktlint, detekt, debug/release Android Lint, debug/release APK assembly and debug instrumentation APK assembly pass across the recorded runs. Debug Lint reports zero errors and 148 warnings. Benchmark APK packaging is outstanding. Existing experimental-option and duplicate-Kotlin-plugin warnings remain; no dependency upgrade was performed.

The connected Android14/API34 phone passed manual surface checks and **four automated navigation/recreation tests** with ADB shell storage access. [Device report](../testing/DEVICE_SURFACE_TEST_2026-10-19.md) records the exact scope and main-thread disk-read findings. No user-file mutation test was run. GitHub authentication succeeds outside the sandbox. Fifteen draft PRs are open, [#44–58](REVIEW_BRANCHES.md); none is merged. The Code Review connector cannot inspect remote CI without connecting its GitHub account, so no remote green-check claim or automatic merge is made.

Independent review and full-suite failures produced fixes for canonical DAV names, multiline FTP replies, incomplete Android XML parsing, network root metadata, correctly attached provider fixtures, paused Android cancellation callbacks, and Windows storage fixtures. Assertions were retained and the complete app suite now passes. Approval-service usage failures recovered after reset. A later publication rejection was resolved by showing the explicit user PR instructions and verifying the existing repository destination; no approval block remains at this checkpoint.
