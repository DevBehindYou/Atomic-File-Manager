# Plan reconciliation — 29 October 2026

Status: review checkpoint; implementation remains paused for this reconciliation. This document supersedes stale current-status summaries below the linked historical records, without rewriting their dated evidence. It awards no new implementation or acceptance credit.

## Sources and method

Compared the original supplied COMPLETE PROJECT AUDIT, ARCHITECTURE MODERNIZATION & OPTIMIZATION request (attachment 5afa3502-32d9-48a6-83f1-542aef06366e), [original weighted roadmap](OPTIMIZATION_ROADMAP.md), [implementation ledger](IMPLEMENTATION_PROGRESS.md), [expanded feature plan](FEATURE_UPGRADE_PLAN.md), [feature evidence ledger](FEATURE_UPGRADE_PROGRESS.md), [plugin intake](ADVANCED_BUILT_IN_PLUGINS_INTAKE.md), [42-group matrix](../plugins/modernization/PLUGIN_FEATURE_MATRIX.md), and preserved validation records against live Git/GitHub state and selected source anchors. This is an evidence reconciliation, not a fresh exhaustive code/security audit or a new test run. Existing official research is recorded; no provider/dependency recommendations have been revalidated today.

Read-only checks on 29 October found:

- Root branch codex/beta-0.2.0 at 02605684b2b0bab41f2546f69b859c542a375ac6; no tracked changes; untracked docs/design/icon-variations/ is preserved user artwork.
- Remote feature integration: 3fb922d2afde773f9e1060602a3eb2a781f07b9f. Remote main: 7782660972148ed4b2db8f5a43a402995a5d730c. They are different delivery states.
- Public [0.2.0-beta.1](https://github.com/DevBehindYou/Atomic-File-Manager/releases/tag/v0.2.0-beta.1) is a published prerelease, source 02605684, published 28 October at 23:16 IST. Its tag/assets must remain immutable.
- 24 open PRs, all drafts: #44–60 (17), #63, #89–93 (5), and #98. These are not 24 independent missing product features.
- No previously running collaboration agents and no Java process appeared in the initial inspection. Only a read-only plugin reconciliation agent was started for this review. No Gradle, ADB, CI dispatch, merge or release action was run.

## Percentages: four different scopes

| Scope | Verified interpretation | Remaining / caveat |
| --- | --- | --- |
| Original M01–M13 modernization | Recorded implementation coverage 47/60 weighted units = 78%; M01–M11 implemented | Historical implementation measure, not a newly revalidated acceptance or speed figure. M12/M13 partial; older review PRs remain open. |
| Expanded U01–U13 feature plan | 2/13 host complete = 15%; 1/13 fully accepted = 8% | U01 accepted; U04 host complete but broader device matrix open. Partial slices do not earn full milestone credit. |
| Eight-plugin source audit | 8/8 audited | Audit coverage only; existing baseline features are real and remain useful. |
| Eight-plugin modernization | 0/42 complete groups host verified; 0/42 accepted; 0/8 fully modernized = 0% | Several fixes accepted and six prepared candidates exist, but no complete capability group meets all its gates. |
| Cloud Connect | Separate ninth-plugin research/plan accepted through #95 | No implemented/accepted connector. No invented percentage and no change to N=42. |

Do not average these figures, use PR/test counts as completion, or describe 0% modernization as eight absent/nonfunctional plugins. The fixed groups are large; report the concrete slices below alongside the percentage.

## Original request compared with actual work

| Original phase | Work completed or evidenced | What remains |
| --- | --- | --- |
| 1 Discovery and 2 architecture audit | Source inventory, architecture/security findings, root causes and prioritized M01–M13 roadmap; eight-plugin source audit | Refresh summaries that still call old checkpoints current; re-audit changed areas as each increment lands. |
| 3 Performance/resource optimization | IO dispatch; throttled operation progress; hidden/retired Browse ownership; on-demand Analysis; bounded preview work; deterministic benchmark harness/provider repair | Comparable cold/warm startup, first/full listing, navigation, search, memory, idle CPU, throughput and frame measurements. No accepted timing baseline or measured speed-up. |
| 4 Target architecture | Native Kotlin/Compose retained; existing storage IDs/capabilities, operation queue, persistence and preview boundaries reused; architecture decisions documented | Durable scanning/freshness, complete recovery phases, cloud account/paging/token boundaries; measure before further module/framework restructuring. |
| 5 Roadmap | Weighted M plan, U01–U13 feature plan, eight-plugin intake/matrix and separate Cloud Connect plan exist | Keep one current reconciliation entry point and exact acceptance gates; avoid accumulating contradictory current summaries. |
| 6 Implementation | Integrity, browsing, opening, UI and release slices described below | Finish candidate validation, remaining feature behavior and complete acceptance matrices. |
| 7 Parallel PR workflow | Isolated branches and focused integration PRs used | Reconcile the 17 older PRs against integrated code before deciding merge/supersession. Five plugin PR heads lag prepared local branches and still target the older media branch. |
| 8 Tests and quality | Strong scoped host/native regressions, exact-artifact beta checks and preserved failed baselines | Full provider/process-death/format/accessibility/performance journeys; fix invalid controls before treating baseline failures as product evidence. |
| 9 Progress and handover | Detailed evidence logs and per-plugin v0.2 records exist | Headline summaries have drifted; this snapshot provides current state without deleting historical failures. |
| 10 Final review | Beta distribution gates completed for the frozen APK | Final whole-plan architecture/performance/release acceptance has not occurred. |

## Original weighted optimization tasks

| Tasks | Implemented work | Acceptance still to close |
| --- | --- | --- |
| M01 | Source-linked audit, architecture and execution documents | Maintain freshness; audit creation itself is complete. |
| M02 | Staged archive output, collision/failure handling, opaque child identities and recursive-destination guards | Broader formats, hostile archives, provider failures and interruption/recovery journeys. |
| M03 | Journal admission before filesystem mutation and ordered terminal-state handling | Real process-death reconciliation at publication/source-delete boundaries and cross-provider recovery. |
| M04 | Exclusive upload staging/no-clobber publication and bounded connections | Live sharing/concurrency/resource acceptance across supported devices. |
| M05 | Secondary/primary Browse ownership, stopped/hidden pane pausing, tab sessions, accepted work drain and store retirement | Current permission revocation/return and mount/unmount/recreation matrix. |
| M06 | Metadata/listing dispatch to IO | Broad provider behavior and measured responsiveness. |
| M07 | Throttled progress with exact initial/final boundaries | Comparable end-to-end resource/throughput evidence; preserve cancellation/totals. |
| M08 | Trash reconciliation preserves reachable data after cancellation/restore failure | Actual process-death and provider recovery. |
| M09 | Opaque SAF ancestry guards and actual-ID breadcrumbs/navigation | Real SAF/SD/USB/network identity/revocation/disconnect acceptance. |
| M10 | Search category filtering before cap; later atomic folder reconciliation | Measured latency, durable scan generations/checkpoints and reliable freshness. |
| M11 | FTPS TLS/control/data/error handling; bounded DAV XML and conditional writes | Real server/provider matrix; successful protocol fixtures are narrower evidence. |
| M12 | Wrapper checksum, preference IO/startup improvements, loading cleanup and storage adapter fixes | Remaining demonstrated architecture/dependency/diagnostic hygiene; no speculative framework rewrite. |
| M13 | Dedicated signing, R8 packaging and signed API28/API34 startup completed for beta | Full recovery/provider/accessibility/stress/performance and main/stable acceptance. |

## Expanded feature ledger: current disposition

| ID | Done / current state | Remaining next gate |
| --- | --- | --- |
| U01 | Eight bundled plugin entries, About/details v0.2, limitations and maintained records accepted (#61/#81) | Retain accuracy as functionality grows; no need to rebuild inventory. |
| U02 | Harness/provider repair, packaging and historical head CI; #63 draft | Owner deferred benchmarking. Both measured smoke scenarios remain unaccepted; no automatic root probe or permission change. |
| U03 | Listing optimization planned; current streaming/virtualization already exists | Measure first, then bounded publication/partial-state work with ordering/cancellation/selection correctness. |
| U04 | Startup access state, first-draw black-screen repair, pane/tab/work ownership host complete (#62/#66/#70–78) | Revocation/return, mount/unmount and recreation acceptance. |
| U05 | Atomic complete-folder index reconciliation (#67); lightweight Storage and explicit Analysis (#69) | Durable scan checkpoints/generations, interruption recovery, cache freshness, migration/memory/query evidence. |
| U06 | Once/Always/default routing, MIME normalization, missing-handler contracts, remote progress/Cancel, revision refresh and bounded selected PNG detection (#64/#79/#83–85/#87) | Real provider/grant-loss/remote and wider format/recovery/performance matrix. #87 is merged, despite stale header saying pending. |
| U07 | Storage-account contract needs identified; current system-provider/network access preserved | Account-qualified IDs, paging/completion, capability/error/token/cache isolation; useful mock contracts before credentials. |
| U08 | First direct cloud provider research recorded | Scope/provider selection, registration, native auth, browse/download/mutations and sandbox acceptance. |
| U09 | Image retry/revision/gesture repairs; Video retry; Audio retry/shared focus/noisy policy (#80/#82/#88) | Fit modes candidate and full media controls/format/interruption/restoration/accessibility/performance coverage. |
| U10 | Existing PDF raster and DOCX text readers; session-reliability candidates #91/#93 | Candidate validation, engine decisions and requested text/format/fidelity features. |
| U11 | Existing bounded editor/staged saves/archive integrity; candidates #90/#92 | History consumer validation, durable drafts/conflicts, large-file strategy and full archive safety/format/recovery gates. |
| U12 | Existing network implementations, no second direct cloud connector | Second service contract reuse and live network/USB/provider acceptance. |
| U13 | Beta signing/distribution and exact signed startup accepted | Broader recovery/release matrix remains open; beta publication does not close U13. |

Additional requested UI work is already present with scoped evidence: thumbnails/folder miniatures; file-family colours; long-press actions without redundant per-row options; Tasks popups; Files storage shortcuts, List/Grid, filtering/hidden/new-folder controls; explicit ascending/descending sorts; full-row folder title/right metadata; compact popup headings and bottom icon-card menus; Analysis-only scans and centered action. Seven transfer bubbles with transactional capacity admission and a scrollable compact rail shipped in #97. Do not restart these completed slices absent a new defect.

## Plugin work inventory

| Plugin | Existing/shipped foundation | Prepared or incomplete next work | Larger remaining scope |
| --- | --- | --- | --- |
| Image | Sampled gallery/EXIF, source/retry recovery, focused pinch/double-tap/pan (#80/#88) | Unpublished fit screen/width/1:1-preview-pixel modes and restoration candidate | Animation/format fidelity, metadata/actions, nondestructive edits, high-resolution strategy, full journeys. |
| Video | Media3 preview controls, typed preparation retry, focus/noisy handling (#82/#88) | Broad decoded-playback/interruption acceptance still open | Subtitle/audio-track controls, resume/fullscreen/gestures, codec matrix, optional PiP/background policy. |
| Audio | Preview/seek/speed/artwork, revision-owned retry and shared focus (#88) | No complete AUD group accepted | Format/seek corpus, folder queue/metadata, intentional background/service/headset/call behavior, safe tags. |
| PDF | Bounded raster rendering/page navigation/zoom/retry | Source/helper session replacement #91 | Native large/password/scanned fixtures, text/search/links, richer modes/annotations/engine evaluation. |
| Editor | Bounded 2 MiB editing, encoding/BOM/endings, find/replace and staged saves | Bounded Undo/Redo #90; baseline has 3 failures including its supposed control | Durable draft/revision conflicts, broader find/regex/language functions, paged/read-only large files and provider/process-death integrity. |
| Markdown | Native bounded line/fence preview and raw mode | Revision/source ownership and bound disclosure #89 | CommonMark/GFM conformance, safe relative assets/links, TOC/search/restoration, edit/export integration. |
| Document | Bounded DOCX structured text/search and limitations | DOCX session replacement #93 | Real fidelity tables/images, other formats/engine selection, sheets/slides/EPUB journeys. |
| Archive | Existing inspection, ZIP creation, staged extraction, bounds and integrity guards | Duplicate browse-key rejection/cancellation #92 | Format/encryption/multipart capability matrix, selected-entry controls and hostile-input/provider/recovery/performance gates. |

All eight remain bundled/native. Plugin v0.2 is metadata, not evidence every requested feature exists. Cloud Connect is a separate ninth plugin: #95 accepted source/API research and plan, API28 prerequisite shipped via #96; no SDK/provider integration is implemented. CC02 identity/paging contract work can precede credentials; auth, token refresh, account isolation, bounded transfer/conflicts and real-provider acceptance remain.

## Candidate and PR queue: preserve, do not duplicate

| Candidate | Prepared local HEAD | Remote PR / current published HEAD | Status |
| --- | --- | --- | --- |
| Markdown | e217005 | #89 / 67bcfaf | Prepared API28 dependency merge; new local head not accepted. |
| Editor | caa9ac4 | #90 / bd55d83 | Prepared; frozen baseline 515c92c ran, candidate validation open. |
| PDF | 579d1be | #91 / dd800b8 | Prepared; applicable native/session gates open. |
| Archive | 61af2e8 | #92 / 5942082 | Prepared; actual security/format consumers pending. |
| Document | eb2d15a | #93 / 09ec9e5 | Prepared; actual DOCX/session gates pending. |
| Image modes | c05d5d6 | No PR | Prepared; host/native fit/gesture/rotation gates pending. |
| Beta publication record | 0330457 | #98 / same | Documentation; CI owner-cancelled, not failed production behavior or an accepted merge. |

#89–93 still target codex/bundled-media-reliability. Before continuing them, synchronize/retarget to accepted feature integration, inspect the resulting focused diff, then validate current source; older green CI cannot validate unpushed heads. Do not publish a second PR for an existing candidate.

#44–60 are an older review stack, some targeting main and some other review branches. Their open state does not prove the fixes are missing from beta: accepted integration has inherited/evolved implementations. Before merging or closing any, reconcile content/ancestry and review concerns individually. Do not bulk merge old snapshots over newer fixes. #63 remains separately deferred.

## Exact evidence and unresolved failures

- Frozen beta source 02605684: retained beta-host-test-counts.json records 1,278 app tests across 214 suites plus 28 Lint-rule tests; zero failures/errors/skips. Signed R8 packaging/release Lint passed. Normal CI40705484240 and exact signed API28/API34 runtime CI40736625106 passed. This is strong release-artifact evidence, not full feature/provider coverage.
- APK SHA256 BBD5B14FA3B29F15B22756A3BBBCE2EEB620AA696AA4ED98F5EB419FB97572FB, 15,075,706 bytes. Preserved unauthenticated public-download verification dated 28 October 23:17 IST matches that artifact. Live release metadata was checked today; no duplicate download/build was necessary.
- Accepted #88 source7457ffe: retained bundled-media-host-artifacts.json records 224 tests/33 suites, zero failures/errors/skips; scoped native/review/CI evidence retained. Its candidate docs still understate that acceptance. Do not infer all media formats/physical interruption coverage.
- Editor frozen515c92c: 3 tests, 3 assertion failures, zero errors/skips. First two cannot find the missing explicit Undo control. The hardware-control case expected 'original' but sees 'keyboard draftz'. It is not a passing control and cannot establish a valid red/green baseline until keyboard-event/source behavior is diagnosed. Preserve original XML and fix any fixture separately without weakening assertions.
- #98 CI40740796085: static analysis succeeded; unit/APK job and overall run are explicitly CANCELLED in GitHub's detailed job API. The summary command labels it fail; cancellation is the precise classification. Fresh applicable checks are required before merge, but not during this review-only checkpoint.
- No accepted performance baseline: provider fixture success and system-compositor trace rows do not measure Atomic. Owner benchmark deferral remains authoritative.

## Documentation drift and decisions carried forward

The root FEATURE_UPGRADE_PROGRESS header calls #87 pending, CURRENT_STATE still leads with a 24 October checkpoint, implementation summaries still say signing outstanding, some plans/matrix rows still name API27, and plugin/Cloud records describe now-accepted prerequisites as pending. Dated records remain historical; their current headline interpretation is corrected here. Current floor is API28, compile/target36; no new engine dependency was adopted by that platform change. Later source audit/research supersedes the intake's original 'not researched' statement, and the owner's subsequent parallel-plugin instruction supersedes blanket deferral of that work.

Use the three fixed ledgers unchanged. Keep detailed per-plugin tasks/features/bugs/evidence; update the affected plugin record when its actual candidate is validated rather than marking a whole group complete for one fix. Preserve original failures, private signing backups, artwork, phone permissions and all existing worktrees. The permanently removed recurring automation stays removed. Public beta assets are frozen; later implementation needs a new app release version.

## Ordered continuation after review

1. Reconcile documentation and the PR queue first. Preserve #98's focused publication record; do not rerun the beta build or mutate its public tag. This audit is a separate documentation branch, with no CI or implementation restarted.
2. Diagnose Editor's failing keyboard control against frozen baseline/source. Correct fixture assumptions separately if warranted; then run the existing prepared candidate's focused consumers and applicable native save/Unicode/byte-integrity journey. Finish #90 before opening duplicate history work.
3. Use independent review/fixture preparation in parallel for Markdown, PDF, Document, Archive and Image. Execute local Gradle serially on this 8 GB host. Retarget existing PRs and validate each exact head; merge only with required green checks, resolved review, accepted dependencies and a clean target trial.
4. In parallel where independent, close existing U04/U06 provider/permission/recovery acceptance using scoped fixtures and established authorization. Implement durable U05 scanning only after defining generation/checkpoint/reconciliation behavior. Ask for a specific hardware or destructive-test authorization only when it actually becomes necessary.
5. Continue each plugin through the frozen feature matrix; prioritize original-safe save/recovery, source/session lifetime and unsupported-format honesty before larger engines or optional capabilities. Cloud starts with account/paging/token contracts; request provider/scope/registration only at its dependency point.
6. Keep measurement-dependent U02/U03 claims deferred until the owner explicitly reopens benchmarks. Once authorized, collect comparable target-app data before making latency/memory/throughput claims.
7. Finally complete cross-provider recovery, accessibility/themes/rotation/large-input journeys, architecture/dependency review and stable/main release promotion. Beta publication alone does not satisfy this exit gate.

For every next slice record behavior, source, actual checks (including failures), device/provider scope, PR/merge target and remaining group criteria. The efficient next step is validating/reconciling existing work, not expanding all eight implementations simultaneously before their present candidates are trustworthy.
