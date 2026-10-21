# Feature upgrade execution progress

Updated 21 October 2026. Scope: [FEATURE_UPGRADE_PLAN](FEATURE_UPGRADE_PLAN.md), U01–U13. This ledger is separate from the older modernization roadmap's 78% implementation figure.

## Fixed progress calculation

Report two figures using 13 equally weighted milestones. Host-verified delivery = milestones whose complete planned implementation and applicable host checks passed / 13. Accepted delivery = milestones with their required device/provider evidence, resolved review concerns and merged PR / 13. Partial slices do not count as a complete milestone. Round percentages to the nearest whole percent. These are coarse milestone indicators, not effort or release-readiness estimates.

Current: **8% host verified (1/13), 8% accepted (1/13)**. U01 passed its focused host checks, full GitHub CI, physical API34 surface flow, independent review and clean merge. Do not credit U02/U04/U06 merely for partial slices, work starting or PR creation.

| Milestone | Current state | Evidence / next gate |
| --- | --- | --- |
| U01 Bundled plugins | Accepted into feature integration branch | [PR #61](https://github.com/DevBehindYou/Atomic-File-Manager/pull/61), tested head `88bb8d0`, merge `742d473`; physical API34 flow passed; main/release acceptance remains separate |
| U02 Benchmark correctness/baseline | Harness APK builds; 2 non-mutating API34 contract cases pass | Branch `codex/benchmark-fixtures`; real baseline and trace boundaries remain required |
| U03 Listing responsiveness | Planned | Requires U02 measurements |
| U04 Startup access/ownership | Storage-state slice merged; host/static/Lint, CI and granted-phone surface pass | [PR #62](https://github.com/DevBehindYou/Atomic-File-Manager/pull/62), merge `0854185`; broad tab ownership and conditional device cases remain separate |
| U05 Index/cache consistency | Planned | Baseline/schema and restart tests |
| U06 Opening defaults | Routed preference slice integrated for host validation | Branch `codex/file-opening-defaults`; verify actual scope before credit |
| U07 Storage-account contracts | Planned | Preserve real system-provider/network behavior |
| U08 First direct cloud connector | Planned | Provider/scope registration and real test account |
| U09 Media/images | Planned | Bundled feature improvements and hardware acceptance |
| U10 PDF/documents | Planned | Engine/capability evaluation |
| U11 Editor/archives | Planned | Recovery and mutation invariants |
| U12 Second connector/network | Planned | Shared contracts and actual provider/hardware tests |
| U13 Recovery/release acceptance | Open | Process-kill, performance, exact-head CI and owner signing |

## Execution and merge authorization

The user explicitly requested parallel agents, focused PRs, automatic continuation and regular percentage reports on 21 October. They authorize automatic merges when the work and tests are done and the merge has no conflicts. This supersedes the older blanket prohibition on automatic merges; it does not waive missing test evidence, data integrity, protected branch requirements or unresolved owner decisions.

Use isolated worktrees and serialize local Gradle on the 8 GB Windows host. Preserve the user-owned `docs/design/icon-variations/` and ignored build tools/signing backups. Never force-push shared branches.

New focused PRs target `codex/feature-upgrade-base`, a preserved source snapshot at `89b1d45`, until dependent changes land. This integration branch is separate from `main`. Report its merges as integration merges; do not claim the prior modernization PR stack is accepted or merged into main.

Before each merge: check the exact PR head, applicable tests/CI/review, dependency state and a clean trial merge onto the current target. Do not bypass failing/missing required checks. Retain conditional hardware/provider gates where the implementation needs them. The 17 older draft PRs require their own evidence; no bulk merge is implied.

Automatic continuation: active thread heartbeat `continue-atomic-implementation`, every 30 minutes. It resumes unblocked plan work, records evidence and reports meaningful percentage changes. Avoid duplicate work or repeated unchanged blockers.

## Batch 1

- Preserved the previously validated bundled-plugin source and upgrade plan as `12f007d`.
- Published and attached PR #61. Initial full CI caught a cancelled-job teardown race; fixed by joining the ViewModel job before resetMain. New-head CI passed static analysis and the full unit/APK job. Physical API34 plugin navigation and independent review passed. A clean trial merge preceded automatic merge `742d473` into `codex/feature-upgrade-base`.
- Started U02, U04 and U06 implementations in parallel worktrees. Local Gradle is not run concurrently.
- ADB sees the authorized API34 phone; U01 Settings/list/PDF-details/Back/archive-details/Close surface check passed, then the app returned Home. No real user-file mutation was performed.
- PR #62 merged the startup-readiness slice after 13 focused host tests, static analysis, Lint, current-head CI, independent review, a granted-phone surface flow and clean trial merge. Broader U04 ownership and revocation/mount/recreation checks remain open.
- The deterministic benchmark APK compiled after explicitly declaring its existing AndroidX test runner dependency. Both non-mutating contract cases passed on API34; no corpus baseline or speed improvement is claimed.
- File-opening policy, persisted rules, actual external handler selection and displayed-gallery identity changes are integrated for serialized host validation. No whole U06 acceptance is claimed yet.
