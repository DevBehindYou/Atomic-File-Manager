# Advanced built-in plugins — deferred request intake

Recorded 22 October 2026. This is a requirements record, not an implementation, dependency selection, competitive research report or acceptance result. The user supplied the advanced eight-plugin request in attachment `1d435642-21f6-4deb-b73e-a86bab301554/Pasted text.txt` (SHA-256 `6D9DB73EF620B60870460FDE24DC6D1CA88337F5957397A1C37A20E21B3E1F96`). This document consolidates its requirements without treating candidate technologies or competitor claims as verified facts.

## Execution steering — 26 October 2026

The user's subsequent instruction to finish the eight-plugin modernization alongside continuing file-opening fixes supersedes the prior blanket defer-first sequencing for source audit, official research and independent isolated implementation. Existing active acceptance work still finishes without gate bypass or overlapping source ownership. The separate [modernization context](../plugins/modernization/PLUGIN_HANDOVER.md) and parent-reviewed [N=42 ledger](../plugins/modernization/PLUGIN_PROGRESS.md) now track this work. Benchmark deferral and unchanged phone permissions remain in force. This steering earns no completion credit and does not alter U01–U13.

## Entry gate and reporting boundaries (original intake context)

**Phase 0 comes first: finish the existing approved work and its gates.** Inspect active branches, PRs, issues, handovers and agents, continue the actual checkpoint, and avoid duplicate implementations or restarting completed work. Respect dependencies, isolated ownership, focused PRs, applicable current-head checks, independent review and clean guarded integration trials. Keep failed, pending, partial and accepted evidence distinct. No deployment, destructive action or gate bypass is authorized by a roadmap entry.

The execution source is [feature progress](FEATURE_UPGRADE_PROGRESS.md), with [the existing plan](FEATURE_UPGRADE_PLAN.md) and [current handover](../ai-handover/CURRENT_STATE.md). Its fixed denominator remains U01–U13. As of 24 October, the live ledger records **15% host verified (2/13) / 8% accepted (1/13)**: U04 host implementation is complete after combined ownership/visibility checks, while its wider physical matrix remains open. Partial U05/U06 slices do not complete those milestones. Feature integration is separate from main/release acceptance. This new request earns no current progress credit and does not silently enlarge or reset that denominator. Establish a separately identified, evidence-based future milestone/weight scheme only when the deferred scope is audited and scheduled.

At intake, the authorized 100-file measured benchmark still has two failed scenarios and no accepted baseline. Provider-contract success, a passing build or CI, and system-compositor trace rows are not Atomic frame/performance evidence. Current Storage usage work also has unresolved host-test gates. Consult the live ledger rather than freezing those changing checks into this future roadmap. Larger benchmark corpora remain separately scoped.

## Native technology source of truth

This is an existing **native Android Kotlin/Jetpack Compose** app, using the Atomic design system. The request's Flutter/Dart examples express desired capabilities; they do not authorize a Flutter migration, parallel UI framework, replacement app, package installation or wholesale rewrite.

Check actual sources before design: [`gradle/libs.versions.toml`](../../gradle/libs.versions.toml), [`app/build.gradle.kts`](../../app/build.gradle.kts), [`build-logic/convention/src/main/kotlin/atomic.android.application.gradle.kts`](../../build-logic/convention/src/main/kotlin/atomic.android.application.gradle.kts), and the wrapper. At this intake the repository pins Kotlin 2.2.10, AGP 9.1.1, Gradle 9.3.1, Java 21, minSdk 27 and compile/target SDK 36. These are source facts, not a claim of newest versions or future compatibility.

The [architecture inventory](../architecture/CURRENT_ARCHITECTURE.md) and [technology stack](../TECH_STACK.md) distinguish implementation from historical proposals: manual `AppContainer` wiring, custom `AtomicRoute` navigation, Room schema v4 alongside SQLiteOpenHelper stores, and SharedPreferences. Hilt is declared but its graph is inactive. Existing Coil 3, Media3, Android `PdfRenderer`, preview helpers, archive libraries, storage backends and operation infrastructure must be evaluated for reuse. DataStore, a new DI framework, a new navigation framework and an alternative media engine are not implied prerequisites.

