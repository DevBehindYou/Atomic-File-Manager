# Modernization validation — 19 October 2026

Baseline: `7782660`. Integration branch: `codex/architecture-audit`. Read the current [progress record](../roadmap/IMPLEMENTATION_PROGRESS.md) for final gate status. Historical feature CI is separate evidence.

## Recorded checks

| Check | Result and scope |
|---|---|
| Self-contained Kotlin compilation | Passed for current queue, archive, trash, operation engine, storage read use cases, search, breadcrumb resolver, FTP protocol helper and DAV path helper |
| Targeted JVM regressions | 68 passed at the intermediate checkpoint; includes 18 archive, 15 trash, 13 search, 8 breadcrumb, 5 journal, 2 progress, 2 dispatcher, 1 destination and 4 DAV path tests |
| Standalone application Kotlin formatting | Passed with cached ktlint 1.0.1 after explicitly restricting input to this checkout's `app/src` |
| Full app JVM/Robolectric suite | **663 tests, 0 failures, 0 errors** in the final standard Gradle run |
| Custom Lint-rule tests | **28 tests, 0 failures**, all eight test classes |
| Standard ktlint / detekt | Passed in the final standard Gradle run |
| Android Lint / packaging | Running; see `build/audit-tools/final-verification.log` and matching `.exit` marker |
| Socket tests inside sandbox | Could not execute: loopback connection denied by sandbox; this is an environment restriction, not a protocol assertion failure |
| Connected-device / macrobenchmark tests | Not run; no device connected |

The earlier self-contained check uses Kotlin 2.2.10 and cached dependencies, compiles changed logic from source against the existing application compile jar, and invokes selected no-lifecycle JUnit 4/5 methods. It excludes Android provider/Compose/Room integration and network fixtures. Its local reproduction is `build/audit-tools/run-protocol-tests.ps1 -Suite Core`; output is `build/audit-tools/host-core-tests.log`. These generated host tools are local artifacts under ignored `build/`. The final standard Gradle run supersedes this limited checkpoint and includes the later journal-state persistence regression.

Do not describe this subset as the full test suite or APK validation. The standard test source remains in `app/src/test`; no tests have been disabled to accommodate this host.

## Deterministic performance evidence

`OperationProgressThrottleTest` copies 16 MiB + 123 bytes with a frozen injected monotonic clock. It observes two running progress events (initial and final) with exact final byte/item totals. The original source emitted on every copied buffer: 65 buffers plus the initial event for this fixture, or 66 source-derived events. The old event count was inferred from source, not measured on a device. A second test advances the clock by 50 ms and checks interval emissions and monotonic byte totals. This verifies reduced event amplification; it does not establish faster file throughput or lower device CPU.

Search fixtures verify that category matches after more than 2,000 matching folders or other file types remain discoverable, pages are bounded at 500 rows, and the requested cap counts matching results. Android search latency remains unmeasured.

## Required remaining acceptance

1. Complete Android Lint and debug/release/instrumentation/benchmark packaging; the final ktlint, detekt, full JVM/Robolectric and custom Lint tests have passed.
2. Check the PR head revisions independently in CI; the integration branch's results do not prove every isolated or stacked review branch by themselves.
3. Run actual API 27/36 provider, USB, network, process-kill and performance acceptance on generated test data; record device/build/dataset/thermal conditions.
4. Keep filesystem/provider/protocol changes for human review and production signing for the owner.
