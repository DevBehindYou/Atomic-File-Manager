# Claude Code — next 50 tasks

Generated from [tasks.json](tasks.json). Edit that file, then run `python scripts/coordination/validate.py --write`.

All tasks are assigned to Claude Code for execution and Codex for review. Assignment does not mean execution has started. Read the [Coordinator Dictionary](README.md) and [handoff](CLAUDE_START.md) first.

Default order is C001–C050. Choose the lowest eligible ID; log a blocked dependency once and continue independent work. Each task is a bounded slice of the original scope, not a replacement for its full acceptance criteria.

## A — Kotlin foundations

### C001 — Reconcile the live delta and claim the first slice

**P0 · accepted · Owner: Claude Code · Review: Codex**

Dependencies: None. Scope: U04, U06, N42.

Source areas: `docs/dictionary/coordinator`, `docs/ai-handover`.

**Objective.** Establish a current, non-duplicated execution baseline before editing application code.

**Implementation.** Read this dictionary, latest integration, per-plugin records and open PRs. Compare newer evidence against dated pending handovers. Claim only the first eligible task and its exact files. Record existing implementations instead of rebuilding them.

**Expected outcome.** A source-identified delta report, accurate ownership and a first implementation plan.

**Validation.** Record Git HEAD/app tree, clean or explained status, worktrees, active PR heads/check URLs and remaining gates. Codex reviews reconciliation. No whole-plugin credit for this audit.

Branch: codex/claude-c001-reconcile-delta. PR: #107 (into codex/claude-coordinator-50). Evidence records: 6. Updated: 2026-11-07.

### C002 — Close remaining preview IO and cancellation gaps

**P0 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001. Scope: U06, U09.

Source areas: `data/preview`, `domain/usecase`, `ui/components/preview`.

**Objective.** Prevent selected-file metadata/preparation work from blocking Main or publishing after cancellation.

**Implementation.** Trace actual call sites and injected dispatchers. Repair reproduced remaining violations only, preserve CancellationException and FileResult semantics. Do not redo accepted storage-dispatch work.

**Expected outcome.** Preview IO has explicit ownership, bounded work and tested cancellation.

**Validation.** Use dispatcher assertions and suspended fake streams for cancellation, denied reads and source replacement. Run affected opening/preview consumers and static checks; retain a failing reproduction for each defect.

Branch: codex/claude-c002-preview-io-cancellation. PR: #108 (into codex/feature-upgrade-base). Evidence records: 5. Updated: 2026-11-08.

### C003 — Clarify preview resource ownership without a framework migration

**P1 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001, C002. Scope: U06, N42.

Source areas: `AtomicApp.kt`, `ui/components/BuiltInFilePreviewPane.kt`, `data/preview`.

**Objective.** Make duplicated lifetime ownership testable where source analysis demonstrates coupling.

**Implementation.** Extract one narrow session/cleanup seam at a time, retaining manual AppContainer and current source/revision keys. Coordinate shared-file claims with Codex. Avoid new modules, DI frameworks or blanket composable rewrites.

**Expected outcome.** Fewer ambiguous owners with unchanged opening behavior.

**Validation.** Compare before/after ownership call sites. Test close, retry, replacement and recreation; ensure exactly-once release of descriptors/players. Existing host/native opening regressions remain green.

Branch: codex/claude-c003-preview-resource-ownership. PR: #115 (into codex/feature-upgrade-base). Evidence records: 5. Updated: 2026-11-07.

### C004 — Bound thumbnail scheduling and cache identity

**P1 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001, C002. Scope: U03, IMG6.

Source areas: `ui/components/FileThumbnail.kt`, `data/preview/FolderThumbnailReader.kt`, `data/preview/ProviderVideoThumbnail.kt`.

**Objective.** Keep visible thumbnails responsive while respecting existing bounds and account isolation.

**Implementation.** Inspect existing 64-entry/four-preview folder bounds and Coil behavior. Add only missing in-flight limits, revision-aware keys or cancellation. Reuse the existing video thumbnail path and prioritize visible requests.

**Expected outcome.** Documented bounds with no stale thumbnails after source/revision changes.

**Validation.** Use fake slow providers and rapid row reuse; assert maximum concurrency, cancellation, isolation and deterministic cache invalidation. Record request/allocation counts. Device latency claims wait for authorized measurements.

Branch: codex/claude-c004-thumbnail-bounds. PR: #116 (into codex/feature-upgrade-base). Evidence records: 4. Updated: 2026-11-07.

### C005 — Persist analysis freshness and interrupted scan state

**P1 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001, C002. Scope: U05.

Source areas: `data/search/RoomSearchIndex.kt`, `data/volume/StorageUsageRepository.kt`, `data/database/room`.

**Objective.** Make explicit analysis refresh and interrupted scans distinguishable from complete results.

**Implementation.** Inspect existing folder-consistency behavior, then introduce minimal generation/checkpoint and freshness metadata with migration. Preserve the lightweight Storage tab; scans remain user initiated in Analysis. Split schema and consumers into dependent PRs if needed.

**Expected outcome.** Recoverable scan state and honestly marked cached/partial/denied results.

**Validation.** Migration from shipped schema, cancelled/restarted scan, revoked root, delete/rename and stale completion tests. Verify Storage entry/recreation causes no recursive scan. Quantify query count and retained rows.

