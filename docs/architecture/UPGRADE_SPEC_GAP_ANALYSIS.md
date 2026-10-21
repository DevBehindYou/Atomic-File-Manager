# Upgrade specification gap analysis — 20 October 2026

Assessment of `File_Manager_Upgrade_Master_Prompt.md` and the supplied cloud-provider screenshot against Atomic File Manager at HEAD `89b1d45` (application source checkpoint `4792db8`).

Scope: document review, repository inspection, existing evidence review and selected official documentation checks. The user's request was **Analyze**. Instructions inside the supplied master prompt are proposed requirements, not authorization to execute its entire implementation program. No application code, phone state, cloud accounts or release configuration was changed for this assessment.

**Subsequent user decision:** keep viewers/tools built into Atomic and expose them under **Settings → In-app plugins**. Do not implement GitHub/Google Drive executable downloads or an on-demand installation catalogue. The follow-up implementation adds an inventory of the eight shipped viewers/tools with feature and limitation details. They update with the app. The optional delivery discussion below is retained as analysis of the supplied specification, not the selected implementation direction.

## Assessment

The specification provides useful product goals and acceptance criteria, but needs an Atomic-specific revision before implementation. It combines existing functionality, unfinished acceptance work and substantial new products. Its “implementation-ready” label overstates readiness: cloud scope, account identity, distribution strategy, module lifecycle and measurable performance baselines remain unresolved.

The screenshot is a visual reference. It cannot establish that any listed service is connected, functional or present in Atomic. In particular, the document's statement that many requested brands already appear in the current UI is not supported by this repository.

Retain Kotlin, Compose, the storage abstraction, operation engine and Atomic design system. Extend them incrementally. Do not introduce the illustrative TypeScript contracts or dozens of Gradle modules wholesale.

## Verified starting point

| Area | Current evidence | Implication |
| --- | --- | --- |
| Platform | Android, Kotlin, Compose; API 27 minimum, compile/target 36 | Remove iOS/desktop implementation branches from the Atomic execution plan |
| Build structure | `:app`, `:lint-rules`, `:benchmark`, included convention build | Most runtime code is within one application module; no dynamic feature modules |
| Storage | FILE, SAF, MediaStore, USB, SFTP, FTP/FTPS, SMB, WebDAV and Shizuku backend types | Reuse adapters and existing capability checks |
| Local listing | `Files.newDirectoryStream` emits bounded chunks; listing use case dispatches to IO | Progressive enumeration already exists |
| UI | Lazy list/grid, storage shortcuts, contextual actions, thumbnails and Tasks popup | Preserve recent UI decisions |
| Search | Room metadata table, batched builds, targeted folder refresh, substring search | Foundation exists; durable incremental reconciliation is incomplete |
| Cloud | Android-provider linked folders; network connectors | Direct branded OAuth/SDK cloud accounts are new work |
| Opening | Internal previews and safe external read-only URI handoff | Saved per-type routing and default-app settings are missing |
| Viewers | Image, PDF, DOCX text, media, text/code, Markdown and archive features | Assess each feature; do not describe all viewers as absent |
| Delivery | Release still uses debug signing; no plugin catalogue/delivery system | Production distribution and optional-feature delivery need explicit design |

Existing validation reports record 917 app tests, 28 custom Lint-rule cases, 13 selected API34 device cases and passing build/static checks. These were **not rerun for this analysis** and do not certify the new requirements.

The existing release artifact is **7,410,490 bytes (7.41 MB / 7.07 MiB)**. Its SHA-256 matches the recorded validated artifact:
`74dd33db211bb7641dd7f9d17a611a5692168dd8e32f86b4cfb31155164fe27c`.
This is APK size, not Play download size, installed size, cache size or a before/after optimization result.

Evidence: [build configuration](../../app/build.gradle.kts), [module configuration](../../settings.gradle.kts), [current validation](../testing/MENU_REFINEMENT_VALIDATION.md), [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md).

## Performance: improve the existing pipeline

### Listing and rendering

`FileSystemBackend.listChildren` already enumerates immediate children progressively, without recursively scanning descendants. `GetDirectoryListingUseCase` moves this work onto IO. Reimplementing enumeration from scratch would duplicate working code.

`BrowseViewModel.loadDirectory` accumulates all children, publishes the first chunk, then approximately every 120 ms. Each publication copies the accumulated list and `applyNewItems` filters/sorts it on `Dispatchers.Default`. The final publication repeats this work.

Consequences to measure:

- A lazy UI limits composed rows but does not bound the metadata list held in memory.
- Repeated full-list copies/sorts can increase allocations and CPU as folders grow.
- First content waits for remembered sort, folder metadata and breadcrumb resolution before listing collection.
- `isLoading` becomes false after the first publication; an explicit “more entries arriving” state would make partial counts and sorting clearer.

These are code-supported performance hypotheses, **not measured bottlenecks**. Measure time to first usable content, full enumeration, allocation/GC and frames before choosing batching, sorted merges or paged metadata. Preserve selection, scroll anchors, filters, cancellation and deterministic final order.

