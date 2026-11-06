# Document reader — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). This is a DOCX text preview, not a universal Office rendering engine.

Source inspected: `ecf1dc5834c125c9612be74128fc4ac34e65a046` (24 October 2026). The version-only increment changes inventory metadata, not the linked viewer engines.

## Implementation, features and limits

[DocxPreviewHelper.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/preview/DocxPreviewHelper.kt) reads bounded document XML on a worker without extracting entries/resolving external relationships/assets. It preserves text runs, headings/emphasis and list/table-cell order. [DocxPreviewContent.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/DocxPreviewContent.kt) provides selection, find/match navigation, text size, retry and a text-layout disclosure.

Defaults: 16 MiB compressed, 32 MiB expanded, 2 MiB document XML, 256 entries, 500,000 characters, 10,000 paragraphs, 100,000 tokens and depth64. These code limits are not fidelity/performance promises.

**Fidelity: structured/simplified text.** Original pagination, embedded drawings, editing and legacy DOC are absent. Full XLSX/PPTX/ODF/EPUB rendering is not provided by this component. Plain text/Markdown use separate components. MIME/container recognition does not establish faithful format support.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| DOC-T01 | P0 / open acceptance | Malformed ZIP/XML, bounds, complex tables/Unicode, source loss/cancellation; retain layout disclosure/external fallback |
| DOC-T02 | P0 / deferred audit/research | Exact TXT/CSV/RTF/DOCX fidelity matrix and candidate Office/ODF/EPUB engine evaluation before dependencies |
| DOC-T03 | P1 / deferred | Navigation/bookmarks/properties/share/printing supported by the rendering model |
| DOC-T04 | P1 / deferred | Spreadsheet/slide/eBook-specific workflows after engine, size, licensing and privacy evaluation |

## Bugs and risks

The historical DOCX ZIP-bytes-as-text routing was repaired in the [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md); do not restart it. No new document defect is reproduced here; record future confirmed bugs as `DOC-Bnn` with evidence. Layout omissions are disclosed limits. Malformed containers/XML/assets/external references remain security gates. This record does not certify arbitrary input safety or authorize document uploads.

## Validation and continuation

Historical `4792db8` [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) records host/selected API34 DOCX routing/find evidence. `DocxPreviewHelperTest`, `DocxPreviewContentTest` and `PreviewTypeTest` cover parsing/search/routing; native `PreviewSurfaceDeviceTest` includes DOCX routing/find. Require current-head results after changes. U10 and the deferred [eight-plugin intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) remain open.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained record of current read-only DOCX text fidelity, bounds, tests and future formats; no new Office engine is implied.
## Source-session candidate — 26 October2026

DOC-B01 / DOC-T01: source inspection found that the reader retained its result/retry operation only by identity. Same-ID modified time/size and replacement read callable did not invalidate that session. This is source-backed pending reproduction; unsupported Office fidelity remains a separate disclosed limit.

The candidate keys the whole source session by identity, known modified time/size and read callable. The actual caller passes the bounded DocxPreviewHelper::read member reference; same helper rerenders retain its session. New known source/helper invalidates old parsed text/failure/search/font/navigation before rendering while existing coroutine ownership cancels old acquisition. Parser/ZIP/XML bounds, external-asset isolation, read-only behavior and explicit Retry remain.

Seven new actual-helper/parser/Compose consumers specify modified/size/helper replacement, clearing old text while a fresh revision is blocked, explicit failure→Retry recovery, blocked Close and source-switch cancellation. ZIP bytes are generated in memory; tests perform no shared-storage mutation/network/external relationship loading. Distinct opened streams are counted once because both ZIP and outer helper own close paths. Baseline/source commits are separate and uncompiled/unrun at author handoff; record exact execution before marking DOC-B01 reproduced/resolved. Parent host/static/APK/Lint/native/review/CI/dependency/clean integration gates remain. No new format, Office engine, fidelity, complete DOC group or release claim. Version remainsv0.2.
