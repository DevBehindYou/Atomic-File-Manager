# Thumbnail scheduling bounds and cache identity — C004

Claude Code task **C004** (U03, IMG6). Base: feature integration `89140e3f3d4a746e6a907912042911cb10c8f6a3`.
Recorded 7 November 2026. Host-verified only; no device latency is claimed.

## Audit of the existing paths

| Path | Bound before C004 | Cache identity | Finding |
| --- | --- | --- | --- |
| Local, SAF and MediaStore images (`buildThumbnailRequest`) | Coil 3.3.0 decodes at tile pixel size with `Precision.EXACT`. Coil's static image decoders share one 4-permit semaphore (`bitmapFactoryMaxParallelism`, read from the 3.3.0 bytecode). Rows that leave composition cancel their `AsyncImage` request. | Memory key `thumbnail:<id length>:<id>:<modifiedAt>:<size>:<kind>:<px>`. | No defect. A replacement with a new `modifiedAt` or size, another volume, provider authority or user, another kind or another row size each gets a new key. An unchanged revision is a memory hit. |
| Videos (`ProviderVideoThumbnailFetcher`) | 2 process-wide permits, 512 px edge, `CancellationSignal` sent to the provider, late bitmaps recycled. | Same memory key, with kind `VIDEO`. | No defect. New coverage asserts the bound with slow providers and shows that queued rows which scroll away never call the provider. |
| Local folder miniatures (`FolderThumbnailReader`) | Each peek examines at most 64 entries, returns at most 4 previews and checks at most 128 ancestors. **The number of peeks running at once had no limit.** Each row keeps its own reader, so a grid of visible folders started one IO peek (and one open directory stream) per folder at once, competing on `Dispatchers.IO` with Coil's fetchers for the visible images. | Child sources reuse the file key above, including each child's `modifiedAt` and size. | **Defect: no in-flight limit.** Fixed here. |
| Remote IDs (`sftp`, `ftps`, `smb`, `webdav`, accounts by server id) | No thumbnail source is built, so no request is made. | — | No defect. New coverage asserts it. |

The disk-cache key (`thumbnail:<id length>:<id>:<modifiedAt>:<size>`) also carries the revision. The thumbnail path registers no network fetcher. Coil's file and content fetchers read directly and do not write its disk cache. This comes from reading the code and was not tested separately here.

## Change

`FolderThumbnailReader.read` checks eligibility first. This is a pure check: remote, hidden and unreadable folders return at once without taking a permit. The IO peek then runs under a process-wide `Semaphore(MAX_CONCURRENT_PEEKS = 2)`, shared by every row's reader instance. Waiting is suspending and can be cancelled. When a row leaves composition before its turn, `produceState` cancels it and it drops out of the queue without touching the filesystem. The remaining visible rows run in composition (FIFO) order. The 64/4/128 per-peek bounds, the privacy and symlink checks, and the closing of streams on cancellation are all unchanged. No new dependency, module or DI is added.

## Failing control

To compile the new test against unchanged behaviour, only the constant `MAX_CONCURRENT_PEEKS = 2` was added to base; no limiter was added. In that run, `FolderThumbnailReaderTest` had **8 cases and 1 failed**:
`visible rows share a small peek bound and rows scrolled away before starting never open()` failed with `A peek beyond the bound started ==> expected: <false> but was: <true>`. A third directory open started while the first two were still blocked. The same run's coverage tests passed on base: `ProviderVideoThumbnailReaderTest` (10) and `ThumbnailSourceTest` (12, including the new Coil identity case). This confirms there is no video or cache-key defect.

## Measured counts (host fixtures)

| Fixture | Requests | Max concurrent | Opened/loaded | Allocations |
| --- | --- | --- | --- | --- |
| 12 folder rows, slow `openDirectory`, last 4 cancelled while queued | 12 | base: >2 (failed); fixed: **2** | fixed: 8 (rows 0–7); 0 of the cancelled rows | 8 preview lists of 1 source each; 0 streams left open |
| 8 video rows, slow provider, last 3 cancelled while queued | 8 | **2** (base and fixed) | 5; 0 of the cancelled rows | 5 bitmaps (40 px), all distinct and recycled |
| Coil identity: real `ImageLoader`, counting fetcher | 12 `execute` calls | — | 9 fetches, 9 distinct memory keys, 3 memory hits (rapid reuse of an equal node, revisiting two unchanged revisions) | 9 bitmaps, one per fetch |

## Validation

| Command (each wrapped in the shared `flock` Gradle lease) | Result |
| --- | --- |
| `bash ./gradlew --no-daemon -q :app:testDebugUnitTest --tests '*FolderThumbnailReaderTest*' --tests '*ProviderVideoThumbnailReaderTest*' --tests '*ThumbnailSourceTest*'` on base plus the constant only | 30 tests, 1 failed (control above). |
| `… :app:testDebugUnitTest --rerun --tests '*FolderThumbnailReaderTest*' --tests '*ProviderVideoThumbnailReaderTest*' --tests '*ThumbnailSourceTest*' --tests '*FileThumbnailComposeTest*' --tests '*FileThumbnailTest*'`, run 3 times | Each run: 40 tests, 0 failed, 0 skipped. FolderThumbnailReader 8, ProviderVideoThumbnailReader 10 (5 × API 28/34), ThumbnailSource 12 (6 × API 28/34), FileThumbnailCompose 8, FileThumbnail 2. |
| `… :app:ktlintCheck :app:detekt` | BUILD SUCCESSFUL. |

The existing Compose row-reuse cases still pass unchanged: off-screen cancellation, the stale-preview reset on folder metadata change, and late completion after revocation.

## Thumbnail notes for the image viewer record

`docs/plugins/IMAGE_VIEWER.md` is held by an open Codex PR, so these notes live here. Browse thumbnails are a host feature, not part of the viewer. Folder miniatures now admit at most two filesystem peeks at once across all rows, and image/video thumbnail bounds are unchanged (4 decodes, 2 video frames).

## Remaining

- **Folder miniature rescan without folder metadata change** (found by reading the code, not reproduced in this PR). The miniature is keyed on the folder `FileNode`. If a direct child is rewritten in place and the folder's own `modifiedAt`/size stay the same while the row stays composed, the old child keys, and so the old pixels, remain until the row is recomposed with a new node. A fix needs a browse refresh generation or child revalidation; that is a separate behaviour change.
- Revision keys depend on provider metadata. A SAF or MediaStore provider that reports an unchanged `modifiedAt` and size for replaced content cannot be told apart.
- Priority is composition FIFO plus cancellation, not an explicit visible-first queue. Image fetches have no app-level in-flight cap beyond Coil's 4-permit decode bound and the IO dispatcher.
- Device latency and scroll-jank measurements remain owner-deferred.
