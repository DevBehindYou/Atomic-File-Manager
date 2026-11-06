# Modernization validation — 20 October 2026

Baseline: `7782660`. Integration branch: `codex/architecture-audit`. Read the current [progress record](../roadmap/IMPLEMENTATION_PROGRESS.md) for final gate status. Historical feature CI is separate evidence.

## Current UI integration at `4792db8`

All 917 app host cases, 28 custom Lint-rule cases, ktlint/detekt and debug/test
packaging pass. All 13 selected API34 phone cases pass, including decoded preview
pixels, DOCX find, recreation, Tasks and Files controls. Manual content-sized top
options and bottom icon-card menus pass; type classification and light/dark
contrast checks pass. Debug/release Android Lint, R8 release and benchmark test-APK packaging passed in the complete 14m36s Gradle run, exit 0.
See [menu/icon evidence](MENU_REFINEMENT_VALIDATION.md) for exact APK hashes,
scope, earlier fixture failures and current limitations.

## Historical integration checks at `4d19ae1`

The following ledger records that checkpoint only. Later preference/startup and viewer
changes have separate evidence in the [progress record](../roadmap/IMPLEMENTATION_PROGRESS.md).

| Check | Result and scope |
|---|---|
| Self-contained Kotlin compilation | Passed for current queue, archive, trash, operation engine, storage read use cases, search, breadcrumb resolver, FTP protocol helper and DAV path helper |
| Targeted JVM regressions | 68 passed at the intermediate checkpoint; includes 18 archive, 15 trash, 13 search, 8 breadcrumb, 5 journal, 2 progress, 2 dispatcher, 1 destination and 4 DAV path tests |
| Standalone application Kotlin formatting | Passed with cached ktlint 1.0.1 after explicitly restricting input to this checkout's `app/src` |
| Full app JVM/Robolectric suite | **663 tests, 0 failures, 0 errors** in the final standard Gradle run |
| Custom Lint-rule tests | **28 tests, 0 failures**, all eight test classes |
| Standard ktlint / detekt | Passed in the final standard Gradle run |
| Android Lint | Debug and release tasks pass; debug rerun has zero errors, 148 warnings |
| APK packaging | Debug, R8 release and debug instrumentation APKs assembled; benchmark packaging remains |
| Socket tests inside sandbox | Could not execute: loopback connection denied by sandbox; this is an environment restriction, not a protocol assertion failure |
| Connected-device surface tests | Four automated navigation/recreation tests pass on API34; manual tabs/Back/rotation/sheets pass; see [device report](DEVICE_SURFACE_TEST_2026-10-19.md) |
| Broader device / macrobenchmark tests | Not run in this surface-only pass |

The earlier self-contained check uses Kotlin 2.2.10 and cached dependencies, compiles changed logic from source against the existing application compile jar, and invokes selected no-lifecycle JUnit 4/5 methods. It excludes Android provider/Compose/Room integration and network fixtures. Its local reproduction is `build/audit-tools/run-protocol-tests.ps1 -Suite Core`; output is `build/audit-tools/host-core-tests.log`. These generated host tools are local artifacts under ignored `build/`. The final standard Gradle run supersedes this limited checkpoint and includes the later journal-state persistence regression.

The first final run completed tests/static checks and debug assembly, then was stopped during release R8 after sustained full-heap garbage collection (about 100% old-generation occupancy, 438 full collections and 623 seconds collecting). Its log is preserved alongside `final-verification.interrupted.txt`; it has no successful overall exit marker. `release-validation.log` records successful R8 release assembly with one worker and a local 3 GiB Gradle heap. That command exited 1 solely because debug Lint reused the old SDK-property error; release Lint passed. `surface-test-packaging.log` then records an explicit debug analysis rerun, successful debug Lint and instrumentation APK assembly, with exit 0. These resource flags and the ignored SDK-property fix do not change checked-in build configuration.

The debug APK is built and its signature verifies as Android Debug. `aapt dump badging` confirms application ID `com.devbehindyou.atomicfilemanager`, minSdk 27 and targetSdk 36. This is an installable test artifact, not owner-signed production output.

The 68-test intermediate subset alone did not establish full-suite or APK validation. The later standard checks above provide that broader evidence. No tests were disabled to accommodate this host; the phone surface run deliberately selected four existing non-mutating UI tests.

## Deterministic performance evidence

`OperationProgressThrottleTest` copies 16 MiB + 123 bytes with a frozen injected monotonic clock. It observes two running progress events (initial and final) with exact final byte/item totals. The original source emitted on every copied buffer: 65 buffers plus the initial event for this fixture, or 66 source-derived events. The old event count was inferred from source, not measured on a device. A second test advances the clock by 50 ms and checks interval emissions and monotonic byte totals. This verifies reduced event amplification; it does not establish faster file throughput or lower device CPU.

Search fixtures verify that category matches after more than 2,000 matching folders or other file types remain discoverable, pages are bounded at 500 rows, and the requested cap counts matching results. Android search latency remains unmeasured.

## Required remaining acceptance

1. Run macrobenchmarks and generate a baseline profile on approved hardware, including assembly/installation of the tested app variant; benchmark test-APK packaging alone is not measured performance.
2. Check the PR head revisions independently in CI; the integration branch's results do not prove every isolated or stacked review branch by themselves.
3. Run actual API 27/36 provider, USB, network, process-kill and performance acceptance on generated test data; record device/build/dataset/thermal conditions.
4. Keep filesystem/provider/protocol changes for human review and production signing for the owner.
