# Browse delete and trash admission

## Scope

Phase 0 / U04 prerequisite: `BrowseViewModel.deleteNodes` must hand accepted delete and trash requests to the existing app-wide `OperationQueue` before returning. A deferred ViewModel coroutine can be cancelled before it admits the request. Copy the caller's source IDs at acceptance so later list mutation cannot redirect the operation.

The change retains the operation type, options, destination, journal, engine, collision handling and cancellation APIs. Queue admission remains synchronous in memory; journal IO and engine work remain asynchronous in the existing queue scope. This does not introduce a new scope, clear retained Browse models, change permissions or establish process-death recovery acceptance. Create, rename and transfer-bubble ownership remain separate work.

## Controlled regression coverage

`BrowseDeleteAdmissionTest` uses a real `BrowseViewModel` from its production factory, a real `ViewModelStore`, `StandardTestDispatcher`, the real `OperationQueue` with its own test background scope, and an in-memory journal/backend. No real user files or Android storage providers are mutated.

- Delete and trash both appear in the queue before any Main dispatch, with their existing options and no journal or engine work yet.
- Clearing the actual ViewModel store before dispatch cancels the model scope but leaves accepted queue operations able to execute.
- Mutating the caller's source list after acceptance cannot replace either operation's targets.
- Cancelling accepted queue IDs before engine work still records cancellation without executing the engine.

The recording engine checks that the QUEUED journal row exists before it executes. Existing queue tests remain the authority for broader journaling, collision, FIFO and cancellation invariants.

## Validation status

The final frozen test first runs against unchanged production: 4 cases, 4 expected assertion failures, 0 errors/skips, 51s, exit 1 (`browse-delete-admission-red-validation.log` and saved XML). The first attempt also records the same four expected failures in 1m7s; its original log/XML are retained. Teardown now clears and joins the model job before resetting Main, and the final joined-teardown test is used for both final RED and GREEN.

No new platform API, dependency or Lint suppression is required. API 27 compatibility and the existing storage boundary are unchanged.

## Verified host checkpoint — 23 October

Actual host source `ca7c6701295074db2dc7fc4545decb75e1d7e358` and the synchronized PR branch share app tree `f9d9af221b3fa60c9eeef94a9efd6819702e5b89`. `browse-delete-admission-green-validation.log` exits 0 in 7m13s: all 45 focused cases pass, without failures/errors/skips (4 delete admission, 6 journal admission, 9 queue, 5 operation journal, 5 process-kill simulations, 2 create-parent, 3 navigation and 11 sort-loading). ktlint/detekt, debug and AndroidTest packaging and debug Lint pass. Lint reports 0 errors, 154 existing warnings and 1 hint. Result JSON and artifact metadata are retained under `build/audit-tools/browse-delete-admission-*`.

Debug APK SHA-256: `D01B81A47616488EF4BE5A264EF126121A85741AEC7529990EE89CD1A93AAEE2`; test APK: `BED18771E468D770764FB3AD9DDA1742D9ED6A55381E23C79E298858E589ACC8`. These are host-built artifacts, not a new device test or real deletion acceptance. Process-kill cases here are controlled host simulations, not proof of arbitrary provider/device recovery. No app was installed on the phone for this slice.

Independent source review finds no blocker. Final head `1da47038c5556ee406e23254df6823f8c1c8984d` passes both required jobs in CI `39844826697`, including full unit/APK packaging and debug/release Lint. The manual emulator job is skipped, not a physical pass. Review-thread inspection, accepted dependencies and a clean guarded trial preceded feature-integration merge `2bea39c160f376a132f5fb048af4144f278dc7d2`. Gate snapshots and trial records are retained under `build/audit-tools/pr75-acceptance/`. Main/release acceptance, create/rename/bubble job draining and retained-model pruning remain open. Fixed milestone credit stays 8% host verified / 8% accepted (1/13). The owner deferred device benchmarking and keeps phone permissions unchanged; generic continuation does not resume those runs.