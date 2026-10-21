# Deterministic folder benchmark harness — 21 October 2026

U02 implementation slice. This changes the test APK only: no application routing, storage permissions, settings persistence or production dependencies are changed. Real performance measurements and the remaining U02 acceptance are not complete.

## Implemented

- Parameterized 100, 1,000 and 10,000-entry fixture folders, using a unique run ID under the system Downloads collection. Each folder contains deterministic filenames and a bounded media subset: 10%, capped at 100 tiny PNGs, with text/JSON/Markdown filenames for the remaining entries. The payload is synthetic; JSON/Markdown files are listing fixtures, not a viewer-conformance corpus.
- One additional owned reset folder containing one file. Opening it before each sample ensures that the following target-folder request changes the current folder and triggers a real listing.
- Fixture creation and result serialization are outside measured work. Records from compilation/other warmups are excluded using `MacrobenchmarkScope.iteration`.
- Folder-opening and scrolling are separate ten-iteration scenarios. Missing title, exact expected item count, List selection, fixture rows or scroll movement fails the run instead of returning success.
- List is asserted through the existing options popup. The harness does not change the view preference, clear application data, grant permissions, change filters, or silently open another folder.
- The vertical list must contain fixture names and occupy the main content region. A horizontal storage-shortcuts scroller cannot satisfy this check.
- Raw observed first-row and complete-count timings are recorded separately, along with SDK/model/build, installed base APK hash/version, compilation mode, retained-cache conditions and thermal state before/after. No user filenames, credentials or full user paths are logged.
- Cleanup owns only inserted URI identities. It checks owner package, relative path and generated name, then applies those same predicates to the identity-specific deletion. Changed/unknown rows are preserved and cleanup failure fails the test. No recursive directory deletion exists.
- Existing API27+ cold-start and baseline-profile scenarios remain unchanged; their historical limitations remain visible.

## Platform and workload limits

The measured folder scenarios explicitly require API30+, matching Atomic's existing direct shared-storage route with all-files access. MediaStore fixture utilities/contract cases use API29+ facilities. This does not raise the application's API27 minimum or introduce legacy-storage flags. API27/28/29 folder measurements need a separate suitable fixture/grant design before equivalent coverage is claimed.

Use a dedicated, idle test device or emulator and Atomic's `benchmark` target build. Before running, grant the target app's normal storage access through its existing user flow, select List, clear active search/type filters and use one browse pane. If the device or app cannot read the fixture, setup fails with guidance.

`MediaStore.MediaColumns.DATA` is read only from an existing benchmark-owned item to obtain the actual local folder path for the app's existing pinned-folder launch extra. Creation/update use `DISPLAY_NAME` and `RELATIVE_PATH`. No volume path is hardcoded and no new production shortcut is introduced.

Cleanup removes known media rows/bytes, not directory trees. Empty generated folders may remain. Abrupt termination of the instrumentation process can prevent finally/close from running and leave synthetic fixtures behind. Do not bulk-delete Downloads to recover them. A durable fixture recovery journal and a controlled recovery test remain follow-on work; the current harness must not be described as process-kill-safe.

MediaStore inserts/media scanning can warm filesystem/provider caches. The scenarios therefore describe **warm observed folder opening**, not a cold storage-cache baseline. Raw timing includes the activity-launch handshake, Macrobenchmark's frame wait and accessibility polling; it is not an engine-only listing duration or exact first-paint timestamp. Target-process trace hooks are still needed for more precise phase attribution. Ten iterations are exploratory and do not establish reliable p99 tails or a new performance regression budget.

## Validation and execution

Source is prepared for root-coordinated validation. No Gradle build, instrumentation case or physical benchmark has been executed by this agent; no timing result is claimed. Parent/root records exact build and device outcomes after running them.

Compile/package with the repository's normal JDK21/Gradle toolchain:

```powershell
.\gradlew.bat :benchmark:assembleBenchmark
```

Run only the non-mutating fixture contract cases first:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devbehindyou.atomicfilemanager.benchmark.BenchmarkFixtureContractTest
```

On the authorized test device, run the controlled corpus scenarios separately:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devbehindyou.atomicfilemanager.benchmark.FolderScrollBenchmark
```

The class runs six scenarios (opening and scrolling at each of three sizes), each with ten measured iterations plus library warmups. Fixture insertion/cleanup may take substantial time, particularly at 10,000 entries, and is not included in the measured timings. Budget storage/runtime before the large case. Compilation mode may force-stop the target as part of Macrobenchmark; use a test device with no active real file operation.

Macrobenchmark frame/Perfetto artifacts use the normal connected-test additional-output directory. Raw JSONL observations are written to the benchmark APK's own `getExternalFilesDir(null)/benchmark-results/`, with an app-private fallback when external app storage is unavailable. Pull those files before uninstalling the test APK. Each file contains environment, measured observations and a terminal success/failure record; only accept files with successful terminal state and matching expected sample count. The APK hash is the installed **base APK**, not a universal AAB/download-size measure.

Contract cases cover unique/bounded names and thumbnail budgets at all reviewed sizes, rejection of unreviewed/invalid sizes and indices, and refusal to clean up a mismatched or missing owner/path/name. They require no provider mutation. Actual MediaStore write/delete behavior, exported UI selectors and scroll gestures still require physical/emulator acceptance.

## Official API references checked

- [Shared-media ownership, DATA and relative paths](https://developer.android.com/training/data-storage/shared/media).
- [MacrobenchmarkScope launch and iteration contract](https://developer.android.com/reference/androidx/benchmark/macro/MacrobenchmarkScope): primitive launch extras are supported; the launcher category is required for modern Android; null iteration identifies non-measurement work.

No baseline profile, speed-up percentage, p95/p99 target pass, API27 folder result or complete U02 milestone is claimed by this source increment.
