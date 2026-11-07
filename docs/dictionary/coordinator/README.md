# Coordinator Dictionary — Claude Code and Codex

Established **7 November 2026**, Asia/Calcutta. This is the shared coordination entry point. Both agents communicate through committed repository documents and PR records. No Claude process, recurring automation or login flow is started by this handoff.

## Read and synchronize

1. Read [STATE.json](STATE.json), [ownership](OWNERSHIP.md) and [coordination log](COORDINATION_LOG.md).
2. Fetch the current `codex/feature-upgrade-base` and inspect Git status, worktrees, open PR heads, review threads and exact-head check results. A dated document cannot establish a current pass.
3. Read the [50-task roadmap](ROADMAP_50.md), its canonical [task data](tasks.json), the [execution handoff](CLAUDE_START.md) and applicable source/validation records.
4. Claim the lowest eligible task before changing code. Keep one Claude implementation task active at a time. Codex may work independently in its reserved areas. Shared files, builds and devices require coordination.
5. At every completed task or PR, update task evidence, plugin task/bug/features/changelog records, affected architecture/decision docs and the appropriate progress ledger. Record a blocker once; continue independent eligible work.
6. Commit and publish the update in the same focused PR as its implementation. Read the other agent's newer updates before the next task. Never resolve conflicts by discarding their evidence or replacing a complete ledger with an older copy.

Current task data is the source of ownership/status truth; ROADMAP_50.md is generated. Architecture, capability and test facts stay in the linked specialist records. STATE.json is a dated observed snapshot, not a replacement for live GitHub checks. New evidence supersedes dated snapshots while preserving their history.

## Current architecture and conventions

Source baseline for this dictionary is `89140e3f3d4a746e6a907912042911cb10c8f6a3` on feature integration. Application source is native Kotlin/Compose, Atomic design tokens and a single Android app. Manual `AppContainer` / `DefaultAppContainer` owns dependencies; custom `AtomicRoute` owns navigation. Hilt declarations do not establish an active Hilt graph. No Flutter, web replacement, blanket module split or DI migration is planned here.

The repository has `:app`, `:lint-rules`, `:benchmark` and included `build-logic`. Inspected pins are Kotlin 2.2.10, AGP 9.1.1, Gradle 9.3.1, JDK 21, Compose BOM 2026.04.01, Room 2.7.2, coroutines 1.9.0, Coil 3.3.0 and Media3 1.8.0. The current minimum is **API28**, compile/target36. These are source facts, not newest-version or security assurances. Older API27 statements in dated audits are historical. [Current architecture](../../architecture/CURRENT_ARCHITECTURE.md) · [catalog](../../../gradle/libs.versions.toml) · [convention](../../../build-logic/convention/src/main/kotlin/atomic.android.application.gradle.kts)

UI → state/use cases → repository/backend boundaries remain. Opaque provider/account/file identities and capabilities govern access; display paths never authorize mutation. Room schema v4, SQLiteOpenHelper stores and preferences require preservation and explicit migrations. Existing source/revision ownership, staged publication, verified copies, operation admission and cancellation invariants must survive refactoring. [Coding rules](../../CODING_RULES.md) · [security](../../architecture/SECURITY.md) · [storage architecture](../../architecture/STORAGE_ARCHITECTURE.md)

Images use bounded native helpers and the repaired GIF path; media uses Media3; PDF currently has a bounded raster path; DOCX is structured text; Markdown has a documented native subset; editor and archive helpers have existing safe limits and accepted partial improvements. Audit current code before extending any of them. Keep all eight bundled, About versions starting at v0.2, and add no executable plugin downloads or fake installation controls. Cloud Connect is a separate ninth track. [Plugin records](../../plugins/README.md) · [plugin architecture](../../plugins/modernization/PLUGIN_ARCHITECTURE.md) · [Cloud plan](../../plugins/cloud-connect/PLAN.md)

## Objectives and authoritative scope

Safety, reliability and correct storage behavior precede performance. Improve cancellation/lifetime handling, responsiveness, bounded memory, actionable errors, recoverability and verified format support. Record performance gains only against comparable measurements. Keep Storage lightweight and analysis explicitly initiated by the user.

- Original optimization M01–M13 retains its weighted 60-unit scope. Historical implementation credit is 47/60, approximately 78%; this is not overall feature or stable-release completion. [Optimization plan](../../roadmap/OPTIMIZATION_ROADMAP.md)
- Feature U01–U13 retains **2/13 host verified (15%) and 1/13 accepted (8%)** at this checkpoint. [Plan](../../roadmap/FEATURE_UPGRADE_PLAN.md) · [progress](../../roadmap/FEATURE_UPGRADE_PROGRESS.md)
- Advanced eight-plugin scope retains **0/42 complete host groups, 0/42 accepted groups and 0/8 fully modernized plugins**. All eight have audits and many accepted partial slices. [Intake](../../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) · [42-group matrix](../../plugins/modernization/PLUGIN_FEATURE_MATRIX.md) · [progress](../../plugins/modernization/PLUGIN_PROGRESS.md)
- Claude's new execution batch is independently **0/50 accepted tasks (0%)**. Task completion earns original-plan credit only when that original milestone's complete gates pass. Fifty next actions do not exhaust every advanced requirement.
- Stable v0.2.1 is not accepted or published. Integration merges and the existing 0.2.0 beta are distinct from main/stable promotion. [Release history](../../testing/BETA_0_2_0_PUBLICATION_VALIDATION.md)

## Decisions, completed work and conflicts

