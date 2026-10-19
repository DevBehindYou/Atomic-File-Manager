# Current State

Last updated: 2026-10-19

## Active modernization work

The 17–18 October audit found correctness gaps beyond the historical feature-completion claims below. Current source baseline is `7782660`; integration branch is `codex/architecture-audit`. Read [implementation progress](../roadmap/IMPLEMENTATION_PROGRESS.md), [audit](../architecture/PROJECT_AUDIT.md), and [current architecture](../architecture/CURRENT_ARCHITECTURE.md) first. The roadmap uses verified weighted acceptance, not the historical ~99% feature estimate.

Integrated work covers archive staging, durable admission, upload safety, browse lifecycle, IO dispatch, progress throttling, trash recovery, filtered search, SAF ancestry, actual-ID breadcrumbs and FTP/WebDAV hardening. All 663 app JVM/Robolectric tests and 28 custom Lint-rule tests pass; standard ktlint/detekt pass. Android Lint and packaging are running. Fourteen draft PRs are listed in [REVIEW_BRANCHES](../roadmap/REVIEW_BRANCHES.md); none is merged. Device metrics and production signing remain unverified. Historical green CI must not be applied to these changes.

The remainder is the preserved 8 October feature handover, not current validation evidence.
Verified against commit: `e2b1509` (`main`)
Confidence: HIGH for code and CI. LOW for on-device behaviour, which has not been run since 15 Sep 2026.

## Build

All CI gates on `main` are green: ktlint, detekt, Lint, unit and Robolectric tests, and the debug and R8 release APKs. Release is debug-signed (ISS-001).

## Features

| Area | Status | Note |
|---|---|---|
| Plan Phase 0 (queue, database, navigation, credentials, R8 and benchmark) | IMPLEMENTED | `AtomicRoute` stack, not Navigation Compose; Keystore credentials |
| Phase 1 (trash, favourites and recents, selection, indexed search, viewers) | IMPLEMENTED | |
| Phase 2 (rename, archives, editor, tabs, vault, cleanup, apps, checksums) | IMPLEMENTED | |
| Phase 3 (SFTP, SMB, discovery, cloud providers, Wi-Fi share, USB) | IMPLEMENTED_UNTESTED on hardware | SFTP has an in-process server test |
| Phase 4 (compare and sync, metadata, Shizuku, palette, shortcuts and widget) | IMPLEMENTED | Shizuku not tested on a device |
| §16 optimization | PARTIAL | See the table below |

## §16 optimization status

| Item | Status |
|---|---|
| H1 volumes off the main thread | IMPLEMENTED |
| H2 SQLite off the main thread | IMPLEMENTED |
| H3 R8 | IMPLEMENTED |
| H4 baseline profile | PARTIAL: installer and generator in place, profile not generated (TASK-002) |
| H5 one shared walk | PARTIAL: the index seeds categories (ADR-004, TASK-010) |
| H6 saved analysis | PARTIAL: in memory for 10 minutes (TASK-011) |
| H7 thumbnails | IMPLEMENTED (Coil 3) |
| H8 copy buffer | IMPLEMENTED (256 KB), not measured (TASK-016) |
| H9 large screen files | PARTIAL: Browse split, `MainActivity` not (TASK-012) |
| H10 Media3 | IMPLEMENTED |
| StrictMode and JankStats in debug | IMPLEMENTED |
| Monkey test | IMPLEMENTED in the manual job, NOT RUN (TASK-001) |
| Large-data test | NOT_IMPLEMENTED (TASK-013) |

## Git

- Branch: `main`
- Commit: `e2b1509`
- Open PRs: none after this handover merges.
- Agents work on `claude/<topic>` branches. A merged branch is never reused.

## Workarounds

- R8: `-dontwarn org.ietf.jgss.**`, `javax.security.auth.login.**` (for sshj).
- BouncyCastle pinned to 1.86 (ADR-005).
- The mount receiver is exported (ADR-006).

## Open blockers

- No device or emulator was available to the agent. Every P0 task in `PENDING.md` needs one.
- Production signing and the Play policy review need the owner.
