# Storage capacity and on-demand Analysis candidate

Date: 22 October 2026. The latest opt-in behavior is source-complete but host/native validation is **pending**. Earlier automatic-dashboard results below are historical and do not validate this new behavior.

## Current behavior

Storage uses only the supplied mounted-volume snapshot for combined total/used/free capacity and volume cards. Its pure `storageCapacity(volumes)` helper does no filesystem IO or recursive work. Storage does not construct either analysis repository/use case, collect the metadata scanner, or calculate a recursive app-cache size. Tools and network controls remain available; Open Analysis opens the existing Analysis route.

Opening Analysis reads an already cached deep-cleanup result, if available, but starts neither scan. Unscanned cleanup shows an explicit message and Analyze storage action, with no zero-savings success claim. Analyze file types explicitly starts the bounded metadata scan; Analyze storage/Rescan explicitly starts deep cleanup. Their admission guards prevent simultaneous scans, including requests arriving before the next recomposition. Active requests cancel on STOP/disposal and do not restart automatically on return. Scrolling through the lazy list does not dispose scan ownership or discard results. Root changes reset the root-owned scan lease, cached cleanup display, selections and errors. Cleanup invalidates its cached suggestions after the queued action and does not automatically start another scan.

The file-type breakdown remains eleven exclusive families: images, videos, audio, PDFs, office documents, archives, APKs, text/code, e-books, fonts and Other. Hidden accessible files participate. Category percentages use their combined logical sizes, not physical disk capacity. System, private app and inaccessible data are not claimed as an exact file-type breakdown. Other used space is an estimate; unknown capacity or logical sizes exceeding physical usage suppress it.

## Work and cache bounds

The metadata source reads directory entries and attributes only, in batches of at most 128. The walker stores aggregate counts plus bounded path sets/queues: 250,000 entries, 25,000 directories, depth 64, a 30-second planning deadline and separate 30-second walk deadline. Work runs on IO. Explicit empty success can complete; silence, failures, skipped links, bounds, unknown capacity or concurrent invalidation remain partial. Files may change during a walk, so totals describe observations rather than a transactional filesystem snapshot.

A complete result alone can validate the repository's monotonic 120-second TTL; one additional terminal aggregate can retain clearly stale partial observations. Explicit UI requests bypass TTL. Errors, cancelled scans and changed mount/grant/root scopes do not become fresh complete evidence. No content hashing, content decoding or user-file mutation occurs in file-type analysis. Deep cleanup is a separate explicit action and retains its existing duplicate-analysis behavior.

Blocking filesystem syscalls cannot be forcibly interrupted; cancellation is checked between calls and entries. Volume capacity deduplicates reliable IDs or corroborated aliases, never distinct UUIDs merely sharing FUSE device numbers or identical capacity. Unknown/unrepresentable capacity stays explicit. Read-only mounted volumes remain available for browsing/accounting without granting write access or bypassing permissions. Legacy unsupported root discovery stays unavailable rather than guessing paths.

## Focused regression sources

- `StorageUsageTest`: 31 family, metadata, denominator, overflow and estimate cases.
- `StorageUsageRepositoryTest`: 16 empty/failure/retry, cancellation, bounded work, scope restoration and retained-evidence cases.
- `LocalStorageUsageSourceTest`: 7 real temporary-directory metadata cases; the link case explicitly skips when the host disallows creating links.
- `StorageVolumesTest`: 4 platform fixtures on each of API 27 and 34 (8 cases), covering RW/RO/unmounted, permission gating, unsupported legacy discovery and unreadable roots.
- `StorageUsageCapacityTest`: 8 pure snapshot aggregation cases with unknown/overflow, aliases and distinct identities.
- `StorageUsagePanelComposeTest`: 4 capacity/category scope and partial/unknown display cases.
- `StorageUsageLifecycleTest`: explicit requests are required before walking or subscribing; STOP cancels and return does not restart; changed scope clears prior totals and requires another request before a gated new plan.
- `StorageAnalysisOptInComposeTest`: real injected Analysis content proves no scan on entry, explicit separate actions, mutual exclusion, disposal cancellation, cached cleanup without a new traversal, and root-owned request lifetime.
- `StorageScreenLeanComposeTest`: actual Storage screen renders capacity and opens Analysis while guarded analysis getters fail if accessed.
- `StorageAnalyzerUseCaseTest`: adds invalidation coverage so stale cleanup suggestions cannot be returned after a cleanup action.

Latest sources have not been executed by this agent. Recommended serialized acceptance: format/static checks, focused data/Compose classes, existing affected cleanup tests, debug and AndroidTest APK packaging, then Lint.

## Native candidate and historical evidence

`StorageUsageDashboardDeviceTest` contains two focused native cases. Capacity compares the production source with independent StorageManager/StatFs samples before/after, allowing free-space drift of the greater of 64 MiB or 0.1% of total. The UI case now checks that Storage has capacity but no file-type panel, Analysis opens unrequested, an explicit file-type request renders all eleven families, refresh remains usable, and return/recreation require a new request. It does not start deep cleanup on the phone. A test-local keep-screen-on flag is restored and lifecycle/stage breadcrumbs help diagnose lost roots; no device preferences or grants change. This is a fixture guard, not a claimed diagnosis of the previous hierarchy loss.

Historical pre-opt-in host run `storage-usage-dashboard-acceptance-validation.log` passed static checks, 67 focused cases, APK packaging and Lint in 9m27s. Read-only follow-up `storage-readonly-mount-validation.log` passed static checks and 62 data cases (including 8 API27/34 volume fixtures) in 2m30s. These do not validate the latest opt-in changes.

The old native capacity case passed on the connected phone. Its old UI case failed after returning to Storage with No compose hierarchies after 284 seconds; no crash was captured in the retained logs. The failure remains historical and unresolved until the revised candidate passes a controlled native run. The phone's 30-minute timeout does not establish screen-off as its cause. No physical read-only device, grant-revocation, unmount, true process-death or OS-background-scheduling acceptance is claimed. The revised native run is pending; no failing acceptance is merge-ready.
