# PDF reader — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). It is not a PDF engine version or advanced-modernization acceptance.

Source inspected: `ecf1dc5834c125c9612be74128fc4ac34e65a046` (24 October 2026). The version-only increment changes inventory metadata, not the linked viewer engines.

## Implementation, features and limits

[PdfPreviewContent.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/PdfPreviewContent.kt) provides raster pages, previous/next, count/jump, retry, restored page and zoom. [PdfPageController.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/PdfPageController.kt) publishes the latest requested page. [PdfDocumentSession.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/preview/PdfDocumentSession.kt) and [PdfPreviewHelper.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/preview/PdfPreviewHelper.kt) own the spool/descriptor/renderer, serialize rendering and bound allocation. Defaults include a 128 MiB spool cap and 1600px render request; raster sizing caps an edge at 2048px and a default four million pixels.

Android PdfRenderer renders page images. Text search/selection, forms, annotation and password unlocking are not included. Zoom does not create searchable text or unlimited detail. Byte/raster bounds are implementation limits, not measured guarantees for every PDF.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| PDF-T01 | P0 / open acceptance | Large/malformed/scanned/mixed-size/password fixtures, rapid paging, provider loss and recreation with resource ownership/actionable failures |
| PDF-T02 | P0 / deferred engine research | Native text/search/selection/password/link options; maintenance, redistribution, API27 compatibility and size/memory costs |
| PDF-T03 | P1 / deferred | Supported continuous/thumbnail navigation, bookmarks/outline, position and display modes after engine audit |
| PDF-T04 | P1 / deferred | Distinguish annotation/export/printing from structural editing; require capability-specific tests |

## Bugs and risks

No new PDF defect is reproduced by this record; the confirmed-new-bug register is empty at this checkpoint. Unsupported search/password/annotation are feature limits. Host ownership/page-order coverage does not close large-document, provider-disconnect or memory/performance gates. Replace no working raster session without identifying a demonstrated engine gap; record any reproduced future defect with a `PDF-Bnn` ID and exact failing evidence.

## Validation and continuation

Historical `4792db8` [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md) and [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) include selected native PDF rendering/paging. `PdfDocumentLoaderTest`, `PdfPageControllerTest` and `PdfPageSizeTest` cover ownership/ordering/bounds; `PdfReaderDeviceTest` is native rendering coverage. Record new-head results after PDF changes. U10 and the separately deferred [eight-plugin intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) remain incomplete.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and source/evidence record for the bounded raster reader. No new PDF dependency/text engine/advanced editing claim.
## Source-session ownership candidate — 26 October 2026

PDF-B01 / PDF-T01: source inspection found that the actual reader keyed its whole session only by node identity; same-ID modified time/size and helper replacement could retain old document/failure/page state. New test-only baseline is preserved separately before the candidate source correction. No executed reproduction or green result is claimed at author handoff.

The reader now keys its session to identity, known modified time/size and actual helper identity, passing that helper explicitly into acquisition. A new known revision/helper resets page/zoom/jump/failure/retry state; ordinary rerenders retain the current document. Existing structured withDocument cancellation/renderer/descriptor/private-copy cleanup, page ordering, raster/spool caps and explicit Retry remain. Unknown unchanged metadata cannot prove a source change; this is not automatic filesystem observation.

Actual-helper host consumers cover modified/size revisions, helper replacement, repeated explicit failed-open Retry and cancellation of blocked acquisition with owned-copy cleanup. Two generated-private-PDF native Compose consumers cover successful revised page/pixels and helper replacement/old-copy cleanup; originals and an unrelated sentinel are checked. All are uncompiled/unrun at source handoff. Native tests request no permission and add no activity/manifest entry. Parent must execute unchanged-source baseline, corrected affected tests/static/APKs/Lint, native fixtures, CI/review/dependency/clean integration gates. Full PDF1–PDF5, advanced engine/features, physical providers/performance and main/release stay open. Version remains v0.2.
