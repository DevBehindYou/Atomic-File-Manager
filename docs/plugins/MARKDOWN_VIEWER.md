# Markdown viewer — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). It is not a CommonMark/GFM conformance version.

Source inspected: `ecf1dc5834c125c9612be74128fc4ac34e65a046` (24 October 2026). The version-only increment changes inventory metadata, not the linked viewer engines.

## Implementation, features and limits

[MarkdownPreviewContent.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/MarkdownPreviewContent.kt) switches between raw and basic rendered text with headings, simple lists, styled blockquotes, horizontal rules and code blocks. [MarkdownBlocks.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/MarkdownBlocks.kt) pre-parses fenced blocks, including unfinished fences, before lazy composition so scrolling cannot change parsing state. Shared [ReadFileContentUseCase.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/domain/usecase/ReadFileContentUseCase.kt) defaults to a 5,000-line / 1 MiB UTF-8 preview. Its line count describes read preview content, not necessarily the original's full count.

This is a basic subset, not verified CommonMark/GFM. Full tables/tasks, relative image/link navigation, search/TOC, edit-preview synchronization, Mermaid/math and HTML/remote asset rendering are not established. Native text composition does not introduce a privileged WebView/script bridge or automatic asset engine.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| MD-T01 | P0 / source observation, reproduction open | Test fail-then-replace loading: remembered error is not explicitly cleared on a new source. Require unchanged-source consumer before a fix |
| MD-T02 | P0 / deferred audit/research | Exact CommonMark/GFM matrix, native renderer fit and safe local/remote link/image policy before resources |
| MD-T03 | P1 / deferred | Search, TOC/jumps, copy code/text, font and position restoration |
| MD-T04 | P1 / deferred | Supported Preview/Edit refresh/unsaved handling; optional sandboxed diagram/math only with security/size justification |

## Bugs and risks

Historical lazy fence-state behavior was fixed by pre-parsing and retains `MarkdownBlocksTest`. MD-R01 (tracked by MD-T01) is a source-backed risk, not a new accepted bug result; assign a `MD-Bnn` bug after a controlled reproducer. Unsupported extensions may display as plain text. Truncation, untrusted assets/HTML and provider errors need explicit checks; no full-file or universal asset-safety claim is made.

## Validation and continuation

Historical `4792db8` [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md) and [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) describe basic rendering/fences. `MarkdownBlocksTest`, `TextPreviewBoundsTest` and `PreviewTypeTest` cover parsing/reads/routing, not full GFM, link safety, performance or restoration. Expansion remains deferred under the [advanced intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md).

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained record. Preserve the current subset/repaired fences; MD-T01 needs reproduction and richer rendering remains future work.