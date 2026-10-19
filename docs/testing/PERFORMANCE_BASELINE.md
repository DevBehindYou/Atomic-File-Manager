# Performance baseline and verification evidence

Date: 17 October 2026. Source baseline `7782660`. Host Windows/PowerShell, Java 21.0.1, Android SDK configured at the local SDK path; no reference-device performance evidence yet. Existing CI results in handover apply to earlier commits only.

| Metric | Baseline | Target / method | After |
|---|---|---|---|
| Cold startup | Unmeasured | StartupBenchmark COLD, 10 iterations on same device/build; record median/p90 | Pending |
| Warm startup | Unmeasured | WARM same methodology | Pending |
| Large directory load | Unmeasured | Generated 10k and 100k entries; first chunk and completion separately, local and SAF | Pending |
| Navigation | Unmeasured | Root/child/back repeated; trace disk/provider work and main thread | Pending |
| Memory | Unmeasured | Steady and peak heap on fixed dataset; no monotonic growth after repeated browse disposal | Pending |
| Idle CPU | Unmeasured | Same device, 60s idle after settling; record services/network state | Pending |
| Search latency | Unmeasured | 100k synthetic index; text/category filters, median/p95, cold/warm DB | Pending |
| Copy throughput | Unmeasured | Generated files only; verified source and target; SD/USB/internal separately | Pending |
| UI responsiveness | Unmeasured | FolderScrollBenchmark frame p90/p99; fixed screen/thermal/power state | Pending |
| Progress amplification | Source-derived 66 events for 65-buffer fixture | Deterministic clock/input; <=4Hz plus boundaries | 2 events in frozen-clock regression, exact final totals; no device speedup claim |

No percentage speedup, startup improvement or memory reduction is claimed. Do not replace missing hardware measurements with host timings. Targets are methodology/acceptance goals; the existing NFR document holds product budgets.

## Reproduction

Use JDK21. Run wrapper `--version`, then `ktlintCheck detekt :lint-rules:test :app:testDebugUnitTest :app:lintDebug :app:lintRelease :app:assembleDebug :app:assembleRelease :app:assembleDebugAndroidTest :benchmark:assembleBenchmark`. Run device tests on dedicated test storage/devices; never seed or clean a user's real folders. Macrobenchmark generators live in `benchmark/src/main/kotlin/com/devbehindyou/atomicfilemanager/benchmark/`.

Initial baseline attempt: wrapper could not create cache lock inside sandbox; retry with authorized cache access reached download and failed with a 10s connection timeout for Gradle9.3.1. This is an environment failure, not a test pass/failure. Later attempts must be appended with exact task outcomes.

Search correctness reproduction (UI audit): host Python3.14.6/SQLite3.50.4, production schema/query, 2,000 matching directories plus one PDF. SQL LIMIT returned 2,000 directories, subsequent PDF filtering returned 0 (expected 1). Query plan SCAN plus temporary ORDER BY B-tree. This demonstrates a filtering bug, not Android timing.

18 October checkpoint: 68 selected JVM regressions passed using the current changed core sources and cached Kotlin tooling. See [MODERNIZATION_VALIDATION](MODERNIZATION_VALIDATION.md) for the precise subset, limitations, standard gate attempts and deterministic progress evidence. Full Gradle/Android acceptance is recorded separately.
