# Implementation progress

19 October 2026; baseline `7782660`. Weights/acceptance: [OPTIMIZATION_ROADMAP](OPTIMIZATION_ROADMAP.md).

| Task | State | Evidence |
|---|---|---|
| M01 | Complete | Source audits and seven source-linked planning documents created before production edits; independently reviewed findings |
| M02 | Integrated; validation pending | Staged archive output, preserved collisions, propagated failures and opaque child IDs; subtree/cycle guards and 18 regression tests |
| M03/M06/M07 | Implemented; validation pending | Durable admission, terminal-state cancellation repair, IO dispatch and actual progress throttling; new regression tests |
| M04 | Integrated; validation pending | Local commit f58d3d8: exclusive upload staging/publication and bounded socket lifetime |
| M05 | Integrated; validation pending | Local commit 276b593: secondary pane lifetime and compact-layout loading; primary tab ownership deferred |
| M08/M10 | Integrated; validation pending | Cancellation-safe trash reconciliation and filtered search before result cap; focused regression tests |
| M09 | Integrated; unit/provider tests pass; final gates pending | SAF ancestry and destination guard, actual-ID breadcrumbs/history, terminal network parents |
| M11 | Integrated; protocol tests pass; final gates pending | Verified FTPS control/data TLS, bounded replies/listings, bounded complete DAV XML, conditional mutations and canonical paths |
| M12 | Partial | Verified Gradle wrapper checksum; further dependency/diagnostic work open |
| M13 | Blocked on environment/owner | Device acceptance, production signing, external services |

Verified acceptance complete: 3/60 weighted units (5% full roadmap), 3/26 first batch (about 12%). Implementation and verification are tracked separately; no subjective code-complete claim.

Current validation: all **663 app JVM/Robolectric tests pass**, and all **28 custom Lint-rule tests pass**. Standard Gradle ktlint and detekt also pass. Android Lint and packaging are running; acceptance weights remain conservative until these required integrated gates finish. Production/test Kotlin compilation passes. Existing experimental-option and duplicate-Kotlin-plugin warnings remain; no dependency upgrade was performed.

No adb device is connected. GitHub authentication succeeds outside the sandbox. Fourteen focused draft PRs are open, [#44–57](REVIEW_BRANCHES.md); none is merged. The Code Review connector cannot inspect remote CI without connecting its GitHub account, so no remote green-check claim or automatic merge is made. Tests use generated isolated fixtures, not actual user files.

Independent review and full-suite failures produced fixes for canonical DAV names, multiline FTP replies, incomplete Android XML parsing, network root metadata, correctly attached provider fixtures, paused Android cancellation callbacks, and Windows storage fixtures. Assertions were retained and the complete app suite now passes. Approval-service usage failures recovered after reset. A later publication rejection was resolved by showing the explicit user PR instructions and verifying the existing repository destination; no approval block remains at this checkpoint.