[DECISIONS.md](DECISIONS.md) records current boundaries and known gaps. [STATE.json](STATE.json) identifies inspected PR heads/check runs. GIF #101, Editor history #90, Markdown #89, PDF #91, Archive #92 and DOCX #93 are accepted scoped improvements; do not rebuild them. Diagnostic #100 passed a tracing run without reproducing the old SQLite failure; its root cause remains unproven. Ledger #104 is merged.

Image #103 and Audio #105 have green scoped CI and remain open at this snapshot. Codex owns their review and merge gates. The old main/stack PRs #44–#60 and deferred benchmark #63 must be reconciled by ancestry and validation before closure or merge; an old open PR is not a new implementation assignment. A stale handover still calls several now-merged candidates pending. Use fresh Git/PR evidence and the November 6 ledger, and preserve historical entries.

## Task states and evidence

`assigned → claimed → implementing → host_verified → acceptance_pending → accepted`.

`blocked` or `deferred` must list the exact missing input and independent next task. A superseded task must link the replacement and proven equivalent scope; superseded is not accepted credit. A task is claimed only after the local ownership protocol and published claim record. The initial 50 entries are assigned, not running.

Accepted task evidence includes source head and app tree, PR and merge destination/commit, applicable test commands and result counts, static/build/native/provider results, artifact hashes when relevant, retained failures/fixes, review resolution and current-target merge trial. A research-only task requires a reviewed decision report and exact prototype evidence where specified. No research-only result closes a required implemented capability.

Keep source present, host verified, native emulator, physical touch, provider, benchmark, integration and release outcomes distinct. Empty evidence, cancelled runs, missing checks and skipped required cases are never passes. [Report template](REPORT_TEMPLATE.md)

## Autonomous PR workflow

Claude is authorized to execute eligible tasks without asking between tasks or phases. Use isolated `codex/claude-cNNN-description` branches from the current accepted integration base, one logical fix/feature per PR. A large task may use several dependent PRs; do not combine an entire phase into one huge diff. Existing working tree changes and user material must be preserved.

Before submission run appropriate focused validation and retain failures. Use the existing normal CI gates, including **Static analysis (ktlint, detekt, Lint)** and **Unit tests and APK packaging**, and applicable native/format/security/provider tests. Manual skipped CI jobs do not prove native acceptance. Documentation-only changes use link/schema/ownership checks locally; keep remote required checks intact. Do not run unnecessary local Gradle for this documentation handoff.

Codex reviews Claude's changes. Automatic merge authorization applies only after required checks pass on the current head, review concerns are resolved, dependencies are integrated and a fresh trial against the current target is clean. Verify the tested combined application tree when the base moves. Use a head guard; never force-push a shared branch or bypass a failed/missing check. Preserve merge destination and provenance. Unavailable review or external evidence blocks that merge, not independent work.

Subsequent approved-scope tasks may be appended in a separately identified C051+ batch after reconciliation; preserve the original 50 denominator and report. New product choices, credentials, hardware or permissions need only the specific missing input at their dependent milestone. Do not recreate continuation automation, start remote executables, broaden permissions or treat this roadmap as a new authorization for deferred device benchmarks.

## Shared resource locks

Use [lease.ps1](../../../scripts/coordination/lease.ps1) for local claims. It stores metadata under the Git **common directory**, shared by this repository's worktrees and outside tracked content. A short OS-exclusive gate serializes acquisition/release. Resource claims do not expire automatically; tokens prevent accidental release by another session. A stale claim must be reconciled with its owner, task/PR and actual processes before deliberate recovery. Never kill a process or delete a lock solely because it is old.

Acquire a `task-CNNN` claim plus every area lock listed in [OWNERSHIP.md](OWNERSHIP.md), in sorted order. If any claim fails, release this attempt's claims and choose another eligible task; do not edit first. Publish the claim in tasks.json/coordination log before application edits. Keep area ownership through active PR review unless a handoff is recorded.

Acquire `host-gradle` immediately before local Gradle, then check for pre-existing Gradle/JVM work. An uncoordinated existing build still blocks a new one. One local Gradle execution at a time on the 8 GB host. Hold `phone-adb` for the whole device test and restoration sequence. Release resource claims in a finally block after actual processes stop, including failed tests. Hosted CI jobs may run in parallel.

Example from the repository root:

~~~powershell
powershell -NoProfile -File scripts/coordination/lease.ps1 -Action status
powershell -NoProfile -File scripts/coordination/lease.ps1 -Action acquire -Resource task-C001 -Owner "Claude Code" -Task C001
# Retain the returned token in local session state. Release only your claim.
powershell -NoProfile -File scripts/coordination/lease.ps1 -Action release -Resource task-C001 -Token "<returned-token>"
~~~

The script coordinates worktrees with the same Git common directory, not separate clones or machines. Use the shared primary repository worktrees for this host. If Claude uses a separate clone, require a published claim plus Codex acknowledgment before overlapping edits or host/device use; unavailable acknowledgment means choose a disjoint task. Do not assume Markdown edits or independent local lock directories provide distributed exclusion.

## Validation and maintenance

Run `python scripts/coordination/validate.py --write` after editing tasks.json, followed by `python scripts/coordination/validate.py` and `git diff --check`. The validator checks IDs/count, ordering, dependencies, required task fields, acceptance evidence presence, rendered-roadmap agreement and local links. It cannot certify that attached evidence is truthful; Codex reviews that.

On Windows, `python -B scripts/coordination/smoke_test.py` exercises queue rejection and resource-lease behavior using disposable empty Git repositories. It never launches Gradle or ADB or changes user repository locks. Git and Windows PowerShell must be available.

The owner instructed both agents to use documents for contact. Task updates and handoff replies belong here or in the relevant PR, not direct messages to outside services. No separate Claude launch is needed to consume this queue.
