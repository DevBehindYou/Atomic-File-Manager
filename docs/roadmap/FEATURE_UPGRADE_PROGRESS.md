# Feature upgrade execution progress

Updated 21 October 2026. Scope: [FEATURE_UPGRADE_PLAN](FEATURE_UPGRADE_PLAN.md), U01–U13. This ledger is separate from the older modernization roadmap's 78% implementation figure.

## Fixed progress calculation

Report two figures using 13 equally weighted milestones. Host-verified delivery = milestones whose complete planned implementation and applicable host checks passed / 13. Accepted delivery = milestones with their required device/provider evidence, resolved review concerns and merged PR / 13. Partial slices do not count as a complete milestone. Round percentages to the nearest whole percent. These are coarse milestone indicators, not effort or release-readiness estimates.

Current: **8% host verified (1/13), 8% accepted (1/13)**. U01 passed its focused host checks, full GitHub CI, physical API34 surface flow, independent review and clean merge. Do not credit U02/U04/U06 merely for partial slices, work starting or PR creation.

| Milestone | Current state | Evidence / next gate |
| --- | --- | --- |
| U01 Bundled plugins | Accepted into feature integration branch | [PR #61](https://github.com/DevBehindYou/Atomic-File-Manager/pull/61), tested head `88bb8d0`, merge `742d473`; physical API34 flow passed; main/release acceptance remains separate |
| U02 Benchmark correctness/baseline | Harness APK builds; 2 non-mutating API34 contract cases and CI pass at `8e4c679`; strict guard follow-up published; current-head CI pending | [PR #63](https://github.com/DevBehindYou/Atomic-File-Manager/pull/63), branch `codex/benchmark-fixtures`; current head `7c0c14d` restricts the smoke to 100 entries; authorized smoke, real corpus baseline and trace boundaries remain open |
| U03 Listing responsiveness | Planned | Requires U02 measurements |
| U04 Startup access/ownership | Storage-state slice merged; browse composition-pause slice published and independently reviewed; host/device checks pending | [PR #62](https://github.com/DevBehindYou/Atomic-File-Manager/pull/62), merge `0854185`, passed its checks; draft [PR #66](https://github.com/DevBehindYou/Atomic-File-Manager/pull/66), head `fa283bb`, awaits applicable validation; broader ownership and conditional device gates remain open |
| U05 Index/cache consistency | Planned | Baseline/schema and restart tests |
| U06 Opening defaults | Earlier APK/instrumentation/static checks and non-mutating phone flow pass; resumed 42-regression/Lint validation pending | [PR #64](https://github.com/DevBehindYou/Atomic-File-Manager/pull/64), head `d1af094`, branch `codex/file-opening-defaults`; complete milestone acceptance remains open |
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

New focused PRs target `codex/feature-upgrade-base`, originally preserved at `89b1d45` and now at integration merge `0854185` after PRs #61/#62. This integration branch is separate from `main`. Report its merges as integration merges; do not claim the prior modernization PR stack is accepted or merged into main.

Before each merge: check the exact PR head, applicable tests/CI/review, dependency state and a clean trial merge onto the current target. Do not bypass failing/missing required checks. Retain conditional hardware/provider gates where the implementation needs them. The 17 older draft PRs require their own evidence; no bulk merge is implied.

Automatic continuation: active thread heartbeat `continue-atomic-implementation`, every 30 minutes. It resumes unblocked plan work, records evidence and reports meaningful percentage changes. Avoid duplicate work or repeated unchanged blockers.

## Batch 1

- Preserved the previously validated bundled-plugin source and upgrade plan as `12f007d`.
- Published and attached PR #61. Initial full CI caught a cancelled-job teardown race; fixed by joining the ViewModel job before resetMain. New-head CI passed static analysis and the full unit/APK job. Physical API34 plugin navigation and independent review passed. A clean trial merge preceded automatic merge `742d473` into `codex/feature-upgrade-base`.
- Started U02, U04 and U06 implementations in parallel worktrees. Local Gradle is not run concurrently.
- ADB sees the authorized API34 phone; U01 Settings/list/PDF-details/Back/archive-details/Close surface check passed, then the app returned Home. No real user-file mutation was performed.
- PR #62 merged the startup-readiness slice after 13 focused host tests, static analysis, Lint, current-head CI, independent review, a granted-phone surface flow and clean trial merge. Broader U04 ownership and revocation/mount/recreation checks remain open.
- The deterministic benchmark APK compiled after explicitly declaring its existing AndroidX test runner dependency. Both non-mutating contract cases passed on API34, and draft [PR #63](https://github.com/DevBehindYou/Atomic-File-Manager/pull/63) CI passed at `8e4c679`. Published follow-up `7c0c14d` adds a strict 100-entry guard; its current-head CI and local build remain pending. The earlier green CI at `8e4c679` does not validate this new head. The user authorized only a 100-file smoke; 1,000/10,000-entry runs remain separate. The release-like target build and smoke remain pending. No corpus baseline or speed improvement is claimed. See [the fixture validation at the reviewed PR head](https://github.com/DevBehindYou/Atomic-File-Manager/blob/8e4c6790a838b3f5363192d522ca5711fd1c480a/docs/testing/BENCHMARK_FIXTURE_VALIDATION.md) for that checkpoint's scope and limits.
- File-opening policy, persisted rules, actual external handler selection and displayed-gallery identity changes are published in draft [PR #64](https://github.com/DevBehindYou/Atomic-File-Manager/pull/64), head `d1af094`. Review corrected a generic-preview callback bypass with regression coverage. An earlier 42-case run had 40 passes and two MIME-selectivity fixture failures on API27/34; fixture correction `1d70c7d` retains the assertions. Two real `StateFlowValueCalledInComposition` Lint errors were corrected by observing settings (`655d044` in integration, `d1af094` on the opening branch). The following 42-regression/Lint retry was interrupted before compilation and is not passing evidence. Validation has resumed in `build/audit-tools/opening-and-benchmark-resumed-validation.log`; its terminal result is still pending. Earlier APK/instrumentation/static checks passed, and the phone's non-mutating Settings → File opening → PDF default → Back → Close → Home flow passed without changing its built-in default. No whole U06 acceptance is claimed yet.
- Primary browse visibility and composition pause are published in draft [PR #66](https://github.com/DevBehindYou/Atomic-File-Manager/pull/66), head `fa283bb`, on `codex/browse-visibility-lifecycle`. Source implementation and independent review are complete; host/device checks remain pending. Check active worktrees before duplicating this U04 slice; no completed ownership or device acceptance is claimed.
