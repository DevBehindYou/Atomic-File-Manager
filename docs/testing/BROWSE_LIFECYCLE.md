# Browse secondary-pane lifecycle (M05)

The secondary BrowseViewModel now has a ViewModelStore owned by its Browse composition.
The store is cleared when the composition leaves, its tab/root changes, or the layout
becomes compact. RememberObserver also clears an abandoned composition. Clearing cancels
the model's listing and app-wide operation collectors. No secondary model or listing is
created in a single-pane layout. Switching a wide layout between Browse and Preview retains
the secondary model and its current location.

The primary model still belongs to the Activity and is keyed by tab plus initial folder.
Its accumulation across explicit folder opens and closed tabs is deferred: correct cleanup
needs the route/tab owner to distinguish tab closure from temporary screen removal and
configuration changes. Clearing it on composition disposal would discard existing tab and
rotation state. The secondary pane already recreated on rotation before this change; it
continues to do so, with its previous work now cancelled.

`BrowsePaneLifecycleTest` uses the actual BrowseViewModel and OperationQueue with a suspended
fake listing. It covers compact layouts, composition removal, resizing, same-folder tab
switches, folder changes, ordinary recomposition, and an operation finishing after disposal.
Run `:app:testDebugUnitTest --tests '*BrowsePaneLifecycleTest'` and the existing Browse tab
tests, then ktlint/detekt/Lint and the normal build gates. Tests were added but have not been
executed in this worktree: the parent session owns Gradle verification while dependency
bootstrap is unavailable. No Android frame-time or memory improvement is claimed.
