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