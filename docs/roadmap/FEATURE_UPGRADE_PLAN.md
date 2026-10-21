# Atomic feature and optimization implementation plan

Prepared 21 October 2026. Based on the supplied master upgrade specification, [repository gap analysis](../architecture/UPGRADE_SPEC_GAP_ANALYSIS.md), [existing modernization roadmap](OPTIMIZATION_ROADMAP.md), and the user's decision to keep plugins built into the app.

**Planning status: complete. Execution status: staged work remains.** This plan does not claim that cloud connectors, advanced viewers or performance targets have been implemented.

## Decisions already made

- Preserve native Android, Kotlin, Compose, API 27 minimum and the Atomic design system.
- Keep viewers and tools bundled with the application. Settings → In-app plugins is an inventory of shipped functionality, with feature and limitation details.
- No GitHub/Google Drive executable downloads, remote plugin installer, independent plugin update feed, or fake install/remove controls. Features update with the app.
- Keep lightweight previews and external opening available. No advertised “Pro” capabilities until implemented and tested.
- Preserve Files storage shortcuts, List/Grid, long-press actions, content-sized top menus, bottom icon-card action menus and coloured file-family icons.
- Reuse storage IDs, capabilities, operation queue, Room, thumbnails and current viewers. Introduce new interfaces and modules only to solve demonstrated coupling.
- Preserve original files, permissions, preferences, favourites, recents, accounts and journals through migrations.
- Separate source implementation, host tests, phone acceptance and release acceptance in every progress report.

Bundling avoids the external executable-delivery design. It does not reduce the download size by itself or settle the app's other release requirements.

## Baseline and scope

The last fully validated integration is application source `4792db8`, with documentation HEAD `89b1d45`: 917 app cases, 28 custom Lint-rule cases, 13 selected API34 surface cases, debug/release Lint and packaging. These counts are historical checkpoint evidence, not proof for every future change.

The In-app plugins increment is separate; see [its validation record](../testing/BUILT_IN_PLUGINS_VALIDATION.md) for current results and device limitations.

Existing roadmap M01–M11 implementation remains credited at 47/60 weighted units, approximately 78%; M12/M13 acceptance is incomplete. This expanded feature plan has a different scope, so **78% must not be reused as its completion percentage**.

No reliable navigation/search baseline, production signing, full provider matrix or process-kill acceptance has been established. APK packaging of benchmarks is not a performance run.

## Ordered delivery ledger

Each row is a deliverable, potentially split into small reviewable changes. Completion requires the listed exit evidence, not just a screen or class.

| ID | Priority / state | Deliverable | Dependencies | Exit evidence |
| --- | --- | --- | --- | --- |
| U01 | P0 / accepted into feature integration | Bundled In-app plugins inventory with eight real feature entries, details, Back/Close and limitations | Existing viewers | Focused host checks, full CI, API34 surface, independent review and PR #61 merge pass; release acceptance remains separate |
| U02 | P0 / planned | Trustworthy benchmark setup and fixtures | Existing benchmark module; available test device for runs | Setup fails on missing destination/items; known corpus; trace boundaries; baseline with device/build metadata |
| U03 | P0 / planned | Folder listing responsiveness and explicit partial state | U02 measurements | First-content/full-list/frame/allocation comparison; correct final sort, cancellation, selection and clipboard |
| U04 | P0 / planned | Startup access state and screen ownership | Existing M05/M12 work | No false denied banner while access is resolving; no redundant hidden-pane work; recreation/navigation tests |
| U05 | P0 / planned | Index consistency, durable scanning and analysis cache | U02; schema design | Interrupted scan resumes correctly; stale/denied/deleted states differ; query and memory evidence; migration tests |
| U06 | P0 / planned | File-opening router and defaults settings | Existing preview/export helpers | Once/always/reset/missing-handler flows, MIME precedence and safe URI-grant tests |
| U07 | P1 / planned | Unified storage-account contracts and Add storage surface | Existing backends; U06 for remote opening | Honest connection methods, per-account identity/capabilities, isolated credentials/cache; no dead Connect controls |
| U08 | P1 / planned | First direct cloud connector end to end | U07; provider/scope decision and registration | Auth, pagination, account isolation, transfer, reconnect, quota/throttle and conflict tests against sandbox account |
| U09 | P1 / planned | Media and image viewer improvements | U06; performance budgets | Resume/rotation/subtitles/track controls as supported; high-resolution and corrupt-input tests |
| U10 | P1 / planned | PDF and document improvements | U06; PDF engine evaluation | Accurate feature matrix; search/bookmark/password features only when supported; size/licence/memory evidence |
| U11 | P1 / planned | Editor and archive reliability improvements | U05/U06 where relevant; operation journal work | Draft recovery, revision conflicts, bounded large-file edits; archive format/security/interruption tests |
| U12 | P1 / planned | Second direct connector and network acceptance | U08 contract learning; server fixtures/hardware | Shared contract passes on a second service; actual SFTP/SMB/DAV/USB evidence |
| U13 | P0 release gate / open | Operation recovery and release hardening | Applicable feature increments | Process-kill repair, R8 runtime, accessibility, size/performance regression, exact-head CI/review and signing gates |