Branch: codex/claude-c005-analysis-freshness. PR: #118 slice 1, #120 slice 2 (stacked on #118). Evidence records: 5. Updated: 2026-11-08.

## B — Image

### C006 — Make fit controls accessible without obscuring content

**P0 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001. Scope: IMG1, IMG6.

Source areas: `ui/components/ImageGallery.kt`, `image UI tests`.

**Objective.** Finish the control layout and restoration gaps around the existing fit-mode candidate.

**Implementation.** Wait for Codex's disposition of #103, then build on its accepted implementation. Reserve content insets or intentional immersive controls using Atomic tokens. Retain Fit screen/Fit width/decoded-preview 1:1 terminology and gesture arbitration.

**Expected outcome.** Usable controls in narrow, landscape, RTL and large-font layouts.

**Validation.** Actual Compose gestures/semantics, edge-pixel visibility, rotation/session restore and font 2x tests; applicable API28/34 and physical touch evidence. Do not claim source-resolution pixels from a sampled preview.

**Gate.** PR #103 must be integrated or explicitly handed off by Codex.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C007 — Expand malformed and long-animation GIF resource coverage

**P0 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001, C002. Scope: IMG2, IMG6.

Source areas: `data/preview/GifPreviewDecoder.kt`, `GIF native fixtures`.

**Objective.** Preserve the repaired GIF playback while verifying hostile input and lifetime limits.

**Implementation.** Extend existing finite/infinite/lifecycle fixtures with truncated headers, extreme dimensions, frame budgets and rapid replacement. Keep current source/pixel/byte caps unless evidence supports a reviewed replacement.

**Expected outcome.** Actionable bounded failures and no retained decoder after close/replacement.

**Validation.** Native API28/34 fixtures, repeated close/reopen and original/sentinel hashes. Record peak allocations where available. Preserve historical JNI failures and do not substitute static first-frame rendering for animation.

Branch: codex/claude-c007-gif-hostile-inputs. PR: #112 (into codex/feature-upgrade-base). Evidence records: 3. Updated: 2026-11-07.

### C008 — Evaluate high-resolution and additional-image decoding

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C007. Scope: IMG5, IMG6.

Source areas: `data/preview/ImagePreviewHelper.kt`, `docs/plugins/modernization`.

**Objective.** Choose a bounded high-resolution strategy and precise format support matrix.

**Implementation.** Compare current sampling with region/tile support using official native/Android library sources. Evaluate animated WebP/APNG, SVG, TIFF, HEIF, ICO and RAW separately. Adopt only a justified, tested incremental path; otherwise document decision and retained limits.

**Expected outcome.** A dated decoder/format decision and measured resource envelope.

**Validation.** Use 4K/8K/panorama and malformed owned fixtures; report API, static/animated/multipage distinction, size/licence/ABI and allocation evidence. Unimplemented core support remains open even if evaluation finishes.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C009 — Complete capability-aware image information and navigation

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C006. Scope: IMG3.

Source areas: `data/preview/ImageMetadata.kt`, `ui/components/ImageGallery.kt`, `domain/model`.

**Objective.** Provide useful on-demand metadata and folder-order navigation with safe actions.

**Implementation.** Reuse backend identity/order/capabilities and existing share/Open with/operations. Load EXIF on demand with privacy-aware GPS disclosure. Add missing actions incrementally; retain existing working ones and disabled-state explanations.

**Expected outcome.** Correct metadata and navigation without unsolicited IO or unsafe actions.

**Validation.** Tests for EXIF rotation, missing tags, source revisions, read-only providers, folder changes and explicit metadata requests. Verify action routing, no extra grants and no GPS logging.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C010 — Add copy-first image crop and rotate exports

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C008, C009. Scope: IMG4.

Source areas: `data/preview`, `domain/usecase`, `image editing UI`.

**Objective.** Deliver the first non-destructive image editing slice.

**Implementation.** Implement crop and 90-degree rotate/flip with reset and Save as through verified publication. Preserve originals and define format/quality/EXIF behavior. Keep straighten/resize/color adjustments explicitly pending until implemented; split them into follow-on PRs under this task if feasible.

**Expected outcome.** Useful copy exports with explicit format and metadata behavior.

**Validation.** Byte hashes of originals and unrelated files; EXIF/pixel fixtures; denied/low-space/cancellation/revision-conflict cases across local and SAF. Test actual edit-export-reopen UI; full IMG4 stays open for remaining controls.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

## C — Video

### C011 — Verify actual player interruptions and noisy-output policy

**P0 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001. Scope: VID1.

Source areas: `ui/components/preview/PreviewPlayer.kt`, `Video native tests`.

**Objective.** Prove the existing focus policy on real player consumers and fix demonstrated defects.

**Implementation.** Reuse accepted Media3 audio attributes/focus configuration. Add deterministic lifecycle/focus/noisy tests without reimplementing the policy or probing root. Use owned media and preserve phone permissions.

**Expected outcome.** Playback obeys focus and source lifetime consistently.

**Validation.** Native pause/resume/close/source-switch tests with actual Media3. Calls/Bluetooth require available authorized hardware; record those separately. Keep player releases and sentinel integrity asserted.

Branch: codex/claude-c011-native-focus-interruptions. PR: #109 (into codex/feature-upgrade-base). Evidence records: 3. Updated: 2026-11-07.

### C012 — Persist revision-aware video resume and display state

**P0 · implementing · Owner: Claude Code · Review: Codex**

Dependencies: C001, C011. Scope: VID3.

