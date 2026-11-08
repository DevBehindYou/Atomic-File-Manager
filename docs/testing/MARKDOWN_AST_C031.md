# Markdown AST path — C031 slice 1 (CommonMark block structure)

Claude Code task **C031** (MD1; MD-T02 increment). Base: feature integration `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Recorded 7 November 2026. Host-verified only; no device run.

This slice replaces the line-by-line Markdown block scanner with commonmark-java block parsing behind a small adapter, and renders the result in native Compose. Inline parsing is deliberately switched off. It is **not** full CommonMark or GFM conformance and does not claim it: the supported-syntax matrix below is the whole claim.

## Audit of the previous native parser

`MarkdownBlocks.kt` only split fenced code from other lines. `MarkdownPreviewContent.kt` then styled each remaining line by its prefix (`# `, `## `, `### `, `- `, `* `, `> `, `---`, `***`). Measured against CommonMark 0.31.2 for the constructs it claimed (headings, simple lists, quotes, rules, code blocks), these were concrete defects:

| # | Input | Before | CommonMark |
| --- | --- | --- | --- |
| D1 | `***bold italic***` | Drawn as a horizontal rule. **The text was not shown at all.** | Paragraph (4.1: a break holds only marker characters and spaces) |
| D2 | `---not a rule` | Rule; text lost | Paragraph |
| D3 | `#### Level four ####`, `###### six` | Literal `#### Level four ####` | Level 4/6 headings, closing sequence removed (4.2) |
| D4 | `Title` + `=====` | Two plain lines | Setext heading (4.3) |
| D5 | `    ```` then `    code` | Opened a fence that swallowed the rest of the file | Indented code with literal backticks (4.4) |
| D6 | `- outer` + `  - inner` | Second line shown as `  - inner` | Nested list (5.2/5.3) |
| D7 | `>quoted` | Literal `>quoted` | Block quote; the space is optional (5.1) |

Also not handled: `+` bullets, ordered lists, `___`/`- - -`/`* * *` breaks, multi-line paragraphs and lazy continuation, list items with several blocks, fence indentation removal, closing fences indented four or more spaces, and backtick info strings containing backticks.

## Failing control

`MarkdownAstRenderingComposeTest` drives the real `MarkdownPreviewContent` with the real bounded `ReadFileContentUseCase`. It asserts only visible text, so it ran unchanged against the base source. It has seven defect cases (D1–D7) and three controls: level-1 heading plus fence, HTML shown as literal text, and Raw mode showing every original line.

On base `89140e3` (test file copied into a detached base worktree, nothing else changed):
`./gradlew :app:testDebugUnitTest --tests '*MarkdownAstRenderingComposeTest*'` → **10 run, 7 failed (D1–D7), 3 controls passed**. Six failures were `ComposeTimeoutException` while waiting for the CommonMark text. The setext case failed `assertDoesNotExist` on the literal `=====`.

The first base run used `onNodeWithText("Raw")` and the Raw control failed with "could not find node", because the chip exposes its label as a content description. The test was corrected to `onNodeWithContentDescription("Raw")`, as in `MarkdownOwnershipDeviceTest`, before the second base run above. In the first run the seven defect cases had already failed the same way, and the existing `MarkdownBlocksTest` (3) and `MarkdownPreparationRecoveryComposeTest` (7) passed on base.

## Decision: adopt commonmark-java core behind an adapter

D1–D7 come from scanning lines. CommonMark block structure has containers (quotes, list items), lazy continuation, setext headings and indentation rules. Fixing each defect natively would mean writing a container parser. That is the part of the spec where independent implementations are most often wrong. The maintained Java reference implementation is small and has no dependencies, so it is the more verifiable path.

| | commonmark-java core (chosen) | JetBrains markdown (not chosen) |
| --- | --- | --- |
| Artifact | `org.commonmark:commonmark:0.32.0` | `org.jetbrains:markdown-jvm:0.7.18` |
| Official source | https://github.com/commonmark/commonmark-java, Maven Central `repo1.maven.org/maven2/org/commonmark/commonmark/` | https://github.com/JetBrains/markdown, Maven Central `org/jetbrains/markdown-jvm/` |
| Licence | BSD-2-Clause (POM and `META-INF/LICENSE.txt`, © 2015 Atlassian Pty Ltd) | Apache 2.0 (POM) |
| Release / maintenance | 0.32.0 on 2026-11-01. Previous releases: 0.31.0 2026-10-22, 0.30.1 2026-10-11, 0.30.0 2026-08-06 (CHANGELOG). Maven metadata lastUpdated 2026-11-01. | 0.7.18, jar dated 2026-10-15 |
| Spec | README points to its bundled `spec.txt` and links spec.commonmark.org/0.31.2 | README: "the minimal default flavour is CommonMark"; no explicit conformance claim |
| Runtime dependencies | None. The POM lists only test scope. | `kotlin-stdlib` 2.0.0 |
| Jar size | 221,729 B (sha256 `a7075291a1ff852df6e185963a0016d24913abf689f4f913d017d9ff19a14ac2`) | 617,377 B |
| Android | README: "For Android the minimum API level is 24", best-effort. 0.32.0 CHANGELOG: "Fix Android incompatibility with minSdk < 30" (upstream issue #457: core used `List.of` from 0.24.0). | Not stated |
| Hostile-input history | 0.30.0 added `maxOpenBlockParsers` (default 100) and `maxInlineNesting`, and fixed quadratic and stack-overflow inputs. 0.31.0 fixed two more quadratic cases. | Not stated |
| Extensions needed later | Separate `commonmark-ext-gfm-tables` (24,631 B) and `commonmark-ext-task-list-items` (12,009 B) 0.32.0 artifacts | Built-in GFM flavour |

**Version choice.** 0.32.0 is six days old at this record. It is still the only acceptable version, because 0.24.0–0.31.0 are documented as needing API 30 and the app's minimum is API 28. Independent check: `javap -c` over all 182 classes in the 0.32.0 jar lists every JDK method it calls. All of them exist on API 24 or lower (the newest are `Map.putIfAbsent`, `HashMap.computeIfAbsent` and `List.forEach`, all API 24). There is no `List.of`, `Set.of`, `Map.of`, `String.repeat`/`isBlank`/`strip` or stream API. The only resource lookup is `entities.txt`, loaded by an absolute path, so R8 renaming cannot break it.

**Size.** APK_SIZE_RESULT

**Licence obligation.** BSD-2-Clause clause 2 requires the copyright notice and licence in binary distributions' documentation or other materials. The in-app *About & licences* sheet (`SettingsScreen.kt`, area-ui-shell) currently lists only fonts and Material Icons. This PR does not edit it, so adding the commonmark-java notice is a **release blocker** recorded under Remaining.

## Adapter and rendering boundary

- `markdownBlocks(lines)` joins the bounded preview lines and calls one shared, immutable `Parser`. The builder uses `IncludeSourceSpans.BLOCKS` and `maxOpenBlockParsers(32)`, and replaces `inlineParserFactory` with a literal pass. That pass only appends the raw inline source as one `Text` node.
- Because inline parsing never runs, the AST contains no `Link`, `Image`, `HtmlInline`, `Emphasis` or entity nodes. Inline markup, URLs and inline HTML are shown as typed. Nothing can be navigated, fetched or executed, and pathological inline input (nested brackets or emphasis) costs nothing.
- `MarkdownBlockCollector` turns the AST into a flat `List<MarkdownBlock>(kind, text, level, quoteDepth, listDepth, marker)`, so the existing `LazyColumn` composes it without recursion. Parsing still runs once per loaded `lines` in `remember(lines)`, before lazy composition, as in the accepted fence repair.
- `MarkdownPreviewContent.kt` draws headings at levels 1–6, paragraphs, code (fenced and indented, monospace, horizontal scroll), rules, quotes (tinted surface, depth indentation, italic paragraphs) and list items (`•`, or `N.`/`N)` from the source start number, with depth indentation).
- HTML blocks and link reference definitions are shown as monospace raw source. Reference definitions come from source spans. They are never hidden or interpreted. Any future block type the adapter does not know also falls back to raw source text.
- No WebView, no HTML renderer, no network or asset access, and no new permission. The PR #89 ownership, Retry, revision, truncation disclosure and Raw mode code paths are unchanged. `MarkdownPreparationRecoveryComposeTest` still passes.

## Supported-syntax matrix (rendered mode)

| CommonMark 0.31.2 section | State after this slice |
| --- | --- |
| 4.1 Thematic breaks | Supported |
| 4.2 ATX headings, 4.3 Setext headings | Supported, levels 1–6 |
| 4.4 Indented code, 4.5 Fenced code | Supported. Info string parsed but not displayed. No syntax highlighting. |
| 4.6 HTML blocks | **Disclosed as raw source text, never rendered** |
| 4.7 Link reference definitions | **Disclosed as raw source text** (CommonMark would hide them). A footnote definition such as `[^1]: note` is a valid reference definition in plain CommonMark, so it appears here as raw source too. |
| 4.8 Paragraphs, 4.9 Blank lines | Supported. Soft line breaks are kept as visible line breaks. |
| 5.1 Block quotes | Supported (nesting, lazy continuation) |
| 5.2 List items, 5.3 Lists | Supported (bullet/ordered, start number, nesting, multi-block items). Tight/loose spacing is not distinguished. |
| 6.x Inlines (code spans, emphasis, links, images, autolinks, raw HTML, hard breaks, entities, escapes) | **Not interpreted; shown literally** |
| GFM tables, task list items, strikethrough, autolinks | **Unsupported; literal text** (task markers are list item text such as `[ ] todo`) |
| Footnotes, front matter, math, Mermaid, alerts | **Unsupported; literal text** |
| Nesting beyond 32 open blocks | Not started. The extra markers stay as literal text, so nothing is lost. |

Raw mode is unchanged and always shows every original line.

## Validation (local, serialized Gradle lock)

VALIDATION_RESULT

## Remaining

- **Release blocker:** add the commonmark-java BSD-2-Clause notice to *About & licences* (area-ui-shell) before any release containing this dependency.
- Inline rendering (emphasis, code spans, link text) as a separate slice, with no clickable links until a safe link policy exists (MD-T02).
- GFM tables and task list items through `commonmark-ext-gfm-tables` and `commonmark-ext-task-list-items` 0.32.0, each with its own size delta, cell bound (`maxCells`) and corpus.
- The full CommonMark spec example suite is not run. The corpus here is section-targeted. Running `spec.txt` block examples through the adapter is a follow-up.
- Parsing still runs on the composition thread for the bounded input (see the measurement above). Moving it to a cancellable background coroutine is a follow-up.
- Hosted CI (static analysis, unit tests and APK packaging) on the PR head. No native or device run; the R8 release APK was built locally but not launched.
- MD-T03/T04, cross-revision restoration and full MD1–MD5 acceptance stay open. Plugin version remains v0.2.
