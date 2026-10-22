# Deterministic folder benchmark harness — updated 22 October 2026

U02 implementation slice. This changes the test APK only: no application routing, storage permissions, settings persistence or production dependencies are changed. Real performance measurements and the remaining U02 acceptance are not complete.

## Implemented

- Parameterized 100, 1,000 and 10,000-entry fixture folders, using a unique run ID under the system Downloads collection. Each folder contains deterministic filenames and a bounded media subset: 10%, capped at 100 tiny PNGs, with text/JSON/Markdown filenames for the remaining entries. The payload is synthetic; JSON/Markdown files are listing fixtures, not a viewer-conformance corpus.
- One additional owned reset folder containing one file. Opening it before each sample ensures that the following target-folder request changes the current folder and triggers a real listing.
- Fixture creation and result serialization are outside measured work. Records from compilation/other warmups are excluded using `MacrobenchmarkScope.iteration`.
- Compilation is explicit `Partial(baselineProfileMode=Disable,warmupIterations=3)`, so this harness does not require a generated baseline profile that the current app does not ship. The pinned 1.3.3 library's no-argument `Partial()` requires a profile; using that default would block the baseline before its first sample.
- Folder-opening and scrolling are separate ten-iteration scenarios. Missing title, exact expected item count, List selection, fixture rows or scroll movement fails the run instead of returning success.
- List is asserted through the existing options popup. The harness does not change the view preference, clear application data, grant permissions, change filters, or silently open another folder.
- The vertical list must contain fixture names and occupy the main content region. A horizontal storage-shortcuts scroller cannot satisfy this check.
- Raw observed first-row and complete-count timings are recorded separately, along with SDK/model/build, installed base APK hash/version, compilation mode, retained-cache conditions and thermal state before/after. No user filenames, credentials or full user paths are logged.
- Fixture MIME follows the extension: PNG `image/png`, JSON `application/json`, Markdown `text/markdown`, text `text/plain`. Every insert records its exact returned URI and freezes the provider's name/path/owner/MIME before any payload write. Requested-name/MIME or ownership/path mismatch fails setup, with validation repeated after publish and for all rows before measurement.
- Cleanup owns only inserted URI identities and compares the current provider tuple with that frozen insertion tuple. It also requires the original requested run-specific path and benchmark owner, then applies exact name/path/owner/MIME predicates to the single recorded URI deletion. This allows removal of an immediately normalized generated name after setup fails, but never adopts a later rename as trusted. Changed/unknown rows are preserved and cleanup failure fails the test. No recursive directory deletion exists.
- A synced append-only JSONL journal in the benchmark APK's own `filesDir/benchmark-fixture-recovery/<runUUID>.jsonl` records the run, insertion intent, returned URI, frozen tuple, publication and deletion. URI/tuple records are durable before payload writes. Append work and cleanup bookkeeping are linear in row count; no growing manifest is rewritten for each insertion. Completed and unresolved journals remain available for review.
- Existing API27+ cold-start and baseline-profile scenarios remain unchanged; their historical limitations remain visible.

## Platform and workload limits

The measured folder scenarios explicitly require API30+, matching Atomic's existing direct shared-storage route with all-files access. MediaStore fixture utilities/contract cases use API29+ facilities. This does not raise the application's API27 minimum or introduce legacy-storage flags. API27/28/29 folder measurements need a separate suitable fixture/grant design before equivalent coverage is claimed.

Use a dedicated, idle test device or emulator and Atomic's `benchmark` target build. Before running, grant the target app's normal storage access through its existing user flow, select List, clear active search/type filters and use one browse pane. If the device or app cannot read the fixture, setup fails with guidance.

`MediaStore.MediaColumns.DATA` is read only from an existing benchmark-owned item to obtain the actual local folder path for the app's existing pinned-folder launch extra. Creation/update use `DISPLAY_NAME` and `RELATIVE_PATH`. No volume path is hardcoded and no new production shortcut is introduced.

Cleanup removes known media rows/bytes, not directory trees. Empty generated folders may remain. Abrupt termination can prevent finally/close and leave synthetic fixtures behind. The journal narrows identity loss and preserves unresolved records, but insertion into MediaStore and the following URI journal append are separate transactions: a process/device failure in that gap may leave an inserted row without its URI record. No automatic replay/deletion trusts a journal, and no process-kill recovery contract has been run. Pull unresolved journals before uninstalling the benchmark APK; do not bulk-delete Downloads or describe this harness as process-kill-safe.

