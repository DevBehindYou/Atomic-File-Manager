# Media preview focus and output-policy consumers

Prepared 26 October 2026 against feature-integration4d7157f. This is a focused shared Video/Audio candidate, not whole-plugin modernization or decoded-playback acceptance.

## Observed source and implementation boundary

The existing shared rememberPreviewPlayer creates ExoPlayer1.8.0 with the bare builder. It does not request automatic audio-focus management or automatic pause when audio is rerouted from a headset to speakers. These are source-observed gaps, not previously reproduced physical failures. Existing STOP, release-after-player, media staging, Video explicit preparation Retry and playback Retry remain separate and unchanged.

The baseline first extracts the same unconfigured builder into createPreviewPlayer(Context), which both actual preview callers reach through rememberPreviewPlayer. That extraction preserves behavior. New PreviewPlayerAudioPolicyTest invokes this actual factory and real pinned ExoPlayer; Android focus requests/listeners and broadcasts are delivered through Robolectric4.14.1. PendingMediaSource supplies no timeline, I/O, network, codecs or output samples; real ExoPlayer remains buffering. This removes decoder timing while exercising the actual control-policy callbacks. Teardown releases player and pending source with a bounded wait.

Seven consumer methods run on API27 and34 (14 expected cases): paused initial preparation requests no focus; denied focus prevents requested playback; permanent loss pauses until a fresh Play; transient loss suppresses until gain; may-duck retains playback intent and the user's volume; becoming-noisy pauses and headset reconnect does not replay; release abandons focus, unregisters the receiver and ignores a stale gain. The may-duck case does not measure audible attenuation or internal mixer output. No boolean configuration-only assertions substitute for these consumer effects.

## Primary API evidence

Checked 26 October2026. Context7 skill was read; no Context7 resolve/query tool is available in this session, so official Android and pinned upstream sources were used. No dependency was added, upgraded or downloaded as an executable plugin.

- [Android audio-focus guidance](https://developer.android.com/media/optimize/audio-focus) recommends delegating focus to ExoPlayer through audio attributes with handleAudioFocus enabled. Apps targetingAPI35+ must be topmost or use an eligible foreground service to obtain focus; this candidate retains foreground preview lifetime, without adding a service or background claim.
- [Official builder reference](https://developer.android.com/reference/androidx/media3/exoplayer/ExoPlayer.Builder) documents automatic focus and becoming-noisy policy. Current online API pages may describe a later release; the pinned1.8.0 source below controls compatibility.
- [Media3 1.8.0 AudioFocusManager](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/AudioFocusManager.java) implements playback requests and permanent/transient/duck focus callbacks. [Pinned ExoPlayer builder](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/ExoPlayer.java) defines the corresponding opt-ins.
- [Media3 1.8.0 AudioBecomingNoisyManager](https://github.com/androidx/media/blob/1.8.0/libraries/exoplayer/src/main/java/androidx/media3/exoplayer/AudioBecomingNoisyManager.java) owns system-broadcast registration and dispatch; Atomic should not add a competing receiver.
- [Pinned Robolectric AudioManager shadow](https://github.com/robolectric/robolectric/blob/robolectric-4.14.1/shadows/framework/src/main/java/org/robolectric/shadows/ShadowAudioManager.java) exposes actual requested Android focus callbacks and granted/denied results for these host tests.

## Required gates and limits

The author performed only scoped formatting/source inspection; parent owns configured compilation, baseline RED and policy GREEN, static checks, affected lifecycle/staging/retry regressions, APK/Lint/current-head CI and independent review. No host pass is claimed before execution. The planned policy opts the existing shared player into Media3 focus and headset-disconnection handling with media usage; it does not add queues, notifications, background playback, tag writes, format engines, permissions, hardware routing changes or custom focus listeners.

Physical decoded audio/video, calls, audible ducking, wired/Bluetooth transitions, supportedAPI36 top-app behavior, user pause/STOP interplay and release remain explicit device acceptance. A host callback test is not proof of hardware focus/codec behavior. Existing whole-plugin versions stayv0.2 and advanced completion credit stays zero until applicable acceptance passes.