Execution can overlap only where data/schema ownership permits. U06 can proceed while hardware baselines are unavailable; U09–U11 do not require every cloud provider. U13 recovery work must precede advertising resumable cross-provider moves.

## U02–U05: measured speed and reliable state

### First implementation slice: benchmark correctness

Extend `benchmark/src/main/.../FolderScrollBenchmark.kt` and the fixture/test harness:

1. Replace optional navigation and silent success when no scrollable list exists with explicit setup assertions.
2. Navigate to a dedicated generated fixture folder and verify its identity and expected count before measurement.
3. Separate cold start, first usable folder content, complete listing and scroll-frame measurements.
4. Add low-overhead trace boundaries at listing start, first accepted chunk, first displayed content and completion. Record counts/reason codes, not private filenames.
5. Prepare reproducible 100/1,000/10,000-entry datasets. Include a known subset of thumbnail fixtures. Introduce 100,000 entries only after storage/time budgets are checked.
6. Record build/hash, OS/device, compilation mode, cache state, temperature/thermal status and sample count.

Use the same fixtures and conditions before and after changes. Start with repeated exploratory runs and report p50/p95 with sample counts; only publish meaningful p99 estimates with sufficient trials and uncertainty. Five startup samples are not a credible tail-latency gate.

Fixture creation/cleanup must target an explicitly owned test directory. Never scan-and-delete arbitrary user content to prepare a benchmark.

### Listing optimization

Current code already streams local directory entries and uses virtualized rows. Optimize only after attributing cost:

- Compare full accumulating-list copies/sorts with bounded publication and measured merge strategies.
- Avoid making breadcrumb metadata lookup a prerequisite for first rows when a valid requested identity already exists.
- Add explicit loading/partial/complete/error state; show partial counts and sorting honestly.
- Keep rows selectable while enumeration continues without changing selection identity.
- Cancel old folder work and prevent late publication after switching storage or panes.
- Bound metadata and thumbnail work; visible rows and user actions take priority over background scanning.

Acceptance: fixture item sets have no omissions/duplicates; final order matches the existing sort contract; rapid Back/forward/storage switching does not contaminate lists; first-content latency and allocation improvements are measured.

The supplied 350 ms warm, 900 ms cold and 300 ms indexed-search targets are provisional for the defined mid-range workload, not universal promises.

### Index and analysis

Add durable scan state and explicit source/generation ownership before treating the index as authoritative:

- Record scan root/account, checkpoint, generation and terminal outcome.
- Reconcile changed folders and preserve evidence of inaccessible roots instead of treating them as empty.
- Publish consistent searchable snapshots or a documented bounded-consistency model.
- Avoid loading every file row to filter a single root in memory.
- Evaluate substring-aware query strategies; preserve current user-facing search semantics.
- Cache completed analysis with freshness metadata and invalidate on known changes. A persisted timestamp alone does not prove the filesystem is unchanged.
- Coordinate category/index/analyser work without silently reusing stale results.

