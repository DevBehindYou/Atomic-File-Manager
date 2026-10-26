# Audio player — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). It is separate from app and Media3 dependency versions.

Source inspected: `ecf1dc5834c125c9612be74128fc4ac34e65a046` (24 October 2026). The version-only increment changes inventory metadata, not the linked viewer engines.

## Implementation, features and limits

[AudioPreviewContent.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/AudioPreviewContent.kt) shows available artwork/metadata, play/pause, seeking/time, skip and speed controls. Audio starts paused; completion returns to the beginning paused. Shared [PreviewPlayer.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/preview/PreviewPlayer.kt) binds/releases the player and pauses at STOP. [MediaPreviewHelper.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/preview/MediaPreviewHelper.kt) uses direct readable URIs or owned remote staging up to 512 MiB with cancellation cleanup.

Codec support depends on the device; available metadata does not imply tag-writing support. Background service/notification/lock-screen controls, persistent queues/playlists/resume, repeat/shuffle policy and tag writing are not delivered by this player. No new foreground-service permission is introduced by this version baseline.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| AUD-T01 | P0 / source candidate; parent validation pending | Explicit typed preparation Retry and known revision/attempt ownership; five dedicated Audio consumers await baseline/candidate execution |
| AUD-T02 | P0 / open acceptance | Actual formats/VBR, malformed/missing tags, seek/end, source loss, STOP/recreation and repeated open/close |
| AUD-T03 | P0 / source candidate; acceptance pending | Focus/noisy-output/call/Bluetooth policy and accessible controls under pinned Media3 |
| AUD-T04 | P1 / deferred design/research | Intentional background/notification lifetime, folder queue, repeat/shuffle/resume with explicit persistence |
| AUD-T05 | P1 / deferred engine evaluation | Safe single-file tag/artwork editing per supported format before batch writes |

## Bugs and risks

**AUD-B01 — preparation failure has no explicit Retry:** source-confirmed gap, controlled runtime reproduction and fix acceptance pending. Keep its linked task open; a source observation is not a phone result.

**AUD-B02 — known revision at the same identity retains prepared metadata/player state:** source-observed id-only ownership gap. The candidate keys known modified time/size and helper; actual baseline/candidate and physical acceptance remain pending.

AUD-T01 is a source-level recovery gap, not a newly reproduced phone bug. Require a failing unchanged-source consumer before crediting a fix. Focus, missing tags and disconnection need explicit acceptance. Absent background playback is a disclosed feature limit; no existing background service is claimed broken. Complete audio format/performance acceptance remains open.

## Validation and continuation

Historical `4792db8` [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md) and [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) cover earlier media work. `PreviewPlayerLifecycleTest`, `MediaPreviewOwnershipTest` and `MediaPreviewHelperTest` cover shared lifecycle/ownership, not every audio decoder. Current audio-specific native playback remains open. Current U09 and broader requirements/research in the [advanced intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) stay separately tracked and incomplete.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained tasks/features/risks/evidence record. Preserve foreground behavior; background, queue and tag-editing delivery is not implied.
## Focus/output policy candidate — 26 October 2026

The shared player source now delegates audio focus and becoming-noisy handling to pinned Media3 through media audio attributes and its built-in route-interruption receiver. The foreground preview lifecycle, STOP pause, release, preparation/retry behavior and staging limits stay in place. No custom focus listener, service, manifest permission or new dependency is added. Media usage applies to both audio and video; unspecified content type avoids mislabelling every file as music, film or speech.

The [focused candidate validation](../testing/MEDIA_PREVIEW_AUDIO_POLICY_VALIDATION.md) records the behavior-preserving bare-builder baseline8bdd603, seven real-player callback consumers on API27/34 and primary API research. Scoped formatting passed; configured parent host/device/CI/review gates are pending, so this is neither a tested focus fix nor complete modernized-plugin credit. Physical audio/video, audible ducking, calls, headset/Bluetooth events and API35+ top-app focus remain explicit acceptance work. Existing plugin version remainsv0.2 until a versioned, accepted release is justified.
## Audio preparation recovery candidate — 26 October2026

The focused source follows the accepted Video preparation ownership pattern while preserving Audio's paused start: a source/revision/helper key owns a separate retry-attempt key, returned FileError, loading state, prepared URI and player state. Recoverable acquisition failures expose explicit Retry without a loop; new attempts clear prior failure/prepared state. Preparation labels now use resources. Cancellation keeps the existing withMedia cleanup and512MiB remote staging cap; no backend or shared player source changes are included.

The [dedicated validation record](../testing/AUDIO_PREPARATION_RECOVERY_VALIDATION.md) owns test-first3ad2987, five actual Compose consumers, expected unchanged-source regressions versus preservation controls, and pending evidence. No Gradle/device/CI/review result is claimed by this candidate. Shared focus-policy94703bb remains a separate focused slice; neither one establishes actual Audio formats, audible output, background lifetime, tag editing or a complete modernized Audio plugin. Version remainsv0.2.