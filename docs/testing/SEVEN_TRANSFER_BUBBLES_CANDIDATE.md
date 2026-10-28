# Seven transfer bubbles — implementation and validation candidate

28 October 2026. The owner requested a maximum of seven transfer bubbles. This focused increment follows accepted API28 PR #96 and Cloud Connect documentation PR #95; it does not add plugin modernization credit.

## Resulting behavior

The shared maximum is seven. Repository creation counts and inserts inside one SQLite write transaction, including default name and order; the eighth request returns a capacity failure. Existing rows, staged references, schema/version and permissions are preserved. The rail uses the shared limit for Create visibility and scrolls within the available height when its contents overflow. Fitting rails retain vertical movement; overflowing rails retain horizontal docking, click and long-press actions.

## Actual retained evidence

- Baseline source `4f00dce`: capacity7 plus the old rail layout/pointer handlers. A semantics-only test anchor was added to target real pointer gestures; it is not a layout repair. All25 selected repository/database/rail cases ran:22 passed,3 failed,0 errors/skips. Two failures reproduce clipped bubble7 and clipped Create at6. The third is the separate Legacy SQLite fixture failure below. Keep logs, XML and source under `build/audit-tools/seven-bubble-old-rail-evidence/`.
- Rail repair `74e65bb`: all4 actual-pointer cases pass in20.587s (1m32s Gradle),0 failures/errors/skips. They cover scrolling/clicking/holding bubble7, reaching Create below7 and hiding it at7, horizontal docking, and vertical movement when all contents fit. Evidence is retained in `seven-bubble-rail-green-evidence/`. These are host Compose consumers, not physical-touch acceptance.
- Legacy fixture diagnosis: pinned Robolectric4.14.1's sqlite4java shadow opens connections without Android's busy handler and serializes SQL through one executor. The two-helper contention case throws SQLiteBusy at BEGIN EXCLUSIVE; this does not demonstrate overshooting the cap. [Legacy implementation](https://raw.githubusercontent.com/robolectric/robolectric/robolectric-4.14.1/shadows/framework/src/main/java/org/robolectric/shadows/ShadowLegacySQLiteConnection.java). Android9's native connection configures a2500ms busy timeout: [AOSP implementation](https://raw.githubusercontent.com/aosp-mirror/platform_frameworks_base/android-9.0.0_r1/core/jni/android_database_SQLiteConnection.cpp).
- Fixture correction `2164b3b`: method-only `@SQLiteMode(NATIVE)` preserves all8 simultaneous requests,2 helpers on one owned database,7 success/1 rejection and7 persisted unique IDs. That actual case passes on this Windows x64 host in1m10s Gradle,0 failures/errors/skips. No retries, sleeps or assertion removal were added. Keep the original failure and successful native evidence in separate directories. The pinned configurer's Windows-default Legacy choice is not treated as proof that explicit Native cannot work; execution establishes this scoped result.

## Remaining gates

Run the complete current-source app/Lint-rule suite, static checks, signed R8 packaging, Lint, current-head CI, exact signed API28/API34 startup, resolved review and a clean dependency-aware merge trial before beta publication. Existing API28 source `f7bb7c3` already passes1271 app/28 Lint-rule cases, CI40696042718 and signed runtimes40700100953; that earlier APK does not contain this seven-bubble increment. Original records remain under `beta-source-f7bb7c3-pass/`.

Broader file-transfer, drag/drop/provider, accessibility and plugin acceptance retain their existing scope. No performance improvement, phone run, benchmark, main/stable completion or whole plugin group credit follows from these focused checks.