Flutter candidates such as `extended_image`, `media_kit`, `just_audio`, `audio_service`, `pdfrx`, `re_editor`, `markdown_widget` and Dart archive packages are **unverified research leads from the request**, not selected dependencies. Evaluate native equivalents against actual gaps, API/device support, APK/installed size, maintenance, licensing, redistribution, security and measurable costs before any dependency proposal. No research, license review, vulnerability assessment or dependency upgrade has been performed for this intake.

All eight plugins stay bundled with Atomic and update with the app. “Plugin” describes a built-in feature boundary. Do not add remote executable catalogues, fake install/remove/update controls or separately installed add-ons. Optional downloadable engines would require a distinct scoped decision, not an assumption here.

## Audit and research before implementation

Phase 1 inventories actual framework/build/API constraints, source/backends/SAF and existing remote access, thumbnail/cache/metadata pipelines, viewers, ownership/lifecycle, DI/navigation, theme/adaptive/accessibility behavior, tests, logging, performance evidence and documentation. For every requested capability produce this matrix:

| Plugin | Feature | Current status | Issue | Root cause | Proposed solution | Priority | Test coverage |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Each of the eight below | One independently verifiable capability | Implemented / tested / partial / blocked / deferred, with evidence | Reproducible observed gap | Source-backed diagnosis | Smallest compatible change | P0 / P1 / P2 | Exact existing and missing checks |

P0 covers essential reliability, safe handling and core workflows; P1 covers advanced useful features; P2 covers optional expansion. A format name, dependency declaration or working demo alone does not prove complete support. Do not rewrite a functioning component before identifying its actual gap.

Phase 2 researches MiXplorer's official site and Image/Codecs/Archive/PDF/Tagger add-ons plus built-in media, text, HTML and eBook capabilities. Use official documentation and release notes, official store descriptions, credible XDA/user feedback and reproducible demonstrations; compare relevant VLC, gallery, PDF and archive workflows where useful. Record the user need, interaction, Atomic's current behavior, integration fit, costs, compatibility, security, licensing, evidence date and priority. Label confirmed competitor behavior, reported behavior and Atomic proposals separately. Attachment URLs, versions and community assertions remain leads until verified. Do not copy closed-source code or assets.

## Shared foundation to design after the audit

Adapt conceptual `PluginDescriptor`, registry, capability resolver, content source, viewer controller, metadata/action services, plugin settings, errors and lifecycle ownership to existing native conventions. They are design roles, not mandatory class names or a new framework.

- Descriptors cover IDs, icons, descriptions, extensions, MIME/signature detection, read/write/edit capabilities, sources, controls, supported platforms, engine requirements and honest failures/fallbacks. Resolve the best built-in handler and Open with; inspect content where an extension/MIME is insufficient.
- Content boundaries support local files, content URIs/SAF, existing SD/USB and remote backends, streams, seekable/non-seekable sources and read-only access. Materialize paths only when an engine requires them, with bounded cache/quotas, cancellation and cleanup. Avoid full-file RAM copies and redundant local copies. Handle remote revisions/conflicts without overwriting silently.
- Share adaptive bars, titles/breadcrumbs/details, context actions, copy/move/share/delete confirmation, progress, search where meaningful, immersive controls, loading/empty/error/retry states and unsaved-change handling. Preserve capability-aware actions, orientation, keyboard and accessibility support.
- Owners must cancel/close workers, decoders, streams, renderers, listeners and temporary files at the appropriate lifecycle boundary. Background operation is an explicit supported mode, not an accidental leaked viewer.

## Eight-plugin requirements register

All entries are requested scope for later audit and staged implementation. Conditional formats and P1/P2 features remain conditional; none is promised by this intake.

### 1. Image Viewer — P0 highest priority