MediaStore inserts/media scanning can warm filesystem/provider caches. The scenarios therefore describe **warm observed folder opening**, not a cold storage-cache baseline. Raw timing includes the activity-launch handshake, Macrobenchmark's frame wait and accessibility polling; it is not an engine-only listing duration or exact first-paint timestamp. Target-process trace hooks are still needed for more precise phase attribution. Ten iterations are exploratory and do not establish reliable p99 tails or a new performance regression budget.

## Validation and execution

Root-coordinated validation passed for the integrated benchmark source:

| Check | Result |
| --- | --- |
| `:benchmark:assembleBenchmark` | Passed; `benchmark-fixture-build-retry.log`, exit 0, `BUILD SUCCESSFUL in 1m 1s`, 41 tasks |
| `BenchmarkFixtureContractTest` on Android14/API34, device `5ff095c7f80a` | Both non-mutating contract cases passed; `benchmark-contract-device.log`, `OK (2 tests)` |
| Later non-mutating contracts at `7c0c14d`, including explicit size selection | Passed; `device-preview-tools/benchmark-100-smoke/contracts.log`, `OK (3 tests)` |
| Authorized 100-entry generated-corpus smoke at `7c0c14d` | **Failed**, two scenarios; `device-preview-tools/benchmark-100-smoke/smoke.log`. No larger corpus was executed by this repair task. |
| MIME/frozen-identity/journal repair, integrated as `8a10dc6` | Static checks and benchmark APK packaging passed; `benchmark-provider-repair-build.log`, exit 0, `BUILD SUCCESSFUL in 51s` |
| Repaired non-mutating contracts on API34 | All five passed; `benchmark-provider-contract/non-mutating-contracts.log`, `OK (5 tests)`, 0.518s |
| Native `BenchmarkMediaStoreContractTest` on API34 | Passed exact 100-entry corpus plus one-file reset; `benchmark-provider-contract/provider-100.log`, `OK (1 test)`, 13.978s; no target launch or measurement |
| Performance baseline or speed-up measurement | No passing result; the failed smoke does not establish a baseline |

The logs are retained in the integration checkout's ignored `build/audit-tools/` directory. The five repaired non-mutating contract cases do not write MediaStore. The separately selected `BenchmarkMediaStoreContractTest` passed on API34 at integration source `8a10dc6`, which includes repair `1aaf6f7`. It created exactly the reviewed 100-entry corpus plus its one-file reset, verified all 101 requested names, MIME, owner, path and published states and their captured URI journal records, closed the corpus, then confirmed the exact recorded URIs were absent. It did not launch or measure the target app. This validates the bounded provider fixture contract, not the measured opening/scrolling scenarios.

The tested benchmark APK SHA-256 is `1306ADF393FBC4532B4E35ECCBA2250BBE8B91358C84DE3DB9A0B14A313EF83C`. Source/hash records, both case logs, the reviewed recovery journal and `remaining-rows.log` are under `build/audit-tools/device-preview-tools/benchmark-provider-contract/`; the remaining-row query reports `No result found.` The three exact known old/new run UUIDs had only their six empty directories removed using `rmdir`. No recursive deletion was used; `cleanup-summary.json` and `empty-directory-cleanup.exit` (0) retain that finalization evidence.

The failed smoke exposed `.json`/`.md` entries inserted as `text/plain`; the provider normalized them to names such as `bench_00010.json.txt` and `bench_00011.md.txt`. The previous cleanup correctly refused the mismatched identities, leaving 120 rows across the two runs. Root subsequently recovered only those exact known URI/owner/path/name identities after SHA-256 checks; the repair itself did not silently adopt or delete the old mismatched rows. The later query reports no remaining rows for the known runs. Opening produced ten raw observations and ten Perfetto traces, but the library had zero `frameDurationCpuMs` results and failed. Scrolling failed to find the expected list after its flings. `FrameTimingMetric` and strict list/movement checks remain required and unchanged; missing frame data, the scroll selector issue and a passing measured smoke remain unresolved acceptance work. The later provider-contract pass does not convert the original two failed scenarios into a baseline.

