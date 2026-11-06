# GIF playback repair — candidate

29October2026. User-reported defect: GIFs are routed to the image viewer but ImagePreviewHelper.decodeImage uses BitmapFactory, yielding one static bitmap. Existing thumbnail support does not establish fullscreen animation. v0.2.1 stable target requires this gap fixed and tested.

Implementation candidate: native API28 ImageDecoder.decodeDrawable with an immutable bounded encoded source, existing backend selection and IO dispatcher. No new library, remote executable plugin, storage grant, original-file write or engine update. Standard GIF filename or bounded normalized image/gif MIME selects the path; actual GIF87a/GIF89a payload signature is checked. Other static images keep their existing bitmap/EXIF path.

Limits:16MiBencoded input(+one overflow byte),16MPsource canvas,2048maximum output dimension. Stream closure, cooperative between-read/prepost-decode cancellation, no-progress failure and typed backend errors are explicit. These limits do not prove a total-heap/frame-count/native-decode-time budget or interrupt arbitrary blocking provider calls.

The shared static geometry/clipping/pinch/pan/double-tap surface is retained. Current-page and STARTED lifecycle own animation; Stop freezes and Replay restarts. Native stop/start resets; do not label it true Pause/Resume. Encoded loop counts are retained. Disposal cancels the lifecycle-owned playback observer, stops animation and detaches the ImageView. Native animation callbacks are deliberately not registered. Existing unpublished image-view-modes candidate c05d5d6 remains separate and must be reconciled after this repair; no fit-mode credit is claimed here.

GifPlaybackDeviceTest uses a deterministic two-frame red/green looping GIF and a uniquely owned private-cache copy. It checks actual captured colours advancing, stable stopped pixels, replay, background stop/return, final stop/detachment and source bytes. The test-first baseline has unchanged production; its current build/run evidence must be retained separately from this candidate. No native pass is claimed yet. Existing static image gesture/orientation/recovery consumers remain required. New decoder tests cover invalid header, typed provider denial, bounded overflow consumption, IO failure, cancellation and zero-progress closure on API28/API34.

Read-only review found no outstanding concrete source blocker after routing was changed to Gallery.isGif; actual native/host/static/artifact/CI/provider/format/performance acceptance remains open. No IMG2/IMG6 or whole-plugin completion credit.