P0 formats: JPEG/JPG, PNG, WebP, animated GIF, BMP where the decoder supports it, and correct EXIF orientation. Evaluate animated WebP/APNG, SVG, multipage TIFF, device-supported HEIF/HEIC, ICO and RAW through a vetted decoder as P1. Distinguish static decoding, animation, multipage browsing and editing support.

Require pinch zoom, focused double-tap zoom, pan, fit screen/width/original size, adjacent-folder navigation using the actual folder order independently of media indexing, and reliable swipe-versus-pan arbitration. Define zoom restoration, control visibility, immersive mode and portrait/landscape behavior. Use sampled/high-resolution or tiled rendering only where needed. Animation must preserve timing/looping, pause/resume/replay and disposal while bounding malformed-input memory.

Provide Back, name/details, share, edit, rotate, delete and More, with capability-aware copy/move/rename/Open with/wallpaper/metadata/location/favourite integration. Use existing trash policy or explicit permanent-delete confirmation. Editing requests: crop, 90-degree rotation, flips, straighten if supported, resize/aspect controls, format/quality export, reset, Save and Save as. Define copy-first/non-destructive policy and metadata/privacy behavior. P1 adds brightness, contrast, saturation and color adjustments.

Load metadata on demand: actual name/extension/MIME, dimensions, size, creation/modification dates where available, camera/EXIF orientation/exposure/aperture/ISO/focal length/color profile and GPS only when authorized. Test thumbnails, thousands of images, 4K/8K/panoramas, TIFF, long GIFs, corruption, SD/USB and rapid navigation with bounded decode/cache/worker ownership.

### 2. Video Player — P0 highest priority

First evaluate existing Media3 against actual gaps. Any engine comparison must cover hardware decoding, codec/device variability, CPU/RAM/battery, seek/A-V sync, subtitles/tracks, lifecycle, URI/FD support, native binary/APK size, maintenance, licensing and redistribution.

P0 container goals: MP4, MKV, WebM, MOV, M4V and 3GP; P1 evaluates AVI, FLV, MPEG/MPG, TS and others. Verify codecs separately: H.264/H.265, VP8/VP9, AV1, MPEG-4 Part 2, AAC, MP3, Opus, Vorbis and AC3/EAC3 subject to engine/device/legal support. An extension is never a playback guarantee.

Core controls: play/pause; seek with elapsed/total/buffer state and scrubbing; ±10-second seek; folder previous/next; supported 0.5/0.75/1/1.25/1.5/2× speed; fullscreen/exit; rotation; Fit/Fill/Original/Crop aspect; volume/mute; control lock; repeat; remembered position/resume and media information. Efficient seek previews are conditional. Configurable gestures include left/right double-tap seek, horizontal seek, vertical brightness/volume, single-tap controls and engine-supported pinch aspect. Allow disabling advanced gestures; preserve Android Back and accessibility.

Support embedded and external SRT/VTT subtitles and ASS/SSA only where verified, with on/off, style/font/position/offset controls. Expose multiple audio tracks/languages and retain selection, with actionable unsupported-track messages. P1: PiP, opt-in background audio, hardware decode controls where useful, audio-only fallback, frame capture, folder playlists and matching subtitle discovery. P2: A-B repeat, frame stepping, audio delay and separately scoped casting. Separate temporary viewing rotation from permanent transformation.

Exercise corrupt/unsupported files, missing audio, stalls, seek failures, focus/Bluetooth/calls, background/recreation, deleting an active file, disconnected sources and fast switching, with bounded cleanup and actionable recovery.

### 3. Audio Player — P0/P1

Verify MP3, AAC/M4A, WAV, FLAC, OGG/Vorbis and Opus; evaluate WMA/ALAC only with demonstrated support. Require play/pause/seek/time/skip, repeat one/all/off, shuffle, speed, resume, folder playlists, queue management and explicit persistent-state policy.

