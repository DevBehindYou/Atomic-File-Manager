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

Pending parent-run serialized validation. Frozen regression tests will first run against unchanged production to establish RED, followed by the production fix and the same tests for GREEN. No build, CI or device pass is claimed here until its exact evidence is recorded.

No new platform API, dependency or Lint suppression is required. API 27 compatibility and the existing storage boundary are unchanged.
