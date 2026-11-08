# Pending work and exit gates

> **7 November 2026 reconciliation (Claude Code, C001):** TASK-101, TASK-102 and the PDF/DOCX/Archive parts of TASK-103 are superseded by accepted integration merges #101 `33152a5`, #89 `bf2f1ae`, #91 `956839c`, #92 `ac668e5` and #93 `76eff0c`. The image-fit candidate is superseded by Codex-owned PR #103. TASK-104 to TASK-107 remain open. The entries below are kept as dated history. See [C001 reconciliation](../dictionary/coordinator/C001_RECONCILIATION.md).

Updated **4 November 2026**. This handover does not start implementation. Resume the following sequence when requested. Scope and fixed denominators remain in the feature and plugin ledgers; these tasks are continuation slices, not new percentage units.

## TASK-101 - Accept the current GIF repair

Status: PARTIAL. PR #101 at `fb856a6` has passing host/static CI and eight native cases on each of API28 and API34. Preserve those results. Complete the current-repair physical-phone retest using a compatible, traceable APK and owned fixtures; retain source/app-tree, artifact hashes, logs and exit status. The interrupted local full build is not a pass. Resolve reviews, check dependencies, and trial merge against the freshly fetched integration head before merging. Update Image records without awarding an entire N42 group for this slice. See [TESTING](TESTING.md) and [ISSUES](ISSUES.md).

## TASK-102 - Finish the existing Markdown candidate

Status: PARTIAL. Continue PR #89 at `7d54483`; do not open a duplicate. Add missing production paths to `.github/workflows/markdown-ownership-native.yml`:

- `app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/MarkdownPreviewContent.kt`
- `app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/MarkdownBlocks.kt`
- `app/src/main/kotlin/com/devbehindyou/atomicfilemanager/domain/usecase/ReadFileContentUseCase.kt`
- `app/src/main/res/values/markdown_preview_strings.xml`

The recovered checkout is detached and clean; the proposed trigger change was never applied. Validate workflow syntax and current combined source after accepted dependencies are integrated. Run affected Markdown/opening/Editor host tests, static checks, packaging/Lint and ownership native acceptance. Current static CI passes; cancelled full/native jobs leave acceptance open. GIF/Markdown integration has a progress-ledger conflict: preserve both partial records and zero full-group credit rather than discarding either entry.

## TASK-103 - Reconcile prepared plugin candidates

Status: IMPLEMENTED_UNTESTED for the newer local candidates. Recover PDF `579d1be`, Document `eb2d15a`, Archive `61af2e8` and image-fit `c05d5d6` by their retained refs, after checking ownership/worktrees. Published PR #91/#93 unit/APK checks fail; PR #92 green checks apply to its older published head only. Rebase or merge dependencies without force-pushing shared branches, retarget the existing #91-93 PRs to the accepted base as appropriate, and obtain exact-current-source tests. Resolve image-fit/GIF source overlap deliberately. Validate actual format, cancellation, source-ownership and error journeys; do not substitute advertised extensions for functioning support.

## TASK-104 - Finish all eight-plugin requirements

Status: PARTIAL. Maintain per-plugin feature/task/bug/validation records and About version v0.2. Complete the fixed N42 groups: Image 6, Video 6, Audio 5, PDF 5, Editor 6, Markdown 5, Document 4, Archive 5. Current credit remains **0/42 host-complete, 0/42 accepted, 0/8 fully modernized**; all eight have source audits and multiple partial implementations. Finish functional, provider, error, integrity, accessibility and resource acceptance in each group. Consult current official sources for new dependency/competitor decisions; do not repeat completed audits without new evidence.

## TASK-105 - Finish original feature and optimization gates

Status: PARTIAL. Feature credit remains **2/13 host-verified (15%), 1/13 fully accepted (8%)**. The original plan owns all U01-U13 requirements, including mount/revocation/recreation, generation-aware freshness, formats/providers, recovery and release acceptance. U02 measured benchmarking is explicitly deferred; U03 measured optimization depends on valid measurements. Preserve outstanding baseline-profile, SMB/mDNS, Wi-Fi sharing, USB/FAT/exFAT/unplug, real process-death, large-file, resumable-network, APK-size and throughput coverage where required. Historical large-corpus recipes are not authorization to run them now. No permission changes or root probes.

## TASK-106 - Cloud Connect, separately tracked

Status: PROPOSED implementation; research/planning is retained. Cloud Connect is a ninth bundled plugin, excluded from N42. API28 prerequisite is integrated. Finish independent source/contracts and official provider research; first provider, scopes, app registration and account evidence still require resolution. Request only the specific owner input needed when that dependency is reached. SAF-linked folders are not direct cloud-account acceptance.

## TASK-107 - Stable v0.2.1 release

Status: NOT_IMPLEMENTED release acceptance. Reconcile the old PR stack individually; assess diagnostic #100 without treating it as a proved SQLite fix. Complete applicable R8/runtime, provider, resource, policy and signing gates; promote the tested source to main and publish only when accepted. Increment the version code using the existing dedicated signing identity. Preserve immutable `v0.2.0-beta.1` and private backups. Integration merges alone do not complete stable release.
