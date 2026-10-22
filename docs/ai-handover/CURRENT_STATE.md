# Current State

22 October evening checkpoint: fixed progress remains **8% host verified / 8% accepted (1/13)**. Feature integration includes Storage/Analysis #69 (`32a506e`), Browse lifecycle #70 (`d55292d`), startup first-draw repair #72 (`952ba19`) and centered Analysis action #73 (`dbb43f0`), alongside earlier accepted slices. The black startup wait is reproduced by actual API27/34 pre-draw failures, then repaired with 31 selected host cases/static/APK/Lint passing. The final combined app passes all four API34 native cases in 25.425s; its idle Analysis screenshot confirms cleanup button and viewport centers both at 540px. Home, access and test-handler removal are verified without clearing user data. Tab-session #71 has 42 focused host cases and its native close/reopen/recreation pass; its synchronized final app tree `9ec9b8dc8acda01fe5e37fb9e72eaf95876b1c64` matches the phone-tested artifact, and final head `67adad8` passes CI `39804269314`, review-thread inspection and a clean guarded trial; integration merge is `e60d8e7`. Retained-store pruning, mutation ownership, providers/process death, durable scanning, performance and main/release acceptance remain open. Benchmark #63 source CI is green, but its measured smoke still has no accepted baseline; never treat system-only frame rows or packaging as performance evidence. Advanced eight-plugin modernization remains a separate deferred intake after existing gates. **Manual continuation only: the recurring automation was permanently deleted; never recreate a schedule without a new explicit request.** Read the ledger, live PRs and exact validation records before continuing; preserve artwork, files, settings, permissions, ignored tools and signing backups.

## Feature planning and bundled plugins — 21 October 2026

The [feature upgrade plan](../roadmap/FEATURE_UPGRADE_PLAN.md) is complete, with ordered increments, dependencies and acceptance checks. The user chose bundled viewers/tools listed in Settings → In-app plugins; no remote executable installation or independent plugin updates. That inventory is accepted into the feature integration branch through PR #61, including its recorded phone surface check. Its [focused validation](../testing/BUILT_IN_PLUGINS_VALIDATION.md) is separate from the full-suite checkpoint below. Opening defaults are under validation; direct cloud accounts, advanced viewer capabilities and measured performance improvements remain planned. The existing 78% modernization figure does not describe this expanded scope.

## Current validation — 20 October 2026

Current source `4792db8` includes Files storage shortcuts, persisted List/Grid views, content-sized top options, responsive icon-card action menus and distinct theme-aware icons/colours for 15 file families. All 917 app JVM/Robolectric cases, 28 custom Lint-rule cases, ktlint/detekt and debug/instrumentation packaging pass. All 13 selected API34 phone cases pass; manual popup sizing, Commands/Select grids and coloured folder icons are verified. The installed debug APK matches the recorded build hash, and the settled app is back on Home. The current selected log contains no StrictMode or fatal errors; a prior InsetsSourceControl warning remains historical evidence. Debug/release Android Lint, R8 release and benchmark test-APK packaging passed in the complete Gradle run (exit 0). See [menu/icon validation](../testing/MENU_REFINEMENT_VALIDATION.md), [Files validation](../testing/FILES_WORKSPACE_VALIDATION.md) and [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md). Broader provider, hardware, recovery, performance and owner-signing acceptance remains open.

Last updated: 2026-10-20

## Active modernization work

The 17–18 October audit found correctness gaps beyond the historical feature-completion claims below. Current source baseline is `7782660`; integration branch is `codex/architecture-audit`. Read [implementation progress](../roadmap/IMPLEMENTATION_PROGRESS.md), [audit](../architecture/PROJECT_AUDIT.md), and [current architecture](../architecture/CURRENT_ARCHITECTURE.md) first. The roadmap uses verified weighted acceptance, not the historical ~99% feature estimate.

Integrated work covers archive/queue/upload safety, browse ownership, IO dispatch, trash/search, provider identity and FTP/WebDAV hardening: about 78% of the weighted roadmap. Before the current validation above, the complete host checkpoint was `5705606` (712 app tests, 28 custom Lint-rule tests); earlier Lint/release evidence is checkpoint-specific. Startup preference IO now passes a manual API34 surface run with zero StrictMode disk warnings. Earlier checkpoint `c09ec88` added comparison preference IO, shared-storage access, plugin-loading cleanup, thumbnails/folder miniatures, contextual actions and PDF/DOCX/media/image/editor improvements; those changes are included in the current validation above. See the [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md). Broader device/performance acceptance and production signing remain open. Seventeen draft PRs are listed in [REVIEW_BRANCHES](../roadmap/REVIEW_BRANCHES.md); none is merged. Historical green CI must not be applied to these changes.

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
