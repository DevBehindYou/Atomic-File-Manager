# Storage usage dashboard candidate

Date: 22 October 2026. Source implementation and independent review are complete; host compilation, static checks, unit/Compose execution and phone validation are **pending**. No timings or pass rate are claimed here.

## Scope and meaning

- Storage shows combined total/used/free filesystem capacity for mounted internal, SD and USB local volumes. A reliable volume identity or corroborated canonical path alias prevents duplicate capacity; distinct UUIDs are never collapsed just because a FUSE device number matches. Unknown or unrepresentable capacity remains explicitly unknown.
- A separate readable shared-file breakdown contains eleven exclusive families: images, videos, audio, PDFs, office documents, archives, APKs, text/code, e-books, fonts and Other. Hidden files participate. Extensions/MIME metadata classify files without decoding their contents; the local enumerator supplies names and metadata only.
- Category percentages use observed logical shared-file bytes, not total physical capacity. Private apps, system storage, inaccessible folders, links and filesystem overhead are not claimed as an exact breakdown. The unattributed used-space value is an estimate; unknown capacity or logical sizes exceeding physical usage suppress it.
- Existing volume cards, explicit deep-analysis tools, cache controls and network controls remain available. The automatic dashboard never invokes duplicate hashing or uses the hidden-skipping search index as all-file truth.

## Work, cache and cancellation

The collector runs on IO only while the Storage composable is visible and its lifecycle is STARTED. Leaving the screen cancels the scan and operation-finished subscription. Returning revalidates because operations may have completed while hidden. Visible completed operations and manual refresh bypass freshness. Storage/root/grant changes immediately clear the displayed scope; returning to an earlier scope still rescans.

The local source emits batches of at most 128 entries. The walker holds aggregate counts plus bounded path sets/queues, never an entire tree of FileNodes: 250,000 entries, 25,000 directories, depth 64, a 30-second planning deadline and a separate 30-second walk deadline. Progress emits at most roughly every 250 ms. Explicit empty success can complete; silence, denied or mixed failed listings, skipped links, budget exhaustion, unknown capacity or concurrent invalidation remain partial. External coroutine cancellation propagates.

One last complete result has a monotonic 120-second TTL. A separate single terminal aggregate may retain partial observations as clearly stale evidence while retrying; it never validates freshness or becomes a complete result. Failed planning, explicit refresh and mount/grant/root changes invalidate freshness. Failed or cancelled scans never publish a new complete cache.

Filesystem metadata syscalls are synchronous on IO. Cancellation/deadlines are checked between calls and batch entries; a blocked kernel/filesystem call itself is not forcibly interrupted. Files may also change independently during a walk, so the UI describes observed totals rather than a transactional filesystem snapshot. No user files are mutated, no new permissions/dependencies are introduced, and no content hashes or deep decodes occur.

## Added regression targets (not yet executed)

- `StorageUsageTest`: 31 cases including exclusive families, hidden/unknown names, MIME fallback/normalization, denominator separation, overflow and unavailable remainder.
- `StorageUsageRepositoryTest`: 16 tests for explicit empty success, denied/silent/mixed failures, recovery within TTL, bounded/hanging planning and listing, cancellation, entry/directory/depth limits, duplicate roots/entries, invalidation during work, changed scopes, grant loss/restoration and retained partial evidence.
- `LocalStorageUsageSourceTest`: 7 tests using real temporary filesystem metadata, hidden batching, missing/empty directories, symlink exclusion, aliases/UUIDs, mounted unknown/denied coverage, and physical-capacity overflow. The symlink test explicitly skips if the host disallows creating links.
- `StorageUsagePanelComposeTest`: 4 tests for capacity/category scope, actual refresh dispatch with stale complete data, unknown capacity, and retained partial evidence labeled as partial while scanning.
- `StorageUsageLifecycleTest`: 2 tests for actual scan and finished-event subscriber lifetimes across STARTED/STOPPED, and immediate scope reset while the new storage plan remains suspended.

Recommended serialized host check: format/static checks, these five test classes, debug and AndroidTest APK packaging, then Lint. Phone follow-up should verify internal+SD capacity, category meters, partial Android/private-folder coverage, refresh, screen background/return, and storage/grant changes. Run against owned fixture files and preserve unrelated user files.

Independent source review found and resolved stale scope display, unbounded suspended planning and A→B→A permission-cache reuse. `git diff --check` passes; full host/device acceptance is still required before publication or merge.

## Native dashboard candidate

`StorageUsageDashboardDeviceTest` adds two instrumentation cases, not yet compiled or run on a phone:

- Reads current mounted internal/removable capacity through the production local source and independently samples native `StorageManager`/`StatFs` probes before and after. Mounted unreadable volumes remain counted as unknown. Total capacity must match; free capacity allows background-write drift of the greater of 64 MiB or 0.1% of total capacity. No permissions or grants are changed.
- Uses the actual MainActivity Storage tab and real metadata scan, accepts a complete or truthful partial breakdown within 80 seconds per wait, scrolls to all eleven category rows, dispatches manual refresh, leaves/returns, and recreates the Activity. Recreation must retain the Storage screen and permit immediate navigation away. Refresh is a native dispatch/terminal-state smoke check; the separate deterministic host test verifies callback dispatch. The device case deliberately does not require observing a transient scanning state or identical live totals.

The native cases create no shared-storage fixtures, alter no settings, and perform no root/shell operations. They read mounted metadata and leave the UI on Home. They do not exercise grant revocation, physical unmount, true process death or OS background scheduling. Run the focused instrumentation class after host compilation/static/unit checks; native acceptance remains pending.
