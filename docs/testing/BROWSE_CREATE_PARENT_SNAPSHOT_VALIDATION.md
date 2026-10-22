# Browse create-folder acceptance target — narrow prerequisite

Prepared 22 October 2026 from accepted feature integration `dbb43f0`, then synchronized with accepted tab-session integration `e60d8e7`. [PR #74](https://github.com/DevBehindYou/Atomic-File-Manager/pull/74) changes only the argument captured by `BrowseViewModel.createFolder`. Focused host execution, independent source review, final-head CI and guarded feature integration now pass; broader milestone and release gates remain open.

## Problem and change

`createFolder(name)` previously read `currentFolderId` from inside its deferred `viewModelScope.launch`. A queued navigation can publish another current folder before that coroutine starts, redirecting an accepted create to a different parent. Capture the currently displayed parent synchronously when accepting the call, then pass that immutable ID to the existing `CreateDirectoryUseCase`.

Successful completion still calls `refresh()` on the current pane. It must not navigate back to the captured parent or reload that parent after the user has moved elsewhere. Failure reporting and ViewModel-owned cancellation are unchanged. This slice introduces no mutation leases, model registry, pruning, queue admission changes, global work scope, dependencies, permissions or preference writes.

## Controlled host regressions

`BrowseCreateFolderTargetTest` uses the real BrowseViewModel, CreateDirectoryUseCase and existing InMemoryBackend, wrapped only to expose controlled backend entry/completion and listing requests. All directory mutations occur in the test's in-memory map.

- Acceptance race: queue navigation while the displayed model state remains at the original parent; accept Create before advancing test Main. When Main resumes, the queued navigation publishes Destination before create work starts. Require the backend to receive the original parent, verify the new folder exists only there, and preserve Destination as the current pane. A simple Create-then-navigation FIFO test would not expose the old worker-time read; this schedule deliberately does.
- Late success: suspend create after backend entry, navigate to Destination, then release the original-parent create. Require Destination to refresh, no additional original-parent listing or navigation, correct original-parent contents, and intact Back history.

The focused helper injects the controlled Main dispatcher into listing/node use cases, retains the existing real Default filtering path, and cancels/joins the ViewModel owner before resetting test Main.

## Required red/green and integration gates

The parent owns serialized execution. First add only `BrowseCreateFolderTargetTest.kt` to the validation checkout with the old `createFolder` implementation and run:

```powershell
gradle :app:testDebugUnitTest --tests com.devbehindyou.atomicfilemanager.ui.screens.BrowseCreateFolderTargetTest
```

The acceptance-race case must fail because the backend receives Destination rather than the original parent. The late-success case is a behavior-preservation regression and should already pass. Restore the two-line production fix and rerun both cases, related primary navigation/state and sort cases, static checks and applicable compilation gates. This section defines the procedure; actual red/green, build and review results are recorded below. Source synchronization and publication do not replace final-head CI or a guarded integration trial.

This prerequisite does not solve retained model accumulation or accepted mutations surviving model retirement. Those remain separate Phase 0 slices. It adds no complete milestone to the fixed 13-item progress denominator and does not complete main/release acceptance.

## Executed checkpoint — 22 October

The regression first ran against unchanged production app tree `9ec9b8dc8acda01fe5e37fb9e72eaf95876b1c64` with only the new test added. `browse-create-parent-red-validation.log` exits 1 in 54s: 2 cases, exactly 1 expected failure, no errors/skips. The backend receives `file:/mem/1` instead of accepted parent `file:/mem/0`. The late-success preservation case already passes. Original XML is retained as `browse-create-parent-red-test-results.xml`.

With the fix, root source `ecabeea` and synchronized PR source share app tree `015a80f1e0235d4989044473663b4ca7d1fb05f0`. `browse-create-parent-green-validation.log` exits 0 in 6m46s: all 30 focused cases pass without failures/errors/skips (2 create-target, 3 navigation identity, 11 sort-loading, 2 real session-model, 8 STARTED lifecycle, 1 primary state and 3 visibility). ktlint/detekt, debug/AndroidTest packaging and debug Lint pass. Lint reports 0 errors, 154 existing warnings and 1 hint. Saved result JSON and host artifact metadata are under `build/audit-tools/browse-create-parent-*`.

Debug APK SHA-256: `9A2E929061F5D182E7AD2ACE7F0AF0D2264915CF939C3F2171A74EA7724D220E`; test APK: `BED18771E468D770764FB3AD9DDA1742D9ED6A55381E23C79E298858E589ACC8`. These are host-built artifacts, not new physical mutation acceptance. The installed and phone-tested startup/Analysis/tab checkpoint remains the separately recorded `9ec9b8d` app tree; its four native cases must not be relabeled as tests of this new source. No real user file, directory, permission or preference was changed for these regressions.

Independent source review found no remaining blocker in this narrow change. Final head `e89113647585baaa1ecd82a8371ffbc9804ed513` passes both required jobs in CI `39808085463`, including debug/release Lint and full unit/APK packaging. Manual emulator execution is skipped, not a device pass. Review-thread inspection, accepted dependency state and a fresh clean guarded trial preceded feature-integration merge `c76f86ab8a33c8daaebf2799e1e68b43ba791cba`. Gate and trial records are retained in `build/audit-tools/pr74-acceptance/`. Broader leases/pruning/provider/release acceptance stays open.
