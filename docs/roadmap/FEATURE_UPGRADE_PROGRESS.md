# Feature upgrade execution progress

Updated 21 October 2026. Scope: [FEATURE_UPGRADE_PLAN](FEATURE_UPGRADE_PLAN.md), U01–U13. This ledger is separate from the older modernization roadmap's 78% implementation figure.

## Fixed progress calculation

Report two figures using 13 equally weighted milestones. Host-verified delivery = milestones whose complete planned implementation and applicable host checks passed / 13. Accepted delivery = milestones with their required device/provider evidence, resolved review concerns and merged PR / 13. Partial slices do not count as a complete milestone. Round percentages to the nearest whole percent. These are coarse milestone indicators, not effort or release-readiness estimates.

Current: **8% host verified (1/13), 8% accepted (1/13)**. U01 passed its focused host checks, full GitHub CI, physical API34 surface flow, independent review and clean merge. Do not credit U02/U04/U05/U06 merely for partial slices, work starting or PR creation.

| Milestone | Current state | Evidence / next gate |
| --- | --- | --- |
| U01 Bundled plugins | Accepted into feature integration branch | [PR #61](https://github.com/DevBehindYou/Atomic-File-Manager/pull/61), tested head `88bb8d0`, merge `742d473`; physical API34 flow passed; main/release acceptance remains separate |
| U02 Benchmark correctness/baseline | Guarded harness CI passes; 3 non-mutating phone contracts and 2 exact-selection cases pass; authorized 100-file smoke still active with no accepted measurements | [PR #63](https://github.com/DevBehindYou/Atomic-File-Manager/pull/63), head `7c0c14d`, CI `39596127255`; root-detection probe/fallback and a successful terminal run remain unresolved; larger corpus baseline and trace boundaries remain open |
| U03 Listing responsiveness | Planned | Requires U02 measurements |
| U04 Startup access/ownership | Storage-state slice merged; browse slice CI and combined host/static/APK/Lint checks pass; device recreation pending | [PR #62](https://github.com/DevBehindYou/Atomic-File-Manager/pull/62), merge `0854185`; [PR #66](https://github.com/DevBehindYou/Atomic-File-Manager/pull/66), head `80f98ea`, CI `39613995458`; combined integration `5902824` passed 48 focused browse/index tests; broader ownership and device gates remain open |
| U05 Index/cache consistency | Partial index slice passes combined local checks; current isolated CI unit/APK job fails and is under investigation | [PR #67](https://github.com/DevBehindYou/Atomic-File-Manager/pull/67), head `f86db66`, CI `39616679288`: static pass, unit/APK failure; no schema change or durable scan checkpoint is delivered |
| U06 Opening defaults | 42 focused opening cases, static/Lint and debug/instrumentation/R8 benchmark-target packaging pass; current-head CI passes | [PR #64](https://github.com/DevBehindYou/Atomic-File-Manager/pull/64), head `2e9230b`, CI `39612223345`; PDF/settings phone surface used an older candidate; native-handler matrix and complete milestone acceptance remain open |
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
- The deterministic benchmark harness, including strict 100-entry guard `7c0c14d`, passed [PR #63 CI `39596127255`](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/39596127255). Three non-mutating API34 contract cases and two exact 100-entry selection cases passed. The user authorized only the 100-file smoke; 1,000/10,000-entry runs remain separate. The actual smoke is still active: AndroidX Benchmark 1.3.3's `su` root-detection probe hung despite the one-time shell approval. Only the verified probe was terminated to allow the normal fallback; the probe/fallback issue and terminal run outcome remain unresolved. No measurements or clean baseline are accepted. See [the fixture validation at the current harness head](https://github.com/DevBehindYou/Atomic-File-Manager/blob/7c0c14d/docs/testing/BENCHMARK_FIXTURE_VALIDATION.md) for that source checkpoint's scope and limits.
- Opening-defaults [PR #64](https://github.com/DevBehindYou/Atomic-File-Manager/pull/64), head `2e9230b`, passed [CI `39612223345`](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/39612223345). Local `opening-benchmark-final-validation.log`, exit 0, records all 42 focused opening cases, ktlint/detekt, Android Lint, debug and AndroidTest APKs, and R8 benchmark-target packaging passing. Earlier MIME fixture failures, real observed-settings Lint errors and the interrupted retry remain historical; the final run supplies the subsequent passing evidence. The non-mutating Settings → File opening → PDF default → Back → Close → Home surface used an older candidate and retained its built-in default. The native-handler matrix remains pending; no whole U06 acceptance is claimed.
- Browse composition-pause [PR #66](https://github.com/DevBehindYou/Atomic-File-Manager/pull/66), head `80f98ea`, passed [CI `39613995458`](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/39613995458). Combined integration `5902824` passed all 48 selected browse/index tests (Room 21, DAO 8, primary browse 3, state 1, existing regressions 15), static checks, APK packaging and Lint in `browse-index-consistency-retry-validation.log`, exit 0, in 12m 2s. Device recreation and broader ownership acceptance remain pending.
- Index consistency [PR #67](https://github.com/DevBehindYou/Atomic-File-Manager/pull/67), head `f86db66`, has a static-analysis pass but a unit/APK job failure in [CI `39616679288`](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/39616679288), under investigation. The combined local 48-case pass does not override that isolated CI failure. This is a partial index slice, with no schema change or durable scan checkpoint delivered. U05 remains incomplete.
