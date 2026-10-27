# Cloud Connect validation ledger

27 October 2026. **Documentation-only checkpoint.** Production source inspected at feature integration `863f78702de1066f6d7fc3c7f8dddef9788ffb6d`. Official documentation was read as recorded in [Research](RESEARCH.md). No cloud SDK was installed; no account was registered or accessed; no cloud request with credentials, Gradle build, ADB test or transfer was run.

This checkpoint claims source observations and a plan only. Host-verified/cloud-accepted implementation is zero; no separate numeric denominator is frozen yet. Do not change the existing eight-plugin N42 or count this documentation as a completed U07/U08/U12 milestone.

## Required evidence for implementation

- Identity round trips and cross-account isolation, including identical remote IDs, mutable names and malformed input.
- Paging continuation, empty/repeated pages, duplicate IDs, cyclic or expired cursors, partial errors and cancellation; bounded memory and network work.
- OAuth decline, wrong/replayed state, lost callback, expiry, reauthorization, concurrent refresh, refresh rotation and disconnect during completion. SDK-owned token behavior needs its own acceptance.
- Capability changes, inaccessible versus absent files, unknown length/quota, provider-native export and account switch during file preparation.
- Downloads/uploads with hash verification, short reads, low space, quotas, conflict/ETag failures, ambiguous final publication, expired upload sessions, cancellation and retry limits.
- 429 Retry-After and bounded backoff; no blind mutation replay. HTTP continuation/download URLs require provider-specific validation before forwarding credentials.
- Real native and release-like runs on disposable service fixtures; preserve data/permissions and never treat mocked auth as acceptance.
- Exact source/APK/dependency versions, successful current-head CI, resolved review, clean integration trial, size and relevant performance evidence. Measure before asserting improvements.

For each increment record baseline/fix, exact commands, pass/fail/skip counts, artifact hashes and remaining limitations. Keep host, native and release acceptance distinct. No inherited beta runtime or unrelated viewer tests close these cloud gates.