Use Room migration tests and fault-injecting backends for rename, delete, permission loss, cancellation and restart. Add query-plan and memory evidence.

## U06: opening files

Introduce a small domain `FileOpenRouter` and persisted association repository; keep Android launching and URI handling in platform adapters.

Routing precedence: explicit extension override → exact MIME rule → file-family rule → global policy. Resolve metadata first and apply bounded sniffing only when needed to disambiguate a user-selected file. File-icon colouring remains metadata-only.

Settings → File opening:

- Ask / prefer built-in / prefer external behavior.
- Per-type rule with current handler and Change / Reset.
- Explicit once/always selection; reset-all confirmation.
- Missing or incompatible handler recovery.

Use current safe content URIs and read grants. Treat writable external editing as a separate capability and workflow. Never say the original was saved when an external app only exported a copy.

Remote handoff must show preparation/progress and cancellation. Existing 64 MiB external-export and 512 MiB media-staging limits remain until replaced with bounded downloads or suitable streaming. Do not simply remove the caps.

Tests: unknown/wrong MIME, no installed handler, uninstalled saved handler, SAF grant loss, local/content/remote input, cancellation, low space, read-only editor and preference migration.

## U07–U08/U12: storage and cloud

Separate three connection types in Add storage:

1. **Device apps:** Android picker and user-granted files/folders; authentication remains with the provider app.
2. **Cloud accounts:** verified direct integrations with service-specific authentication.
3. **Network connections:** existing SFTP/SMB/FTP/FTPS/WebDAV configuration and health.

Use compact Atomic bottom cards, search and real capability/status labels. Connected accounts receive distinct labels and Files shortcuts. Keep connected-account management separate from the list of available connection methods.

Before the first direct connector, extend contracts for:

- Provider/account-qualified opaque identity and stable remote IDs.
- Revisions, conflict preconditions, parent identity and availability.
- Page continuation/completion; deduplication and provider throttling.
- Optional delta cursor, range reads, quota, native-document export and resumable transfer capabilities.
- Credential lifecycle, isolated metadata/content cache and disconnect cleanup.

An access token, remote folder and device-app grant are different ownership models. Do not force them into one generic password form. Reuse existing credential protection where appropriate, with per-account keys and tests.

Recommended first candidate: Google Drive **subject to scope and registration feasibility**. Selected-file access and full-drive browsing must be presented as different products. Dropbox or OneDrive is the next candidate to test the abstraction; final ordering follows actual setup availability.

MEGA needs a separate native-SDK size/licence/build assessment. Box, pCloud, S3/B2 and screenshot legacy brands remain individually researched backlog items. Do not claim they are unavailable merely because they are not implemented in Atomic.

Connector acceptance includes OAuth decline/expiry/reconnect, no pagination omissions, duplicates/Unicode, read-only actions, low storage/quota, 429/Retry-After, cancellation, account isolation and a verified transfer. Real mutations use disposable service accounts and fixtures.

## U09–U11: bundled viewer upgrades

The Plugins inventory describes current capabilities; update it only when a corresponding feature ships.

| Feature | Next useful increment | Later / separate evaluation |
| --- | --- | --- |
| Video | Stable resume by file identity/revision, rotation recovery, supported track/subtitle controls and clearer decoder failure | PiP, gestures, remote range streaming; no universal codec promise |
| Audio | Audio focus and queue/resume behavior; decide explicit foreground versus background mode | Background service/notification acceptance before advertising background playback |
| Images | Decoder/format matrix, sampled large-image behavior and high-resolution region/tile evaluation | Save-copy crop/rotate/annotation with format and metadata tests |
| PDF | Bookmark/reading-position persistence; score engines for search/select/password handling on supported APIs | Forms/annotations/signatures only after capability, licensing, binary-size and security review |
| DOCX | Better structured text fidelity and clear format disclosure | Full Office page rendering/editing remains a separate engine/product decision |
| Code/text | Crash-safe recoverable drafts and revision-aware saves; bounded large-buffer model | Incremental syntax highlighting, language packs bundled only if justified; no code execution |
| Markdown | Defined supported syntax and parsing fixtures, shared editor draft behavior | Extended syntax only with explicit rendering/security tests |
| Archives | Verify selected entry extraction, progress/cancel, destination conflicts and restart repair | New formats/password support only after engine/security/licence acceptance |

