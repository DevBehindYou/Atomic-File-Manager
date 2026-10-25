# Audio player — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). It is separate from app and Media3 dependency versions.

Source inspected: `ecf1dc5834c125c9612be74128fc4ac34e65a046` (24 October 2026). The version-only increment changes inventory metadata, not the linked viewer engines.

## Implementation, features and limits

[AudioPreviewContent.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/AudioPreviewContent.kt) shows available artwork/metadata, play/pause, seeking/time, skip and speed controls. Audio starts paused; completion returns to the beginning paused. Shared [PreviewPlayer.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/PreviewPlayer.kt) binds/releases the player and pauses at STOP. [MediaPreviewHelper.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/preview/MediaPreviewHelper.kt) uses direct readable URIs or owned remote staging up to 512 MiB with cancellation cleanup.

Codec support depends on the device; available metadata does not imply tag-writing support. Background service/notification/lock-screen controls, persistent queues/playlists/resume, repeat/shuffle policy and tag writing are not delivered by this player. No new foreground-service permission is introduced by this version baseline.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| AUD-T01 | P0 / source gap; not reproduced here | Audit/test explicit preparation recovery; audio has the video's generic permanent failure shape. Reuse shared behavior only after its own consumers |
| AUD-T02 | P0 / open acceptance | Actual formats/VBR, malformed/missing tags, seek/end, source loss, STOP/recreation and repeated open/close |
| AUD-T03 | P0 / source candidate; acceptance pending | Focus/noisy-output/call/Bluetooth policy and accessible controls under pinned Media3 |
| AUD-T04 | P1 / deferred design/research | Intentional background/notification lifetime, folder queue, repeat/shuffle/resume with explicit persistence |
| AUD-T05 | P1 / deferred engine evaluation | Safe single-file tag/artwork editing per supported format before batch writes |

## Bugs and risks

**AUD-B01 — preparation failure has no explicit Retry:** source-confirmed gap, controlled runtime reproduction and fix acceptance pending. Keep its linked task open; a source observation is not a phone result.

AUD-T01 is a source-level recovery gap, not a newly reproduced phone bug. Require a failing unchanged-source consumer before crediting a fix. Focus, missing tags and disconnection need explicit acceptance. Absent background playback is a disclosed feature limit; no existing background service is claimed broken. Complete audio format/performance acceptance remains open.

## Validation and continuation

Historical `4792db8` [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md) and [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) cover earlier media work. `PreviewPlayerLifecycleTest`, `MediaPreviewOwnershipTest` and `MediaPreviewHelperTest` cover shared lifecycle/ownership, not every audio decoder. Current audio-specific native playback remains open. Current U09 and broader requirements/research in the [advanced intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) stay separately tracked and incomplete.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained tasks/features/risks/evidence record. Preserve foreground behavior; background, queue and tag-editing delivery is not implied.
## Focus/output policy candidate — 26 October 2026

The shared player source now delegates audio focus and becoming-noisy handling to pinned Media3 through media audio attributes and its built-in route-interruption receiver. The foreground preview lifecycle, STOP pause, release, preparation/retry behavior and staging limits stay in place. No custom focus listener, service, manifest permission or new dependency is added. Media usage applies to both audio and video; unspecified content type avoids mislabelling every file as music, film or speech.

The [focused candidate validation](../testing/MEDIA_PREVIEW_AUDIO_POLICY_VALIDATION.md) records the behavior-preserving bare-builder baseline8bdd603, seven real-player callback consumers on API27/34 and primary API research. Scoped formatting passed; configured parent host/device/CI/review gates are pending, so this is neither a tested focus fix nor complete modernized-plugin credit. Physical audio/video, audible ducking, calls, headset/Bluetooth events and API35+ top-app focus remain explicit acceptance work. Existing plugin version remainsv0.2 until a versioned, accepted release is justified.