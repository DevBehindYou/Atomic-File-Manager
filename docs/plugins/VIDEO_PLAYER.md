# Video player — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). This is not the Media3 dependency version or decoded-playback acceptance.

Source checkpoint: `3b0ede60f1804d5277b43810a6c42c2611cc77a5` (24 October 2026). The existing U09 preparation-recovery increment changes failure/attempt ownership; the engine, helper staging cap and shared player lifecycle remain unchanged.

## Implementation, features and limits

[VideoPreviewContent.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/VideoPreviewContent.kt) binds Media3 PlayerView controls for play/pause, seek, available speed settings, buffering and dimensions/duration. Shared [PreviewPlayer.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/PreviewPlayer.kt) owns the player, binds its lifecycle and pauses on STOP; post-player playback failures expose retry. [MediaPreviewHelper.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/preview/MediaPreviewHelper.kt) uses direct readable local/content URIs or owned remote staging capped at 512 MiB, with cancellation/cleanup.

Codec/container support depends on the device. A prepared URI or host-created player is not decoded-video evidence. Remote inputs are staged within a cap, not universally streamed. Rotation/resume, comprehensive subtitle/track controls, PiP, playlists, background modes and advanced gestures remain unaccepted.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| VID-T01 | P0 / host/review and app-surface pass; CI/integration pending | Explicit preparation retry now passes frozen no-loop/staging/cleanup consumers; complete exact-head CI/guarded integration and track real playback separately under VID-T02 |
| VID-T02 | P0 / open acceptance | Real codec/container fixtures, corruption, seek/stalls, rapid switching, recreation/position and disconnection |
| VID-T03 | P0 / deferred audit | Pinned engine audio-focus/noisy-output policy, calls, Bluetooth/headset loss and STOP/re-entry |
| VID-T04 | P1 / deferred research | Supported subtitle/tracks, resume/aspect/rotation and optional PiP/background controls, with engine/API/size/license evidence |

## Bugs and risks

**VID-B01 — preparation failure has no explicit Retry:** reproduced by two actual unchanged-source Compose assertions; source correction passes the host gate and awaits final CI/integration. See VID-T01 and [the validation record](../testing/VIDEO_PREPARATION_RECOVERY_VALIDATION.md).

The prior preparation surface discarded the typed failure and could not recover without reopening. The current keyed source/attempt retains the returned error and exposes explicit Retry only when recoverable; new attempts clear loading/failure/prepared/player state. VID-T01 has two intended old-source failures and a passing cancellation control, then all 33 affected host cases/static/APKs/Lint pass. Four current native app surfaces pass; these are not decoded-video acceptance. CI/integration remains pending; preserve the accepted STOP/ownership work. Some remote helper failures become `IoFailure`; that does not support provider-specific diagnosis.

No current decoded-playback device matrix, seek/frame benchmark or focus acceptance is established here. These are open checks rather than fabricated newly observed failures.

## Validation and continuation

Historical `4792db8` [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md) and [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) distinguish acquisition/lifecycle changes from decoded playback. `PreviewPlayerLifecycleTest`, `MediaPreviewOwnershipTest` and `MediaPreviewHelperTest` cover STOP, direct sources, staging limits, IO/cancellation/cleanup, not every codec. VID-T01 is an existing U09 increment. [Current validation](../testing/VIDEO_PREPARATION_RECOVERY_VALIDATION.md) records source3b0ede6/treeaa40f2ab57da681e0e9e3135bb30ff8e26092948, 33 host cases/static/APKs/Lint in11m26s and four API34 app-surface cases in23.58s, with exact hashes and limits. Publication/current-head CI/guarded integration remain pending. The [advanced intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) and its research stay separate and deferred.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained record; preserve Media3 controls/ownership limits. VID-T01 preparation Retry passes host/review and selected app-surface checks and awaits CI/integration; real playback remains separate from implemented lifecycle work. No new engine or advanced-video acceptance is claimed.