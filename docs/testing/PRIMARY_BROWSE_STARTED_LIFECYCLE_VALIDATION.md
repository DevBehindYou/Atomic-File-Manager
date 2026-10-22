# Primary Browse STARTED lifecycle — U04 follow-up

Prepared 22 October 2026 against accepted feature integration `7c8ebe1`. This source increment is separate from the accepted composition-visibility PR #66. Host static checks, test execution, APK packaging, current-head CI and physical background/return acceptance are **pending**. No test pass, speed-up or complete U04 milestone is claimed.

## Behavior and boundaries

Primary Browse models remain in the Activity store. Their previous composition-only visibility guard paused listing/filter work and operation subscriptions when leaving Files, but an Activity stopped in the background could keep all three running while its composition remained present.

`PrimaryBrowseVisibility` now registers a `LifecycleEventObserver` with the current composition's lifecycle owner. A composed model activates on `ON_START`, pauses on `ON_STOP`/`ON_DESTROY`, and removes its observer and pauses on disposal. An already-started owner's observer catches up through `ON_START`; a CREATED or destroyed owner cannot start work. Lifecycle-owner or explicit-open-request changes dispose the previous registration. Recomposition with unchanged keys does not relaunch work; PAUSE to STARTED alone does not pause it.

The existing `BrowseViewModel.onVisible` path still owns single-refresh/resume and explicit-request ordering. A new request while stopped remains deferred until the current owner starts; that request overrides a pending old-folder refresh. Retained folder history, clipboard and valid selections remain with the model. Global operations and already accepted mutation/preference-save jobs are unaffected.

`refilter` also refuses to launch while hidden. Filter/search/sort settings still update retained state, and the next visible directory refresh applies them. This closes a separate way for a composed stopped screen to restart filtering after its previous filter job had been cancelled.

Secondary pane lifetime, cached-model pruning/tab identity, persisted process-death restoration and provider revocation/mount behavior are outside this increment. A STARTED multi-window Activity may legitimately keep work active. This is not an assertion of measured battery, CPU or frame improvements.

## Regression candidates

Five `PrimaryBrowseStartedLifecycleTest` cases use real BrowseViewModels, a controlled CREATED→STARTED owner and the existing in-memory operation queue/listing fixture:

- Constructed/composed but stopped starts no work; START starts one listing; RESUME/PAUSE within STARTED does not relist; STOP cancels; operation completion while stopped does not relist; return starts one refresh and keeps the retained model.
- A request changed while stopped overrides a deferred old-folder refresh on return, resets to the initial folder once and clears old Back history.
- Replacing the lifecycle owner pauses the model until the new owner starts; events from the removed owner cannot reactivate it.
- Composition disposal removes the registration, leaves the retained model alive and does not stop the global queue; later lifecycle events cannot relaunch the removed screen.
- A search change while stopped updates the retained query without launching filter work or replacing the current result; the fresh visible listing applies that query. The test waits for owner-child jobs to finish so an asynchronous wrong filter cannot pass by racing the assertion.

The shared fixture adds optional initial entries and observed listing IDs; its default suspended behavior remains unchanged for existing tests. Teardown clears the retained store and waits for model jobs to complete before the outer fixture resets Main.

The existing `PrimaryBrowseRecreationDeviceTest.navigationAndActivityRecreationRetainPrimaryFolderClipboardAndBackHistory` is extended with real ActivityScenario CREATED→RESUMED background/return before its existing recreation. Its generated directories stay in the app's own cache; copy stages only an in-memory clipboard. No real shared files, grants, user preferences or installed apps change. This narrow device flow checks state/navigation survival; exact background listing/observer counts are the controlled host cases above.

Recommended parent-serialized checks:

```text
ktlintFormat ktlintCheck detekt
:app:testDebugUnitTest --tests '*PrimaryBrowseStartedLifecycleTest'
  --tests '*PrimaryBrowseVisibilityTest' --tests '*PrimaryBrowseStateTest'
  --tests '*BrowsePaneLifecycleTest' --tests '*BrowseNavigationIdentityTest' --tests '*BrowseSortLoadingTest'
:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Then run the named native case on the authorized device, record source/APK hashes and access/finalization evidence, require applicable current-head CI and review, and trial-merge onto the latest feature integration target. The older #66 phone/CI evidence must not be relabeled as validating this new lifecycle behavior.

Independent source review found no blockers in the lifecycle/request ordering, observer removal or deferred filtering. Its diagnostic suggestion was applied: the native helper retains an original background-entry failure and suppresses a simultaneous resume failure rather than masking the first error. Source review is not a substitute for the pending build and runtime checks above.

## API references

The [official Compose effect guidance](https://developer.android.com/develop/ui/compose/side-effects) describes owner-keyed observer registration/removal with `DisposableEffect`. The [Lifecycle contract](https://developer.android.com/reference/androidx/lifecycle/Lifecycle#addObserver(androidx.lifecycle.LifecycleObserver)) specifies catch-up to the owner's current state. These existing AndroidX APIs require no new platform-version branch or dependency.
