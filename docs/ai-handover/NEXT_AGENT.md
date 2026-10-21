# Next Agent

## Latest direction — 21 October 2026

Execution update: read [FEATURE_UPGRADE_PROGRESS](../roadmap/FEATURE_UPGRADE_PROGRESS.md) first. Its fixed U01–U13 denominator reports 8% host verified and 8% accepted (1/13). PRs #61/#62 merged into `codex/feature-upgrade-base`, separately from main/release. Benchmark [PR #63](https://github.com/DevBehindYou/Atomic-File-Manager/pull/63), head `7c0c14d`, passes CI and three non-mutating phone contracts plus two exact-selection cases. Only its 100-file smoke is authorized; that run is still active after AndroidX Benchmark 1.3.3's root-detection probe hung. Only the verified probe was terminated to permit normal fallback. Do not accept measurements or claim a clean baseline without a successful terminal result and reviewed artifacts; larger runs remain separate. Opening [PR #64](https://github.com/DevBehindYou/Atomic-File-Manager/pull/64), head `2e9230b`, passes CI and local 42-case/static/Lint/debug/instrumentation/R8 benchmark-target checks in `opening-benchmark-final-validation.log`, exit 0; the older phone surface does not establish its pending native-handler matrix. Browse [PR #66](https://github.com/DevBehindYou/Atomic-File-Manager/pull/66), head `80f98ea`, passes CI; combined integration `5902824` passes 48 focused browse/index cases plus static/APK/Lint checks in `browse-index-consistency-retry-validation.log`, exit 0, with device recreation pending. Index [PR #67](https://github.com/DevBehindYou/Atomic-File-Manager/pull/67), head `f86db66`, passes static CI but fails unit/APK CI; inspect the ongoing investigation before changing it. No schema change or durable scan checkpoint is delivered by that partial slice. Inspect active worktrees before duplicating work. The user authorizes parallel PR work and conditional automatic merges after current-head tests/review and a clean trial merge; failing or missing applicable gates still prevent merging. Automation is active. Serialize local Gradle and preserve all existing worktrees/artwork/signing backups.

Read [FEATURE_UPGRADE_PLAN](../roadmap/FEATURE_UPGRADE_PLAN.md) and [bundled plugin validation](../testing/BUILT_IN_PLUGINS_VALIDATION.md) first. Planning is complete; execution remains staged. Preserve the new Settings inventory and the user's decision that all plugins stay built into Atomic and update with the app. Do not implement GitHub/Drive installers, a remote executable catalogue or fake install/remove controls. Benchmark correctness (U02), opening defaults (U06) and browse visibility (U04) are active; measured listing/index work still needs its baseline. The authorized API34 phone was connected on 21 October; recheck the connection before further device work. Do not apply the previous full-suite/device results to newer working-tree changes.

Do not restart from scratch. The active modernization branch is `codex/architecture-audit`, based on `7782660`. Preserve uncommitted work and worktrees under ignored `build/audit-worktrees`. Read [IMPLEMENTATION_PROGRESS](../roadmap/IMPLEMENTATION_PROGRESS.md) and the source-linked audit before the historical handover. Feature completion did not establish file-integrity acceptance.

## Historical continuation checkpoint (20 October 2026)

