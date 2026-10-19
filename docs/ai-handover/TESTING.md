# Testing

Current modernization verification: see [MODERNIZATION_VALIDATION](../testing/MODERNIZATION_VALIDATION.md), [IMPLEMENTATION_PROGRESS](../roadmap/IMPLEMENTATION_PROGRESS.md) and the [API34 phone surface report](../testing/DEVICE_SURFACE_TEST_2026-10-19.md). All 663 app tests, 28 custom Lint tests, ktlint/detekt, debug/release Lint and debug/release/instrumentation APK assembly pass. Four automated phone UI tests and manual surface checks pass. Benchmark packaging and broader device/recovery/performance acceptance remain. Do not apply historical green CI to the current branch.

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