Source areas: `ui/components/preview/VideoPreviewContent.kt`, `data/repository`.

**Objective.** Restore intentional video position and viewing mode across recreation.

**Implementation.** Key saved state by source/account/revision and clear incompatible stale state. Bound writes, preserve paused state and distinguish process recovery from merely recomposing. Use current persistence patterns.

**Expected outcome.** Reliable resume without jumping into a replacement file.

**Validation.** Position tolerance, paused restore, changed revision, failed seek, rotation, source loss and repeated navigation tests. Assert write counts and original integrity. Run native fixtures on lowest/current supported test APIs.

Branch: codex/claude-c012-video-resume. PR: #119 (into codex/feature-upgrade-base). Evidence records: 5. Updated: 2026-11-08.

### C013 — Complete accessible transport and fullscreen controls

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C012. Scope: VID3, VID5.

Source areas: `ui/components/preview/VideoPreviewContent.kt`, `core/designsystem`.

**Objective.** Fill verified gaps in transport, speed, aspect and control visibility.

**Implementation.** Inventory existing controls first. Add missing elapsed/buffer/skip/speed/lock/fullscreen controls with accessible alternatives to gestures. Preserve Android Back; preferences only expose supported behavior.

**Expected outcome.** Core playback controls work in portrait, landscape and with assistive input.

**Validation.** Real seek/end/buffering consumers, speed bounds, control-lock escape, TalkBack semantics, 2x font and rotation tests. No universal codec or background playback claim.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C014 — Add verified track and subtitle selection

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C013. Scope: VID4.

Source areas: `ui/components/preview/VideoPreviewContent.kt`, `data/preview`.

**Objective.** Expose actual supported embedded and external subtitles/audio tracks.

**Implementation.** Use pinned Media3 APIs and capability-driven selection. Start with SRT/VTT, stable track/language identity and restored selection. Evaluate ASS/SSA/style/offset separately; external files must use user-selected backend access.

**Expected outcome.** Working track/subtitle controls with honest unsupported states.

**Validation.** Owned multi-track fixtures, missing/malformed sidecars, source replacement, rotation and retained-selection tests on API28/34. Confirm no broad storage grant or network subtitle fetch.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C015 — Publish a fixture-backed video format matrix

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C011, C014. Scope: VID2, VID6.

Source areas: `Video native fixtures`, `docs/plugins/VIDEO_PLAYER.md`.

**Objective.** Separate container recognition from actual codec/device playback.

**Implementation.** Exercise MP4/MKV/WebM/MOV/M4V/3GP and evaluate remaining intake containers with redistributable small fixtures. Record engine/codec/device combinations and useful fallback errors; repair only reproduced unsupported/error-path defects.

**Expected outcome.** Accurate supported-format documentation and regression coverage.

**Validation.** Decode/play/seek/audio/end tests plus corrupt/no-audio/stall/source-disconnect cases. Record fixture licences/hashes and runtime metadata. Absence of an available codec is an explicit limit, never a fabricated pass.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

## D — Audio

### C016 — Expand real audio decoding and seek acceptance

**P0 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001. Scope: AUD1, AUD2.

Source areas: `Audio native fixtures`, `data/preview/MediaPreviewHelper.kt`.

**Objective.** Build a broader engine-backed format suite from the existing WAV test.

**Implementation.** Wait for Codex #105 disposition. Extend its owned-fixture/integrity pattern to MP3, AAC/M4A, FLAC, OGG/Vorbis, Opus, VBR and malformed inputs with rights-cleared fixtures. Reuse current retry behavior.

**Expected outcome.** Core audio formats have exact device/engine evidence.

**Validation.** Actual play/pause/seek/end and Retry/revision native cases on API28/34; source/sentinel unchanged and no surviving player. Record unsupported cases and no sound-output claim from silent WAV alone.

**Gate.** PR #105 must be integrated or explicitly handed off by Codex.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C017 — Implement a bounded folder audio queue

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C016. Scope: AUD3.

Source areas: `ui/components/preview/AudioPreviewContent.kt`, `audio queue model`.

**Objective.** Add stable next/previous, repeat and shuffle behavior.

**Implementation.** Model queue identity using backend IDs/revisions, not filenames. Bound retained queue state, handle deletion/reordering and preserve current opening/session ownership. Keep metadata loading lazy.

**Expected outcome.** Predictable queue transitions with intentional resume behavior.

**Validation.** Deterministic shuffle/repeat/end tests, unavailable next item, source replacement, queue restore and actual next/previous UI. Assert no duplicate players and bounded retained entries.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C018 — Expose bounded audio metadata and player presentation

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C017. Scope: AUD3.

Source areas: `data/preview/AudioTags.kt`, `ui/components/preview/AudioPreviewContent.kt`.

**Objective.** Show useful title/artist/album/artwork/duration/format without eager scans.

**Implementation.** Reuse existing artwork byte/dimension limits, lazy tag reads and Atomic components. Define coherent mini/full state without a second playback owner. Preserve missing-tag fallback.

**Expected outcome.** Readable, accessible metadata and one synchronized playback state.

**Validation.** Missing/corrupt/oversized tags, artwork bounds, changed source and recycled UI tests. Landscape/2x font and semantics checks; record no unsolicited folder-wide tag read.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C019 — Implement intentional background audio ownership

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C011, C017, C018. Scope: AUD4.

