# Decisions

Only decisions that will shape future work. The owner's product decisions are in `docs/roadmap/ALL_IN_ONE_PLAN.md` §10, and are not repeated here.

## ADR-001 — Manual DI container, not Hilt

Status: ACTIVE

Decision: New services are wired in `AppContainer` / `DefaultAppContainer` (`AtomicApp.kt`).

Reason: That is the graph that actually runs. Hilt is declared but not used.

Consequence: Add a `val` to the interface, plus a `by lazy` in `DefaultAppContainer`. Test fakes implement the interface.

## ADR-002 — One callback for opening routes in the shell

Status: ACTIVE

Decision: `ShellCallbacks.onOpenRoute: (AtomicRoute) -> Unit` replaces the separate per-screen callbacks.

Reason: detekt's `LongParameterList` threshold is 15, and the shell kept hitting it (PR #32).

Revisit when: a route needs arguments that `AtomicRoute` can't carry.

## ADR-003 — Copies hash while streaming; no `FileChannel.transferTo`

Status: ACTIVE (PR #38)

Decision: A 256 KB user-space buffer that feeds both the output and SHA-256.

Reason: A kernel-side copy skips the buffer, so verifying it would need a second full read of the source. That costs more than the syscalls it saves.

Revisit when: verification becomes optional per copy (plan §16.5, "Verify: always / large files / off").

## ADR-004 — Categories show index files first, but the walk stays

Status: ACTIVE (PR #40)

Decision: `PhoneFileIndex` shows the search index's files at once. Only the finished walk replaces them.

Reason: The index refreshes every 6 hours plus after the app's own operations. Without MediaStore-generation change detection, a photo the camera just took would be missing from Images.

Revisit when: `MediaStore.getGeneration` freshness (plan §16.4) is in place. Then drop the walk.

## ADR-005 — BouncyCastle pinned to one version

Status: ACTIVE

Decision: `bcprov`, `bcutil` and `bcpkix` are all 1.86.

Reason: sshj and smbj pulled in mismatched versions, and `checkDebugDuplicateClasses` failed (PR #28).

Consequence: Upgrade all three together.

## ADR-006 — Receivers registered as exported where Android 8.1 needs it

Status: ACTIVE

Decision: The USB/SD mount receiver in `MainActivity` uses `RECEIVER_EXPORTED`.

Reason: `ContextCompat`'s `RECEIVER_NOT_EXPORTED` goes through a permission path on old Android, and that crashed the API 27 smoke test (PR #33). These are system broadcasts, so exporting the receiver exposes nothing.

## ADR-007 — Shizuku is read-only, under Labs

Status: ACTIVE (PR #36)

Decision: `BackendType.SHIZUKU` maps to `READ_ONLY`. It is turned on by the user in Labs.

Reason: Plan §4.3 and the §10 decision. Play policy has not been checked yet (TASK-006).
