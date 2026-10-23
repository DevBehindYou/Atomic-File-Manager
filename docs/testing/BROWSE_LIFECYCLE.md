# Browse secondary-pane lifecycle (M05)

The secondary BrowseViewModel now has a ViewModelStore owned by its Browse composition.
The model retires when the composition leaves, its tab/root changes, or the layout
becomes compact. RememberObserver also retires an abandoned composition. Retirement immediately
stops the model's listing and app-wide operation collectors. Its own store is cleared once
already accepted model-owned mutation jobs finish, or immediately when none remain. No secondary model or listing is
created in a single-pane layout. Switching a wide layout between Browse and Preview retains
the secondary model and its current location.

Primary models now use child ViewModelStores in an Activity-retained `PrimaryBrowseStoreRegistry`.
The registry reconciles every saved tab's session-aware state key plus initial root, including
inactive tabs, while constructing models only when requested. Selecting a tab, going Home,
pushing Analysis/Operations or recreating the Activity does not remove saved identities.
Ordinary navigation inside a model also does not change its initial-root identity.

The shell reconciles above pushed-route early returns, and synchronously reconciles every tab
mutation before publishing the encoded saved state. Closing a tab or explicitly replacing its
root removes its exact entry before retirement. Even A-to-B-to-A updates before recomposition
retire old A; the screen reads its registry entry on each composition rather than remembering
an old model solely by the now-reused textual key. Same-root explicit opens retain their model
and existing open-request navigation reset, clipboard and search semantics.

Removed entries stop observation immediately and clear only their captured child store after
accepted work drains. A stale acquisition for an identity absent from the saved tab set is
rejected. Final Activity-store clearing seals the registry, removes all entries and retires them
through the same drain contract; a cleared registry cannot acquire models. The implementation
uses supported ViewModelProvider/child-store APIs, without Activity-store enumeration or
individual-key removal. Restored tabs keep their existing encoded session IDs, including legacy
empty IDs; process death still reconstructs models rather than persisting model-only state.

## Accepted work and retirement contract

`BrowseViewModel` delegates to its internal `BrowseAcceptedWork` helper, which counts accepted create, rename, bubble metadata, bubble transfer/cleanup and
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
after an A-to-B-to-A root change. Primary and secondary owners both distinguish permanent
retirement from ordinary visibility changes before invoking this contract.

This retains the existing Main-owned mutation scope; it adds neither a global scope nor blanket
NonCancellable mutations. Existing sort writes still start UNDISPATCHED and serialize inside
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

The frozen old-source composition consumers reproduce both failures; all 59 selected corrected host cases, static checks, APKs and debug Lint now pass. See [the validation record](BROWSE_SECONDARY_DRAIN_VALIDATION.md) for exact source, hashes, retained negative evidence and pending CI/device limits.

## Primary registry validation

`PrimaryBrowseRootReplacementComposeTest` uses the real shell with in-memory directory/node data:
Home/Files preserves its staged clipboard, but explicit root A-to-B-to-A must not resurrect the
retired A model's clipboard. The frozen consumer is tested against unchanged source for RED
before the registry is copied for GREEN.

`PrimaryBrowseStoreRegistryTest` covers lazy all-tab reconciliation, inactive closure, saved
session restoration and owner retention, same-root reopen behavior, numeric-ID reuse, held
create/rename through two root updates before dispatch, exact-entry completion isolation, actual
parent-store clearing with independent queued work, and ordered NonCancellable sort drain.
Existing session-key tests continue to cover key encoding independently of registry ownership.
Native recreation/tab fixtures now read existing child entries instead of obsolete Activity
model keys; no native run is claimed by updating those lookups.

Primary registry passes 66 focused host cases, static checks, APKs and Lint, and the exact APK passes four selected API34 native cases. Final-head CI/merge and broader revocation/mount acceptance remain pending. See [the primary validation record](BROWSE_PRIMARY_REGISTRY_VALIDATION.md). Retired models
with never-returning accepted work can remain until completion/cancellation, so no strict global
memory bound, frame-time improvement or process-death mutation durability is claimed.



