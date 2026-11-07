# C003 slice 1 — one owner for preview scratch copies across process death

7 November 2026 (UTC). Author: Claude Code (Claude cloud). Reviewer: Codex. Base `89140e3f3d4a746e6a907912042911cb10c8f6a3` (`codex/feature-upgrade-base`). Branch `codex/claude-c003-preview-resource-ownership`.

## Defect

Three preview paths stage a private copy of the selected file in the app cache:

| Creator | Prefix | Bound | In-process owner |
| --- | --- | --- | --- |
| `MediaPreviewHelper.copyToCache` (audio/video from FTP, SMB, WebDAV, cloud and other non-direct sources) | `media-preview-` | 512 MB | `withMedia` `finally` |
| `PdfDocumentLoader.useDocument` | `atomic-pdf-` | 128 MB | `useDocument` `finally` |
| `TempFileSpool.spool` (7z inspection and extraction) | `atomic-7z-` | archive size | `Spooled.close` |

Each copy is deleted correctly when its session ends, is cancelled or fails (the existing ownership tests cover that). Process death skips every `finally`. That happens when the system kills the backgrounded app, the user force-stops it, or it crashes during a long preview. Nothing in a later process owns the copy afterwards, so it stays in `cacheDir` until Android trims the cache under storage pressure. `external-preview/` already prunes after 24 h, but only on the next remote export. These three prefixes had no such owner.

## Change

`data/preview/ScratchCopies.kt` is the single process-wide owner of the three prefixes:

- `create(directory, prefix, suffix)` replaces the three `File.createTempFile` calls. It first runs `recoverOnce(directory)`.
- `recoverOnce` removes top-level regular files with an owned prefix, once per canonical directory per process, under one lock. Every creator passes through the same lock. So no copy made by the current process can exist while recovery runs, and a live copy can never be swept. This holds without relying on file times or the wall clock.
- `DefaultAppContainer`'s `init` launches `recoverOnce(cacheDir)` on `Dispatchers.IO`. Space is reclaimed at startup instead of at the next preview. If a preview starts first, its creation waits for the same lock.
- The following are never touched:
  - Folders and links that share a prefix (no symlink following).
  - `external-preview/`: external readers may open those copies later; its 24 h expiry is unchanged.
  - `clean/`: EXIF clean copies that are shared to other apps.
  - Coil's `image_cache/`.
  - Anything without an owned prefix.
- The scan is bounded to 4,096 top-level entries. One undeletable copy doesn't stop the rest. A missing or non-directory cache recovers nothing, and creation reports its usual error.

No module, DI framework, manifest, permission or dependency change. Manual `AppContainer` wiring and every existing source and revision key are unchanged. `PdfReaderDeviceTest` now asserts that its session leaves no new `atomic-pdf-` copy (`after - before` is empty) instead of strict set equality, because the startup sweep may legitimately remove an earlier process's orphan between the two snapshots.

## Ownership call sites, before and after

| | Before | After |
| --- | --- | --- |
| Creators of the three prefixes | 3 independent `File.createTempFile` calls | 3 calls to `ScratchCopies.create` |
| Owners within a session | 3 `finally`/`close` paths | unchanged |
| Owners across process death | none | `ScratchCopies.recoverOnce` (startup or first creation, whichever is first) |

## Failing control (unchanged base code)

`ScratchCopyRecoveryTest` drives the real creators through existing APIs only: `MediaPreviewHelper.withMedia` on an FTP id, `PdfDocumentLoader.withDocument` and `TempFileSpool.spool`. Each run starts from a fresh cache that already holds four orphans and eight unrelated entries.

- On base `89140e3` it fails **6/6** (3 creators × API 28/34). Every failure is the orphan assertion: `Orphaned scratch copies must be removed, found [media-preview-1.mp4, media-preview-2.tmp, …]`. No setup or compile error.
- With the change it passes 6/6, and all unrelated entries survive byte for byte.

## Validation

All Gradle runs below were local in the cloud container with `ANDROID_HOME` set to SDK platform 36, serialized with a shared lock.

| Command | Result |
| --- | --- |
| `:app:testDebugUnitTest --tests '*ScratchCopyRecoveryTest'` on base | 6 run, 6 failed (control) |
| `:app:testDebugUnitTest` for `ScratchCopyRecoveryTest`, `ScratchCopiesTest`, `MediaPreviewOwnershipTest`, `PdfDocumentLoaderTest`, `ArchiveFormatsTest`, `RemoteOpenPreparationComposeTest`, `ExternalPreviewHelperTest` | 55 run, 0 failed, 0 skipped (6 + 6 + 12 + 11 + 9 + 7 + 4) |
| `:app:ktlintCheck :app:detekt` | pass. Detekt first flagged `LoopWithTooManyJumpStatements` and ktlint flagged statement wrapping in the new test; both were fixed before the final run. |

`ScratchCopiesTest` (plain JVM) covers:

- a live copy survives later recovery requests;
- startup recovery counts orphans once;
- prefix-sharing links aren't followed or removed;
- a cache that isn't a directory recovers nothing and creation still fails;
- only owned prefixes can be created;
- 8 concurrent creations racing startup recovery over 32 orphans all survive, and exactly one caller recovers.

Hosted CI (Static analysis; Unit tests and APK packaging) on the PR head is the acceptance gate. The PDF native workflow runs if path filters include the device test.

## Remaining

- Hosted CI, Codex review and a clean trial against the current target. C002 (#108) is an unmerged dependency of C003 but touches no file in this slice.
- `external-preview/` copies still prune only on the next remote export. Running that existing 24 h prune at startup needs Codex's view on asynchronous external readers.
- Second C003 seam: `BuiltInFilePreviewPane` keeps an `ExternalPreparationRequest` that production never starts, because `FilePreviewPane` always passes `onOpenExternalForNode`. It is only reachable through the test-only fallback entry covered by `RemoteOpenPreparationComposeTest`. It could be made fallback-only or removed once Codex confirms the fallback's role.
- `PDF_READER.md` pointer deferred to the docs pass after C021 lands, to avoid an append conflict.
- No device process-death run was performed (no device use in this session). The recovery is exercised on host with fresh caches seeded as a killed process leaves them.