Root's first compile found that `androidx.test.filters.SdkSuppress` was not on the benchmark compilation classpath. Cached `androidx.test:runner:1.6.2` was inspected and contains both `SdkSuppress` and the module's declared `AndroidJUnitRunner`. The benchmark module now explicitly depends on the existing `libs.androidx.test.runner` catalog entry. This is a test-APK dependency only, with no version change or production APK dependency. Root's retry then packaged the benchmark APK successfully, as recorded above.

Review also corrected popup-dismissal verification to wait until the real **Close file options** accessibility node disappears. The View submenu's title can outlive/overlap other View text, so it is not the popup-lifetime signal. List selection is read from the checkable ancestor row, matching the existing physical UI hierarchy; it is not inferred from the child text node's `selected` flag. The bounded provider contract now passes; the generated-corpus UI journey still needs its own passing measured rerun.

Compile/package with the repository's normal JDK21/Gradle toolchain:

```powershell
.\gradlew.bat :benchmark:assembleBenchmark
```

Run only the non-mutating fixture contract cases first:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devbehindyou.atomicfilemanager.benchmark.BenchmarkFixtureContractTest
```

To reproduce the bounded provider contract independently of a measured smoke, run the following after rebuilding. It writes and deletes only its own 100 corpus rows plus reset, with no target UI or measured iterations:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devbehindyou.atomicfilemanager.benchmark.BenchmarkMediaStoreContractTest
```

For the currently authorized **100-entry smoke only**, select the corpus with an explicit instrumentation argument. This runs opening and scrolling only for the 100-entry corpus; a method-name filter alone is not the workload bound:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devbehindyou.atomicfilemanager.benchmark.FolderScrollBenchmark -Pandroid.testInstrumentationRunnerArguments.fixtureEntries=100
```

`fixtureEntries` accepts exactly `100`, `1000` or `10000`. An omitted key retains the full three-size suite for separately authorized full runs. A supplied null, blank, whitespace-padded, signed, zero-padded, comma-separated or unsupported value fails parameter discovery before fixture generation; it never falls back to all sizes. The `AtomicBenchmark` log tag reports selected numeric sizes only. For the smoke, verify `Selected fixture entries: 100`, the two `[100 entries]` scenario names and JSONL `entries: 100`; do not accept a log showing 1,000/10,000 scenarios. Selection and provider contracts have their own passing evidence above; neither replaces the still-failed measured smoke.

The default full-suite command below is retained for documentation and is **not authorized by the current 100-entry smoke approval**:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devbehindyou.atomicfilemanager.benchmark.FolderScrollBenchmark
```

Without `fixtureEntries`, the class runs six scenarios (opening and scrolling at each of three sizes), each with ten measured iterations plus library warmups. With `fixtureEntries=100`, it runs two scenarios at 100 entries. Each corpus also owns the one-file reset folder described above. Fixture insertion/cleanup may take substantial time, particularly at 10,000 entries, and is not included in measured timings. Budget storage/runtime before the large case. Compilation mode may force-stop the target as part of Macrobenchmark; use a test device with no active real file operation.

Macrobenchmark frame/Perfetto artifacts use the normal connected-test additional-output directory. Raw JSONL observations are written to the benchmark APK's own `getExternalFilesDir(null)/benchmark-results/`, with an app-private fallback when external app storage is unavailable. Pull those files before uninstalling the test APK. Each file contains environment, measured observations and a terminal success/failure record; only accept files with successful terminal state and matching expected sample count. The APK hash is the installed **base APK**, not a universal AAB/download-size measure.

Non-mutating contract cases cover unique/bounded names and thumbnail budgets at all reviewed sizes, rejection of unreviewed/invalid sizes and indices, MIME/extension pairing and refusal to clean up a changed/missing frozen owner/path/name/MIME. The native provider case is distinct and mutates only its owned generated fixtures; its exact 100-plus-reset write/delete contract now passes on API34. Larger corpora, process-kill repair, exported UI selectors and scrolling/frame measurements still require separate acceptance.

## Official API references checked

