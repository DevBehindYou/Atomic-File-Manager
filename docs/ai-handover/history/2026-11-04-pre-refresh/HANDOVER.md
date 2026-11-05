# Project Handover

Last updated: 2026-10-19

Current checkpoint: [IMPLEMENTATION_PROGRESS](../roadmap/IMPLEMENTATION_PROGRESS.md) and [CURRENT_STATE](CURRENT_STATE.md) supersede the historical October 8 status below. Active branch `codex/architecture-audit`, source baseline `7782660`. Audit/design are complete; safety, performance, provider and protocol fixes are integrated and under validation. Preserve worktrees and generated verification logs. Use `codex/` branches, focused draft PRs, actual check results, and human review for high-risk filesystem changes. No current device benchmark or production-signing evidence exists.

The compact block and feature inventory below describe the earlier merged feature plan.
Verified against commit: `e2b1509` (`main`)

## Compact context

```text
@CTX/2
P:atomic-file-manager(repo DevBehindYou/Refract-Full-File-Manager)
OBJ:all-in-one-plan done -> device verification + remaining §16 optimization
STACK:kotlin2.2|compose|agp9.1|room|manual-DI|minSdk27/target36
DONE:plan phases 0-4 (#1-#37), §16 H1 H2 H3 H7 H8 H10 + jank/monkey tooling (#38-#41)
PEND:emulator job, baseline profile, SMB/mDNS/Wi-Fi share/USB/Shizuku on hardware, real kill, signing, play policy
RULE:1 PR per task on claude/*, merge only green+no conflict, no model ids, no skipped tests, no secrets
STAT:build=V;unit=V;device=?;runtime=?;signing=B;§16=P
NEXT:TASK-001>TASK-002>TASK-003..005
```

## Goal

An all-in-one Android file manager without clutter, following `docs/roadmap/ALL_IN_ONE_PLAN.md`.

## Current status

| Area | Status |
|---|---|
| Build (CI) | VERIFIED |
| Unit and Robolectric tests | VERIFIED (about 535) |
| Device and instrumented tests | UNKNOWN (manual job not run on current code) |
| Runtime on a phone | UNKNOWN since 15 Sep 2026 |
| Release signing | BROKEN: debug key (ISS-001) |
| Plan Phases 0–4 | IMPLEMENTED |
| §16 optimization | PARTIAL |

## Completed

All plan features: see `CHANGES.md`, CHG-001 to CHG-023.

## Active problems

- ISS-001: no production signing key.
- ISS-002: device-only behaviour has never run on hardware.

## Highest-priority work

TASK-001 (run the emulator job), then TASK-002 (baseline profile), then the hardware checks TASK-003 to TASK-005.

## Critical constraints

- One PR per task. Merge only when CI is green and there is no conflict.
- Every mutation goes through `FileOperationsEngine` and the journal. Trash is the default.
- Plain-language UI. Never fake an Android capability that isn't there.
- Never weaken or skip a test.

## Read next

`CURRENT_STATE.md`, then `PENDING.md`, then `NEXT_AGENT.md`.
