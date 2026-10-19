# DOCX text preview

This focused implementation adds a read-only body-text reader for `.docx` files. The caller supplies a remembered `DocxPreviewHelper(backendSelector, io)` and passes `helper::read` to `DocxPreviewContent(node, readDocument)`. Existing preview routing and AppContainer wiring are intentionally owned by the integrating change.

It shows paragraphs, heading styles, bold and italic runs, generic list markers and table-cell paragraphs in document order. Text is selectable. Find is case-insensitive across run boundaries, shows a bounded match count, highlights matches and moves to the next or previous matching paragraph. Text-size controls retain Android's font scaling. Failed reads offer retry; the enclosing preview retains its external-open action.

This is not an Office layout renderer or editor. Images, drawings, headers, footers, pagination, exact list numbering, table grids and complex styles require an external Office app. Legacy `.doc`, macro-enabled `.docm`, spreadsheets and presentations are not claimed as supported by this viewer.

The injected IO dispatcher owns backend selection, `openInput`, stream creation, ZIP/XML reads and closing. Cancellation is checked throughout byte reads, ZIP iteration and XML tokens, and is rethrown rather than displayed as a failure. Backend errors retain their original model. No ZIP entries are extracted, temporary files created, relationship targets fetched or embedded code executed. DTD declarations and non-predefined/non-numeric entity references are rejected. Only the transitional and strict Word namespaces and a document/body root are accepted.

Default limits: 16 MiB compressed bytes consumed, 32 MiB total expanded entry bytes, 2 MiB document XML, 256 entries, 500,000 text characters, 10,000 paragraphs, 100,000 XML tokens and XML depth 64. Every unknown ZIP entry is drained through the same aggregate byte cap, including entries after `word/document.xml`. Search retains at most 1,000 matches and 200 query characters. All document limits fail closed rather than showing a silently truncated document.

Added tests cover real generated ZIP/XML fixtures, emphasis, lists and tables, strict namespace, escaped/numeric text, malformed XML, wrong namespace/missing document, DTD/XXE rejection, compressed/expanded/XML/text/token/depth/paragraph/entry bounds, cancellation and closure, backend failure, injected worker dispatch, search across runs and UTF-16 offsets, bounded matching, UI navigation and retry. Data tests target API 27 and 34; UI tests target API 34.

Validation status: source review and standalone formatting only until the integrating agent runs the serialized Gradle pipeline. No host or phone pass is claimed here.
