# Revision-aware video resume — C012

Claude Code task **C012** (VID3; VID-T02/VID-T04 increment). Base: feature integration `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Recorded 7 November 2026. Host-verified; the native workflow is added and runs on the PR.

## Defect and failing control

The Video preview kept no resume point. Each new player started at zero and autoplayed. This happened after a recreation and after a close and reopen, even for an unchanged file the viewer had just paused.

The control was `VideoResumeComposeTest`, with two cases written against the base API only (`VideoPreviewContent(node, container)`), run on unchanged `89140e3`:

| Case | Result on base |
| --- | --- |
| `recreationRestoresPausedPositionOfTheSameRevision` (seek to 42 s, pause, `StateRestorationTester.emulateSavedInstanceStateRestore`) | **failed**: `expected:<42000.0> but was:<0.0>` |
| `reopeningTheSameRevisionResumesWhereItWasClosed` (seek to 65 s, dispose, recompose) | **failed**: `expected:<65000.0> but was:<0.0>` |

That is 2/2 failing on base. Both pass after the change. The replacement-file guard passes on base only because base never resumes anything, so it is a guard and not a control.

Two mutations were also run against the new code. Turning the STOP checkpoint into DESTROY made `stopFlushesThePositionSoABackgroundProcessDeathCanResume` fail on both API levels. Dropping the revision comparison in the store made `aReplacementFileAtTheSameSourceStartsFromTheBeginning` (`expected:<0> but was:<42000>`) and `aChangedTimeOrSizeIsAReplacementAndClearsTheStaleEntry` fail. Both mutations were reverted.

## Change

- `domain/repository/VideoResumeMemory.kt` adds three types:
  - `VideoRevision`: the source id (`FileNodeId.raw`, which already names the provider and account), `modifiedAt` and `size`. These are the same values that already key preparation. It is null for directories and for an unknown modification time, and nothing is remembered for such a revision.
  - `VideoResumeState`: position, play/pause and speed.
  - The `VideoResumeMemory` interface.
- `data/repository/VideoResumePreferences.kt` and `VideoResumeCodec.kt` follow the `FolderSortPreferences` and codec pattern, with no Room schema:
  - Storage is the private SharedPreferences file `atomic_video_resume`. It keeps one entry per source, keyed by SHA-256 of the source id, so no path or account name is stored.
  - Values are versioned JSON capped at 256 characters. Position must be 0 to 7 days, speed 0.25 to 4×, and anything else is dropped.
  - The store keeps at most **200** entries and evicts the least recently written. Reading a different revision of the same source removes the stale entry.
  - `read` loads the file on an injected IO dispatcher. After that, `save` and `forget` only edit the framework's in-memory map and `apply()` it, so disk writes stay asynchronous and off the main thread. A write that arrives before any read loads the file on IO first.
  - All instances in a process share the framework's one map for the file. A recreated viewer therefore never reads behind its predecessor's last write, and the store keeps no process-wide state of its own.
- `ui/components/preview/VideoResumeSession.kt` holds the policy:
  - It decides the start point once, when a player is created. A recomposition reuses the same session and never seeks again.
  - Explicit checkpoints write only real changes. Play/pause and speed are always written. A position change is written only if it is at least 1 s.
  - Progress writes happen at most once per 15 s of monotonic time.
  - Ending playback, a point within 2 s of the known duration, or a point under 1 s at 1× forgets the entry.
  - A saved point the file can no longer reach is not applied and is forgotten.
- `VideoPreviewContent.kt` does the wiring:
  - It reads the saved state for the exact revision before the player exists.
  - It passes the restored play/pause request through the existing autoplay policy, and seeks and sets speed only on the newly created player. This happens in a `DisposableEffect(player)` that runs before the recorder attaches, so restoring is never itself recorded.
  - It records on play/pause change, speed change, `STATE_ENDED`, lifecycle `ON_STOP` (STOP comes before any background process death), every 15 s while playing, and disposal. Disposal runs before the shared player is released.
- `PreviewPlayer.kt`, `MediaPreviewHelper.kt` and `AtomicApp.kt` are unchanged. The preview's default argument creates its own `VideoResumePreferences(application)`, and tests inject their own store. Moving it onto the manual `AppContainer` is left for after C003 releases `AtomicApp.kt`.
- The stored play/pause follows the accepted STOP policy. STOP pauses the preview, so a preview recovered after a background kill opens paused at its position, and foregrounding still never restarts playback.

## Validation (local, serialized Gradle)

| Command | Result |
| --- | --- |
| `./gradlew :app:ktlintCheck :app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest --tests '*VideoResume*' --tests '*VideoPreparationRecoveryComposeTest*' --tests '*PreviewPlayer*' --tests '*MediaPreview*'` | BUILD SUCCESSFUL. **79 tests, 0 failed, 0 skipped**. |

The 79 tests break down as follows:

| Suite | Tests | What it covers |
| --- | --- | --- |
| `VideoResumeComposeTest` | 20 (10 cases × API 28/34) | Real Media3 players with the real store |
| `VideoResumePreferencesTest` | 14 (7 × API 28/34) | The store |
| `VideoResumeSessionTest` | 9 | Pure JVM policy |
| Existing `VideoPreparationRecoveryComposeTest`, `PreviewPlayerAudioPolicyTest`, `PreviewPlayerLifecycleTest`, `MediaPreviewHelperTest`, `MediaPreviewOwnershipTest` | 36 | Unchanged |

Validation items and the cases that cover them:

| Validation item | Covered by |
| --- | --- |
| Position tolerance | Compose cases assert the restored position within ±1 s. Session: 999 ms is not written and 1 000 ms is. |
| Paused restore | `recreationRestores…` and `repeatedNavigation…`: paused, `playWhenReady == false`. |
| Changed revision | `aReplacementFile…`: a changed time and size starts at 0, keeps the default autoplay and 1× speed, and removes the stale entry. Store: a change to time alone or size alone clears the entry. |
| Failed seek | `aSavedPositionTheFileCannotReach…`: 119.5 s of 120 s is not applied, starts at 0 and is forgotten once. Session: an unknown duration still applies the point. |
| Rotation/recreation | `StateRestorationTester` disposes and recreates the preview. The new player resumes paused at 42 s. |
| Process recovery | `stopFlushes…`: pause, then seek (0 writes from seeking), then STOP. That makes exactly 1 write, and a fresh store instance over the same file reads 33 s paused. |
| Recompose vs recreate | `recomposingWithAnEqualRevision…`: a refreshed node of the same revision keeps the same player and the user's 50 s position, with no re-seek to the saved 20 s and 0 writes. |
| Source loss | `sourceLoss…`: when the file is gone, preparation fails, nothing is written and the entry is kept. After an explicit Retry with the file back, the preview resumes paused at 24 s. |
| Repeated navigation | Three close/reopen cycles of a restored revision produce **0** saves and **0** forgets. |
| Write counts | Recreation after a pause: 1 save. Close while playing: 1 save. Speed change: 1 save. Session bound: 10 minutes of 100 ms ticks give at most 40 progress writes (≤ 600 s / 15 s). |
| Original integrity | Every compose case checks the original bytes and `lastModified` in `@After`. The device test checks the bytes and the directory listing. |
| Unknown revision | `modifiedAt == 0` makes no reads, saves or forgets and always starts at 0. |

Robolectric fixture bytes are not decodable video. These host cases prove where a new player starts, what it requests and what is written. They do not prove decoded playback.

## Native

`.github/workflows/video-resume-native.yml` is new. No video native workflow existed before. It runs `VideoResumeDeviceTest` on API 28 and API 34 emulators, path-filtered to the resume, preview, player and helper sources.

The device test generates a 20 s app-private PCM WAV and plays it through `VideoPreviewContent` with real Media3 decoding. It does the following:

1. Waits for ≥ 1 s of playback, seeks to 9 s and pauses.
2. Recreates the preview and asserts the decoded position is within 500 ms and stays paused. It checks that the clock holds over 600 ms.
3. Asserts the original bytes are unchanged.
4. Replaces the file at the same path with 12 s of audio and a newer time, and asserts the replacement plays from the start.
5. Removes only its own files and resume entries.

PCM is not a video codec, so this is decoded-resume evidence and not codec acceptance.

| Head | Run | Result |
| --- | --- | --- |
| `017ddbd` | [42340397852](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/42340397852) | `video-resume (28)` **success**, `video-resume (34)` **success** |

## Lint fix on the second commit

On head `017ddbd`, hosted Static analysis (run 42340397793) failed Android Lint for both `lintDebug` and `lintRelease`. The error was `VideoPreviewContent.kt:180: remember calls must not return Unit [RememberReturnType]`, caused by the one-time seek/speed side effect inside `remember(player)`.

That side effect now runs in `DisposableEffect(player)`, which still runs once per new player. Behaviour and test counts are unchanged. There is no baseline and no suppression.

The fix was rerun locally under the same lock:

| Command | Result |
| --- | --- |
| `./gradlew :app:ktlintCheck :app:detekt :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest` with the same four filters as above | BUILD SUCCESSFUL, 79 tests, 0 failed |
| `./gradlew :app:lintDebug` | LINT_RESULT |

The native workflow reruns on the new head because `VideoPreviewContent.kt` changed.

## Still open

- Hosted Static analysis and the native rerun on the lint-fix head, and Codex review.
- The preview dialog's open state is held in a plain `remember` in Browse, Category, Search and Home, so a real activity rotation still closes the preview. The resume point makes the reopened video continue where it was, but keeping the dialog open across rotation is host-screen work outside this slice.
- Wiring `VideoResumePreferences` into `AppContainer` is waiting on `AtomicApp.kt`, which is held by C003.
- PlayerView has no aspect or resize control, so speed is the only viewing mode restored. Subtitle and track selection, PiP and real video-codec fixtures stay under VID-T02 and VID-T04.
- The Audio preview does not use this store.
