# Selected-type owner transition consumers

Prepared 26 October 2026. Test-only addition to the current U06 increment. These are actual `FilePreviewPane` Compose consumers, separate from the frozen five-case policy suite; no existing method or fixture is changed.

## Regressions

`OpeningTypeOwnerTransitionComposeTest.manualOpenWithFromKnownBuiltInDrainsThePreviousUnknownProbeBeforeStartingAnother` holds unknown A's first input read, changes the parent to known PNG B in Built-in mode, waits for B's actual decoded image and all its image streams to close, then changes B's included metadata to an opaque name/MIME and chooses Open with. Only after decoded-viewer reads have finished does the fixture arm B's next acquisition as the selected-type probe. A bounded one-second negative acquisition latch and an overlap flag require B's new stream to wait for A's close. After releasing A, the test requires B's reader and exact-byte/read-only handoff, no stale A activity/preferences and unchanged source/sentinel bytes. Both gates are released in finally and teardown has bounded waits. It makes no network request: FTP-shaped opaque identities select a controlled FILE backend.

`successfulExternalHandoffDismissesRetainedPaneBeforeIncludedMetadataChanges` opens an opaque PNG, explicitly selects its PNG reader, retains the composition after acknowledged launch, and changes the included name/modified time. A bounded negative acquisition latch, unchanged stream count, absent choice panel, single activity/Close and raw original Recent metadata require successful handoff to terminate that opening session. Display-name-only recomposition would not exercise this case.

## Source review and evidence boundary

The interrupted owner-transition fixture was recovered as an untracked file, not recreated. Added success coverage and formatting are the only subsequent changes. Scoped ktlint formatting passes; no Gradle, APK, device or CI run was performed by this author. The parent owns configured compilation, baseline RED and final GREEN evidence.

Candidate `2d54dbee12353bbf3965005e2c0a4be97e25b166` disposed/replaced the selected-type owner on Open with while its non-cancellable input cleanup could still be blocked. The correction `2be25091575faa2ef4bf933c18120c69cfa26a76` retains the actor, admits metadata-only B through it, provides invalidation without acquiring another source, and creates a replacement only when a disposed actor has drained and a new non-dismissed parent is present. Discovery now keys raw metadata and checks the latest raw selection/actor before accepting results; successful handoff disposes through dismiss after acknowledgement, explicit preference handling and raw Recent recording. Independent source review found no remaining blocker in that correction. Exact test results remain pending; source review alone is not an acceptance claim.

The tests inspect the Recent repository argument, not Room persistence. They do not establish physical provider behaviour, transport-prefix support, broad format recognition, performance or full milestone/plugin completion. No source artwork, settings, grants or external files are mutated.