Source areas: `service`, `ui/components/preview/PreviewPlayer.kt`, `AndroidManifest.xml`.

**Objective.** Support explicitly enabled background playback with correct service/session lifetime.

**Implementation.** Write a small lifecycle ADR before implementing native Media3 session/service controls. Use only necessary declared permissions and in-app opt-in. Preserve foreground-only preview semantics until opted in. Coordinate shared player ownership with Codex.

**Expected outcome.** One playback owner with notification/lock-screen controls and reliable stop.

**Validation.** Native foreground/background/recreation/service dismissal/notification denial/focus tests. Headset/Bluetooth/call acceptance when hardware is available. No automatic permission grants, root or user-media mutation.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C020 — Evaluate and implement safe single-file tag editing

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C016, C018. Scope: AUD5.

Source areas: `data/preview/AudioTags.kt`, `domain/usecase`, `docs/plugins/AUDIO_PLAYER.md`.

**Objective.** Offer tag changes only for independently verified safe format/version combinations.

**Implementation.** Research maintained native/JVM writers and licences. Implement one justified format behind copy-first save and revision checks; preserve unknown tags/artwork or disclose unsupported writes. Batch editing follows later.

**Expected outcome.** An evidence-backed tag-write capability or explicit non-adoption decision.

**Validation.** Round-trip tags/audio payload checks, unknown fields, cancellation, read-only/low-space/conflict fixtures and original hashes. A research-only outcome cannot close requested tag-edit functionality.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

## E — PDF

### C021 — Exercise large and hostile native PDF sessions

**P0 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001, C002. Scope: PDF1, PDF5.

Source areas: `data/preview/PdfDocumentSession.kt`, `data/preview/PdfPreviewHelper.kt`.

**Objective.** Establish bounded rendering and closure behavior beyond accepted source-session tests.

**Implementation.** Extend the accepted #91 fixture pattern to generated 1000-page, mixed-size, scanned and malformed documents. Preserve PDF staging caps and generation ownership; repair observed allocation/leak failures.

**Expected outcome.** Bounded progressive rendering with clear failures.

**Validation.** Native API28/34 page navigation, source loss/replacement and repeated disposal tests; record peak retained pages/bitmap allocations/FD closure. Distinguish password unsupported from generic corruption.

Branch: codex/claude-c021-pdf-hostile-sessions. PR: #117 (into codex/feature-upgrade-base). Evidence records: 5. Updated: 2026-11-07.

### C022 — Finish PDF reading modes and position restoration

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C021. Scope: PDF2.

Source areas: `ui/components/preview/PdfPreviewContent.kt`, `ui/components/preview/PdfPageController.kt`.

**Objective.** Make page navigation, fit/zoom and restored position usable.

**Implementation.** Retain the bounded renderer. Add missing single/continuous orientation modes incrementally, with page jump/count and revision-aware position. Use Atomic theme/night presentation and accessible controls.

**Expected outcome.** Reliable page/viewport state across layout and source changes.

**Validation.** Actual page-jump/zoom/scroll/rotation consumers, stale revision rejection, mixed-page sizes, font 2x and TalkBack semantics. Measure visible-page cache bounds; do not preload entire documents.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C023 — Refresh the PDF engine feasibility decision

**P1 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001, C021. Scope: PDF3, PDF4.

Source areas: `docs/plugins/modernization/PDF_ENGINE_EVALUATION.md`, `gradle/libs.versions.toml`.

**Objective.** Choose the smallest supported route for search, selection, links and passwords.

**Implementation.** Update the existing evaluation using official release/API/security/licence sources and exact artifacts. Compare API28 behavior, ABI/R8/size/memory against retained PdfRenderer fallback. No speculative bulk dependency addition.

**Expected outcome.** A dated ADR with supported capabilities, costs and adoption or deferral rationale.

**Validation.** Minimal isolated prototype and resolved dependency/licence inventory, API28/34 functional fixtures and APK delta. Record unanswered security/redistribution concerns; no advanced-feature claims from dependency presence.

Branch: codex/claude-c023-pdf-engine-decision. PR: #124 (into codex/feature-upgrade-base). Evidence records: 3. Updated: 2026-11-08.

### C024 — Add supported PDF search and safe link/password flows

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C022, C023. Scope: PDF3.

Source areas: `PDF engine adapter`, `ui/components/preview/PdfPreviewContent.kt`.

**Objective.** Implement the first verified advanced reading features behind an engine boundary.

**Implementation.** Follow the selected engine decision; preserve raster fallback where advanced APIs are unavailable. Handle password failure without persistence/logging, bounded search, selection/copy and allowed external/internal links.

**Expected outcome.** Real search/navigation and safe unsupported/password recovery.

**Validation.** Text/scanned/encrypted/wrong-password/malicious-link fixtures, next/previous results and source changes on API28/34. If no acceptable engine exists, mark blocked with evidence and continue independent tasks.

**Gate.** Accepted engine decision from C023; licence/API blockers cannot be bypassed.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C025 — Add source-aware bookmarks and bounded page export

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C022. Scope: PDF4, PDF5.

Source areas: `PDF persistence`, `domain/usecase`, `ui/components/preview/PdfPreviewContent.kt`.

**Objective.** Provide bookmarks and useful copy/share/print actions without altering source PDFs.

**Implementation.** Persist bookmarks against source/revision and document page-range behavior. Export a selected page/range through bounded staging and existing share/print paths. Keep annotations/forms/OCR separate until evaluated.

