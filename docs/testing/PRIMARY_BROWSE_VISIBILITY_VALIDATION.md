# Primary Browse visibility — U04 second slice

Prepared 21 October 2026 against `0854185` (merged storage-readiness slice). Validation record updated 22 October; the physical recreation check ran on 21 October.

## Source evidence and behavior

Primary Browse models are cached in the Activity ViewModelStore by tab/start-folder key. Previously each cached model started listing and collected OperationQueue.active/finished for its entire Activity lifetime, including after leaving Files, changing tabs, closing a tab, or replacing its root. Every finished operation could trigger listings for all cached models.

Primary model factories now construct in a paused state. `PrimaryBrowseVisibility` activates the model only after its composition is applied and pauses it on disposal. Constructing an unused/abandoned primary model starts no listing or queue collector. Standalone and secondary model construction retain their existing eager behavior.

Pausing cancels listing/filter work and the model's queue subscriptions, while keeping its navigation, clipboard and accepted mutation/save work in its existing scope. The global operations queue is unaffected. Late refresh requests while hidden become one pending location instead of reading storage.

Returning activates queue observers and refreshes the current/pending folder once because finished events are not replayed. A changed explicit-open request resets to the start folder once, taking precedence over a pending old-folder refresh. Returning to the same folder retains selections still present in the final fresh listing, and drops selections whose entries disappeared. The current clipboard and Back history stay in the retained model.

## Regression coverage

- `PrimaryBrowseVisibilityTest`: initially hidden models start zero listings; recomposition does not reload; leaving cancels current listing without clearing the state owner; operation completion while hidden starts zero listing; return starts one; changed explicit-open request starts one; root replacement pauses old collectors while only the visible replacement refreshes.
- `PrimaryBrowseStateTest`: actual in-memory folder navigation, clipboard and selection survive hide/return; deleted selected entries are pruned; Back history remains; explicit reopen resets navigation once while retaining clipboard. Teardown joins cancelled model work before resetting Main.
- `PrimaryBrowseRecreationDeviceTest`: disposable app-private directories, real Files → Home → Files and actual ActivityScenario recreation, retained model identity/current folder/clipboard/Back history. No copy/move/delete operation is enqueued; fixture cleanup checks the cache parent before removing only the owned tree.

Root's serialized combined validation and physical acceptance are recorded below. The focused checks remain reproducible with:

```text
ktlintFormat ktlintCheck detekt
:app:testDebugUnitTest --tests '*PrimaryBrowseVisibilityTest' --tests '*PrimaryBrowseStateTest'
  --tests '*BrowsePaneLifecycleTest' --tests '*BrowseNavigationIdentityTest' --tests '*BrowseSortLoadingTest'
:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

## Recorded validation

Source `80f98ea` passed PR #66 CI `39613995458`. Combined integration `5902824` also passed 48 selected browse/index cases with no failures/errors/skips, static checks, debug/instrumentation APK packaging and Android Lint in `browse-index-consistency-retry-validation.log` (exit 0, 12m 2s). Its categories were Room index 21, DAO reconciliation 8, primary Browse 3, state 1 and existing regressions 15. The index tests are combined evidence, not additional primary Browse cases.

The later combined integration `66d7451` passed 44 focused host cases, ktlint/detekt, debug and AndroidTest APK packaging and Android Lint in `browse-layout-sort-validation.log` (exit 0, 11m 46s). Orthogonal UI, opening and index changes are present in that candidate; the primary visibility behavior from this branch is unchanged.

On the API34 phone on 21 October, `PrimaryBrowseRecreationDeviceTest.navigationAndActivityRecreationRetainPrimaryFolderClipboardAndBackHistory` passed with `OK (1 test)`. It exercised actual Files → Home → Files and Activity recreation, preserving the current folder, clipboard and Back history. Three other selected native cases passed on the same combined artifact. Case logs, hashes and screenshots are under `build/audit-tools/device-preview-tools/browse-layout-acceptance/`.

Debug APK SHA-256: `7AB8B77A5564787F23180AB3CE4E437FA2BC74F87752849EE12728294970B350`; AndroidTest APK: `7B1FD4C1D30F905E9A89BDDE154632E6612A2B3B4334CC9D1CCAD23E3A442B6E`. The original phone wrapper exited 1 only in final reporting after all four cases, screenshots, Home return and test-handler removal, because it used PowerShell's reserved `$HOME` variable. Separate read-only finalization passed (`browse-layout-phone-finalization.exit`, 0), confirming Home, preserved all-files access and removal of the test-only handler; the original wrapper error remains recorded. No claim of an isolated unmodified PR phone build is made.

## Remaining acceptance

The Activity store still retains cached model objects after tab closure/root replacement. Their expensive work is now paused, but explicit store pruning and bounded tab-owned identity remain a follow-up; this increment does not claim full ownership cleanup. Existing tab-ID reuse can still recover an old paused model for the same Activity key. Persisted process-death restoration, provider lifecycle behavior and measured folder/frame improvements are also separate. The recorded recreation case closes that narrow physical gate and does not complete all U04 ownership acceptance.
