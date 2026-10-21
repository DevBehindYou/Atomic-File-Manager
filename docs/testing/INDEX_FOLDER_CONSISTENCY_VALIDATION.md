# Search-index folder consistency — U05 slice

Prepared 21 October 2026. The combined 48-case host validation, APK packaging and Android Lint pass. The 38-case CI teardown follow-up also passes. Isolated CI at `b985fc2` ran 941 cases with one process-kill fixture failure; the next fixture-only correction awaits validation.

Folder refresh previously treated a flow with no emissions as an empty directory. It also committed inserts and disappearance cleanup separately, allowing an interrupted reconciliation to persist only part of the folder update.

The candidate accepts a folder snapshot only after at least one successful emission, no failure emission and normal completion. A successful explicit empty list clears the indexed children. Failure, silence or cancellation preserves the previous folder evidence. Enumeration occurs outside a Room transaction; a DAO transaction commits inserts and disappeared-child cleanup together. The existing path-based descendant contract and wildcard escaping remain in place.

One writer mutex serializes full builds and folder reconciliations. A reconciliation can wait behind a running full walk; this is a consistency change, not a responsiveness improvement. No database transaction spans filesystem enumeration.

A full walk with unreadable/silent folders does not globally prune old generations or advance the last successful build time. Searches may still see mixed old and new rows from flushed batches. Known unreadable walks and exceptional public rebuild failures retry within the previous freshness window while the index instance remains alive. A successful full build clears the exceptional retry flag; cancellation still propagates. Empty root input cannot erase the index. Durable scan checkpoints, persisted failure state, coherent all-root snapshots and account/source ownership remain separate work.

Database version 4, entity columns and exported schema are unchanged. No migration, production dependency, storage permission or user-file mutation is introduced.

Prepared checks cover explicit empty versus silent/denied enumeration, partial cancellation, writer ordering, incomplete build evidence, missing roots and retry. Real file-backed Room tests on API27/34 cover successful reconciliation and reopen, sibling/root preservation, an injected delete failure rolling back earlier inserts, enclosing-transaction cancellation, wildcard escaping and rejection of another parent's rows. The rollback test must observe the injected failure itself. These tests do not establish process-kill recovery.

Root coordinates serialized validation. Required: formatting/static analysis, `RoomSearchIndexTest`, `SearchIndexReconciliationTest`, existing Room migration cases, APK compilation and Android Lint. Record terminal logs and actual results before claiming any pass. Broader U05 acceptance remains open.

## Current host evidence

The combined browse/index run `build/audit-tools/browse-index-consistency-retry-validation.log` at root integration source `5902824` completed successfully with exit 0 in 12 minutes 2 seconds. Formatting, ktlint and detekt passed, along with 48 selected cases with no failures/errors/skips. Its index subset is 21 `RoomSearchIndexTest` cases, 8 file-backed `SearchIndexReconciliationTest` cases across API27/34, and 1 existing `AtomicDatabaseMigrationTest` case. Debug and instrumentation APKs packaged successfully. Android Lint reported 0 errors, 154 warnings and 1 hint. The exported schema has no diff. This evidence precedes the CI teardown follow-up and does not replace isolated current-head PR CI or physical recovery acceptance.

## CI teardown follow-up

PR #67 run `39616679288` completed 940 tests with one failure before `BrowseNavigationIdentityTest.switchingStorageKeepsCopyAndMoveClipboardsAndBackHistory` entered its test body. Its report records a cancelled `Dispatchers.Default` continuation accessing Main after `Dispatchers.resetMain()`. Both navigation tests cancelled their model without joining its children, unlike the already corrected sort-loading fixture.

The follow-up waits for each navigation model's cancellation to finish before resetting Main. Existing navigation and clipboard assertions remain intact. A regression uses a real model and IO listing whose cancellation cleanup waits on a controlled gate; teardown must stay suspended until that cleanup finishes. No sleeps, exception suppression or production-index changes are introduced.

The serialized follow-up from integration source `7b66082`, with formatter output recorded in `2aa3625`, completed with exit 0 in 2 minutes 24 seconds (`build/audit-tools/index-ci-teardown-validation.log`). Formatting/static checks passed, as did all 38 selected cases with no failures/errors/skips: migration 1, DAO reconciliation 8, index 21, navigation 3 and sort-loading 5. A fresh complete CI run remains required.

## Process-kill fixture scheduling follow-up

PR #67 run `39619803103` at `b985fc2` ran 941 tests with one failure. Its downloaded report under `build/audit-tools/index-ci-second-report/` records `ProcessKillTest` line 140: expected `RUNNING`, actual `QUEUED`. The preceding no-partial-real-name assertion passed. This failure is a journal-state scheduling assumption, not evidence that a partial file was published.

The engine emits initial running progress before copying but uses buffered `flowOn(Dispatchers.IO)`. Its producer can reach the test's simulated mid-write death before the queue's journal consumer persists that progress. The fixture freezes later writes after death, leaving a valid admitted `QUEUED` intent. Increasing payload size does not guarantee journal delivery. Production recovery already considers both queued and running records unfinished; the index PR does not change this engine, queue or recovery code.

The fixture now waits for acknowledgement of an actually forwarded `RUNNING` journal update before opening its output. The four existing kill locations, exact `RUNNING` assertions, partial cleanup, completed destination bytes and untouched source assertions remain unchanged. A new deterministic coroutine-scheduler case holds the running journal write at a gate, proves the first storage write and kill cannot happen while the journal remains queued, then releases delivery and verifies the original mid-write kill and staged bytes. There are no sleeps, relaxed assertions or production-engine changes.

This fixture-only correction was source-reviewed and passed serialized formatting, ktlint and detekt plus 38 selected cases, with no failures/errors/skips (`build/audit-tools/index-process-kill-fixture-validation.log`, terminal exit 0, 2m52s). The cases are ProcessKill 5, navigation 3, index 21, DAO reconciliation 8 and migration 1. The combined root includes unrelated opening/layout changes; the relevant index and fixture sources match this branch. A fresh complete CI run on the published PR head remains required; the earlier failing CI records are retained.