Official sources checked29October2026: [Android ImageDecoder](https://developer.android.com/reference/android/graphics/ImageDecoder), [AnimatedImageDrawable](https://developer.android.com/reference/android/graphics/drawable/AnimatedImageDrawable), [Coil GIF documentation](https://coil-kt.github.io/coil/gifs/). Context7 skill was read, but no Context7 tools were available; official sources were the fallback. Android's native decoder covers the minimum API28 without adding coil-gif; the existing Coil3.3.0 version is unchanged.

## Callback crash regression (29 October)

Candidate `9c96985` passed 55 focused host cases, ktlint/Detekt, both APKs and debug Lint. Its exact APK failed the physical API34 run: 7 tests, 1 GIF failure; all six static gesture/orientation cases passed. The failure at disposal is a framework `AnimatedImageDrawable.postOnAnimationEnd` null callback-list dereference, not an accepted GIF result. Original APK/hash manifest, host XML and phone failure remain under ignored `build/audit-tools/gif-candidate-artifacts`, `gif-9c96985-evidence` and `gif-candidate-phone`.

[AOSP Android14 source](https://github.com/aosp-mirror/platform_frameworks_base/blob/android14-release/graphics/java/android/graphics/drawable/AnimatedImageDrawable.java) confirms queued start/end lambdas iterate the mutable callback list after unregister can clear it. The repair avoids registering these callbacks. A lifecycle/current-page-owned coroutine observes `isRunning` every 100ms only during requested playback, stops on cancellation and marks natural completion without changing the encoded repeat count. It does not implement true pause/resume. Expanded native coverage adds inactive-page stop/return and a separate finite-loop fixture. Repair validation is pending.

## 30 October recovery and API28 compatibility

The former root `build/audit-worktrees` and `build/audit-tools` directories were absent on resume. Committed source `60031fa` was recovered into a managed worktree; local validation logs that disappeared are not represented as newly verified. Signing backups and user artwork remain present. CI artifacts from run40931415466 were downloaded into ignored `.local-evidence/ci-60031fa` with their original source/app-tree/APK hashes. Normal CI40931415358 passes all1306 unit cases (zero failures/skips), APK packaging and static/Lint jobs; its HTML reports were recovered separately.

API34 reports8 tests,1 failure: looping GIF frame advancement, Stop/Replay, active-page transitions, background/return and disposal plus6 static gesture/orientation cases pass. The finite fixture observes only final green at its initial capture; its600ms lifetime can expire during initial UI synchronization. The revised test first checks natural completion/final green, then explicitly uses Replay and still requires both actual colours and a second natural completion. Its generated finite fixture now has a2-second red frame and1-second green frame so capture has an explicit observation interval. No infinite-loop substitution or assertion removal is used.

API28 reports a process crash during its first case; the other7 cases did not execute. The native backtrace reaches `ByteArrayStream::read` then `get_env_or_die` on `AnimatedImageTh`. Replace the heap buffer with an immutable direct ByteBuffer while retaining the encoded-size/dimension limits. [Coil3.3.0's animated decoder](https://github.com/coil-kt/coil/blob/3.3.0/coil-gif/src/main/java/coil3/gif/AnimatedImageDecoder.kt) also uses a direct-buffer source for its buffered fallback. The overflow path throws its budget exception inside stream ownership so a secondary close failure does not erase the primary size error. Current repair validation remains pending; neither full IMG nor v0.2.1 acceptance is claimed.

## Accepted bounded playback repair - 5 November 2026

The parent completed compatible local artifact and physical-phone acceptance of source `fb856a6dac87ede7b6fbed58697a0be5e1b4a6b8`, app tree `8c6d7cf5d35d82817a3ce642dd4127554aed9e5f`. Local build completes in **12m13s, exit 0**. Physical API34 phone: **8 tests, 0 failures, 13.586s, exit 0**, covering looping/finite native playback and controls/ownership plus static gestures/orientation and original-byte integrity. Existing normal CI [41064609636](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/41064609636) and native [41064609699](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/41064609699) pass on the same source; the native matrix passes eight cases on each of API28/API34. The original callback/API28/finite-fixture failures above remain retained history, superseded by this scoped repair result.

Artifact SHA-256 identities:

- Main APK: `60c99dd17f09f2eb21838932a13f9ed7d05e726025cfc7fbc163e6e36650c1e6`
- Test APK: `bd4e5382924517daf500b88389032a714b3d9c62d96b2caa1182fee4e49b0952`
- Compatible local debug signer: `1a18fd513c1737ae448fb8f69b9a738366f3713ac7ac426ffd7a0ce225815126`

Ignored evidence in managed `gif-playback-recovery/Atomic-File-Manager/.local-evidence/` includes `gif-fb856a6-phone-20261105.log`, its `.exit` marker and acceptance JSON. This documentation records the parent-verified evidence; no additional run was performed while integrating Markdown. Existing files/preferences/permissions and signer were preserved: no root probe, grant, app-data clear or uninstall. The native fixtures are uniquely owned and retain source-integrity assertions.

After review, exact-head checks, dependencies and clean integration trial, [PR #101](https://github.com/DevBehindYou/Atomic-File-Manager/pull/101) merged into feature integration as `33152a5bc92e38bc34a2e72a962e61405da55b56`. This is not main/stable-release completion. GIF decoded-input/resource/provider/accessibility limits and broader Image groups remain open; no true Pause/Resume or complete format/performance guarantee is added. N42 stays 0/42 host/accepted and 0/8 whole plugins.