Archive gates include path/link escapes, malicious metadata, decompression budgets, entry/depth limits, corrupt/truncated data, low disk space and cleanup that never deletes originals.

Do not turn every viewer into a new build module immediately. Keep ownership boundaries clear within the current structure, extracting a module only where dependency isolation or build measurements justify it.

## U13: operation and release acceptance

Retain verified staging/hash checks and durable queue admission. Complete the remaining recovery protocol:

- Persist phases needed to distinguish staged bytes, published destination, verified copy and source deletion.
- On restart, inspect known identities and preserve the only good copy.
- Never replay ambiguous mutations as if they were idempotent.
- Test cross-provider copy/move, external revision changes, destination collisions and failures between each phase.
- Distinguish cancelled, failed, awaiting user conflict resolution and restart-repair states in Tasks.
- Only display Pause/Resume where the backend and journal implement them.

Run host fault injection before real process-kill tests. Real-device mutation/recovery/USB/network tests use owned fixtures and their separately established authorization; previous surface-test approval does not imply permission to disrupt real user transfers.

Release gate:

- Required host/static checks and exact-current-head CI/review.
- API 27 and modern Android coverage, plus real SAF/MediaStore/SD/USB/network/provider behavior where advertised.
- R8 runtime and controlled stress test.
- Accessibility: large fonts, light/dark contrast, screen readers, focus, narrow/wide/rotated layouts.
- Comparable size, startup, folder/search/frame/memory results; no unverified percentage improvement.
- Production signing, all-files-access/Shizuku declarations and store review remain owner gates.
- Rollback preserves schemas/data or documents a tested compatible recovery path; app downgrade is not assumed safe.

## Decisions needed only at the dependent milestone

| Decision / external input | Needed before | Work that can continue meanwhile |
| --- | --- | --- |
| Available test device and permitted fixture budgets | Physical baseline/recovery runs | Benchmark harness, host fixtures, router, docs |
| First direct provider and selected-file versus broad-drive scope | Real cloud authentication and connector acceptance | Account contracts, capability/error tests, system-provider UX |
| OAuth registration, redirect identities, test accounts | Provider sign-in acceptance | Mocked expiry/throttle/pagination contract tests |
| Foreground-only versus background audio experience | Audio service behavior | Viewer error/resume improvements |
| PDF engine capabilities/licence acceptance | Shipping a heavier PDF engine | Bookmarks, bounded rendering and external routing |
| Offline retention/disconnect cleanup behavior | Persistent cloud content caching | Metadata-only browsing and cancellation |
| Production signing and distribution registrations | Production release | All normal local engineering checks |

No question is required to finish this plan. Ask for an input when it actually blocks its dependent work, after preparing a concrete choice.

## Progress and reporting

Maintain one status per ledger item: planned → implementing → host verified → device/provider verified → accepted. An implementation may be host verified while its physical or external-account gate remains open.

Each implementation report records:

- User-visible behavior and source/diff identifier.
- New and existing tests actually run, including failures and subsequent fixes.
- Before/after measurements only when collected under comparable conditions.
- APK/build evidence, screenshots when available, and exact unsupported features.
- Migration/rollback implications and the next smallest safe change.

Reuse existing audit/testing documents and link new evidence. Do not overwrite historical results or count unrun tests as current passes.

**Immediate sequence:** U01 host checks passed; repeat its surface flow when a phone is available. Implement U02 benchmark correctness next, then collect its baseline when hardware is available. U06 is the next independent functional slice if hardware remains unavailable. No cloud registration or production release action is needed merely to begin these steps.
