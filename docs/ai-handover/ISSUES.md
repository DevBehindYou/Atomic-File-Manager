# Issues and failed approaches

Updated **4 November 2026**. Historical IDs/fixes remain in [the archive](history/2026-11-04-pre-refresh/ISSUES.md).

## ISS-101 — GIF acceptance incomplete

Status: PARTIAL. Original BitmapFactory preview showed one static frame. Candidate 9c96985 failed phone disposal: `AnimatedImageDrawable.lambda$postOnAnimationEnd$2` iterated a cleared callback list. Callback-free 60031fa passed the looping API34 journey, but API28 aborted on `AnimatedImageTh -> ByteArrayStream::read -> get_env_or_die` (`Failed to get JNIEnv for JavaVM`). Its 600 ms finite fixture also expired before initial API34 capture.

Source-backed repairs at fb856a6: lifecycle/current-page coroutine owns playback without native animation callbacks; immutable direct ByteBuffer avoids per-frame heap-array JNI reads; finite fixture retains actual Replay colours and natural-completion assertions with a usable observation interval. Host/API28/API34 CI passes (TESTING). Current phone, review and integration gates remain. Encoded/dimension caps are not a whole-heap/frame-count/time guarantee. Stop/Replay is not true Pause/Resume.

## ISS-102 — Markdown acceptance and CI trigger gap

Status: PARTIAL. Published 7d54483 static CI passes; full unit/native cancelled. Its workflow watches only workflow/native-fixture files, omitting production viewer/reader/strings. Proposed trigger repair was never applied: recovered checkout remains clean. Add exact paths from PENDING. Combining Markdown/GIF conflicts in `docs/plugins/modernization/PLUGIN_PROGRESS.md`; production sources are disjoint. Preserve both partial records and denominator.

## ISS-103 — Missing local evidence/worktrees

Status: VERIFIED absence; cause UNKNOWN. Primary `build/audit-tools` and nested checkout directories were absent on 30 October. Git retains refs/stale registrations. Recovered GIF/Markdown managed checkouts exist. GIF full-build log stops after compilation, lacks `.exit`, and no Java build is running. Never infer success or claim missing raw logs still exist. Recover hosted evidence by run/source IDs; inspect retained commits/ownership before pruning anything.

## ISS-104 — Measured performance baseline deferred

Status: BROKEN measured scenarios; owner deferred execution. Prior 100-file startup/scroll scenarios failed; provider fixture correctness passed separately. Later preflight hung on root-manager probing. Owner chose unchanged permissions and deferred benchmarking. Host/provider tests, packaging and system-compositor rows do not establish an Atomic performance baseline or speedup.

## ISS-105 — Native SQLite JNI intermittency

Status: UNKNOWN root cause. PR#98 retained 42 native SQLite failures before assertions, then an unchanged rerun passed. PR#100 compares six unchanged consumers under normal/isolated forks; 49 cases each passed. A diagnostic pass is not an app fix. Preserve failures; don't globally switch to LEGACY SQLite to conceal them.

## Superseded issues

ISS-001 (missing release key) is OBSOLETE: dedicated signing/published beta exist. ISS-002 (no hardware work ever) is OBSOLETE as a blanket claim: selected native/phone journeys pass, while wider network/USB/process-death/performance remains PARTIAL. API27 is no longer the supported baseline.

## Do not repeat

- Array-backed animated buffers on API28 or callback unregister around queued completion caused real crashes.
- Initial finite-frame capture must not assume a brief frame survives cold UI synchronization; test Replay and natural completion explicitly.
- ADB exit 0, a compiled APK, a cancelled build, old-head CI, missing logs or planned agent work do not establish acceptance.
- Preserve original failures and exact provenance. Keep FA-001 buffer-relative kill fixtures, FA-002 linter exit-code checks and FA-003 category freshness prerequisites from the archive.