Background playback must correctly handle notification/lock-screen controls, headsets/Bluetooth, focus and calls, disconnects, notification removal and service lifetime. Request only necessary foreground-service permissions and keep background behavior intentional. Show artwork, title, artist, album, duration and format in coherent mini/full-player controls. A visualizer is optional and must be bounded.

P1 tag editing covers title/artist/album/artwork/genre/track/year/comments only for verified safe formats/tag versions. Batch editing follows proven single-file integrity and safe-save behavior; no corruption or universal tag-write claim.

### 4. PDF Viewer — P0/P1

Core: smooth vertical scrolling, single/continuous and horizontal modes, pinch/double-tap zoom, fit width/page, page/total and jump, scroll thumb, portrait/landscape, night mode, viewing rotation and remembered position. Text selection/copy/search with next/previous/highlights, outline/TOC, internal and safely handled external links, information, password opening, share and platform printing depend on actual engine capabilities.

P1 adds bookmarks, highlights, notes/drawing/annotations, page-to-image export, page ranges and thumbnail navigation. P2 evaluates merge/split, forms, TTS and justified offline OCR. Treat viewing, annotation and structural editing as separate capabilities. Require progressive rendering and bounded caches with clear zoom quality. Validate one-page and 1000+ page files, scans, high-resolution drawings, passwords, corruption and mixed page dimensions.

### 5. Document Viewer — P0/P1

P0 goals: TXT, CSV, verified RTF and local DOCX. P1 evaluates XLSX, PPTX, ODT/ODS/ODP, EPUB and legacy DOC/XLS/PPT through a justified engine. For each format state **faithful layout**, **structured/simplified**, **text only**, or **no internal support with Open with**. Extracted text is not Word layout fidelity.

Request zoom, selection, search, page/section navigation, reflow/font controls, bookmarks/position, properties, print/share and landscape where supported. Spreadsheets need sheet selection, readable rows/columns/cell formats/formulas, search and frozen headers if supported. Slides need navigation, fit and thumbnails; EPUB needs chapters/reflow/fonts/themes/TOC.

Prefer on-device processing. Do not upload private documents without informed authorization. Bound malformed embedded assets, oversized XML, decompression and external references. Unsupported features need honest limits and a useful external handoff.

### 6. Code/Text Editor — P0/P1

Core: open/edit/Save/Save as, dirty state, undo/redo, find/replace/find all/replace all with case/whole-word/regex options, go to line, wrapping, line numbers, selection/copy/paste, monospaced font, read-only mode, long-line behavior and keyboard shortcuts.

Evaluate maintained grammar-based highlighting for Dart, Python, JavaScript/TypeScript, HTML/CSS, JSON/YAML/XML, Java/Kotlin, C/C++/C#, Rust, Go, SQL, Bash, PowerShell, Markdown and INI. Do not grow fragile one-off parsers. P1: indentation/tab-space settings, matching brackets/current line, supported folding, comment shortcuts, encoding detection/selection, UTF-8 BOM handling, preserving/selecting line endings, and JSON/XML formatting/validation. P2: multiple cursors, diffs, snippets and multiple tabs.

Define KB-to-hundreds-of-MB handling with bounded/paged read-only fallback and practical search, explaining limits without silent truncation. Saves must use backend-specific staging/publication; SAF is not assumed POSIX atomic. Test denied writes, low space, external revisions, disconnects, interrupted saves and conflicts. Preserve the original and offer Save as when safe replacement is unavailable.

### 7. Markdown Viewer — P0/P1

Evaluate verified CommonMark/GFM coverage: headings, emphasis/strike, links, nested/task lists, tables, quotes, highlighted fences, rules, images, footnotes where supported and local relative images/links. Require TOC/heading jumps, search, copy text/code, position restoration, font/themes and reliable scroll restoration.

Integrate Preview/Edit with the existing editor; optionally add synchronized wide-screen split preview, refresh and supported export/share with unsaved-change handling. P1/P2 evaluates sandboxed Mermaid/math, image zoom, anchors and internal navigation. Treat Markdown as untrusted: govern remote assets and unsafe HTML; no arbitrary scripts or privileged WebView bridge.

