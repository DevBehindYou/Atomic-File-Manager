# Search-index folder consistency — U05 slice

Prepared 21 October 2026. Source candidate only; host and database checks have not yet run.

Folder refresh previously treated a flow with no emissions as an empty directory. It also committed inserts and disappearance cleanup separately, allowing an interrupted reconciliation to persist only part of the folder update.

The candidate accepts a folder snapshot only after at least one successful emission, no failure emission and normal completion. A successful explicit empty list clears the indexed children. Failure, silence or cancellation preserves the previous folder evidence. Enumeration occurs outside a Room transaction; a DAO transaction commits inserts and disappeared-child cleanup together. The existing path-based descendant contract and wildcard escaping remain in place.

One writer mutex serializes full builds and folder reconciliations. A reconciliation can wait behind a running full walk; this is a consistency change, not a responsiveness improvement. No database transaction spans filesystem enumeration.

A full walk with unreadable/silent folders does not globally prune old generations or advance the last successful build time. Searches may still see mixed old and new rows from flushed batches. Known unreadable walks retry within the previous freshness window while the index instance remains alive. Empty root input cannot erase the index. Durable scan checkpoints, persisted failure state, coherent all-root snapshots and account/source ownership remain separate work.

Database version 4, entity columns and exported schema are unchanged. No migration, production dependency, storage permission or user-file mutation is introduced.

Prepared checks cover explicit empty versus silent/denied enumeration, partial cancellation, writer ordering, incomplete build evidence, missing roots and retry. Real file-backed Room tests on API27/34 cover successful reconciliation and reopen, sibling/root preservation, an injected delete failure rolling back earlier inserts, enclosing-transaction cancellation, wildcard escaping and rejection of another parent's rows. The rollback test must observe the injected failure itself. These tests do not establish process-kill recovery.

Root coordinates serialized validation. Required: formatting/static analysis, `RoomSearchIndexTest`, `SearchIndexReconciliationTest`, existing Room migration cases, APK compilation and Android Lint. Record terminal logs and actual results before claiming any pass. Broader U05 acceptance remains open.
