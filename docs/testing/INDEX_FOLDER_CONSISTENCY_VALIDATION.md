# Search-index folder consistency — U05 slice

Prepared 21 October 2026. The combined 48-case host validation, APK packaging and Android Lint pass. Host validation of the CI teardown follow-up and fresh isolated PR CI remain pending.

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

The follow-up waits for each navigation model's cancellation to finish before resetting Main. Existing navigation and clipboard assertions remain intact. A regression uses a real model and IO listing whose cancellation cleanup waits on a controlled gate; teardown must stay suspended until that cleanup finishes. No sleeps, exception suppression or production-index changes are introduced. Root is running serialized follow-up checks from integration source `7b66082` in `build/audit-tools/index-ci-teardown-validation.log`; no completed result is claimed yet. A fresh complete CI run also remains required.
