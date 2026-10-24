# Image viewer — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). This is not an image decoder version or a release acceptance claim.

Source inspected: `ecf1dc5834c125c9612be74128fc4ac34e65a046` (24 October 2026). The version-only increment changes inventory metadata, not the linked viewer engines.

## Implementation, features and limits

[ImageGallery.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/components/ImageGallery.kt) provides ordered gallery paging, pinch/pan and double-tap zoom; paging is disabled while the active image is zoomed. [ImagePreviewHelper.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/data/preview/ImagePreviewHelper.kt) uses worker decoding, a default 2048px preview limit, bounded optional metadata, all eight EXIF transforms and cancellation/private-allocation cleanup.

Current source adds typed load failures, explicit Retry for recoverable errors, and source/revision/decoder/attempt-owned loading and transforms. **That existing U09 slice passes its focused host and selected native gates; final-head CI/integration acceptance is still pending.** A replacement must not publish the previous bitmap under its new name. Unchanged identity/revision is not continuously polled for changed bytes.

Format recognition is not a decoder matrix. A sampled bitmap does not establish animation support. Very large images use reduced previews; region/tiling, crop, annotation and saved editing are absent. The UI must not recycle a bitmap a frame may still draw.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| IMG-T01 | P0 / host/selected native pass; CI/integration pending | Complete PR #80 current-head CI and guarded integration; preserve host/native evidence and track wider touch/provider acceptance separately |
| IMG-T02 | P0 / open acceptance | Corrupt/missing/denied/disconnected inputs, rapid switching and recreation/transform behavior across supported APIs/providers |
| IMG-T03 | P1 / deferred audit/research | Animation and high-resolution strategy; evaluate native compatibility, memory/size and licensing before engines |
| IMG-T04 | P1 / deferred | Metadata/view controls and safe edit/save-copy workflows with original-integrity tests |

## Bugs and risks

| Bug ID | Scope / state | Evidence owner |
| --- | --- | --- |
| IMG-B01 | Recoverable load failure lacks explicit Retry; source correction present, acceptance pending | IMG-T01 and linked current validation record |
| IMG-B02 | Replacement can retain old pixels under a new name; source correction present, acceptance pending | IMG-T01 and linked current validation record |
| IMG-B03 | Same-ID known revision does not start a fresh decode; source correction present, acceptance pending | IMG-T01 and linked current validation record |

Source replacement, same-ID revision and missing Retry are IMG-T01 targets. The active [validation record](../testing/IMAGE_LOAD_RECOVERY_VALIDATION.md) owns actual regression results and retained fixture failures; failed host capture fixtures are not reproduced production defects. Broader format, memory, touch and recreation scenarios remain open checks, not invented confirmed bugs.

## Validation and continuation

Current existing-U09 checkpoint: source `ecf1dc5834c125c9612be74128fc4ac34e65a046`, app tree `9988d56611aaf331a9659c9364e0d648e0b52608`. The three corrected unchanged-source consumers fail for the intended behaviors before the fix; retained window-capture fixture failures remain separately classified. Corrected source passes 30 affected host cases, ktlint/Detekt, debug/AndroidTest APKs and debug Lint in **9m44s**, exit0. Six selected API34 native cases pass in **26.134s**: two unchanged decoder EXIF/BMP methods plus four Storage/Analysis/Browse/session surfaces. Installed hash, fixture cleanup, unchanged access, runner removal and selected Home are verified in the linked [validation record](../testing/IMAGE_LOAD_RECOVERY_VALIDATION.md). New Retry/replacement assertions are host Compose consumers; this is not physical gesture/recreation or full-format acceptance. [PR #80](https://github.com/DevBehindYou/Atomic-File-Manager/pull/80) final-head CI and guarded integration remain pending. These results precede the separate v0.2 metadata/test addition; they do not validate the version consumer.

Historical checkpoint `4792db8`: [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md) and [preview validation](../testing/PREVIEW_TOOLS_VALIDATION.md) record selected API34 EXIF/BMP and image/folder thumbnail evidence. Thumbnails do not certify full-screen animation.

Host consumers: `GalleryTest`, `ImagePreviewHelperTest`, `PreviewTypeTest`, `PreviewExternalActionTest`; active `ImageLoadRecoveryComposeTest` covers replacement/revision/explicit retry. Native `ImageOrientationDeviceTest` checks all eight JPEG transforms and BMP using owned cache fixtures and original hashes. Record current source/build results separately. U09 and the [future eight-plugin intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) remain incomplete and separately tracked.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained record. Existing capabilities are documented; IMG-T01 passes focused host/selected native gates and awaits CI/integration acceptance. No new engine or completed advanced-image claim is introduced.