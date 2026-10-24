# Video player — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). This is not the Media3 dependency version or decoded-playback acceptance.

Source inspected: `ecf1dc5834c125c9612be74128fc4ac34e65a046` (24 October 2026). The version-only increment changes inventory metadata, not the linked viewer engines.

## Implementation, features and limits

[VideoPreviewContent.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/VideoPreviewContent.kt) binds Media3 PlayerView controls for play/pause, seek, available speed settings, buffering and dimensions/duration. Shared [PreviewPlayer.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/PreviewPlayer.kt) owns the player, binds its lifecycle and pauses on STOP; post-player playback failures expose retry. [MediaPreviewHelper.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/preview/MediaPreviewHelper.kt) uses direct readable local/content URIs or owned remote staging capped at 512 MiB, with cancellation/cleanup.

Codec/container support depends on the device. A prepared URI or host-created player is not decoded-video evidence. Remote inputs are staged within a cap, not universally streamed. Rotation/resume, comprehensive subtitle/track controls, PiP, playlists, background modes and advanced gestures remain unaccepted.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| VID-T01 | P0 / source gap; separate regression preparation active | Explicit preparation retry without reopening: prove no automatic loop, fresh loading, recovery/cancellation and owned-copy cleanup |
| VID-T02 | P0 / open acceptance | Real codec/container fixtures, corruption, seek/stalls, rapid switching, recreation/position and disconnection |
| VID-T03 | P0 / deferred audit | Pinned engine audio-focus/noisy-output policy, calls, Bluetooth/headset loss and STOP/re-entry |
| VID-T04 | P1 / deferred research | Supported subtitle/tracks, resume/aspect/rotation and optional PiP/background controls, with engine/API/size/license evidence |

## Bugs and risks

**VID-B01 — preparation failure has no explicit Retry:** source-confirmed gap, controlled runtime reproduction and fix acceptance pending. Keep its linked task open; a source observation is not a phone result.

Current source discards preparation failure details and shows a permanent generic message without Retry, unlike post-player errors. VID-T01 is this source-confirmed recovery gap; unchanged-source UI reproduction/fix evidence is pending. Do not duplicate accepted STOP/ownership work or call the preparation change accepted before its tests. Some remote helper failures become `IoFailure`; that does not support provider-specific diagnosis.

No current decoded-playback device matrix, seek/frame benchmark or focus acceptance is established here. These are open checks rather than fabricated newly observed failures.

## Validation and continuation

Historical `4792db8` [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md) and [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) distinguish acquisition/lifecycle changes from decoded playback. `PreviewPlayerLifecycleTest`, `MediaPreviewOwnershipTest` and `MediaPreviewHelperTest` cover STOP, direct sources, staging limits, IO/cancellation/cleanup, not every codec. VID-T01 is an existing U09 increment. The [advanced intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) and its research stay separate and deferred.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained record; preserve Media3 controls/ownership limits. Record VID-T01 separately from implemented lifecycle work. No new engine or advanced-video acceptance is claimed.