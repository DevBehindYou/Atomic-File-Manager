# Evidence-based optimization roadmap

17 October 2026. This is the modernization execution plan; `ALL_IN_ONE_PLAN.md` remains the product feature plan. Audit IDs refer to [PROJECT_AUDIT](../architecture/PROJECT_AUDIT.md). Effort weights are relative planning units, not time estimates. Completion counts only tasks whose acceptance and required verification have passed.

| Task / order | Priority; evidence and problem | Changes / affected files | Dependencies; risk; effort | Acceptance and validation |
|---|---|---|---|---|
| M01 | P1; inaccurate current architecture/status | Seven audit/architecture/roadmap/baseline/decision documents and handover links | None; low; 3 | Source-linked inventory, explicit measured/unmeasured evidence and executable plan |
| M02 | P0; A01/A04 archive data loss and false success | ArchiveOperationsHelper and focused archive tests; stage extraction, resolve real IDs, propagate errors | M01; high; 5 | Existing data survives cancellation/corruption/collision; opaque child IDs and denied IO tested |
| M03 | P0; A02 unjournaled mutations | OperationQueue + fault journal tests; persist before execute, bound ordered state writes | M01; high; 5 | Suspended insert blocks engine, failed insert never mutates, next operation continues, recovery tests pass |
| M04 | P0/P1; A03/A10 sharing corruption/resource leak | ShareServer + publication helper/tests; unique staging, no-clobber output, bounded sockets | M01; high; 5 | Concurrent uploads/old staging symlink cannot overwrite; stop closes active clients; isolated fixtures only |
| M05 | P1; A05 orphaned browsing work | BrowseScreen, lifecycle helper and tests; own secondary model and avoid single-pane work | M01; medium; 3 | Disposal clears collectors; single pane creates no secondary listing; UI behavior retained |
| M06 | P1; A06 Main-thread storage | GetNode/GetDirectoryListing use cases + dispatcher tests | M01; medium; 2 | Calls and cold flow execute on injected IO; cancellation/error semantics retained |
| M07 | P1; A07 progress/database amplification | Engine progress gate, deterministic clock tests/benchmark | M03; medium; 3 | Actual emissions <=4Hz except first/final boundaries; byte totals/cancellation preserved; before/after event counts recorded |
| M08 | P1; A08 trash recovery | TrashManager/store transaction and injected-failure tests | M03; high; 5 | Cancellation and failed restore leave data reachable and retryable; process death reconciliation specified |
| M09 | P1; A09/A12 opaque ancestry and breadcrumbs | Backend ancestry contract + engine + browse history | M02/M06; high; 5 | SAF child destination rejected before mutation; remote/provider breadcrumbs navigate actual IDs |
| M10 | P1; A11 missing search matches | Search DAO/repository/UI filter boundary + >2,000 result test | M01; medium; 3 | Matching category not discarded by pre-filter limit; bounded result/latency measurements |
| M11 | P1; A13/A14/A15 protocol safety | FTP TLS/errors, WebDAV parser/preconditions/bounds + server fixtures | M01; high; 8 | Protected data channel, host verification, denied-list errors, hostile XML bounded, no-clobber protocol checks |
| M12 | P2; A16/A18 dependency/architecture hygiene | Wrapper integrity, dependency review, shell decomposition, diagnostics | M02–M07; medium; 5 | Verified checksums; no new dependency warnings; privacy-safe logging; no arbitrary multi-module split |
| M13 | P1 release gate; A17 and missing device measurements | API27/36 + providers/USB/network/process kill, macrobenchmarks/baseline profile, signing | M02–M11; hardware/owner; 8 | Actual artifacts and test reports, measured budgets, owner signing; no historical passes substituted |
| M14 | P3 optional extensibility | New providers, persistent analysis and watcher freshness | Measured need; high; excluded | Separate feature proposal with provider contracts and freshness tests |

Critical path: M01 → M03 → M07/M08 → integrated verification → M13. M02, M04, M05 and M06 can run independently after M01; M09 and M11 require deeper provider validation. First implementation batch is M01–M07 (26 units). Full current roadmap M01–M13 is 60 units. P3 is outside the denominator. The first-batch percentage must never be presented as full-project completion.

Focused branches use `codex/` for this session, replacing the prior agent-specific `claude/` convention. No fabricated session trailers or tool attribution. Create PRs only if authentication works. Require green relevant checks and reviews; keep high-risk filesystem changes for human review. If remote tooling is unavailable, preserve committed local branches with exact continuation commands.