Evidence: [filesystem listing](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/backend/FileSystemBackend.kt), [listing dispatch](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/domain/usecase/GetDirectoryListingUseCase.kt), [browser state](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/screens/BrowseViewModel.kt).

### Search and indexing

The existing Room index uses generation-based batch updates and targeted folder refresh. It does not provide the proposed durable scan cursor, source-specific delta sync and fully resumable reconciliation.

Search uses `LIKE` substring matching with ordered LIMIT/OFFSET pages. The schema only explicitly indexes generation in addition to its primary key. `RoomSearchIndex.files` obtains all file rows before filtering roots in memory. Search itself documents that concurrent writes can shift offset pages.

Priorities:

1. Measure SQL query plans, search latency and memory on deterministic datasets.
2. Define a coherent generation/snapshot and durable scan checkpoint.
3. Move root/account scoping into queries where supported by the identity model.
4. Preserve substring semantics. Replacing LIKE with token FTS alone would change searches such as `port` matching `report`.
5. Test rename, deletion, interrupted reconciliation and permission loss without silently retaining misleading results or erasing inaccessible entries.

Evidence: [index implementation](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/search/RoomSearchIndex.kt), [schema and queries](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/database/room/SearchIndexTable.kt).

### Benchmark quality comes first

The benchmark module exists, but packaging it is not executing it. `FolderScrollBenchmark` optionally clicks a text match for Files and returns from the measurement when no scrollable object exists. It can therefore complete without measuring the intended folder interaction.

Require a deterministic fixture destination, exact UI target, confirmed item count and failure on unmet setup conditions. Start with generated 100/1,000/10,000-entry fixtures; stage the 100,000-entry case after storage/runtime budgets are understood. Keep these fixtures separate from user files.

The current startup benchmark has five iterations. That does not establish reliable p95/p99 tail behavior for the new specification. Define sample counts, warm/cold conditions, compilation mode, thermal state and device tier, and report uncertainty. The proposed 350/900/300 ms goals remain provisional.

Evidence: [scroll benchmark](../../benchmark/src/main/kotlin/com/devbehindyou/atomicfilemanager/benchmark/FolderScrollBenchmark.kt), [startup benchmark](../../benchmark/src/main/kotlin/com/devbehindyou/atomicfilemanager/benchmark/StartupBenchmark.kt).

## Cloud: capability before logos

`LinkedFolders` explicitly delegates authentication to other installed apps and stores access through Android's persisted tree grants. It is not a direct cloud account manager. Some providers expose individual documents but no selectable folder tree; neither every installed cloud app nor every account is guaranteed to appear. [Android SAF documentation](https://developer.android.com/guide/topics/providers/document-provider).

| Proposed group | Atomic status | Next gate |
| --- | --- | --- |
| System document providers | Linked-folder integration exists | Test actual installed provider capabilities and revoked grants |
| SFTP, SMB, FTP/FTPS, WebDAV | Backend implementations exist | Retain source-specific authentication and expand integration evidence |
| Google Drive, Dropbox, OneDrive | No direct account connectors found | Account/auth model, registrations, scopes, pagination, cache and contract tests |
| MEGA | No direct connector found | Separate SDK, native packaging, licence and size evaluation |
| Box, pCloud, S3/B2 | No direct connectors found | Provider-specific feasibility and operations audit |
| Other screenshot brands | No matching direct connectors found | Research official API eligibility individually; status is unassessed, not “unsupported” |

A WebDAV-compatible service may be reachable through the generic backend, but that does not prove a complete branded integration.

