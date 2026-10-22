# Browse create-folder acceptance target — narrow prerequisite

Prepared 22 October 2026 from accepted feature integration `dbb43f0`. This candidate changes only the argument captured by `BrowseViewModel.createFolder`; host execution, review and CI remain pending.

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

The acceptance-race case must fail because the backend receives Destination rather than the original parent. The late-success case is a behavior-preservation regression and should already pass. Restore the two-line production fix and rerun both cases, related primary navigation/state and sort cases, static checks and applicable compilation gates. These commands describe pending work; no red/green, build or CI result is claimed here. The parent will synchronize accepted dependencies and record exact-source evidence before publishing or merging a focused PR.

This prerequisite does not solve retained model accumulation or accepted mutations surviving model retirement. Those remain separate Phase 0 slices. It adds no complete milestone to the fixed 13-item progress denominator and does not complete main/release acceptance.
