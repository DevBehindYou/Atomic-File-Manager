# Claude Code execution handoff

Owner authorization covers sequential implementation of the 50-task queue and subsequent eligible work in the existing approved scope without approval between phases. Coordination is through these repository documents. Codex has assigned the tasks; none is marked running.

1. Read README.md in this directory, STATE.json, OWNERSHIP.md, DECISIONS.md and ROADMAP_50.md. Read the root CLAUDE.md, source architecture and affected per-plugin records. Refresh source/PR state, not the entire historical audit.
2. Use a linked worktree from this repository's current `codex/feature-upgrade-base` after the coordinator PR is integrated. Until then, read the assignment on `codex/claude-coordinator-50` and record a documentation-only claim there through a focused PR; application implementation waits for the coordination dependency. Preserve the primary checkout's user artwork, preferences, ignored tools and signing files. Do not work directly on main or overwrite a Codex worktree.
3. Start **C001**. Acquire its task and area claims using the lease helper. Publish a dated claim in tasks.json and COORDINATION_LOG.md, including branch/base/file scope. Regenerate the roadmap. Read open PR reservations before implementation.
4. After C001, select the lowest eligible ID in dependency order. Do one implementation task at a time. If a task is blocked, state the exact blocker and choose the next independent eligible task. Do not request owner confirmation for ordinary implementation choices.
5. Use a separate focused PR for each logical behavior change. Larger tasks may produce several dependent PRs. Link the C-ID, canonical U/N42 group, exact validation, unresolved scope and rollback implications in every PR.
6. Run relevant tests before submitting. Acquire `host-gradle` for local Gradle and `phone-adb` for existing authorized device work. Do not infer new permission from this roadmap. Keep failing evidence.
7. Request Codex review through the PR and document handoff log. Continue independent work while review/checks run. Merge only under the shared exact-head/dependency/review/trial gates. Record the actual target branch and merge commit.
8. After every completed task or PR, update this queue and the affected plugin tasks/features/bugs/validation/changelog, decisions, architecture and progress documents. Keep About version v0.2 unless deliberately changing a shipped plugin version with corresponding evidence.
9. Continue through eligible tasks and subsequent existing-scope phases without waiting for routine approval. If credentials, hardware or an owner decision is necessary, finish independent work and document/request only that missing input. Do not repeatedly ask about unchanged blockers.
10. Maintain an interim report using REPORT_TEMPLATE.md. Once C001–C049 have passed their defined gates, complete C050 and publish the comprehensive report for Codex. If any task is blocked, label the report interim and preserve the incomplete count.

## First task response to record

Append a C001 claim with observed integration SHA, current #103/#105 disposition, branch/worktree, planned file scope and lease resource names. Do not copy November 7 snapshot statuses as if freshly checked. No need to contact Codex through a CLI or another messaging service.

## PR description contents

Explain the concrete user-visible problem and resulting behavior. Include task/group IDs, dependencies, tested head/app tree, relevant commands/run links/counts and retained failures, API/provider/device limits, migration/rollback impact and remaining requirements. A PR is ready for merge only after its current head meets every applicable gate.

Use [REPORT_TEMPLATE.md](REPORT_TEMPLATE.md) for the evidence format and [README.md](README.md) for shared operational rules.
