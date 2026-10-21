# Primary Browse visibility — U04 second slice

Prepared 21 October 2026 against `0854185` (merged storage-readiness slice).

## Source evidence and behavior

Primary Browse models are cached in the Activity ViewModelStore by tab/start-folder key. Previously each cached model started listing and collected OperationQueue.active/finished for its entire Activity lifetime, including after leaving Files, changing tabs, closing a tab, or replacing its root. Every finished operation could trigger listings for all cached models.

Primary model factories now construct in a paused state. `PrimaryBrowseVisibility` activates the model only after its composition is applied and pauses it on disposal. Constructing an unused/abandoned primary model starts no listing or queue collector. Standalone and secondary model construction retain their existing eager behavior.

Pausing cancels listing/filter work and the model's queue subscriptions, while keeping its navigation, clipboard and accepted mutation/save work in its existing scope. The global operations queue is unaffected. Late refresh requests while hidden become one pending location instead of reading storage.

Returning activates queue observers and refreshes the current/pending folder once because finished events are not replayed. A changed explicit-open request resets to the start folder once, taking precedence over a pending old-folder refresh. Returning to the same folder retains selections still present in the final fresh listing, and drops selections whose entries disappeared. The current clipboard and Back history stay in the retained model.

## Regression coverage prepared

- `PrimaryBrowseVisibilityTest`: initially hidden models start zero listings; recomposition does not reload; leaving cancels current listing without clearing the state owner; operation completion while hidden starts zero listing; return starts one; changed explicit-open request starts one; root replacement pauses old collectors while only the visible replacement refreshes.
- `PrimaryBrowseStateTest`: actual in-memory folder navigation, clipboard and selection survive hide/return; deleted selected entries are pruned; Back history remains; explicit reopen resets navigation once while retaining clipboard. Teardown joins cancelled model work before resetting Main.
- `PrimaryBrowseRecreationDeviceTest`: disposable app-private directories, real Files → Home → Files and actual ActivityScenario recreation, retained model identity/current folder/clipboard/Back history. No copy/move/delete operation is enqueued; fixture cleanup checks the cache parent before removing only the owned tree.

No Gradle or device run is claimed here. Root schedules serial validation. Suggested checks:

```text
ktlintFormat ktlintCheck detekt
:app:testDebugUnitTest --tests '*PrimaryBrowseVisibilityTest' --tests '*PrimaryBrowseStateTest'
  --tests '*BrowsePaneLifecycleTest' --tests '*BrowseNavigationIdentityTest' --tests '*BrowseSortLoadingTest'
:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

## Remaining acceptance

The Activity store still retains cached model objects after tab closure/root replacement. Their expensive work is now paused, but explicit store pruning and bounded tab-owned identity remain a follow-up; this increment does not claim full ownership cleanup. Existing tab-ID reuse can still recover an old paused model for the same Activity key. Persisted process-death restoration and measured folder/frame improvements are also separate. The new recreation device case is acceptance evidence only after it actually runs.