**Expected outcome.** Bookmarks survive recreation and exports preserve original documents.

**Validation.** Migration/restore, removed pages, changed revisions, denied destinations, large page bounds, cancellation and export-reopen tests. Verify source/sentinel bytes and explicit limits.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

## F — Code/Text

### C026 — Persist crash-recoverable editor drafts

**P0 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001, C003. Scope: EDT1.

Source areas: `ui/components/TextEditorSession.kt`, `domain/usecase/TextEditing.kt`, `data/repository`.

**Objective.** Recover unsaved edits after process loss without overwriting source files.

**Implementation.** Retain accepted bounded undo/redo. Persist bounded drafts keyed by opaque ID/revision with atomic local metadata publication, debounce and explicit recover/discard controls. Protect sensitive content and respect vault policy.

**Expected outcome.** Durable, revision-aware draft recovery.

**Validation.** Fault injection between each draft write phase, restart/corrupt-draft/source-change tests, bounded write counts and original hashes. Physical process-kill tests require appropriate owned-fixture authorization.

Branch: codex/claude-c026-editor-drafts. PR: #121 (into codex/feature-upgrade-base). Evidence records: 3. Updated: 2026-11-08.

### C027 — Strengthen revision-aware editor publication

**P0 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C026. Scope: EDT1, EDT6, U13.

Source areas: `domain/usecase/TextEditing.kt`, `data/backend`, `editor UI`.

**Objective.** Prevent a stale edit from silently replacing a newer external revision.

**Implementation.** Use backend-specific capability/precondition checks with staged Save/Save as. Preserve the only good copy when publication status is ambiguous. Reuse journal infrastructure where needed rather than assume SAF rename is atomic.

**Expected outcome.** Actionable conflicts and safe retry/recovery across supported providers.

**Validation.** Fault every publish boundary; denied writes, low space, concurrent change, disconnect/cancellation and process restart. Compare original/output/sentinel bytes; test actual conflict and Save as UI.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-08.

### C028 — Add a bounded large-text read-only path

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C026. Scope: EDT3.

Source areas: `domain/usecase/ReadFileContentUseCase.kt`, `ui/components/TextEditorDialog.kt`.

**Objective.** Make oversize text useful without removing safe editing limits.

**Implementation.** Keep the existing edit cap; introduce explicit paged/read-only viewing and bounded search for large inputs. Preserve encoding/line boundaries and disclose any windowed content. Handle long lines and cancellation.

**Expected outcome.** Large files can be inspected without silent truncation or unbounded buffers.

**Validation.** Generated large/long-line/Unicode/invalid-encoding fixtures, peak retained bytes, search across chunk boundaries and early close. No device throughput claim without authorized comparable runs.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C029 — Complete bounded search/replace and keyboard workflows

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C026, C028. Scope: EDT2, EDT6.

Source areas: `ui/components/TextEditorHistory.kt`, `ui/components/TextEditorDialog.kt`.

**Objective.** Finish practical editing controls while preserving accepted history semantics.

**Implementation.** Inventory existing find/replace/undo first. Fill missing case/whole-word/regex/find-all/go-to-line behavior with explicit regex limits and undo grouping. Keep selection/IME and keyboard shortcuts usable.

**Expected outcome.** Predictable edits with bounded history and reversible replacement.

**Validation.** Zero-width/pathological regex, Unicode, long lines, replace-all undo, IME selection and actual keyboard/Compose consumers. Test cancellation/time budgets and exact saved content.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C030 — Preserve text encoding and evaluate incremental syntax

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C027, C029. Scope: EDT4, EDT5.

Source areas: `domain/usecase/TextEditing.kt`, `editor rendering`, `docs/plugins/CODE_TEXT_EDITOR.md`.

**Objective.** Improve editing fidelity and highlighting without whole-buffer recomputation.

**Implementation.** Verify encoding/BOM/line-ending round trips first. Evaluate a maintained grammar engine against the intake language corpus; adopt only a bounded incremental rendering slice. JSON/XML formatting must fail safely and never execute content.

**Expected outcome.** Explicit encoding controls and an evidence-backed syntax strategy.

**Validation.** Byte-exact BOM/mixed-newline/invalid-encoding fixtures, syntax/formatting corpus, undo/selection correctness and allocation/latency comparison. Unsupported languages and optional multi-cursor/folding work stay explicit.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

## G — Markdown

### C031 — Adopt a bounded maintained Markdown AST path

**P1 · claimed · Owner: Claude Code · Review: Codex**

Dependencies: C001, C002. Scope: MD1.

Source areas: `ui/components/preview/MarkdownBlocks.kt`, `Markdown parser adapter`.

**Objective.** Expand the existing documented subset through a verifiable parser/rendering boundary.

**Implementation.** Reuse accepted #89 ownership/retry. Evaluate exact maintained CommonMark/GFM artifacts with official sources/licence/size checks, then implement headings/lists/tables/fences/tasks incrementally in native Compose.

**Expected outcome.** A precise supported-syntax matrix with no silent claim of full conformance.

**Validation.** Conformance corpus and bounded nested/large input cases, malformed syntax, raw disclosure and cancellation. Keep unsupported footnotes/extensions explicit and preserve native rendering security.

Branch: codex/claude-c031-markdown-ast. PR: none. Evidence records: 1. Updated: 2026-11-08.

### C032 — Resolve relative assets and links safely

