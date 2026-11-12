# Document fidelity and fallback precision — C036 slice 1

Claude Code task **C036** (U10; DOC-T01/DOC-T02 increment). Base: feature integration `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Recorded 6 November 2026 UTC (7 November, Asia/Calcutta). Host-verified only.

## Fidelity matrix (current source, after this slice)

| Input | Route | What the user sees |
| --- | --- | --- |
| `.txt`, `text/plain` | Text | Plain text; editable through the existing editor |
| `.csv`, `text/csv` | Text | Raw comma-separated text. There is no table rendering and that is not claimed. |
| `.rtf` with any of `text/rtf`, `application/rtf`, `text/richtext`, `application/x-rtf` or an octet stream; also a `text/rtf` file with no extension | **Generic (changed)** | File details and Open with. Before, a provider that reported `text/rtf` sent RTF to the text preview, which showed raw control words such as `{\rtf1\ansi…`. |
| `.docx` ZIP package | DOCX | Structured read-only text (headings, emphasis, lists, table cells in order), as already accepted. |
| `.docx` that is an OLE container: legacy `.doc` renamed, or a password-protected/IRM Office file | DOCX, then failure | **Changed:** "This is an older Word file (.doc) or a password-protected document…" and no Retry. |
| `.docx` that is not a ZIP (e.g. text renamed), or an empty file | DOCX, then failure | **Changed:** "This file is not a Word .docx package…" and no Retry. |
| DOCX over a preview bound (16 MiB compressed, 32 MiB expanded, 2 MiB XML, 256 entries, 500,000 characters, 10,000 paragraphs, 100,000 tokens, depth 64) | DOCX, then failure | **Changed:** "This document is larger or more complex than the preview reads…" and no Retry. |
| Damaged package (missing part, invalid XML) or failed storage read | DOCX, then failure | Unchanged meaning: "could not be read… Retry or open it in another app". The text no longer lists encryption or limits, which now have their own messages. |
| `.doc`, `.docm`, `.dotx`, `.xls(x)`, `.ppt(x)`, `.od*` | Generic | Unchanged |

Bounds are unchanged; only how they are reported changes. Retry stays only where a new read can succeed.

## Validation (local, `host-gradle` lease held)

| Command | Result |
| --- | --- |
| `./gradlew ktlintFormat :app:testDebugUnitTest --tests '*DocxPreviewHelperTest' --tests '*DocxFailureTest' --tests '*PreviewTypeTest' --tests '*DocxSourceSessionComposeTest' --tests '*DocxPreviewContentTest'` | BUILD SUCCESSFUL in 4m 5s. 39 tests: DocxPreviewHelper 22 (11 cases × API 28/34), PreviewType 5, DocxFailure 2, DocxPreviewContent 3, DocxSourceSession 7. 0 failed, 0 skipped. |
| Control: `PreviewType.kt` reverted to base | 5 run, 1 failed (`rtf never becomes a raw text preview…`). |
| `./gradlew ktlintCheck detekt …PreviewTypeTest` | BUILD SUCCESSFUL in 1m 17s. |

On base, OLE, plain and empty inputs and every limit reached `FileError.IoFailure`. That gave one message suggesting damage, plus a Retry that could never succeed. The new helper tests assert the distinct outcomes, and the existing provider-failure Retry journey (`DocxSourceSessionComposeTest`) still passes unchanged.

## Still open

- Hosted CI and DOCX native workflow on the PR head.
- No RTF, CSV-table, legacy `.doc` or encrypted-Office reading. These belong to C040 engine evaluation. Generic keeps Open with.
- No device run. No DOC group credit is claimed.
