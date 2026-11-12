# Hostile GIF inputs through the real decoder — C007 slice 1

Claude Code task **C007** (U09; IMG2/IMG6 increment). Base: feature integration `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Recorded 6 November 2026 UTC (7 November, Asia/Calcutta). This is test-only coverage: no production change and no defect found.

## What is covered

`GifHostileInputTest` runs `GifPreviewDecoder` through the real platform `ImageDecoder` (Robolectric native graphics, API 28 and API 34). It uses GIFs built byte by byte in the test: a GIF89a with a 2-colour table, a NETSCAPE loop block and 1-pixel LZW frames. Every case also asserts that the owned input closes exactly once.

| Case | Outcome on current source |
| --- | --- |
| 2×2, three looping frames | Decodes; intrinsic size 2×2 |
| Screen descriptor 65,535 × 65,535 (≈4.3 G pixels) | `FileTooLarge(id, 16 Mi)` from the header listener, before allocation |
| 8,192 × 2 canvas (inside the pixel budget) | Decodes, scaled to the 2,048 dimension cap |
| Cut after the header, the screen descriptor, the palette, or the first frame descriptor | `UnsupportedFormat("GIF")`; never `IoFailure`, a crash or a partial image |
| Frame descriptor 60,000 × 60,000 inside a 4 × 4 canvas | Stays within caps (decoded ≤ 2,048, or a typed failure) |

The existing caps (16 MiB encoded, 16 Mi pixels, 2,048 px target) and the direct-buffer API 28 path are confirmed as they are. No change is proposed without evidence.

## Validation (local, `host-gradle` lease held)

- `./gradlew ktlintFormat :app:testDebugUnitTest --tests '*GifHostileInputTest' --tests '*GifPreviewDecoderTest'`: BUILD SUCCESSFUL in 2m 30s. GifHostileInput 10 (5 cases × API 28/34) and GifPreviewDecoder 14. 0 failed, 0 skipped.
- `./gradlew ktlintCheck detekt`: BUILD SUCCESSFUL.

## Deferred (and why)

- **Native long-animation, rapid replacement and decoder-release fixtures.** These belong in `GifPlaybackDeviceTest` and `gif-playback-native.yml`, which Codex's open Image PR #103 changes. Per ownership they wait until #103 is merged or handed off.
- Peak allocation measurement on a device is not collected (benchmarking is deferred).
- `IMAGE_VIEWER.md` is not edited in this PR to avoid a third append conflict with #103 and #108. The plugin record will be updated in one docs pass after those merge.