**P0 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C031. Scope: MD2.

Source areas: `Markdown asset resolver`, `data/backend`, `ui/components/preview`.

**Objective.** Support useful local relative images/links without content escape or unsolicited network access.

**Implementation.** Resolve against provider-qualified parent identity and existing granted capabilities. Bound bytes/pixels/depth, reject traversal/unsupported schemes and disable unsafe HTML/scripts. Require explicit policy before remote asset requests.

**Expected outcome.** Local assets work within granted scope with safe fallback.

**Validation.** Opaque SAF IDs, traversal/encoded traversal/symlink, remote URL, HTML/script, revoked grants and oversized images; assert no network requests or secret/path logs by default.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C033 — Add Markdown navigation and reading-state controls

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C031. Scope: MD3.

Source areas: `ui/components/preview/MarkdownPreviewContent.kt`.

**Objective.** Provide TOC, search, copy and meaningful restored reading position.

**Implementation.** Use stable AST heading identity and source/revision ownership, font/theme settings and bounded search. Preserve raw view and Retry without duplicate state owners.

**Expected outcome.** Accessible navigation and reading state across recreation.

**Validation.** Heading collisions, revision replacement, large-font/landscape, copy-code/text and search-next/previous consumers. Verify location restoration and no hidden parsing loops.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C034 — Connect edit/preview to the current durable draft

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C026, C031, C032, C033. Scope: MD4.

Source areas: `MarkdownPreviewContent.kt`, `TextEditorSession.kt`.

**Objective.** Show the actual unsaved editor state when switching to preview.

**Implementation.** Reuse durable drafts rather than re-reading stale disk content. Debounce/cancel parse jobs and preserve dirty-state prompts. Add refresh/share only with explicit saved-versus-draft semantics; wide split view is an incremental follow-up.

**Expected outcome.** Preview stays aligned with current edits and source revisions.

**Validation.** Edit-preview-refresh-save-reopen, source conflict, rapid switching, parse cancellation and unsaved-close tests. Original bytes unchanged until explicit safe save.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C035 — Validate Markdown integration and optional extensions

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C032, C033, C034. Scope: MD5, MD4.

Source areas: `Markdown native fixtures`, `docs/plugins/MARKDOWN_VIEWER.md`.

**Objective.** Prove the relative-image/edit-preview journey and decide optional extension boundaries.

**Implementation.** Run actual provider/native integration. Evaluate Mermaid/math/export only with maintained, sandboxed implementations and recorded costs; do not introduce privileged WebView bridges.

**Expected outcome.** End-to-end evidence and explicit extension adoption/defer decisions.

**Validation.** Native relative assets/edit-refresh/recreation, corruption, large document and repeated open/close checks; report retained nodes/asset bytes. Keep unresolved optional/core requirements in the matrix.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

## H — Documents

### C036 — Make TXT/CSV/RTF/DOCX fidelity and fallback precise

**P0 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001. Scope: DOC1.

Source areas: `document routing`, `docs/plugins/DOCUMENT_READER.md`, `document fixtures`.

**Objective.** Ensure supported formats and visible fidelity labels match real behavior.

**Implementation.** Reuse accepted #93 routing/Find. Build exact fixtures for quoted CSV, Unicode, basic/unsupported RTF and DOCX. Implement missing bounded basic support only after a safe parser decision; retain external fallback.

**Expected outcome.** A verified format/fidelity table and useful user-facing limits.

**Validation.** Real routing/selection/search fixtures and malformed/unsupported handling, no private uploads. A text extract must never be labeled faithful Office pagination.

Branch: codex/claude-c036-document-fidelity-precision. PR: #111 (into codex/feature-upgrade-base). Evidence records: 4. Updated: 2026-11-07.

### C037 — Render structured DOCX paragraphs and tables

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C036. Scope: DOC1, DOC4.

Source areas: `data/preview/DocxPreviewHelper.kt`, `ui/components/preview/DocxPreviewContent.kt`.

**Objective.** Preserve useful document structure in the existing bounded native preview.

**Implementation.** Extend parsed blocks with paragraphs/headings/lists/tables and explicit unsupported-layout disclosure. Keep ZIP/XML expansion limits and cancellable parsing; avoid an unreviewed full Office engine.

**Expected outcome.** Readable tables and sections without false layout-fidelity claims.

**Validation.** Nested/merged cells, Unicode/RTL, oversized tables, malformed XML and source replacement fixtures. Bounded node counts, search/selection behavior and actual Compose/native consumers.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C038 — Handle embedded DOCX images with strict budgets

**P0 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C037. Scope: DOC1, DOC4.

Source areas: `DocxPreviewHelper.kt`, `document asset decoder`.

**Objective.** Show supported embedded assets while defending against hostile ZIP/XML relationships.

**Implementation.** Resolve only bounded internal relationships, reject path escapes/external references, sample image dimensions and release caches by source owner. Maintain source/sentinel integrity.

**Expected outcome.** Useful images within explicit extraction/pixel/resource limits.

**Validation.** Zip bombs, duplicate entries, missing relationships, external links, huge dimensions and malformed images. Assert no network/file escape and bounded decoded bytes on API28/34.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C039 — Finish document reading position and share/print controls

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C037, C038. Scope: DOC1, DOC3, DOC4.

Source areas: `DocxPreviewContent.kt`, `document persistence`, `data/share`.

**Objective.** Make structured document reading usable across layout changes.

