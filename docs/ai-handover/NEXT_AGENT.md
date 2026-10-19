# Next Agent

Do not restart from scratch. The active modernization branch is `codex/architecture-audit`, based on `7782660`. Preserve uncommitted work and worktrees under ignored `build/audit-worktrees`. Read [IMPLEMENTATION_PROGRESS](../roadmap/IMPLEMENTATION_PROGRESS.md) and the source-linked audit before the historical handover. Feature completion did not establish file-integrity acceptance.

## Active continuation (19 October 2026)

1. Inspect `git status`, `git log`, `git worktree list`, and the latest `build/audit-tools/*.log` plus matching `.exit` markers. Interrupted logs are not passes.
2. M02–M11 sources are integrated; 663 app tests, 28 custom Lint tests, ktlint/detekt, debug/release Lint and debug/release/instrumentation APK assembly pass. Benchmark packaging remains. Serialize Gradle on this 8 GB Windows host. JDK21 is at `C:\Program Files\Java\jdk-21`; SHA-verified Gradle is `build/audit-tools/gradle-9.3.1/bin/gradle.bat`. Release R8 required a local 3 GiB heap; do not repeat the 1.5 GiB full-heap collection loop.
3. Read the [phone surface report](../testing/DEVICE_SURFACE_TEST_2026-10-19.md): four automated API34 UI tests and manual surface checks pass. The user requested surface testing only for this pass; broader mutation/provider/performance tests remain separate. Prioritize the newly device-confirmed baseline main-thread preference reads. About 78% of weighted implementation scope is present; this is not release readiness. Fifteen draft PRs exist; see [REVIEW_BRANCHES](../roadmap/REVIEW_BRANCHES.md). Check exact head revisions and review requirements before any merge.
4. Keep high-risk filesystem changes for human review; never substitute host tests for device/provider/process-kill acceptance.

Two unvalidated candidates are preserved outside the integration source: `build/audit-worktrees/kotlin-loading` (two build-plugin files) and `build/audit-worktrees/storage-access` (permission adapter, shell/enumerator callers and tests). Both contain uncommitted work; neither is included in the current APK or the passing gates. Inspect and validate them before integration, preserve focused PR scope, and do not discard them as cleanup.

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
