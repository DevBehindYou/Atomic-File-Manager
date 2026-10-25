# Video player — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). This is not the Media3 dependency version or decoded-playback acceptance.

Source checkpoint: `3b0ede60f1804d5277b43810a6c42c2611cc77a5` (24 October 2026). The existing U09 preparation-recovery increment changes failure/attempt ownership; the engine, helper staging cap and shared player lifecycle remain unchanged.

## Implementation, features and limits

[VideoPreviewContent.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/VideoPreviewContent.kt) binds Media3 PlayerView controls for play/pause, seek, available speed settings, buffering and dimensions/duration. Shared [PreviewPlayer.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/PreviewPlayer.kt) owns the player, binds its lifecycle and pauses on STOP; post-player playback failures expose retry. [MediaPreviewHelper.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/preview/MediaPreviewHelper.kt) uses direct readable local/content URIs or owned remote staging capped at 512 MiB, with cancellation/cleanup.

Codec/container support depends on the device. A prepared URI or host-created player is not decoded-video evidence. Remote inputs are staged within a cap, not universally streamed. Rotation/resume, comprehensive subtitle/track controls, PiP, playlists, background modes and advanced gestures remain unaccepted.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| VID-T01 | P0 / accepted into feature integration | Explicit preparation retry now passes frozen no-loop/staging/cleanup consumers; PR #82 passes exact-head CI/review/guarded integration; track real playback separately under VID-T02 |
| VID-T02 | P0 / open acceptance | Real codec/container fixtures, corruption, seek/stalls, rapid switching, recreation/position and disconnection |
| VID-T03 | P0 / source candidate; acceptance pending | Pinned engine audio-focus/noisy-output policy, calls, Bluetooth/headset loss and STOP/re-entry |
| VID-T04 | P1 / deferred research | Supported subtitle/tracks, resume/aspect/rotation and optional PiP/background controls, with engine/API/size/license evidence |

## Bugs and risks

**VID-B01 — preparation failure has no explicit Retry:** reproduced by two actual unchanged-source Compose assertions; fixed in feature integration by PR #82 after host/review/CI gates; broader playback acceptance stays open. See VID-T01 and [the validation record](../testing/VIDEO_PREPARATION_RECOVERY_VALIDATION.md).

The prior preparation surface discarded the typed failure and could not recover without reopening. The current keyed source/attempt retains the returned error and exposes explicit Retry only when recoverable; new attempts clear loading/failure/prepared/player state. VID-T01 has two intended old-source failures and a passing cancellation control, then all 33 affected host cases/static/APKs/Lint pass. Four current native app surfaces pass; these are not decoded-video acceptance. PR #82 is accepted into feature integration; preserve the STOP/ownership work and wider acceptance boundary. Some remote helper failures become `IoFailure`; that does not support provider-specific diagnosis.

No current decoded-playback device matrix, seek/frame benchmark or focus acceptance is established here. These are open checks rather than fabricated newly observed failures.

## Validation and continuation

Historical `4792db8` [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md) and [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) distinguish acquisition/lifecycle changes from decoded playback. `PreviewPlayerLifecycleTest`, `MediaPreviewOwnershipTest` and `MediaPreviewHelperTest` cover STOP, direct sources, staging limits, IO/cancellation/cleanup, not every codec. VID-T01 is an existing U09 increment. [Current validation](../testing/VIDEO_PREPARATION_RECOVERY_VALIDATION.md) records source3b0ede6/treeaa40f2ab57da681e0e9e3135bb30ff8e26092948, 33 host cases/static/APKs/Lint in11m26s and four API34 app-surface cases in23.58s, with exact hashes and limits. [PR #82](https://github.com/DevBehindYou/Atomic-File-Manager/pull/82), final headde85c88, passes CI40115420936 and a clean guarded feature merge338f37ddd54bb679d448124a054e42148d360fb9. The [advanced intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) and its research stay separate and deferred.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained record; preserve Media3 controls/ownership limits. VID-T01 preparation Retry passes host/review and selected app-surface checks and is accepted into feature integration through PR #82; real playback remains separate from implemented lifecycle work. No new engine or advanced-video acceptance is claimed.
## Focus/output policy candidate — 26 October 2026

The shared player source now delegates audio focus and becoming-noisy handling to pinned Media3 through media audio attributes and its built-in route-interruption receiver. The foreground preview lifecycle, STOP pause, release, preparation/retry behavior and staging limits stay in place. No custom focus listener, service, manifest permission or new dependency is added. Media usage applies to both audio and video; unspecified content type avoids mislabelling every file as music, film or speech.

The [focused candidate validation](../testing/MEDIA_PREVIEW_AUDIO_POLICY_VALIDATION.md) records the behavior-preserving bare-builder baseline8bdd603, seven real-player callback consumers on API27/34 and primary API research. Scoped formatting passed; configured parent host/device/CI/review gates are pending, so this is neither a tested focus fix nor complete modernized-plugin credit. Physical audio/video, audible ducking, calls, headset/Bluetooth events and API35+ top-app focus remain explicit acceptance work. Existing plugin version remainsv0.2 until a versioned, accepted release is justified.