**Implementation.** Add supported font/reflow/zoom, section navigation and source-aware position/bookmarks. Reuse safe share/print boundaries with accurate output disclosure; expose only implemented actions.

**Expected outcome.** Accessible reading and intentional export/handoff.

**Validation.** 2x font/landscape/RTL, source replacement and restored-position consumers; search/selection and denied external handler tests. No source mutation or silent temporary grant escalation.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C040 — Evaluate additional Office and ebook engines

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C036. Scope: DOC2, DOC3.

Source areas: `docs/plugins/modernization`, `isolated document engine spike`.

**Objective.** Choose evidence-backed support for XLSX/PPTX/ODF/EPUB and legacy formats.

**Implementation.** Research official native/JVM engines and licences, privacy, API28/ABI/R8, size and maintenance. Prototype one highest-value supported format in isolation; record formula/macro/external-link policy and avoid execution/uploads.

**Expected outcome.** A ranked capability/cost decision with concrete implementation follow-ons.

**Validation.** Redistributable workbook/slide/ebook fixtures, malformed archive/XML and bounded memory tests. Distinguish evaluated from shipped. Preserve unimplemented formats as remaining requirements.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

## I — Archives

### C041 — Verify actual archive formats and encrypted variants

**P0 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001. Scope: ARC1, ARC4.

Source areas: `domain/usecase/ArchiveFormats.kt`, `archive fixtures`.

**Objective.** Align format dispatch and capabilities with actual read/extract behavior.

**Implementation.** Reuse accepted #92 inspection uniqueness and existing libraries. Test ZIP/ZIP64/TAR/TAR.GZ/GZIP/BZIP2 first; evaluate exact 7z/RAR/RAR5/XZ/Zstd/encrypted/multipart variants separately with official engine/licence evidence.

**Expected outcome.** Fixture-backed read/browse/extract/create capability table.

**Validation.** Valid, truncated, wrong-password, missing-volume and unsupported-method fixtures. Compare extracted bytes, preserve originals, record licences/hashes and actionable unsupported errors.

Branch: codex/claude-c041-rar5-honest-errors. PR: #110 (into codex/feature-upgrade-base). Evidence records: 4. Updated: 2026-11-07.

### C042 — Extend archive security and work-budget defenses

**P0 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C041. Scope: ARC5.

Source areas: `domain/usecase/InspectArchiveUseCase.kt`, `domain/usecase/ArchiveOperationsHelper.kt`.

**Objective.** Close reproducible traversal, expansion and metadata budget gaps.

**Implementation.** Retain existing path uniqueness and staging. Add adversarial absolute/traversal/symlink/case-collision/header/depth/count/ratio fixtures, including provider identity boundaries; enforce limits while streaming.

**Expected outcome.** Rejected malicious inputs leave unrelated files intact and release resources.

**Validation.** Fault-injection and property/adversarial tests, sentinel hashes, bounded counters and cancellation. Test malformed metadata before mutation. Do not count file-extension rejection as security coverage.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C043 — Complete safe archive entry actions

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C041, C042. Scope: ARC2.

Source areas: `archive browser UI`, `archive use cases`.

**Objective.** Provide hierarchy/search/sort, preview and selected extraction to chosen storage.

**Implementation.** Use stable entry identity and explicit destination capabilities. Bound entry preview and route existing viewers safely; display unsupported actions instead of fake copy/extract success.

**Expected outcome.** Usable entry-level actions across supported destinations.

**Validation.** Actual UI selection/search, duplicate names, opaque SAF destinations, cancelled picker, denied/read-only targets and corrupt entries. Verify exact selected output set and unaffected siblings.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C044 — Reconcile interrupted extraction and publication

**P0 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C042, C043. Scope: ARC5, U13.

Source areas: `data/operations/OperationRecovery.kt`, `ArchiveOperationsHelper.kt`, `operation journal`.

**Objective.** Preserve recoverability across cancellation and ambiguous destination publication.

**Implementation.** Record sufficient operation phases and owned temporary identities before mutation. On restart reconcile only known outputs, preserve the only good copy and require explicit conflict handling for ambiguity.

**Expected outcome.** Retryable extraction with safe cleanup and honest Tasks status.

**Validation.** Fault every journal/stage/publish boundary; restart, low space, provider disconnect, external replacement and cancellation. Owned native process-kill fixtures only within authorization; no user-file cleanup.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C045 — Complete ZIP creation progress and cancellation reliability

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C041, C042, C044. Scope: ARC3.

Source areas: `archive creation use cases`, `data/operations/OperationQueue.kt`.

**Objective.** Make supported ZIP creation predictable through progress, cancellation and failures.

**Implementation.** Reuse verified staging/queue and terminal progress rules. Add missing compression/destination controls, bounded buffers and intentional background ownership. Evaluate new write formats/encryption separately; no universal pause/resume claim.

**Expected outcome.** Correct ZIP outputs and clear terminal/recovery behavior.

**Validation.** Byte-for-byte extracted contents, large-count/small-buffer tests, no-clobber collision, cancelled/low-space/disconnected writes, throttled progress and no orphaned owned temp artifacts.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

## J — Cloud and delivery

### C046 — Implement isolated Cloud Connect account/page contracts

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C003. Scope: U07, Cloud Connect.

Source areas: `domain/model`, `cloud contract tests`, `docs/plugins/cloud-connect`.

**Objective.** Prepare provider-qualified account identity and bounded pagination without fake connectivity.

