# C005 slice 2 — file-type evidence and interrupted walks survive process death

7 November 2026 (UTC). Author: Claude Code (Claude cloud). Reviewer: Codex. Stacked on slice 1 (`codex/claude-c005-analysis-freshness`, PR #118). Branch `codex/claude-c005-analysis-persistence`.

## Gap

After slice 1, Analysis re-entry restores what *this process* knows. Process death still lost the last complete breakdown, the last observed partial one, and the fact that a walk had started and never finished. So the first entry after a restart looked like "never analysed", even right after a walk was killed mid-way.

## Change

- **`StorageUsageStore`:** new, with `SharedPreferencesStorageUsageStore` (private file `atomic_storage_usage`; app backups are disabled by `allowBackup="false"`). It holds the last-good and observed results, each with its input and plan key, and the input key of a walk that never published a result.
  - It stores aggregate category totals and counts only, never file names or listings.
  - Writes use `commit()` on the repository's IO dispatcher, so the walk marker is on disk before the first listing.
- **`StorageUsageCodec`:** a versioned line codec with a CRC32 trailer.
  - A truncated, altered, foreign-version, oversized (>16 KB) or invariant-violating record (negative sizes, free above total, duplicate or unknown category) decodes to null and is simply not shown.
- **`StorageUsageRepository(store = …)`:**
  - Loads once, on IO only, and only fills fields this process hasn't produced itself.
  - Restored results carry revision −1, so they can **never** satisfy a cached `COMPLETE` hit. The next request always walks, and the restored result stays visible as `lastGood` while it does (when the plan key matches).
  - Writes happen twice per walk: the marker at walk start, then the final result. Entry, `lastResult` and `restore` write nothing.
- **Dashboard:** a `LaunchedEffect` calls `repository.restore(volumes)` (IO) and fills only an untouched `IDLE` entry. Entry still requires an explicit action, and no plan or listing runs.
- **Wiring:** `DefaultAppContainer` passes the store. Manual `AppContainer`, no Room schema change, no new dependency.

There's no shipped schema to migrate: the shipped app stored nothing for file types. A missing record behaves exactly like slice 1.

## Control

These are new capabilities, so the recreation tests can't compile against slice 1. As the control, the same `StorageUsagePersistenceTest` was run with `persist()` made a no-op, which is slice-1 behaviour. Result: **4/7 fail**:

- the new process sees nothing (×2);
- no restored previous result while rescanning;
- 0 of the expected 2 writes.

The codec, failing-store and isolation guards pass either way. The control edit was reverted before commit.

## Validation

All runs are local Robolectric (SDK 36) or plain JVM, under the shared Gradle lock.

| Command | Result |
| --- | --- |
| `--tests '*StorageUsagePersistenceTest'` with persistence disabled | 7 run, 4 failed (control) |
| Storage usage, analysis and cleanup tests (`*StorageUsage*`, `*StorageAnalysisOptIn*`, `*StorageScreenLean*`, `*StorageIntelligence*`, `*LocalStorageUsageSource*`, `*StorageCleanup*`) | 94 run, 0 failed, 0 skipped |
| `:app:ktlintCheck :app:detekt` | pass |

`StorageUsagePersistenceTest` covers:

- a new process shows the last complete result with 0 plans and 0 listings;
- a walk ended by process death is reported as interrupted, with the earlier complete result kept;
- restored evidence is never a cached hit but stays visible while rescanning;
- writes are bounded: 0 on entry, 2 per walk;
- other storage, mounts or grants see nothing;
- an unreadable or failing store only loses previous evidence;
- the codec round-trips and rejects 12 corrupt or foreign variants.

## Remaining

- Merge #118 first, then this PR retargets to `codex/feature-upgrade-base`. Hosted CI and Codex review.
- Search index: `retryRequired` after an interrupted rebuild is still in memory only. That needs a durable generation or checkpoint after Codex reconciles legacy #47 (`SearchIndexDao`).
- Revoked-root and delete/rename staleness show as "previous … may be stale" until the user refreshes. No automatic revalidation, by design (Storage stays lightweight).
- No device process-kill run.

## 10 November 2026 — persistence failure handling

The scan can finish in memory when preferences reject a commit or the store throws. Analysis shows that the history could not be saved. The warning follows the matching storage selection and survives an in-process cache hit. A successful refresh clears it. A restored interrupted marker means there is no saved final result, since a completed scan can also fail to save. The existing tests model process restart without touching user storage. New checks exercise rejected writes, throwing writes and recovery on retry.

The Windows run found existing fake paths built with Unix separators, which caused the scanner to skip synthetic files and report partial results. Both stacked PRs now construct the same fixture paths with the host separator. Production path handling and the assertions are unchanged.
