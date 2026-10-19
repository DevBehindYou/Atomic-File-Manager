# Phone surface test — 19 October 2026

Result: functional surface checks passed, with main-thread disk-read findings requiring follow-up. This is not full device, filesystem or release acceptance.

## Build and device

- Integration branch `codex/architecture-audit`; production source checkpoint `4d19ae1` (later changes at this checkpoint are documentation only).
- Debug APK: `app/build/outputs/apk/debug/app-debug.apk`, 33,272,915 bytes. SHA256: `0786b4c449fd332390413372a43e287af36b4309688b515dddadf2a54e423630`.
- Android Debug signature verified; application ID `com.devbehindyou.atomicfilemanager`, minSdk 27, targetSdk 36.
- Physical phone model `23076RN4BI`, Android 14 / API 34, custom `bliss_sky` build; 1080×2460, density 440.
- ADB ran as `uid=2000(shell)`. All-files access was granted with `appops ... MANAGE_EXTERNAL_STORAGE allow`, and the declared notification permission was granted. No superuser operation was needed for these checks.
- Battery indicated 1% while connected. No controlled performance or thermal benchmark was attempted.

## Observed checks

| Check | Result |
|---|---|
| Install and fresh process launch | Passed; activity reported `Status: ok`, `LaunchState: COLD` |
| Home | Rendered internal storage and SD card summaries |
| Files | Rendered the internal storage listing and breadcrumbs |
| Storage | Rendered both mounted volume cards and tools |
| Settings | Rendered appearance, files and trash settings |
| About | Sheet opened and closed with Back |
| Search | Screen and keyboard opened; Back dismissed the keyboard, then returned Home |
| Command palette | Opened and closed without executing a command |
| Files → Back | Returned to the originating Storage tab |
| Storage → Back | Returned Home |
| Landscape recreation | Home rendered with navigation rail; original `lock 0` portrait state restored |
| App crash / ANR | None observed in the captured app logs or process-exit records for the manual run |

The single debug cold launch reported 5,539 ms total activity launch time. This is a diagnostic sample under uncontrolled conditions, not a startup benchmark or a before/after performance claim.

## Automated device surface tests

Installed the current debug instrumentation APK and ran only:

```text
com.devbehindyou.atomicfilemanager.MobileNavigationDeviceTest
com.devbehindyou.atomicfilemanager.StorageJourneyInstrumentedTest#activitySurvivesRecreation
```

AndroidJUnitRunner reported **OK (4 tests)** in 15.406 seconds, with all four test status codes successful. The navigation tests cover Back to the originating tab, Back to Home and selected-tab recreation. The fourth test checks Activity recreation directly. File-copy, trash, removable-storage mutation and other device suites were excluded from this surface-only run.

Local evidence is preserved under ignored `build/audit-tools/device-surface/`: `instrumentation-surface.txt`, `app-logcat.txt`, `exit-info.txt`, `cold-launch.txt`, UI XML and screenshots. These artifacts are local and are not published with the report.

## Finding: main-thread disk access

The first captured navigation log contains ten StrictMode disk-read reports across five existing flows:

| Flow | Source callsites |
|---|---|
| Folder sort preferences | `data/repository/FolderSortPreferences.kt:12,14`, called during Browse setup/load |
| Transfer bubble position | `ui/interaction/bubble/TransferBubbleRail.kt:76,78`, preferences accessed inside `remember` |
| Saved servers | `data/backend/network/NetworkCredentialsStore.kt:20,26`, called by `StorageScreen.kt:87` |
| Saved sync pairs | `data/repository/SyncPairStore.kt:9`, constructed by `StorageScreen.kt:176` |
| Home quick-access folders | `ui/screens/HomeScreen.kt:211`, `context.filesDir` accessed during composition |

Paths are relative to `app/src/main/kotlin/com/devbehindyou/atomicfilemanager/`. These callsites also exist in baseline `7782660`; the phone run confirms existing issues rather than establishing modernization regressions. StrictMode durations are not isolated disk-latency measurements.

Next implementation should load preference construction and snapshots on an injected IO dispatcher, expose cached state to Compose, and resolve the Home fallback directory outside composition. Retest cold entry into Files, Storage and Home, including persistence across restart. No preference-format or dependency migration is required to address this finding.

## Scope limits

No user file was renamed, moved or deleted. No sharing server, remote connection, cleanup, root filesystem action or random-event monkey test was started. Shell storage/notification grants and the installed test build remain available; the original rotation setting was restored. The final app state is Home.

API 27/36 acceptance, SAF/USB provider behavior, file operations on generated fixtures, real process-kill recovery, live network services, macrobenchmarks and production signing remain separate work.
