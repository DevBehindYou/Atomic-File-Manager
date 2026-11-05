# Pending

## Expanded feature backlog — 21 October 2026

The [feature upgrade plan](../roadmap/FEATURE_UPGRADE_PLAN.md) now defines U01–U13 with dependencies and exit checks. The built-in plugins inventory has its [own validation](../testing/BUILT_IN_PLUGINS_VALIDATION.md). Remaining implementation starts with benchmark correctness and measured optimization; file-opening defaults, direct cloud accounts, deeper viewer improvements and recovery/release acceptance remain open. Remote plugin downloads were explicitly rejected by the user; keep features bundled.

## Current validation — 20 October 2026

Current source `4792db8` includes Files storage shortcuts, persisted List/Grid views, content-sized top options, responsive icon-card action menus and distinct theme-aware icons/colours for 15 file families. All 917 app JVM/Robolectric cases, 28 custom Lint-rule cases, ktlint/detekt and debug/instrumentation packaging pass. All 13 selected API34 phone cases pass; manual popup sizing, Commands/Select grids and coloured folder icons are verified. The installed debug APK matches the recorded build hash, and the settled app is back on Home. The current selected log contains no StrictMode or fatal errors; a prior InsetsSourceControl warning remains historical evidence. Debug/release Android Lint, R8 release and benchmark test-APK packaging passed in the complete Gradle run (exit 0). See [menu/icon validation](../testing/MENU_REFINEMENT_VALIDATION.md), [Files validation](../testing/FILES_WORKSPACE_VALIDATION.md) and [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md). Broader provider, hardware, recovery, performance and owner-signing acceptance remains open.

Last updated: 2026-10-20

Feature Phases 0–4 are merged, but the October audit found implementation defects requiring fixes. Follow [OPTIMIZATION_ROADMAP](../roadmap/OPTIMIZATION_ROADMAP.md) and its [progress record](../roadmap/IMPLEMENTATION_PROGRESS.md) before the historical tasks below. M02–M11 implementation is integrated; seventeen draft PRs await review. Preference/startup/comparison IO changes are implemented, and startup's manual API34 retest recorded zero StrictMode disk warnings. Current thumbnail/viewer/contextual-action host and selected device evidence is recorded above. Broader device/provider/recovery/performance acceptance remains. Do not auto-merge high-risk filesystem changes. Hardware and owner gates below still apply.

## P0 — device verification (needs a phone or emulator)

### TASK-001 — Run the manual emulator CI job once

Status: OPEN

Why: It is the only automated run of the device tests, the release launch, the 10,000-event monkey test (PR #39), and R8 at runtime.

Action: GitHub → Actions → CI → "Run workflow" on `main`. The job is `instrumented-tests` in `.github/workflows/ci.yml`.

Done when: The job is green. If the monkey test fails, `monkey.log` shows the crash, and it is fixed.

### TASK-002 — Generate and commit the baseline profile

Status: OPEN

Files: `benchmark/.../BaselineProfileGenerator.kt` (the run command is in its KDoc), and the target `app/src/main/baseline-prof.txt`.

Action:

1. Run the generator on an API 33+ emulator.
2. Copy the `*-baseline-prof.txt` output.
3. Commit it.
4. Re-run `StartupBenchmark`, and record cold start before and after in plan §16.1.

Done when: The profile ships in the release APK and the startup numbers are recorded.

### TASK-003 — Network features on real hardware

Status: OPEN

Action: On the same Wi-Fi:

1. Connect to an SMB share (Windows or Samba), browse it, copy both ways, and check that a wrong password gives a clear message.
2. Run mDNS discovery (`NsdServerDiscovery`) and confirm it finds the server.
3. Start Wi-Fi share, open the URL on a PC, enter the PIN, then download and upload (uploads on). Confirm it stops on leave, on Stop, and after 15 minutes idle.

Done when: Each passes, or a bug is filed or fixed with a test.

### TASK-004 — USB OTG checklist

Status: OPEN

Action: Work through `docs/testing/USB_OTG_CHECKLIST.md` with a FAT32 and an exFAT drive. Include unplugging mid-copy.

### TASK-005 — A real process kill mid-copy

Status: OPEN

Action:

1. Start a copy of a file of 1 GB or more to the SD card.
2. Run `adb shell am kill com.devbehindyou.atomicfilemanager`, or force-stop.
3. Reopen the app.

Expected: The resume prompt appears, there is no partial file under the real name, and Resume finishes with a matching checksum.

Done when: The plan §16.8 "Process-kill tests" row no longer says "real-device kill run is still to do".

## P1 — owner decisions and actions

### TASK-006 — Check Play policy for Shizuku and all-files access

Status: OPEN (owner)

Why: Plan §12 risk register. Shizuku (PR #36) and `MANAGE_EXTERNAL_STORAGE` both need declarations.

Files: `docs/architecture/PERMISSIONS.md` §7.

### TASK-007 — Production signing

Status: OPEN (owner). See ISS-001.

## P2 — optimization left in plan §16 (each must come with a measurement)

| ID | Item | Where | Note |
|---|---|---|---|
| TASK-010 | Drop the category walk when the index is fresh | `PhoneFileIndex`, `RoomSearchIndex` | Needs `MediaStore.getGeneration` freshness first (ADR-004) |
| TASK-011 | Keep the analysis result across restarts, and rescan incrementally | `StorageAnalyzerUseCase` | Today it is in memory for 10 minutes only (PR #40) |
| TASK-012 | Split `MainActivity.kt` (958 lines), and measure Browse recomposition | `MainActivity.kt`, `ui/screens/Browse*.kt` | `BrowseScreen` was split in PR #41; use Layout Inspector recomposition counts |
| TASK-013 | Large-data test: 100,000 files, a 4 GB file, a 10,000-entry folder | new JVM or device test | Needed to check the §16.1 budgets |
| TASK-014 | Network: resume interrupted transfers (FTP `REST`, WebDAV `Range`, SFTP offset) | `data/backend/network/*` | Connection pooling and reconnect are already in SFTP and SMB |
| TASK-015 | APK size report in CI | `.github/workflows/ci.yml` | Fail on unexplained growth |
| TASK-016 | Measure copy speed on SD and USB with the 256 KB buffer | device | PR #38 only changed the constant |

## Optional

- Migrate transfer bubbles and hidden files from SQLiteOpenHelper to Room. Both already load on IO, so this is about tidiness, not speed.
- Add more device rows to `docs/testing/DEVICE_MATRIX.md`: API 27, 29, 30, 33 and 36, plus a tablet and a foldable. So far only an API 34 phone.