### 8. Archive Manager — P0/P1

P0 format goals: ZIP/ZIP64, TAR, TAR.GZ, GZIP and BZIP2. P1 evaluates 7z, RAR/RAR5 extraction, XZ, Zstandard/TAR.XZ/TAR.ZST, ZIP AES and multipart handling with vetted engines. P2 evaluates ISO/CAB or specialized formats only when maintained. Record browse/extract/create/modify/encrypted-read/encrypted-write/multipart capabilities separately.

Browse without automatically extracting everything: folder hierarchy, entry name/size/date/compressed size, search/sort, safe entry preview, selected/all extraction to a chosen destination and copying entries where feasible. ZIP creation includes files/folders, compression level, progress/cancel and intentional background ownership; encrypted writes require a verified secure engine. P1 creation/update/splitting for TAR/7z is conditional on actual support.

Defend against traversal, absolute paths, symlinks, bombs/ratios, excessive depth/count, duplicates/case collisions, bad headers/corruption, disk exhaustion, interruption and overwrite conflicts. Bound limits and estimates, stage/roll back/clean up safely, and never overwrite silently. Progress reports current entry/counts and bytes/ETA only when meaningful; pause/resume is shown only if real. Cancellation, actionable errors and safe retries must release resources and preserve unrelated data.

## Product experience and preferences

Keep the Atomic design language: plugin cards with icon/name/description and built-in navigation, details with actual formats/features/preferences/limits/associations, coherent spacing/type/contrast, adaptive one/two-column layouts, tablet/landscape support, touch targets, restrained motion and loading/empty/disabled/error states. Do not present fake installation/update controls.

Use existing settings persistence and meaningful options: image zoom/animation/gallery; video gestures/resume/subtitles/useful fallback; audio background/repeat/shuffle; PDF mode/theme/resume; editor wrap/line numbers/font/tab/syntax; Markdown font/code theme/TOC; archive destination/conflict/limits. Persist only supported behavior and avoid controls that imply absent features.

## Security, privacy and performance gates

Reuse valid scoped SAF grants and backend capabilities. No new broad storage, root or Shizuku permission is implied. Do not upload documents/metadata without authorization or log sensitive paths/content. Bound temporary sensitive/vault material; govern untrusted inputs, assets, HTML and external intents. Verify native-library redistribution, licenses and vulnerabilities before adopting engines. Do not promise secure deletion, universal format support or provider-atomic saves without proof.

Load engines lazily, metadata on demand, and thumbnails/caches/workers within budgets; stream/incrementally process large inputs, honor cancellation/owners/device limits and avoid duplicated copies/decoders. Measure APK/AAB and installed-size impact. Establish before/after evidence for first content, folder transition, frame/jank, RAM/CPU, zoom/seek, audio start, PDF paging, editor latency, archive throughput, battery when measurable, startup and size. Use reproducible representative devices and release-like profiles; debug timings are not a baseline. Derive budgets from valid measurements rather than invented targets.

## Validation matrix and eight acceptance journeys

Use meaningful unit, Compose, integration, storage/parser/lifecycle/error/security and performance checks with deterministic fixtures. Keep compilation, host tests, instrumentation, manual device acceptance and benchmarks separate, including exact source/build/device/test count and retained failures.

| Family | Required representative fixtures |
| --- | --- |
| Images | JPEG/PNG, animated GIF/WebP/APNG, high resolution, EXIF variants, corruption |
| Video | H.264 MP4/MKV, HEVC, VP9 WebM, device-supported AV1, multiple tracks/subtitles, corruption |
| Audio | MP3/FLAC/AAC/WAV/OGG/Opus, VBR, missing and malformed tags |
| PDF | Plain, password-protected, scanned, large and malformed |
| Documents | DOCX/XLSX/PPTX/EPUB/CSV/RTF, complex tables/images and explicit fidelity limits |
| Code/text | Large files, Unicode/BOM, mixed line endings, long lines and invalid encodings |
| Markdown | Tables, relative images, fences, tasks, links, unsafe HTML and remote assets |
| Archives | ZIP/ZIP64/TAR, verified 7z/RAR/encryption/multipart, corrupt files, Zip Slip and bombs |

