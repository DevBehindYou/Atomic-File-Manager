# Preview player focus interruptions on real Android — C011

Claude Code task **C011** (U09; VID-T03 / AUD-T03 increment). Base: feature integration `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Recorded 6 November 2026 UTC (7 November, Asia/Calcutta).

## Why

`createPreviewPlayer` hands audio focus and becoming-noisy handling to pinned Media3 (`setAudioAttributes(USAGE_MEDIA, handleAudioFocus = true)`, `setHandleAudioBecomingNoisy(true)`). `PreviewPlayerAudioPolicyTest` covers this on the host through Robolectric shadows on API 28 and 34. Until now, no native run had exercised the real platform focus stack.

## What the native journey checks

`PreviewAudioFocusDeviceTest` is a new test only; no production code changes. It uses the same `createPreviewPlayer` as the Audio and Video previews, an owned 20 s silent PCM WAV in the app cache and a resumed `ComponentActivity`, so the app is in the foreground. A competing `AudioFocusRequest` from a separate listener (usage media) stands in for another player app.

| Case | Expected |
| --- | --- |
| Permanent loss (`AUDIOFOCUS_GAIN` by the competitor) | Pauses (`playWhenReady` false). It stays paused after the competitor abandons focus. An explicit Play resumes it. |
| Transient loss (`AUDIOFOCUS_GAIN_TRANSIENT`) | The Play request is kept, with suppression reason `TRANSIENT_AUDIO_FOCUS_LOSS` and not playing. It resumes once the competitor abandons focus. |
| Ducking (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) | Keeps playing. The user-visible `player.volume` is unchanged before, during and after the duck. |

Every case also checks that the source bytes are unchanged and that no extra files are left behind.

Workflow `.github/workflows/preview-audio-focus-native.yml` runs the test on hosted API 28 and API 34 emulators (`google_apis`, x86_64). It keeps source and app-tree identities, APK SHA-256 values and the instrumentation reports.

## Local evidence

- `./gradlew ktlintFormat :app:compileDebugAndroidTestKotlin ktlintCheck detekt`: BUILD SUCCESSFUL in 4m (`host-gradle` lease held). Compiled only; no local device or emulator run.
- Hosted workflow [42297379230](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/42297379230) passed all three native cases on API 28 and all three on API 34, with zero failures, errors or skips. The artifacts record PR head `fb11f2e0ba12031db4585b1b6fdbf72ce045480b`, tested merge source `ceaed791da5cd1f949e043ba2e67ece24eba6e24`, app tree `427bfd321bffcf665dc121bbcfb56d29aed335ab` and both APK hashes per API. Fresh checks are required when the integration source changes.

## Blocked or remaining

- **Becoming noisy (headset or Bluetooth unplug).** `ACTION_AUDIO_BECOMING_NOISY` is a protected broadcast. An instrumentation test or `adb shell` cannot deliver it to the real receiver. The host test covers the Media3 receiver. Native acceptance needs physical headset or Bluetooth route loss on an owned device. That is a hardware blocker, so it has no native workaround.
- Phone calls, API 35+ top-app focus rules and audible ducking level are not covered.
- Video uses the same player factory. A decoded-video focus case belongs with the C015 fixture matrix.
