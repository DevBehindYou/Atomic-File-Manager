# Testing

## Current validation — 20 October 2026

Current source `4792db8` includes Files storage shortcuts, persisted List/Grid views, content-sized top options, responsive icon-card action menus and distinct theme-aware icons/colours for 15 file families. All 917 app JVM/Robolectric cases, 28 custom Lint-rule cases, ktlint/detekt and debug/instrumentation packaging pass. All 13 selected API34 phone cases pass; manual popup sizing, Commands/Select grids and coloured folder icons are verified. The installed debug APK matches the recorded build hash, and the settled app is back on Home. The current selected log contains no StrictMode or fatal errors; a prior InsetsSourceControl warning remains historical evidence. Debug/release Android Lint, R8 release and benchmark test-APK packaging passed in the complete Gradle run (exit 0). See [menu/icon validation](../testing/MENU_REFINEMENT_VALIDATION.md), [Files validation](../testing/FILES_WORKSPACE_VALIDATION.md) and [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md). Broader provider, hardware, recovery, performance and owner-signing acceptance remains open.

Current modernization verification: see [IMPLEMENTATION_PROGRESS](../roadmap/IMPLEMENTATION_PROGRESS.md) and [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md). The previous complete host checkpoint `5705606` passed 712 app tests, 28 custom Lint tests, ktlint/detekt and debug/instrumentation packaging. Four automated phone tests passed earlier; the later startup manual surface recorded zero StrictMode disk warnings. The current validation above supersedes that host/device checkpoint. [MODERNIZATION_VALIDATION](../testing/MODERNIZATION_VALIDATION.md) preserves earlier Lint/release evidence. Broader device/recovery/performance acceptance remains. Do not apply historical green CI to the current branch.

Historical last verified feature commit: `e2b1509` (`main`)

| Check | Status | Evidence |
|---|---|---|
| ktlint, detekt, Android Lint (debug and release, custom rules) | VERIFIED | CI job "Static analysis" green on every merged PR |
| Unit and Robolectric tests | VERIFIED | CI job "Unit tests and APK packaging": about 535 tests, plus a Robolectric smoke test on SDK 27 and 34 |
| Debug and R8 release APK packaging | VERIFIED | Same CI job |
| Process-kill recovery (simulated) | VERIFIED | `ProcessKillTest`, 4 kill points (JVM) |
| SFTP backend | VERIFIED (in-process server) | sshd-based tests |
| Instrumented device tests | UNKNOWN for current `main` | The emulator job runs only by hand (`workflow_dispatch`); the last known pass was 12 tests on an API 34 phone, on 15 Sep 2026, before Phases 1–4 |
| Release launch and monkey (10,000 events) | NOT RUN | Added in PR #39 |
| Macrobenchmarks (startup, scroll, baseline profile) | NOT RUN | They need a device |

## How to run locally

```bash
./gradlew ktlintCheck detekt :app:lintDebug :app:lintRelease
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease
```

CI (`.github/workflows/ci.yml`) is the build authority. Agents without an Android SDK can still run ktlint and detekt as standalone jars on changed files. Pure Kotlin tests can run in a plain Kotlin/JVM Gradle project that compiles `domain/`, `data/operations`, `data/search` and similar folders.

## Not tested (needs hardware)

SMB live, mDNS, USB OTG, Shizuku, Wi-Fi share from a browser, a real process kill, cold start and scroll budgets, copy speed on SD and USB. See `PENDING.md` P0.

## Known gaps

- No large-data test (100,000 files, a 4 GB file); see TASK-013.
- The device matrix covers only an API 34 phone.