Cover supported APIs, low/mid/high-resource phones and tablets; portrait/landscape and light/dark; local/SAF/SD/USB/read-only/existing remote sources; network interruption, disconnection, revoked grants, low space, process kill/restart and repeated open/close. Obtain authorization for any real mutation or external resource required by a test.

1. Animated GIF: animation, zoom, metadata, edit and save a copy without damage.
2. MKV: seek, pause, rotate, change tracks/subtitles and resume accurately.
3. Audio: folder queue, background/lock-screen/notification controls and focus behavior.
4. Large PDF: search, page jump, zoom and restored position within budgets.
5. DOCX: tables/images with truthfully labeled rendering fidelity and fallback.
6. JSON: edit, undo, search/replace, safe save, reopen and byte/content integrity.
7. Markdown: relative images, edit/preview refresh and safe local/remote link policy.
8. Protected archive: browse, extract selection, cancel another extraction and preserve unrelated data.

## Sequencing, ownership and deliverables

After Phase 0 and audit/research, the requested default sequence is shared foundation → Image → Video → Audio → PDF → Code/Text → Markdown → Document → Archive → integration/performance. Reconcile dependencies and actual gaps before scheduling; Document remains required even though its detailed request precedes the editor sections.

Where useful and explicitly authorized, assign isolated workstreams: A shared foundation; B images; C video; D audio; E PDF/documents; F code/Markdown; G archives; H QA/UI/integration. Foundations precede dependents. Keep local Gradle serialized on this 8 GB host. Each focused PR records changed components, dependencies, actual checks, measurements, limits and rollback. Do not duplicate active work, bypass failing checks or use parallelism to hide shared-file ownership.

Produce the following records using repository documentation conventions after the corresponding work is performed; these titles are requested outputs, not claims that such reports already exist:

| Requested record | Required content |
| --- | --- |
| PLUGIN_AUDIT | Actual source inventory and reproducible gaps |
| MIXPLORER_COMPETITIVE_RESEARCH | Dated attributed evidence, reports versus confirmed behavior, Atomic proposals |
| PLUGIN_ARCHITECTURE | Native boundaries, ownership, source contracts and failure behavior |
| PLUGIN_FEATURE_MATRIX | Eight plugins, per-feature/format capabilities and honest support status |
| PLUGIN_DEPENDENCIES | Evaluated existing/new engines, compatibility, size, licenses, maintenance/security evidence |
| PLUGIN_TEST_REPORT | Exact host/device/negative-case results and remaining acceptance gates |
| PLUGIN_PERFORMANCE_REPORT | Valid baselines, comparable measurements, budgets and limitations |
| PLUGIN_HANDOVER | Current commits/PRs, blockers, evidence, safe continuation and remaining scope |

Continue autonomously within authorized scope, favor working components and small reversible changes, and expose honest unsupported/error states rather than placeholder buttons. Use feature flags only when they make integration safer. Report verified current phase, plugin capability status, completed versus active PRs, actual tests, blockers/risks and the next dependency. Do not call the future program 100% complete until all committed acceptance criteria pass. CI alone is insufficient for device, safety, format, lifecycle and performance claims.

The eventual final report must cover: (1) summary; (2) architecture audit; (3) competitive evidence; (4) final design; (5) feature/format matrix; (6) completed capabilities; (7) unsupported limits; (8) dependency changes; (9) license/security evidence; (10) PRs/commits; (11) automated checks; (12) real-device checks; (13) performance; (14) safety/privacy; (15) deferred P2/remaining work; and (16) independently verifiable progress. Release gates include applicable build/static checks with no new warnings, no unresolved P0 crashes or destructive behavior, preserved controls and documented real evidence, separately from this intake and feature-integration merges.

