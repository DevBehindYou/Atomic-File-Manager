# Target architecture and incremental migration

Keep the existing Android modular monolith: `:app`, `:lint-rules`, `:benchmark`, convention build. Keep Kotlin, Compose, manual AppContainer and StorageBackend. No framework replacement, new backend, cloud service or plugin framework is justified.

## Boundaries and ownership

* Presentation owns immutable view state, route/tab identity and lifecycle; it submits intent and renders progress. Screen-owned work must stop when its owner is cleared. Process operations survive screen removal.
* Application orchestration owns operation admission, durable intent, cancellation, conflicts and completion. Retain serial mutation execution until an overlapping-path lock design is proven.
* Domain owns operation invariants, capability-aware rules and typed FileResult failures. Opaque FileNodeId values are resolved by backends, never reconstructed as paths.
* Infrastructure owns actual filesystem/provider/network calls, IO dispatching, persistence and resource cleanup. A suspend signature alone does not move work off Main.
* AppContainer assembles dependencies and injectable dispatcher/clock seams. Shared helpers must encode a demonstrated invariant, not introduce another generic framework.

```mermaid
sequenceDiagram
  participant UI as Screen / ViewModel
  participant Q as OperationQueue
  participant DB as Room journal
  participant E as Engine
  participant B as StorageBackend
  UI->>Q: enqueue(operation)
  Q->>DB: persist intent
  DB-->>Q: acknowledged or failure
  alt persisted
    Q->>E: execute
    E->>B: stage / stream / verify
    B-->>E: completed staged output
    E->>B: safe publication
    E-->>Q: throttled progress / terminal result
    Q->>DB: ordered state update
    Q-->>UI: completion or partial failure
  else persistence failed
    Q-->>UI: failure; no mutation started
  end
```

## Contracts

Keep `StorageBackend` source compatibility for the first fixes. `getNode` and listing use-case boundaries receive defaulted injectable IO dispatchers. Listing stays cold; cancellation propagates. Use backend-returned `FileNode` IDs for created/resolved children. Failures must not look like empty success.

`OperationQueue.enqueue` stays non-suspending, returning its OperationId promptly. Internal execution waits for intent persistence. Journal-less mode remains supported for explicitly configured tests. State writes must be ordered and bounded/backpressured; do not accumulate a write closure per byte buffer. Terminal state remains distinct from progress. Completion of bytes is not proof that a terminal journal update persisted; surface/document persistence failure conservatively.

Archive output uses unique staging and validates before publishing; never truncate an existing final file. Existing collision UX stays where supported; fail explicitly where archive policy is not implemented. Arbitrary provider atomic no-replace is not available through the present interface: do not claim protection against all external-writer races. A future capability-gated publish API needs contract tests per backend before stronger guarantees.

Sharing uses bounded accepted connections and uniquely owned temporary files. No-clobber final creation must not silently replace preexisting data. If publication is streamed to an exclusively created target, hide in-flight output from share clients and acknowledge external filesystem visibility. Stop closes listener and all accepted sockets.

## Consistency, recovery and errors

Successful/partial mutations invalidate affected source/destination folders and search rows. Direct rename/editor/share paths must eventually adopt the same application admission/invalidation seam. Until then, audit each path and retain category freshness scans. No automatic destructive retry: resume requires reconciliation of source, staging, backup and destination. Source deletion always follows verified publication. Persist staging identity before claiming crash-atomic recovery; existing per-operation journal alone is insufficient.

Cancellation rethrows CancellationException; cleanup uses bounded non-cancellable sections only for owned resources. Network timeouts and shutdown close sockets; cancellation cannot be guaranteed by coroutine checks during a blocking native call. Preserve primary errors and report cleanup/recovery problems without deleting unrelated files.

## Read performance and future seams

Reuse Lazy lists, stable keys and Coil caches. Measure provider listing, sort allocations and first visible chunk before changing chunk sizes or caching. Search filters belong before the result limit; index rebuild/refresh require serialized publication and freshness markers. Keep category walk until external-change freshness exists. Add watchers/provider observers only with lifecycle-owned subscriptions and explicit invalidation semantics.

Continue current Room schema and preference formats. No migration is required by these initial fixes. Persistent analysis/index improvements require migration and stale-cache tests. Diagnostics should record operation IDs, durations, counts and typed error markers, not passwords or user paths/content. Add no new dependency for initial fixes.

Possible future provider extensions use StorageBackend capabilities. Cloud-native APIs, additional OSes and plugins are P3; not part of the current implementation claim.

## Migration and verification gates

Audit and roadmap first; independent archive, share and browsing branches next; integrate journal/progress changes with existing recovery tests; then run formatting/static analysis/unit tests/builds, review each diff and update handover. No automatic high-risk merge or production release. Hardware, API27/API36 runs, signing and unavailable external services remain explicit gates.
