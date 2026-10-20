# Built-in viewer audit — 2026-10-20

Current integration `4792db8` passes 917 host cases and all 13 selected API34 native cases, including image/folder decoded pixels, PDF and DOCX find. Current debug/release Lint and release/benchmark test-APK packaging pass. See [menu/icon validation](MENU_REFINEMENT_VALIDATION.md), [Files validation](FILES_WORKSPACE_VALIDATION.md) and [preview validation](PREVIEW_TOOLS_VALIDATION.md), including the retained Android resource warning and scope limits.

| Surface | Existing essentials | Changes in this increment | Limits / further work |
| --- | --- | --- | --- |
| File rows | Type icons, local media loader | Thumbnails in categories/search/recent/favourites; SAF/MediaStore sources; bounded local folder miniatures; per-row More boxes removed | Folder miniatures do not enumerate remote/SAF folders |
| Images | Gallery swipe, pinch/pan, double tap | AVIF routing; all eight EXIF rotations/reflections; bounded metadata and bitmap decoding; API34 native orientation/source-hash, BMP and PNG thumbnail display passed | Native decoded folder-miniature pixels pass; broader provider/device coverage remains open |
| PDF | Page count and previous/next | One bounded owned session, strict bitmap bounds, latest-page publication, retry, page jump, restored page and zoom | Raster zoom; no text search, forms, annotation or password unlocking |
| DOCX | Previously misrouted ZIP bytes into text by openxml MIME | Bounded read-only document text, headings/emphasis, list/table-cell order, selection, find and font size | Text layout; no Office page fidelity, inline drawings, editing or legacy DOC support |
| Audio/video | Media3 play/pause/seek/speed and background pause | Direct local/content-URI playback; bounded remote staging with cancellation cleanup; player reassignment and visible errors/retry | Remote staging limited to 512 MiB; codec support depends on device; rotation resume still needs work |
| Text/code | Monospace, line numbers, wrapping, find/replace; encoding/line-ending-preserving staged saves | Preview follows replacement IDs; expanded source extensions; ViewModel-owned drafts/selection across recreation; bounded read failures and lifecycle-safe save acknowledgement | Drafts are memory-only and do not survive process death; no IDE/LSP claim |
| Markdown | Raw/rendered modes, headings/lists/code | Fence parsing occurs before lazy composition, including unfinished fences | Basic Markdown subset |
| External reader | Optional callback, absent at most call sites | Default Open with action using read-only content URI grants; bounded on-demand remote export | Remote export cap 64 MiB; successful cache copies expire after 24 hours and are pruned on a later export |

PDF, DOCX and media acquisition use IO and structured ownership. Cancellation must not strand a newly
prepared private copy. Existing user files are not deleted by media cleanup. No new dependency or
minimum SDK change is required. Existing API 27 compatibility remains part of validation.

Regression tests cover routing (including DOCX/XLSX/PPTX), Markdown fences, media acquisition/cap/
cleanup, external URI mapping/export cleanup, PDF ownership/page ordering and native PDF rendering.
Phone checks must exercise real image/video decode, folder miniatures, long-press toolbar, DOCX
reading and PDF paging. Real user-file mutations are outside this surface-test run.

API references checked: [PdfRenderer](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer),
[Media3 player lifecycle](https://developer.android.com/media/media3/exoplayer/hello-world),
[ZipInputStream](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/zip/ZipInputStream.html).