- [Shared-media ownership, DATA and relative paths](https://developer.android.com/training/data-storage/shared/media).
- [MacrobenchmarkScope launch and iteration contract](https://developer.android.com/reference/androidx/benchmark/macro/MacrobenchmarkScope): primitive launch extras are supported; the launcher category is required for modern Android; null iteration identifies non-measurement work.

## Current CI and existing-trace diagnosis — 22 October 2026

Repair documentation head `e705ebc` passed static analysis but failed CI `39670109383`: 924 app cases ran and the Browse navigation teardown case failed after resetting Main while cancellation was still pending. The accepted index slice already contained the joined-teardown correction. Integrating the accepted feature base produced `9a48eee`, which passed static analysis and full unit/APK CI `39671870513`. No production benchmark metric or assertion was removed to obtain this pass.

Read-only inspection of the previously captured opening iteration 000 found 122 frame-timeline rows: 61 attributed to SurfaceFlinger and 61 to System UI. The target process is present with ART/profile-saver activity, but it has no captured `Choreographer#doFrame`, `DrawFrame` or target frame-timeline rows. The stored trace configuration includes `gfx`/`view`, wildcard atrace apps and the SurfaceFlinger frame-timeline data source. The trace also reports 12 `slice_out_of_order` and 13 `sorter_push_event_out_of_order` errors. These observations narrow the missing-frame evidence; they do not establish its root cause or validate a performance baseline. Logs: `device-preview-tools/benchmark-100-smoke/trace-000-frame-details.log` and `trace-000-config.log`, using the existing trace and cached trace processor only. No new measured scenario or corpus was run for this diagnosis.

No baseline profile, speed-up percentage, p95/p99 target pass, API27 folder result or complete U02 milestone is claimed by this source increment. The PR remains a draft while the measured opening/scrolling acceptance is unresolved.

## Current integration and preflight preparation — 22 October 2026

Accepted feature integration `e60d8e78a9eb7f932461c0b5dd65a9911afe9a59` was merged normally into this branch as `64c4fe6dc0ad12828734a7423f99c199250f3a4c`, without conflicts or benchmark metric/assertion changes. This carries the accepted startup first-frame correction, explicit Analysis scanning, Browse sort/header and tab-session changes. Its app tree is `9ec9b8dc8acda01fe5e37fb9e72eaf95876b1c64`; its benchmark tree is `23a693793c9d7793c7eb2eb44deda5711167f823`. These tree identities describe this synchronization, not a new packaging or measurement pass. The independent create-parent candidate is separate until its accepted dependency is merged.

The accepted startup fix removes a first-frame dependency that was observed in native UI testing. It is not established as the explanation or repair for the earlier benchmark's missing target frames: that opening scenario already completed ten raw observations before FrameTimingMetric rejected zero frame samples. The post-fling list-selection failure remains unresolved too. Existing strict frame, count, List and actual movement assertions are retained.

A new ignored workspace helper, `build/audit-tools/Run-AtomicCurrent100Smoke.ps1`, is prepared for parent review. The older helper and failed-run/intervention evidence remain unchanged. The new helper accepts explicit frozen current debug, benchmark-target and runner APKs. Before phone work, it requires genuine host evidence identifying the exact source head, app/benchmark trees, successful applicable build/static gates and the three APK hashes. It separately requires reviewed bounded, non-intervened root-launch evidence for the exact runner/device. The pinned AndroidX `su root id` hang remains an unresolved gate; this preparation neither reruns that probe nor generates a success record, suppresses a benchmark error or changes library flags.

The helper creates a unique evidence directory, checks five non-mutating contracts and log-only discovery of exactly the two `fixtureEntries=100` scenarios, and preserves original failures with cleanup errors. Terminal UI/screenshot, available private recovery/result archives and runner-owned external benchmark output are collected on failure as well as success. Any target-install attempt requires restoration of the supplied current debug APK, verification of its installed base hash, existing all-files access and actual selected Home. Runner removal follows verified journal cleanup and evidence backup; if either is unresolved, the helper fails and preserves the runner owner for exact recovery rather than losing its remaining fixture ownership or evidence. It never grants permissions, changes preferences, clears app data or recursively cleans shared storage.

Only PowerShell parser inspection has been performed for this prepared helper, with no syntax errors. No phone command, corpus generation, root probe or measured run was executed by this preparation. The current 100-entry approval does not authorize 1,000/10,000 entries or the default six-scenario suite. Fresh host packaging, source review, root-launch resolution and parent approval of this concrete preflight remain required before any execution; current-head CI and a passing measured smoke remain required before accepting this PR.