**Implementation.** Follow the existing ninth-plugin plan. Add narrow revision/capability/page/completion contracts and mock tests; retain current backends. Refresh official auth/scope research only where needed. Do not expose Connect until a real connector is usable.

**Expected outcome.** Reusable tested contracts with explicit registration/provider decisions left visible.

**Validation.** Account separation, opaque IDs, page duplicates/continuations, cancellation, 429/retry, revision conflict and credential-generation mocks. No real auth/transfer completion without scoped registration/test-account evidence.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C047 — Audit resolved dependencies and harden build provenance

**P1 · host_verified · Owner: Claude Code · Review: Codex**

Dependencies: C001. Scope: M12, U13.

Source areas: `gradle/libs.versions.toml`, `build-logic`, `.github/workflows`, `scripts/release`.

**Objective.** Resolve dependency/security/build uncertainty while preserving reproducibility.

**Implementation.** Inventory exact resolved runtime/transitive artifacts, SBOM/notices and official advisories. Review Actions pinning and wrapper integrity. Upgrade one justified compatible cluster per PR, retain JDK/API/Compose constraints and avoid blanket newest-version churn.

**Expected outcome.** Traceable dependency decisions and reproducible artifact verification.

**Validation.** Official-source date/URLs, resolved graph and licence records; static/unit/native/R8 checks affected by each change, APK size delta and existing release tamper guards. No secrets or private signing assets in outputs.

Branch: codex/claude-c047-dependency-provenance. PR: #114 slice 1, #122 slice 2 (Guava D1). Evidence records: 6. Updated: 2026-11-07.

### C048 — Assemble stable plugin/provider/accessibility acceptance

**P0 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C002, C003, C004, C005, C006, C007, C008, C009, C010, C011, C012, C013, C014, C015, C016, C017, C018, C019, C020, C021, C022, C023, C024, C025, C026, C027, C028, C029, C030, C031, C032, C033, C034, C035, C036, C037, C038, C039, C040, C041, C042, C043, C044, C045, C047. Scope: U04, U06, U13, IMG6, VID6, AUD5, PDF5, EDT6, MD5, DOC4, ARC5.

Source areas: `docs/testing`, `plugin native suites`, `release candidate`.

**Objective.** Run only implemented features through the full stable acceptance matrix and expose gaps.

**Implementation.** Consume completed slices from C002–C047 and Codex accepted work. Test eight journeys plus local/SAF/SD/USB/network, access revocation, R8, low space, recreation and accessibility where advertised. Fix failures in focused PRs. Final stable certification requires all applicable prior feature gates.

**Expected outcome.** An exact-candidate pass/fail/blocked matrix suitable for a release decision.

**Validation.** Record head/app tree, device/API, fixture/artifact/signer hashes, counts, logs and failures. Skipped/missing hardware cases stay pending. No stable promotion until every required gate passes.

**Gate.** Hardware/provider accounts or disruptive owned-fixture tests may require specific existing or new authorization.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C049 — Collect comparable performance evidence when permitted

**P1 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C004, C005, C047. Scope: U02, U03, U13, M13.

Source areas: `benchmark`, `docs/testing/PERFORMANCE_BASELINE.md`, `plugin performance report`.

**Objective.** Measure changes without claiming speedups from functional tests.

**Implementation.** Prepare reproducible metadata and baseline/candidate analysis now. The owner-deferred benchmark remains deferred: do not invoke macrobenchmark/root probes or increase corpus budgets. Resume device measurements only when the owner explicitly reauthorizes the needed scope.

**Expected outcome.** Comparable startup/list/search/frames/memory/preview/archive and size evidence, or explicit deferred results.

**Validation.** Same device/build mode/corpus/cache/thermal conditions, enough samples and p50/p95 with counts; keep raw traces and failures. Baseline profiles and optimization claims require actual attributable measurements.

**Gate.** Device benchmarking explicitly deferred; permissions unchanged. This task cannot be marked accepted while required measurements are absent.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.

### C050 — Deliver the complete coordination report and next backlog

**P0 · assigned · Owner: Claude Code · Review: Codex**

Dependencies: C001, C002, C003, C004, C005, C006, C007, C008, C009, C010, C011, C012, C013, C014, C015, C016, C017, C018, C019, C020, C021, C022, C023, C024, C025, C026, C027, C028, C029, C030, C031, C032, C033, C034, C035, C036, C037, C038, C039, C040, C041, C042, C043, C044, C045, C046, C047, C048, C049. Scope: U01-U13, N42, Cloud Connect, stable v0.2.1.

Source areas: `docs/dictionary/coordinator`, `docs/ai-handover`, `docs/plugins`, `docs/releases`.

**Objective.** Give Codex a complete, evidence-based account of this 50-task batch.

**Implementation.** Maintain interim reporting after each PR and write the final report only after all prior tasks are accepted; otherwise label it interim. Reconcile original M/U/N42 scopes and document residual requirements, rejected alternatives, open PRs and release blockers. Append subsequent approved tasks without changing this batch denominator.

**Expected outcome.** A reviewable report, accurate progress and prioritized follow-on work.

**Validation.** Every task links source/PR/current evidence or explicit blocker; verify percentages and links. Codex reviews report and release readiness. All 50 tasks alone do not establish all N42 groups or stable release completion.

Branch: unclaimed. PR: none. Evidence records: 0. Updated: 2026-11-07.