The proposed first wave is too broad. Start with system-provider UX and **one** direct connector, then validate shared contracts with a second. Keep MEGA behind its own feasibility gate: its official SDK is a C++ SDK and requires Android-specific integration work. [Official MEGA SDK](https://github.com/meganz/sdk).

Google Drive needs a product decision about scope: `drive.file` provides selected/app-authorized file access, not unrestricted full-drive browsing. Broader scopes carry different verification obligations. Do not promise a full-drive manager using narrow per-file access. Native Google Docs/Sheets/Slides also need export semantics rather than assuming every item is a downloadable byte stream. [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth).

Extend current Kotlin contracts with explicit provider/account identity, revision, availability, pagination/completion state and optional range/delta capabilities. Do not build remote identity from display paths. Add migration handling for persisted IDs, recents, favourites and journals rather than replacing their format indiscriminately.

Provider-native pagination must be distinct from local chunk emission. Continuation state and exhausted/error status need an explicit contract; a short or empty page is not universally the end of a listing. [Drive listing contract](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/list).

### Curated Atomic picker

Use the screenshot's recognizable logos and clear names, but avoid reproducing its very long undifferentiated list. The crop does not show a title/search/header, so their absence from the original app cannot be established.

Recommended Atomic presentation:

- “Add storage” sheet with search and Cloud / Device apps / Network grouping.
- Compact, accessible provider cards following the existing bottom-sheet icon style; adaptive columns and bounded scrolling for long lists.
- Show connection method, actual support state and available action. “Via installed app” must be distinct from direct “Connect”.
- Connected accounts appear separately with account label and health, and allow adding another account.
- Unsupported/unshipped integrations never receive an active Connect button.
- Active account becomes a Files storage shortcut; transfers appear in the existing Tasks surface.

Do not restore per-file overflow boxes from the document's generic wireframe: the user explicitly removed them. Preserve compact top popups and reserve icon grids for the appropriate bottom action menus.

## File opening and viewers

The highest-value functional addition is a persistent open router and **Settings → File opening**. Build it around the existing URI exporter and previews:

- Explicit precedence for extension overrides, exact MIME rules, category rules and global policy.
- Stable external component selection, missing-handler recovery, once/always consent and reset.
- Separate viewing from editing grants and never imply automatic write-back of exported copies.
- Unsupported formats retain a useful details/external fallback.

The current external exporter stages remote files up to **64 MiB**; media preview staging is capped at **512 MiB**. Those bounds prevent unbounded work but limit large remote-file interoperability. Replace them only with a designed streaming or user-requested download path, progress, storage budget and cancellation.

Viewer priorities should remain feature-specific:

| Viewer | Useful current behavior | Material proposed additions |
| --- | --- | --- |
| PDF | Bounded page render, jump, zoom, retry | Engine evaluation for text/search, encryption, bookmarks and later forms/annotation |
| Image | Gallery, zoom/pan, EXIF handling, bounded decoding | Extreme-resolution tiles, tested format matrix, safe optional editing |
| Media | Media3 playback/seek/speed and resource lifecycle | Durable resume, tracks/subtitles, PiP and separate background-audio behavior |
| DOCX | Read-only text, structure, selection/find | Explicit fidelity boundary; full Office rendering/editing is a separate project |
| Code/text | Line numbers, find/replace, staged saves, recreation-safe draft | Process-death recovery, large-buffer strategy and measured incremental highlighting |
| Archive | Existing browse/extract/compress safety work | Test any added engine/format against expansion, path/link and interruption limits |

Keep the lightweight basic viewers available while assessing heavier optional engines. “Advanced” needs a tested feature list, not a new label.

Evidence: [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md), [external exporter](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/preview/ExternalPreviewHelper.kt).

## Optional modules: revise lifecycle promises

For a Play distribution, feature modules are approved parts of the application's bundle. They share the app version/signing configuration; a remote catalogue does not turn them into independently versioned executable plugins. Model updates and rollback around compatible app releases, with truthful pending/removal states. [Play Feature Delivery](https://developer.android.com/guide/playcore/feature-delivery).

Installation/removal capabilities must map to the real delivery API. Deferred install requests are best effort and do not expose download progress; don't invent pause/progress or immediate-uninstall guarantees. [On-demand delivery](https://developer.android.com/guide/playcore/feature-delivery/on-demand).

Play policy restricts executable downloads outside its update mechanism. Signing a DEX/JAR/native library hosted on Drive or GitHub does not itself make that a permitted Play feature-delivery path. [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/16559646).

Architectural distinction: constraining the file handle passed to an in-process feature is good API design, but is not a security sandbox against hostile code running with the host's privileges. Treat bundled/dynamic features as trusted application code. Untrusted third-party extensions would require a different isolation and distribution design.

At 7.41 MB for the current release APK, size alone does not justify splitting every existing lightweight viewer. Measure dependency/resource contributions and compare fresh-install size, first-open latency and all-features installed size. A larger future PDF engine or cloud SDK may justify extraction; architecture boundaries can be introduced before delivery changes.

## Revised sequence and exit gates

| Order | Increment | Evidence required |
| --- | --- | --- |
| 1 | Repair benchmark setup and record current baseline | Deterministic folders, valid measurements, traces, artifact/device metadata |
| 2 | Optimize proven listing/index hotspots | Before/after timing and allocations; unchanged search/selection semantics |
| 3 | Open router and default-app settings | Once/always/reset/uninstalled-handler and URI grant tests |
| 4 | Account model, Add storage UX and one direct connector | Real sign-in, paginated listing, isolated credentials/cache, reconnect and transfer tests |
| 5 | Highest-value viewer gaps | Per-feature format/error/lifecycle acceptance, retained external fallback |
| 6 | Optional heavy module pilot | Distribution decision, measured size benefit, genuine install/unavailable/update behavior |
| 7 | Additional providers and release hardening | Provider contract suites, process-kill/recovery, hardware/performance and production signing gates |

Use existing audit, architecture and test reports as the baseline, linking new evidence rather than maintaining duplicate contradictory documents. Preserve the existing broad modernization backlog. Its roughly 78% weighted progress must not be reused as completion of this larger specification.

The user has resolved feature delivery in favour of bundled features and an in-app inventory. Replace step 6 above with improvements to bundled viewers and measured base-size checks; no optional installer is planned. Decisions still needed before their dependent implementation: release distribution; first direct provider and access scope; basic versus advanced viewer capabilities; cloud offline retention and disconnect behavior. Developer-console registrations, redirect/signing identities and test accounts will be needed for real authentication acceptance. None prevents an initial benchmark-harness improvement.

The next smallest implementation slice is **a trustworthy folder benchmark and baseline**, followed by changes justified by its measurements. No new speed, cloud-support or release-readiness claim is made by this assessment.
