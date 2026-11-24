# C005 slice 1 — honest file-type results when Analysis is re-entered

7 November 2026 (UTC). Author: Claude Code (Claude cloud). Reviewer: Codex. Base `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Branch `codex/claude-c005-analysis-freshness`.

## Defect

`StorageUsageRepository` keeps a last-good (complete) and a last-observed (partial or complete) file-type result in memory. `rememberStorageUsageAnalysis` started every owner at `IDLE` with nothing attached, so:

1. After a complete scan, leaving Analysis and coming back showed no breakdown, even though this process still held it. The only way to see it again was another explicit scan, and that scan wasn't served from the cache because `refresh = true`.
2. A scan the user left mid-way (navigation, or the screen stopping) wasn't distinguishable afterwards from never having scanned.

The cleanup analysis already restores a recent result on entry without scanning (`recentCleanupResultIsShownWithoutStartingANewScanOnEntry`). File types now get the same treatment.

## Change

This slice is in process only. It adds no persistence and no schema change.

- `StorageUsageRepository.lastResult(volumes)` returns what is known for exactly these volumes: `lastGood`, `previousObserved`, and `interrupted` (a walk started but published no final result). It plans nothing, lists nothing and does no IO.
- The key is the same input key the scan uses: id, mount state, root and grant requirement. Other storage, an unmounted volume or a revoked grant gets nothing.
- A restored entry is always `IDLE`. It offers the same explicit "Analyze file types" action and never starts work. That keeps Storage lightweight and Analysis user-initiated, and it preserves Codex's `changingAnalysisRootCancelsMetadataAndRequiresAnotherExplicitAction`.
- The panel labels restored evidence with the existing "Showing the previous complete/partial file breakdown from … It may be stale." wording. For an interrupted walk, the status reads "The last file type scan stopped before it finished. Analyze file types again when ready."
- A partial result never becomes last-good. A later final result (complete, partial, no storage or failure) clears `interrupted`.

## Failing control (unchanged base code)

`StorageUsageReentryComposeTest`, base API only: on base **2/3 fail**.

- `reenteringAfterACompleteScanShowsThePreviousBreakdownWithoutScanning`: no "Showing the previous complete file breakdown" node is shown.
- `reenteringAfterLeavingMidScanSays…`: the status falls back to the fresh IDLE text.
- `aResultForOtherStorageIsNotShownAfterAccessChanges` passes on base and after the change. It is the guard against showing results for storage whose access changed.

The first control attempt timed out because of a test bug: the second Refresh click landed outside the Robolectric viewport. The tests now use `performScrollTo()`. That was a test fix, not a product change.

## Validation

All runs were local Robolectric (SDK 36) under the shared Gradle lock.

| Command | Result |
| --- | --- |
| `--tests '*StorageUsageReentryComposeTest'` on base | 3 run, 2 failed (control) |
| `--tests '*StorageUsage*' '*StorageAnalysisOptIn*' '*StorageScreenLean*' '*StorageIntelligence*' '*LocalStorageUsageSource*' '*StorageCleanup*'` | 87 run, 0 failed, 0 skipped |
| `:app:ktlintCheck :app:detekt` | pass |

Breakdown of the 87: Reentry 3, LastResult 5, Repository 16, StorageUsage 31, Capacity 8, LocalSource 7, OptIn 5, Cleanup 5, Lean 1, Lifecycle 2, Panel 4.

The entry assertions count source calls: re-entry adds **0** `plan` and **0** `list` calls in every test. Retained state is unchanged: two snapshots, each about a dozen category totals.

## Remaining (C005 continues)

- **Slice 2:** persist `lastGood`, `observed` and the interrupted marker across process death, using a bounded preferences codec or an explicit migration. Then a scan killed with the process is reported as interrupted on the next start. Tests needed: migration from shipped data, a cancelled or restarted scan, a revoked root, and stale-completion ordering.
- **Search index:** `retryRequired` (after a failed or interrupted rebuild) lives only in memory. A rebuild interrupted by process death while `built_at` is still fresh isn't retried until the freshness window passes. It needs a durable generation or checkpoint. Legacy #47 (search filtering, Codex) touches `SearchIndexDao` and must be reconciled first.
- Hosted CI and Codex review. No device run, and no recursive scan on Storage entry by construction.
