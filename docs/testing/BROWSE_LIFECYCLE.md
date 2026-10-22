# Browse secondary-pane lifecycle (M05)

The secondary BrowseViewModel now has a ViewModelStore owned by its Browse composition.
The model retires when the composition leaves, its tab/root changes, or the layout
becomes compact. RememberObserver also retires an abandoned composition. Retirement immediately
stops the model's listing and app-wide operation collectors. Its own store is cleared once
already accepted model-owned mutation jobs finish, or immediately when none remain. No secondary model or listing is
created in a single-pane layout. Switching a wide layout between Browse and Preview retains
the secondary model and its current location.

The primary model still belongs to the Activity and is keyed by tab plus initial folder.
Its accumulation across explicit folder opens and closed tabs is deferred: correct cleanup
needs the route/tab owner to distinguish tab closure from temporary screen removal and
configuration changes. Clearing it on composition disposal would discard existing tab and
rotation state. The secondary pane already recreated on rotation before this change; it
continues to do so, while accepted mutation work drains on its original model.

## Accepted work and retirement contract

`BrowseViewModel` counts accepted create, rename, bubble metadata, bubble transfer/cleanup and
sort-save jobs synchronously before launching them in its existing `viewModelScope`. A completion
handler releases the count after success, returned failure, coroutine failure or cancellation,
including cancellation before the body starts. Background listings, filters and operation
collectors are not counted. Queue admission remains app-owned; delete/trash/copy/move/archive
operations already handed to the global queue do not delay an otherwise idle store's retirement.

The owner calls `retireAfterAcceptedWork` on Main only when that model will never be displayed
again, supplying a callback that clears that exact entry's store. Retirement is one-shot, stops
observation immediately and prevents `onVisible`/directory loading from resuming it. Delayed create
success therefore cannot restart a forgotten listing. The callback is released exactly once
after the last tracked job completes; a retiring A entry cannot clear a fresh A entry created
after an A-to-B-to-A root change. Primary Activity models do not use this contract yet: their
future owner must distinguish permanent closure from ordinary hiding/configuration changes.

This retains the existing Main-owned mutation scope; it adds neither a global scope nor blanket
NonCancellable mutations. Existing sort writes still start UNDISTPATCHED and serialize inside
their existing NonCancellable mutex block. External scope cancellation keeps its prior meaning;
retirement itself waits instead of cancelling accepted work. A provider operation that never
returns can retain its retired model until it finishes or is otherwise cancelled. This is not
process-death durability or a complete memory bound for such operations.

Bubble cleanup retains its existing whole-bubble behavior after successful transfer. Concurrent
edits to that bubble and transactional/snapshot cleanup remain a separate gap; ownership tracking
does not claim to solve them. No permission, dependency or platform API changes are introduced.

`BrowsePaneLifecycleTest` uses the actual BrowseViewModel and OperationQueue with a suspended
fake listing. It covers compact layouts, composition removal, resizing, same-folder tab
switches, folder changes, ordinary recomposition, and an operation finishing after disposal.
`BrowseSecondaryWorkDrainComposeTest` adds real composition disposal and root A-to-B-to-A
consumer regressions. Its held rename uses an absent ID, covering failure completion; the
controlled retirement suite separately uses a real in-memory node for rename success.
`BrowseAcceptedWorkRetirementTest` covers deferred acceptance, last-job drain, cancelled and
already-cancelled jobs, one-shot callbacks, ordered NonCancellable sort saves, bubble failure and
successful transfer through held cleanup, plus idle abandonment with independently queued work.

Validation is pending parent-run serialized host checks. The parent owns RED on unchanged
production using the frozen consumer fixture/tests, followed by GREEN and existing lifecycle,
sort, queue and Browse tests. No host, CI, device, frame-time or memory-improvement pass is
claimed for this slice until its exact evidence is recorded.