1. Inspect `git status`, `git log`, `git worktree list`, and the latest `build/audit-tools/*.log` plus matching `.exit` markers. Interrupted logs are not passes.
2. Source `4792db8` passes 917 app host cases, 28 custom Lint cases, ktlint/detekt, debug Android Lint and packaging. All 13 selected API34 surface cases pass, including Files storage/view/options, native fixtures and Tasks popup navigation. Debug/release Lint, R8 release and benchmark test-APK packaging passed in `menu-refinement-complete-validation.log`, exit 0, in 14m36s. Serialize Gradle on this 8 GB Windows host. JDK21 is at `C:\Program Files\Java\jdk-21`; SHA-verified Gradle is `build/audit-tools/gradle-9.3.1/bin/gradle.bat`. Use a local 3 GiB Gradle heap; do not repeat the 1.5 GiB R8 full-heap collection loop. Never run clean: ignored build directories contain tools, worktrees and the user's signing backup.
3. Read [menu/icon validation](../testing/MENU_REFINEMENT_VALIDATION.md), [Files validation](../testing/FILES_WORKSPACE_VALIDATION.md) and the [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md). Generated fixtures, physical Grid/List and Internal/SD switching, Tasks dismissal, long-press → More → Rename (cancel), and video thumbnails/playback frame are verified; do not repeat them without a new reason. The current 13-case selected log is clean; the preceding Files run retains a nonfatal InsetsSourceControl/SurfaceControl diagnostic. An immediate pre-refresh Home capture briefly showed access off, but app-ops stayed allowed and settled storage displayed correctly; startup loading-state polish remains. The user requested surface testing only; broader mutation/provider/performance tests remain separate. About 78% of weighted implementation scope is present; this is not release readiness. Seventeen draft PRs exist; see [REVIEW_BRANCHES](../roadmap/REVIEW_BRANCHES.md). Check exact head revisions and review requirements before any merge.
4. Keep high-risk filesystem changes for human review; never substitute host tests for device/provider/process-kill acceptance.

The Kotlin-loading and shared-storage-access candidates are now committed and integrated; do not reapply them. Their preserved worktrees remain useful for focused reviews. Startup review is draft #60, based on #59. Comparison, preview and Files/menu/icon changes still need focused publication after validation. Preserve the user's untracked `docs/design/icon-variations/` artwork; stage only explicitly owned files. Existing per-pane clipboards remain independent.

Use `codex/<topic>` branches. Do not fabricate tool provenance or session trailers. The older agent-specific instructions below are historical and superseded for this session.

## Load first

1. `STATE.yaml`
2. `HANDOVER.md`
3. `CURRENT_STATE.md`
4. `PENDING.md`

Read the other files only when one of these points to them. Before touching a feature, read the plan section it implements in `docs/roadmap/ALL_IN_ONE_PLAN.md`, plus `docs/AI_DEVELOPMENT_GUIDE.md` (the 13 rules).

## Verify the repo position

```bash
git fetch origin main
git status --short
git rev-parse --short origin/main   # compare with STATE.yaml repo.commit
git log --oneline <recorded-commit>..origin/main
```

If `main` has moved on, read that delta first and update these files with it, rather than re-auditing everything.

## First objective

**With a device or emulator:** TASK-001, then TASK-002, then TASK-003 to TASK-005 (`PENDING.md` P0).

**Without one** (cloud agent, no Android SDK): pick a P2 task from `PENDING.md`. Two have no device dependency:

- TASK-013: a large-data JVM test.
- TASK-012: split `MainActivity`.

Do not claim any P0 task is done without device evidence.

## How to work

1. Branch `codex/<topic>` off the appropriate reviewed base. One PR per substantial task.
2. Before pushing, run ktlint and detekt on the changed files and check **their own** exit codes (see FA-002). Run the pure-Kotlin tests where you can.
3. Open the PR with actual validation evidence and limitations. Do not invent attribution or session links.
4. Merge (squash) only when every check on the head SHA is green and the PR is mergeable. Before merging, dry-run the merge onto current `main`.
5. Update the plan row the change touches, and update this folder (`STATE.yaml`, `CHANGES.md`, `PENDING.md`).

## Do not repeat

- FA-001: test fixtures sized for a particular buffer.
- FA-002: filtering a linter's output and losing its exit code.
- FA-003: dropping the category walk before MediaStore freshness exists.

## Definition of done (handover-level)

- [ ] The emulator job has passed on `main`.
- [ ] The baseline profile is committed, with before and after numbers.
- [ ] Every hardware check in P0 has passed, or has a bug filed or fixed.
- [ ] `STATE.yaml` has been updated with the new commit and statuses.
