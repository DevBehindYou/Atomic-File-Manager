# Deterministic folder benchmark harness — 21 October 2026

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
- Cleanup owns only inserted URI identities. It checks owner package, relative path and generated name, then applies those same predicates to the identity-specific deletion. Changed/unknown rows are preserved and cleanup failure fails the test. No recursive directory deletion exists.
- Existing API27+ cold-start and baseline-profile scenarios remain unchanged; their historical limitations remain visible.

## Platform and workload limits

The measured folder scenarios explicitly require API30+, matching Atomic's existing direct shared-storage route with all-files access. MediaStore fixture utilities/contract cases use API29+ facilities. This does not raise the application's API27 minimum or introduce legacy-storage flags. API27/28/29 folder measurements need a separate suitable fixture/grant design before equivalent coverage is claimed.

Use a dedicated, idle test device or emulator and Atomic's `benchmark` target build. Before running, grant the target app's normal storage access through its existing user flow, select List, clear active search/type filters and use one browse pane. If the device or app cannot read the fixture, setup fails with guidance.

`MediaStore.MediaColumns.DATA` is read only from an existing benchmark-owned item to obtain the actual local folder path for the app's existing pinned-folder launch extra. Creation/update use `DISPLAY_NAME` and `RELATIVE_PATH`. No volume path is hardcoded and no new production shortcut is introduced.

Cleanup removes known media rows/bytes, not directory trees. Empty generated folders may remain. Abrupt termination of the instrumentation process can prevent finally/close from running and leave synthetic fixtures behind. Do not bulk-delete Downloads to recover them. A durable fixture recovery journal and a controlled recovery test remain follow-on work; the current harness must not be described as process-kill-safe.

MediaStore inserts/media scanning can warm filesystem/provider caches. The scenarios therefore describe **warm observed folder opening**, not a cold storage-cache baseline. Raw timing includes the activity-launch handshake, Macrobenchmark's frame wait and accessibility polling; it is not an engine-only listing duration or exact first-paint timestamp. Target-process trace hooks are still needed for more precise phase attribution. Ten iterations are exploratory and do not establish reliable p99 tails or a new performance regression budget.

## Validation and execution

Root-coordinated validation passed for the integrated benchmark source:

| Check | Result |
| --- | --- |
| `:benchmark:assembleBenchmark` | Passed; `benchmark-fixture-build-retry.log`, exit 0, `BUILD SUCCESSFUL in 1m 1s`, 41 tasks |
| `BenchmarkFixtureContractTest` on Android14/API34, device `5ff095c7f80a` | Both non-mutating contract cases passed; `benchmark-contract-device.log`, `OK (2 tests)` |
| Full generated-corpus / MediaStore mutation / scrolling scenarios | Not run |
| Performance baseline or speed-up measurement | Not run; no timing result claimed |

The logs are retained in the integration checkout's ignored `build/audit-tools/` directory. The tests exercise deterministic fixture names/budgets and cleanup identity predicates without creating or deleting MediaStore files. They do not certify the entire generated-corpus lifecycle or measured UI flows.

Root's first compile found that `androidx.test.filters.SdkSuppress` was not on the benchmark compilation classpath. Cached `androidx.test:runner:1.6.2` was inspected and contains both `SdkSuppress` and the module's declared `AndroidJUnitRunner`. The benchmark module now explicitly depends on the existing `libs.androidx.test.runner` catalog entry. This is a test-APK dependency only, with no version change or production APK dependency. Root's retry then packaged the benchmark APK successfully, as recorded above.

Review also corrected popup-dismissal verification to wait until the real **Close file options** accessibility node disappears. The View submenu's title can outlive/overlap other View text, so it is not the popup-lifetime signal. List selection is read from the checkable ancestor row, matching the existing physical UI hierarchy; it is not inferred from the child text node's `selected` flag. The generated-corpus UI journey itself still needs a dedicated run.

Compile/package with the repository's normal JDK21/Gradle toolchain:

```powershell
.\gradlew.bat :benchmark:assembleBenchmark
```

Run only the non-mutating fixture contract cases first:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devbehindyou.atomicfilemanager.benchmark.BenchmarkFixtureContractTest
```

For the currently authorized **100-entry smoke only**, select the corpus with an explicit instrumentation argument. This runs opening and scrolling only for the 100-entry corpus; a method-name filter alone is not the workload bound:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devbehindyou.atomicfilemanager.benchmark.FolderScrollBenchmark -Pandroid.testInstrumentationRunnerArguments.fixtureEntries=100
```

`fixtureEntries` accepts exactly `100`, `1000` or `10000`. An omitted key retains the full three-size suite for separately authorized full runs. A supplied null, blank, whitespace-padded, signed, zero-padded, comma-separated or unsupported value fails parameter discovery before fixture generation; it never falls back to all sizes. The `AtomicBenchmark` log tag reports selected numeric sizes only. For the smoke, verify `Selected fixture entries: 100`, the two `[100 entries]` scenario names and JSONL `entries: 100`; do not accept a log showing 1,000/10,000 scenarios. Existing validation above predates this argument change; root must rebuild and rerun the added non-mutating selection contract before the smoke is claimed passed.

The default full-suite command below is retained for documentation and is **not authorized by the current 100-entry smoke approval**:

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.devbehindyou.atomicfilemanager.benchmark.FolderScrollBenchmark
```

Without `fixtureEntries`, the class runs six scenarios (opening and scrolling at each of three sizes), each with ten measured iterations plus library warmups. With `fixtureEntries=100`, it runs two scenarios at 100 entries. Each corpus also owns the one-file reset folder described above. Fixture insertion/cleanup may take substantial time, particularly at 10,000 entries, and is not included in measured timings. Budget storage/runtime before the large case. Compilation mode may force-stop the target as part of Macrobenchmark; use a test device with no active real file operation.

Macrobenchmark frame/Perfetto artifacts use the normal connected-test additional-output directory. Raw JSONL observations are written to the benchmark APK's own `getExternalFilesDir(null)/benchmark-results/`, with an app-private fallback when external app storage is unavailable. Pull those files before uninstalling the test APK. Each file contains environment, measured observations and a terminal success/failure record; only accept files with successful terminal state and matching expected sample count. The APK hash is the installed **base APK**, not a universal AAB/download-size measure.

Contract cases cover unique/bounded names and thumbnail budgets at all reviewed sizes, rejection of unreviewed/invalid sizes and indices, and refusal to clean up a mismatched or missing owner/path/name. They require no provider mutation. Actual MediaStore write/delete behavior, exported UI selectors and scroll gestures still require physical/emulator acceptance.

## Official API references checked

- [Shared-media ownership, DATA and relative paths](https://developer.android.com/training/data-storage/shared/media).
- [MacrobenchmarkScope launch and iteration contract](https://developer.android.com/reference/androidx/benchmark/macro/MacrobenchmarkScope): primitive launch extras are supported; the launcher category is required for modern Android; null iteration identifies non-measurement work.

No baseline profile, speed-up percentage, p95/p99 target pass, API27 folder result or complete U02 milestone is claimed by this source increment.
