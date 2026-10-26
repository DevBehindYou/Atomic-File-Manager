# Image viewer — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). This is not an image decoder version or a release acceptance claim.

Source inspected: `ecf1dc5834c125c9612be74128fc4ac34e65a046` (24 October 2026). The version-only increment changes inventory metadata, not the linked viewer engines.

## Implementation, features and limits

[ImageGallery.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/ImageGallery.kt) provides ordered gallery paging, pinch/pan and double-tap zoom; paging is disabled while the active image is zoomed. [ImagePreviewHelper.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/preview/ImagePreviewHelper.kt) uses worker decoding, a default 2048px preview limit, bounded optional metadata, all eight EXIF transforms and cancellation/private-allocation cleanup.

Current source adds typed load failures, explicit Retry for recoverable errors, and source/revision/decoder/attempt-owned loading and transforms. **That existing U09 slice passes its focused host and selected native gates; PR #80 current-head CI and guarded feature integration pass; wider acceptance remains open.** A replacement must not publish the previous bitmap under its new name. Unchanged identity/revision is not continuously polled for changed bytes.

Format recognition is not a decoder matrix. A sampled bitmap does not establish animation support. Very large images use reduced previews; region/tiling, crop, annotation and saved editing are absent. The UI must not recycle a bitmap a frame may still draw.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| IMG-T01 | P0 / accepted into feature integration | PR #80 head3ef374a passes CI40106431841 and guarded merge82e7e94; preserve evidence and track wider touch/provider acceptance under IMG-T02 |
| IMG-T02 | P0 / open acceptance | Corrupt/missing/denied/disconnected inputs, rapid switching and recreation/transform behavior across supported APIs/providers |
| IMG-T03 | P1 / deferred audit/research | Animation and high-resolution strategy; evaluate native compatibility, memory/size and licensing before engines |
| IMG-T04 | P1 / deferred | Metadata/view controls and safe edit/save-copy workflows with original-integrity tests |

## Bugs and risks

| Bug ID | Scope / state | Evidence owner |
| --- | --- | --- |
| IMG-B01 | Recoverable load failure lacks explicit Retry; fixed in feature integration by PR #80; wider matrix tracked separately | IMG-T01 and linked current validation record |
| IMG-B02 | Replacement can retain old pixels under a new name; fixed in feature integration by PR #80; wider matrix tracked separately | IMG-T01 and linked current validation record |
| IMG-B03 | Same-ID known revision does not start a fresh decode; fixed in feature integration by PR #80; wider matrix tracked separately | IMG-T01 and linked current validation record |

Source replacement, same-ID revision and missing Retry are IMG-T01 targets. The active [validation record](../testing/IMAGE_LOAD_RECOVERY_VALIDATION.md) owns actual regression results and retained fixture failures; failed host capture fixtures are not reproduced production defects. Broader format, memory, touch and recreation scenarios remain open checks, not invented confirmed bugs.

## Validation and continuation

Current existing-U09 checkpoint: source `ecf1dc5834c125c9612be74128fc4ac34e65a046`, app tree `9988d56611aaf331a9659c9364e0d648e0b52608`. The three corrected unchanged-source consumers fail for the intended behaviors before the fix; retained window-capture fixture failures remain separately classified. Corrected source passes 30 affected host cases, ktlint/Detekt, debug/AndroidTest APKs and debug Lint in **9m44s**, exit0. Six selected API34 native cases pass in **26.134s**: two unchanged decoder EXIF/BMP methods plus four Storage/Analysis/Browse/session surfaces. Installed hash, fixture cleanup, unchanged access, runner removal and selected Home are verified in the linked [validation record](../testing/IMAGE_LOAD_RECOVERY_VALIDATION.md). New Retry/replacement assertions are host Compose consumers; this is not physical gesture/recreation or full-format acceptance. [PR #80](https://github.com/DevBehindYou/Atomic-File-Manager/pull/80), final head `3ef374a50ce67bde39f33723938aa0eb18f343a8`, passes both required jobs in CI `40106431841`; review and a clean guarded trial preceded feature-integration merge `82e7e94ff7fcdbf8d7a84a121054f6d27658fe42`. These results precede the separate v0.2 metadata/test addition; they do not validate the version consumer.

Historical checkpoint `4792db8`: [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md) and [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) record selected API34 EXIF/BMP and image/folder thumbnail evidence. Thumbnails do not certify full-screen animation.

Host consumers: `GalleryTest`, `ImagePreviewHelperTest`, `PreviewTypeTest`, `PreviewExternalActionTest`; active `ImageLoadRecoveryComposeTest` covers replacement/revision/explicit retry. Native `ImageOrientationDeviceTest` checks all eight JPEG transforms and BMP using owned cache fixtures and original hashes. Record current source/build results separately. U09 and the [future eight-plugin intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) remain incomplete and separately tracked.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained record. Existing capabilities are documented; IMG-T01 passes focused host/selected native/CI/review gates and is accepted into feature integration through PR #80; broader IMG-T02 acceptance remains open. No new engine or completed advanced-image claim is introduced.
## Focused transform candidate — 26 October 2026

**IMG-T05 / P0 / source candidate; validation pending.** The separately audited IMG1 group now has an isolated focused transform increment: tap/pinch centroid preservation, fitted-bitmap pan bounds, viewport clipping and size-change re-clamping. Existing revision/Retry ownership and fit-scale one-finger paging remain. See [candidate validation](../testing/IMAGE_FOCUSED_TRANSFORMS_VALIDATION.md) and [modernization scope/progress](modernization/PLUGIN_PROGRESS.md).

The tap-location and letterbox gaps are source observations until the parent retains controlled unchanged-source failures. Four pointer/native-pixel consumers and eight pure geometry contracts are prepared; the author ran no Gradle or device tests. Host/CI/device acceptance is open, and no full IMG1 or whole-plugin percentage credit is earned. Fit-width/original controls, restoration, animation/editing/high-resolution and the complete GIF journey remain open. Version stays v0.2.
