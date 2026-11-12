# Plugin progress — frozen capability ledger

**Reconciled 29 October 2026:** [plugin-by-plugin state and candidate queue](../../roadmap/PLAN_RECONCILIATION_2026-10-29.md). PR88 reliability slices are accepted and six further candidates are prepared; complete-group credit remains 0/42 host / 0/42 accepted. Audit coverage is 8/8, not modernization completion.

26 October 2026: **fully modernized plugins 0/8 = 0%**. Existing accepted inventory/version v0.2 (#81) is baseline context, not modernization credit. Existing feature plan remains 15% host (2/13), 8% accepted (1/13).

Parent-reviewed fixed denominator **N=42** capability groups in PLUGIN_FEATURE_MATRIX: Image 6, Video 6, Audio 5, PDF 5, Editor 6, Markdown 5, Document 4, Archive 5. **Host-verified 0/42 = 0%; fully accepted 0/42 = 0%.** Source audit 8/8 coverage is only stage coverage. The parent reviewed the 42 rows against the full intake and froze this denominator on 26 October 2026; all nested requirements remain authoritative.

Host credit requires complete group behavior plus applicable actual consumers/static/artifacts on named source. Accepted credit additionally requires applicable device/provider/format/security/performance/review/CI/dependency/clean-merge gates. Smaller PRs earn no whole-group credit until all group criteria pass; report increments separately. Shared foundations are prerequisites, not extra numerator. Whole plugin numerator only when all its groups and end-to-end journey pass. Core unsupported work remains open; conditional/P2 decisions must be explicit and evidence based. Future user scope changes require logged before/after denominator, never silent reweighting.

Next Image transform geometry is only part of IMG1; fit/original controls/restoration/physical touch remain before full credit. No plan/research/document/version credit. Feature integration is distinct from main/release.

29 October: scoped Editor history PR90 is accepted into feature integration with43 host cases, static/APKs/Lint/full CI and API34 emulator/phone evidence. Markdown source/retry/disclosure has16 passing host cases and corrected fixture packaging; combined/native acceptance remains pending. These increments do not close full EDT/MD groups: fixed credit remains0/42 host groups,0/42 accepted groups,0/8 whole plugins.

## 30 October partial increment

GIF repair [PR #101](https://github.com/DevBehindYou/Atomic-File-Manager/pull/101) is under native compatibility validation. Source60031fa passes normal CI; API34 looping/control/lifecycle/disposal and6 static cases pass, finite capture fails; API28 native heap-buffer read aborts. A direct-buffer repair is being validated. These are partial IMG2/IMG6 results, not complete groups. Fixed credit remains **0/42 host,0/42 accepted,0/8 fully modernized**; feature-plan credit remains **15% host /8% accepted**.

## 5 November accepted GIF slice and Markdown continuation

GIF [PR #101](https://github.com/DevBehindYou/Atomic-File-Manager/pull/101), tested source `fb856a6dac87ede7b6fbed58697a0be5e1b4a6b8`, is accepted into feature integration as `33152a5bc92e38bc34a2e72a962e61405da55b56`. Normal CI `41064609636` and native CI `41064609699` pass, including eight cases on each of API28/API34. Compatible local artifact validation completes in 12m13s, exit 0; the physical API34 phone passes all eight GIF/static/source-integrity cases in 13.586s, exit 0. Exact source/app-tree, APK/signing identities, retained failure history and device boundaries belong to [GIF evidence](../../testing/GIF_PLAYBACK_CANDIDATE.md). This accepts the bounded playback/ownership repair, not complete IMG2/IMG6 or Image modernization.

Markdown #89 now includes the production workflow triggers. Combined source `560db57` failed its first native Raw-control selector after recovered content/source-integrity checks; both selectors are repaired at `eecca49` to match the existing accessible label. That fixture repair and this dependency integration require current combined CI/native acceptance; no new Markdown runtime pass is claimed. Editor's accepted scoped history and both prior partial records are preserved above. **Fixed credit remains 0/42 host, 0/42 accepted, 0/8 fully modernized; feature credit remains 15% host / 8% accepted.** All full capability groups remain open.

## 6 November accepted slices

Feature integration reached `76eff0c34822885fd18e945e39f04b818a83b4bf` after four scoped merges. Static checks, APK packaging, host tests, the listed native tests, review and clean merge trials passed. Host totals below count app tests. Each PR also passed 28 Lint rule tests. All host reports show zero failures and zero skipped.

| Slice | Tested PR head | Integration merge | Validation |
| --- | --- | --- | --- |
| Markdown retry, revision and Raw disclosure | `5e7476b` | `bf2f1ae` | 1,315 host cases. One native case passed on the API 34 emulator and on the physical API 34 phone in 9.466 s. [PR #89](https://github.com/DevBehindYou/Atomic-File-Manager/pull/89) · [Validation](../../testing/MARKDOWN_OWNERSHIP_CANDIDATE.md) |
| PDF source session ownership | `152eef2` | `956839c` | 1,313 host cases and three native cases on each API 28 and 34 emulator. [PR #91](https://github.com/DevBehindYou/Atomic-File-Manager/pull/91) · [Validation](../../testing/PDF_SOURCE_SESSION_CANDIDATE.md) |
| Archive path uniqueness and cancellation | `c0d287a` | `ac668e5` | 1,314 host cases and one Android FILE inspection case on each API 28 and 34 emulator. [PR #92](https://github.com/DevBehindYou/Atomic-File-Manager/pull/92) · [Validation](../../testing/ARCHIVE_INSPECTION_CANDIDATE.md) |
| DOCX source sessions and preview routing | `e8e61b6` | `76eff0c` | 1,315 host cases and one FILE routing and Find case on each API 28 and 34 emulator. [PR #93](https://github.com/DevBehindYou/Atomic-File-Manager/pull/93) · [Validation](../../testing/DOCUMENT_SOURCE_SESSION_CANDIDATE.md) |

The fixed counts remain **0/42 host-complete groups, 0/42 accepted groups and 0/8 fully modernized plugins**. Feature-plan credit remains **2/13 host verified (15%) and 1/13 fully accepted (8%)**. Wider capability, provider and release gates remain open.

## 10 November scoped work

The [EXIF and Details revision fix](https://github.com/DevBehindYou/Atomic-File-Manager/pull/108) merged into feature integration as `e10effe` after focused host and current hosted checks. The [Editor stale-recovery guard](https://github.com/DevBehindYou/Atomic-File-Manager/pull/126) passed hosted checks on `c6533c5`. Editor recovery and revision-aware publication remain a stack pending save-conflict acceptance. [Video resume](https://github.com/DevBehindYou/Atomic-File-Manager/pull/119) passed hosted native API 28/34 and static checks on `0a2cec2`, with two unknown-revision reopen timeouts in its full host suite. The [Audio focus journey](https://github.com/DevBehindYou/Atomic-File-Manager/pull/109) has six original native passes and awaits checks on its refreshed integration candidate.

Fixed plugin credit remains **0/42 host-complete groups, 0/42 accepted groups and 0/8 fully modernized plugins**. Stable v0.2.1 acceptance is open.

On 12 November, the folder-thumbnail concurrency change merged into feature integration after current hosted checks and review. It covers the Browse thumbnail path adjacent to the Image viewer. The fixed full-group and plugin counts remain 0/42 and 0/8. [PR #116](https://github.com/DevBehindYou/Atomic-File-Manager/pull/116)
