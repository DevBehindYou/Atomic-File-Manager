# Large and hostile native PDF sessions — C021

Claude Code task **C021** (PDF1/PDF5; PDF-T01 increment). Base: feature integration `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Branch `codex/claude-c021-pdf-hostile-sessions`. Recorded 7 November 2026 UTC. Host results were run locally. Native results come only from the hosted PDF workflow; no phone or device benchmark was used.

## Defect PDF-B02: a password-protected PDF was reported as damage

`PdfRenderer(ParcelFileDescriptor)` throws `SecurityException` when a document needs a password or uses a security handler it does not support. It throws `IOException` for a damaged or non-PDF file. `PdfDocumentLoader.withDocument` caught both with one `catch (_: Exception)`, so both became `FileError.IoFailure("PDF preview unavailable")`. The reader then showed one message, "may be damaged or password protected", and a Retry button. Retry could never open an encrypted file.

**Change.** Only the renderer construction step (`openStaged`) maps `SecurityException` to `FileError.UnsupportedFormat(PDF_PASSWORD_PROTECTED)`. This follows the archive `PASSWORD_PROTECTED` marker convention and adds no new `FileError` variant. Staging already happens before this step, so a revoked provider grant cannot be read as a password: a `SecurityException` from `openInput`/`stream()`/`read()` still gives a generic failure. The reader shows `pdf_password_protected` and leaves out Retry for that case only. A new source revision still reopens the document automatically. The generic text is now "It may be damaged or unavailable." The 128 MB staging cap, the staged-copy and renderer ownership, the serial renderer mutex, the raster bounds and the C003-owned `File.createTempFile` line are all unchanged.

## Failing control

| Run | Source | Result |
| --- | --- | --- |
| Host `PdfHostileSessionTest` on unchanged loader (only the marker constant added) | `7be3dde` | 6 run, **1 failed**: `a password protected document is reported distinctly from a corrupt one` — expected `UnsupportedFormat(password-protected PDF)` but was `IoFailure(PDF preview unavailable)`. The other 5 pass on base. |
| Native, test-only commit, workflow dispatch [run 42338904387](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/42338904387) | `7be3dde` | API 28 and API 34 each: 11 run, **2 failed**, and only the two expected cases failed: `passwordProtectedDocumentIsReportedDistinctlyFromCorruption` (expected `UnsupportedFormat(password-protected PDF)` but was `IoFailure(PDF preview unavailable)`) and `sourceLossKeepsTheOpenCopyThenPasswordAndRecoveryAreReported`. `repeatedDisposalReleasesEveryNativeDocument` passed on both. |
| Native, fix head, PR [run 42339191724](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/42339191724) | `38f037d` | API 28: 11/11 passed. API 34: 11 run, 1 failed: `repeatedDisposalReleasesEveryNativeDocument`, "Descriptor growth 12 across 12 reader lifetimes". Investigated below. |

## Coverage added

Host (`app/src/test/.../data/preview/PdfHostileSessionTest.kt`, `ui/components/preview/PdfOpenErrorTest.kt`):

- Password versus corruption: `SecurityException` from the renderer gives the password marker, and `IOException` gives generic `IoFailure`. The two are distinct. In both cases the source closes and the staged copy is removed.
- A provider `SecurityException`, thrown either at stream open or on read, is never reported as a password. The renderer is never opened and the copy is removed.
- 1000-page session over eight mixed page boxes: 1×1, 72², A4 portrait and landscape, 14400², 14400×1, 1×14400, and 7016×9921 (A4 at 600 dpi). 1000 concurrent `renderPage` calls give **peak in-flight renders 1**, 1000 renders, a peak of ≤ 4,000,000 px and a max edge of ≤ 1600. Out-of-range and zero-dimension requests fail without rendering. The renderer closes once and the copy is removed.
- A malformed page (index 500) fails only itself. Pages 499, 501 and 999 still render.
- Source loss: the source is deleted during the session, and every page still renders from the byte-identical staged copy. The next session reports `FileNotFound`. A replaced source is staged fresh, not reused.
- 150 repeated sessions (completed, zero-page `UnsupportedFormat` and cancelled mid-session): 150 renderers opened, every one closed exactly once, and the cache directory is empty after each cycle.
- UI mapping: the password case gets its own text and no Retry. Corruption, zero-page and missing-file cases keep the generic text and Retry. Too-large is unchanged.

Native (`app/src/androidTest/.../PdfHostileSessionDeviceTest.kt`, fixtures in `HostilePdfFixtures.kt`), added to `pdf-source-session-native.yml` (API 28 and 34):

| Case | Asserted bounds |
| --- | --- |
| 1000-page generated document; pages 0, 999, 500, 1, 998 and every 37th | Each bitmap ≤ 1600 px per edge and ≤ 4 MP. Peak `allocationByteCount` ≤ 16,000,000 bytes. Every bitmap's centre pixel is that page's own colour (red/blue by parity). Exactly one staged copy. ≥ 1 descriptor on the staged copy while open and **0 after close**. The escaped session fails. |
| Mixed page boxes: 1², 10², 72², A4, A4 landscape, 14400², 14400×20, 20×14400 | Every bitmap stays within budget. Aspect ratio is within 5% for any bitmap of at least 50 px per side. |
| Scanned-like page: A4 carrying a 2000×2800 incompressible grey raster (> 4 MiB file) | The staged copy length equals the source length. The rendered page stays within budget and is not blank. |
| Malformed: empty, 4 KiB garbage, header-only, zero-page catalog | Each fails clearly, never with the password marker. No staged copy and no staged descriptor remain. |
| Truncated to 30/60/90% | Either a non-password failure, or pages that render within budget. Cleanup as above. |
| RC4-40 standard-security PDF (user password set) with an unencrypted twin | The twin renders blue. The encrypted file gives `UnsupportedFormat(PDF_PASSWORD_PROTECTED)`. Garbage gives a different error. |
| Compose reader on 1000 pages: jump to 1000, Next disabled, Previous, jump to 500, 20 rapid Next | The label tracks the displayed page (520/1000). **Exactly one `pdf_page` node** is on screen. The staged descriptor is held. |
| Compose source loss and replacement | Source deleted while open: Next still renders from the copy. A new revision shows the generic error, with copy and descriptors released. Encrypted bytes plus Retry show the password text with no Retry. A valid replacement plus a new revision shows 1/1. |
| Repeated disposal, 12 reader lifetimes | Each lifetime holds one staged copy and its descriptor. After each one, 0 copies remain and no descriptor targets any test-owned file. |

The fixtures are generated in each test's private cache directory and only owned files are deleted. The encrypted fixture was checked locally against independent readers: poppler `pdfinfo` rejects the empty password and opens the file with the user and owner passwords, and `pypdf` decrypts the content stream to the original operators.

## API 34 descriptor growth: platform frame fences, not a PDF leak

The first disposal test bounded the *total* process descriptor count (growth < cycles). At `38f037d` it failed on API 34 with +12 over 12 lifetimes. The same test passed on API 34 at `7be3dde`, where the disposal path was identical. The commit `56ddf9d` added a temporary diagnostic test. It diffed `/proc/self/fd` readlink targets (with inode numbers normalized) after one warm-up lifetime, and reported through its failure message because artifact downloads are blocked from this host. [Run 42339979995](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/42339979995) (jobs 128305292059 API 28, 128305292347 API 34) gave:

| Phase | API 28 | API 34 |
| --- | --- | --- |
| 12 PDF reader lifetimes | 62→62, diff {} | 114→130; the whole diff is `anon_inode:sync_file` +16. Per cycle: 126, 127, 129, 128, 127, then 130 ×7 |
| 12 plain `Image` (non-PDF) lifetimes | 62→62, diff {} | 130→130, diff {}; fluctuates 129–130 |
| 24 further PDF lifetimes | 62→62, diff {} | 130→130, diff {}; fluctuates 127–130 |
| 3× GC + finalization | diff {} | diff {} |
| Descriptors on any test-owned file (source or staged copy) | none | none |

Conclusion: **(b), not ours.** The growth is a pool of HWUI/graphics `sync_file` fence descriptors. It fills to a plateau within about six lifetimes, varies frame to frame and then stops growing. The PDF path opens no `sync_file`. On both API levels no descriptor to the source or staged copy survives a lifetime. Whether the earlier pass and fail cross the cycle bound depends only on where the pool started. The diagnostic was removed in the next commit. The disposal test now asserts by target instead: after each of 12 lifetimes the staged-copy descriptor was held (≥ 1) while open and **no descriptor at all remains on any test-owned file**. `@After` asserts the same for every test. This is stricter for our descriptors and does not tolerate platform descriptors as a blanket allowance.

## Validation (local, serialized Gradle lease)

| Command | Result |
| --- | --- |
| `:app:testDebugUnitTest --tests '*PdfHostileSessionTest*'` on control commit | 6 run, 1 failed (above) |
| `:app:ktlintFormat :app:testDebugUnitTest --tests '*PdfHostileSessionTest*' --tests '*PdfOpenErrorTest*' --tests '*PdfDocumentLoaderTest*' --tests '*PdfPageControllerTest*' --tests '*PdfPageSizeTest*' --tests '*PdfSourceSessionComposeTest*' :app:compileDebugAndroidTestKotlin` | 28 run, 0 failed: Hostile 6, OpenError 1, Loader 11, Controller 4, PageSize 1, SourceSession Compose 5. androidTest compiles. |
| `:app:ktlintCheck :app:detekt` | BUILD SUCCESSFUL |

## Measured bounds and what they mean

- Retained pages: the controller publishes one rendered page. The 1000-page Compose journey asserts that exactly one page node is ever on screen. There is no prefetch or page cache, so peak retained page bitmaps is 1 (plus a page whose render is in progress and not yet published).
- Bitmap allocation: each page is at most 4 MP ARGB_8888, which is 16,000,000 bytes, and the native test asserts this as its observed peak. Renders are serialized: host peak in-flight is 1.
- Descriptor closure: the native tests read `/proc/self/fd` link targets under the test's private cache. The count is ≥ 1 while a session is open and 0 after every close, failure, loss and disposal.
- No allocation or descriptor leak was observed in host runs. No timing or throughput figure is claimed.

## Remaining

- Native outcome on the current PR head (API 28/34) after the targeted disposal assertion.
- No password entry or unlock (PDF-T03/T04 engine decision). Text, search and forms are still not supported.
- Physical-provider disconnects (SAF/USB/network) and memory pressure on real devices are not exercised. Device benchmarking remains owner-deferred.
- The PDF1–PDF5 groups are not complete. This is a scoped slice.
