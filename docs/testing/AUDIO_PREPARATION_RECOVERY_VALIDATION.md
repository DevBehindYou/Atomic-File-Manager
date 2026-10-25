# Audio preparation recovery — candidate validation

Prepared 26 October2026. This is a focused Audio P0 slice within the separately tracked eight-plugin program. No complete Audio/plugin/milestone, physical playback or performance acceptance is claimed.

## Source gap and frozen consumers

The inspected AudioPreviewContent remembers preparation/failure/player state and launches acquisition only by node identity. Failure is reduced to a permanent hardcoded message without explicit preparation Retry. Updated modifiedAt/size at the same identity does not start fresh preparation. These are source observations until the parent runs unchanged-source consumers.

AudioPreparationRecoveryComposeTest adds five actual AudioPreviewContent consumers using the existing AppContainer/MediaPreviewHelper boundary and only a uniquely owned cache directory. The payloads verify staging bytes and ownership; controlled metadata does not establish decoded audio.

1. Unavailable source remains idle, then explicit preparation Retry acquires once and shows the recovered staged audio metadata; close removes the captured copy.
2. Repeated preparation failures each require another explicit Retry; no retry loop, metadata read or player setup occurs after failed acquisition.
3. Same-ID changed modifiedAt/size blocks a new acquisition; old metadata disappears while loading, the old copy is removed, revised metadata/bytes arrive and the player starts paused. A display-name-only change would not reproduce this ownership gap.
4. Switching identity cancels pending acquisition, removes its partial copy and shows only replacement metadata/bytes, paused. This is a preservation control.
5. Disposal cancels pending acquisition and removes the partial staging file without reading metadata. This is a preservation control.

The first test-only commit precedes production changes; no existing Video/media test assertion is changed. Scoped formatting passes. The parent must compile/run the frozen baseline and record actual failure/control counts before claiming reproduction, then rerun unchanged consumers on the candidate. Expected baseline shape is three missing-recovery/revision failures and two cleanup controls; this is not an executed result.

## Candidate boundary

The intended source correction follows the accepted Video preparation/attempt ownership pattern without changing its code: key the Audio source by identity, known revision/size and helper, keep typed returned FileError in the attempt, and expose an explicit recoverable preparation Retry that starts a fresh keyed attempt. Keep Audio starts-paused, current shared STOP/release/focus policy, playback Retry, owned bounded media staging and all controls intact. New preparation text uses resources. Some helper failures are normalized to IoFailure, so this surface cannot promise provider-specific diagnosis. No background service, permissions, queues, tag writes, engine/dependency change, hardware mutation or unrelated storage is introduced.

## Pending gates

Author has not run Gradle, ADB or CI. Parent owns actual baseline/candidate host evidence, affected media lifecycle/helper/Video regressions, static/APK/Lint, current-head CI, independent review and guarded feature integration. Physical decoded audio, formats/VBR/tags, audible focus/headset behavior, STOP/recreation, source loss and performance stay open. Earlier accepted Media3/Video surfaces cannot certify this new Audio slice. Existing plugin version staysv0.2; partial source work earns no whole-plugin acceptance credit.
## Source candidate prepared — 26 October2026

Test-first3ad298772c92ff19baa37abaf0bbc3b40f22a1fa is frozen before the Audio source fix. The subsequent production candidate implements the boundary above in AudioPreviewContent.kt and audio_preparation_strings.xml only, plus this plugin context. Source revision/size/helper and attempt keys own all preparation/player state; returned typed failures drive explicit recoverable Retry. Scoped source/test formatting passes. Parent has not yet run the baseline or corrected consumers; original expected counts remain predictions, not results. Existing media policy94703bb is the branch parent and must retain its own focused checks/review.