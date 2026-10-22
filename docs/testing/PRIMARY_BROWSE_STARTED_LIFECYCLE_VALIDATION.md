# Primary Browse STARTED lifecycle — U04 follow-up

Checked 22 October 2026. This source increment is separate from the accepted composition-visibility PR #66. PR #70 head `4cc1a35` passes current-head CI `39745799708`; focused host checks and physical background/return/recreation pass as recorded below. No measured speed-up or complete U04 milestone is claimed.

## Behavior and boundaries

Primary Browse models remain in the Activity store. Their previous composition-only visibility guard paused listing/filter work and operation subscriptions when leaving Files, but an Activity stopped in the background could keep all three running while its composition remained present.

`PrimaryBrowseVisibility` now registers a `LifecycleEventObserver` with the current composition's lifecycle owner. A composed model activates on `ON_START`, pauses on `ON_STOP`/`ON_DESTROY`, and removes its observer and pauses on disposal. An already-started owner's observer catches up through `ON_START`; a CREATED or destroyed owner cannot start work. Lifecycle-owner or explicit-open-request changes dispose the previous registration. Recomposition with unchanged keys does not relaunch work; PAUSE to STARTED alone does not pause it.

The existing `BrowseViewModel.onVisible` path still owns single-refresh/resume and explicit-request ordering. A new request while stopped remains deferred until the current owner starts; that request overrides a pending old-folder refresh. Retained folder history, clipboard and valid selections remain with the model. Global operations and already accepted mutation/preference-save jobs are unaffected.

`refilter` also refuses to launch while hidden. Filter/search/sort settings still update retained state. On returning to the same folder, `onVisible` schedules its one directory refresh and then reapplies the latest filter to cached raw rows, independently of the first provider batch. This order matters because `loadDirectory` cancels old filter work. A remembered sort that arrives later and changes the retained order also refilters cached rows on this same-folder resume path; it cannot leave a rejected old-sort filter waiting indefinitely for provider data. New user sort choices still win through the existing sort revision check. These changes avoid both background filtering and visible stale-filter rows if the new listing stalls or fails. The existing current-folder/raw-list/settings checks prevent a late cached filter from replacing newer directory results. A changed explicit request or deferred navigation to a different folder does not refilter the old folder's rows as the new folder.

Secondary pane lifetime, cached-model pruning/tab identity, persisted process-death restoration and provider revocation/mount behavior are outside this increment. A STARTED multi-window Activity may legitimately keep work active. This is not an assertion of measured battery, CPU or frame improvements.

## Regression candidates

Eight `PrimaryBrowseStartedLifecycleTest` cases use real BrowseViewModels, a controlled CREATED→STARTED owner and the existing in-memory operation queue/listing fixture:

- Constructed/composed but stopped starts no work; START starts one listing; RESUME/PAUSE within STARTED does not relist; STOP cancels; operation completion while stopped does not relist; return starts one refresh and keeps the retained model.
- A request changed while stopped overrides a deferred old-folder refresh on return, resets to the initial folder once and clears old Back history.
- Replacing the lifecycle owner pauses the model until the new owner starts; events from the removed owner cannot reactivate it.
- Composition disposal removes the registration, leaves the retained model alive and does not stop the global queue; later lifecycle events cannot relaunch the removed screen.
- Three separate query, hidden-file and category cases change a setting while stopped without launching filter work or replacing the cached result. Each holds the resumed provider before its first batch, requires cached rows to reflect the new setting while the single rescan is still pending, then releases an explicit access failure and verifies the visible filtered result stays consistent while raw evidence/error state is retained. Exactly two listings (initial plus resume) are allowed. Waiting for owner-child jobs while stopped prevents a wrongly launched asynchronous filter from racing past the assertion.
- A fourth cached-result case holds the resumed sort-preference read before directory enumeration. The latest query must filter cached rows during that hold. It then restores a different order, requires cached rows to use that order while the provider remains held, and finally releases an access failure. Query, ordering and raw evidence remain correct with one resumed directory read and no preference writes.

The shared fixture adds optional initial entries, a controlled result flow and observed listing IDs; its default suspended behavior remains unchanged for existing tests. Teardown clears the retained store and waits for model jobs to complete before the outer fixture resets Main.

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

Parent review then identified the held/failed-refresh cached-filter gap. Independent follow-up review also confirmed the delayed remembered-sort ordering case. Both source corrections and the four held/failure cases are now included; a fresh independent source review found no remaining blocker in this resume path. Ordinary refresh calls without `retainSelection` retain their pre-existing behavior and are not claimed as an expanded sort-consistency fix.

## Verified checkpoint

`primary-browse-started-validation.log` exits 0 in 2m22s: static checks and all 48 selected cases pass (17 ordering, 3 navigation identity, 5 pane lifecycle, 11 sort loading, 8 new STARTED lifecycle, 1 primary state and 3 visibility cases), with no failures/errors/skips. CI at `4cc1a35` passes full unit/APK packaging and static analysis including debug/release Lint; its manual emulator job is skipped, not a phone result.

On 22 October the extended native case passes on the authorized API34 phone together with the two Storage/Analysis cases (3 tests in 16.98s). Combined checkout `6421207` has identical Browse source/test files to this PR and adds the independent Storage candidate. Debug APK SHA-256 is `45C39BFC4BC76C4D45504C02EF8B2E0D6C7BCAC1215B986803AFD95203350865`; AndroidTest SHA-256 is `79CDA75A78EB03559B125E9FC13C315B2715F4E4AFFB6EFB7956BDF05A4AE924`. Logs are under `build/audit-tools/device-preview-tools/storage-analysis-acceptance/`. The post-test screenshot helper initially failed on selected-tab handling; separate corrected finalization exits 0 in `storage-analysis-phone-finalization.log`, verifies the actual selected Home tab and unchanged access, and confirms removal of the test-only handler. No native case is relabeled or rerun to hide that reporting failure. These results cover the named lifecycle slice, not the broader pending U04 provider/ownership/process-death gates.

## API references

The [official Compose effect guidance](https://developer.android.com/develop/ui/compose/side-effects) describes owner-keyed observer registration/removal with `DisposableEffect`. The [Lifecycle contract](https://developer.android.com/reference/androidx/lifecycle/Lifecycle#addObserver(androidx.lifecycle.LifecycleObserver)) specifies catch-up to the owner's current state. These existing AndroidX APIs require no new platform-version branch or dependency.
