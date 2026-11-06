# Image load recovery and source replacement

Existing U09 image-viewer increment, prepared 24 October 2026 from accepted feature integration `39994ef`. This is not the separate future eight-plugin modernization program or completion of U09.

## Behavior

- Recoverable image-read failures expose the existing Atomic **Try again** action. Retrying is explicit; errors do not start an automatic retry loop.
- The viewer retains typed `FileError` results. Storage availability, access, missing images, unsupported content, preview limits, memory and other failures have accurate messages without raw backend paths or exceptions. Retry is omitted when the existing error contract marks a failure nonrecoverable; no permission request or root-policy change is introduced.
- Source identity, known revision (`modifiedAt` and `size`), decoder identity and retry attempt own a keyed composition. A replacement starts with fresh loading and zoom/pan state synchronously; changing only a producer key would retain the previous bitmap State until its new producer ran. A source with unchanged identity and revision is not continuously polled for byte changes.
- Existing pinch/pan, double-tap zoom, gallery paging, 2048px default sampled preview, all eight EXIF transforms, worker dispatch, stream closure and decoder cancellation remain in place. No engine, dependency, downloaded module, image editing or user-file write is added.
- Published bitmaps are released from composition state when their keyed owner leaves; the UI does not recycle bitmaps that a frame may still draw. The existing decoder continues to recycle unsuccessful or cancelled private allocations. This increment does not claim a new measured memory budget or region/tile decoding.

## Frozen old-API consumers

Test-only commit `5a27f923d07b64008b72fc8e8095b74335ed4c3c` precedes the production change. `ImageLoadRecoveryComposeTest` uses the existing `ZoomableImage`/`AppContainer` boundary, the real `ImagePreviewHelper` and native BitmapFactory decoding from controlled in-memory PNG streams. Other container properties remain lazy and unused. It adds no production test hooks or mock framework.

The original test-only checkout SHA256 was `CC1AD6AF8D3689B772F3B4586616AF7F183135DCFF03EBE9C6B1F33CDC2F0341`. Its first run exposed an unsupported host window-capture fixture before either replacement assertion: PixelCopy force-redraw timed out in both initial pixel captures. The log/exit/XML are retained as `image-load-initial-capture-failed.*` (2m45s, exit 1); those two failures are not credited as source regressions.

Three API34 native-graphics consumers cover:

1. Decode a solid red image, replace its identity with a gated blue image, require no image under the replacement name while its decoder is waiting, then assert blue rendered pixels after release.
2. Change the known revision at the same identity, require a new decode and no previous image while waiting, then assert the revised blue rendered pixels.
3. Fail the first read with a typed unavailable-provider result, require a visible explicit retry action and no automatic retry, recover the provider, click the action and assert decoded red pixels and disappearance of Retry.

Generated PNGs exist only in memory. Test-controlled reads are released in `finally`, and composition disposal cancels outstanding decode work. The tests do not access real provider files or request grants.

## Validation status and limits

The corrected test draws the actual Compose View to a native Bitmap on the UI thread, using the Image semantics bounds for pixel inspection. It retains real PNG decoding and both pixel assertions without PixelCopy. On unchanged image production, all three consumers fail for the intended behavior: the prior bitmap is published as second.png while its new decode is gated; same-identity revision never starts its new decode; Retry never appears after the unavailable-provider read. Initial red pixels were verified before both replacement checks. RED: 3 cases, 3 failures, 0 errors/skips, 2m09s, exit 1; `build/audit-tools/image-load-red-validation.log/.exit` and `image-load-red-test-results.xml`. RED checkout SHA256: `E891ACF84853F9AF72EF2F5D5B76F5CF5E6787B618AE7F2CAE244263E397C5A0`.

Root test-only commit `d8b7d2e` follows that run with import-order formatting only; its LF checkout SHA256 is `28294DE21B09CEA857F3F06890FCD9D6DBAB15D7FFEF3F000A62684A8D1039C9`. Source commit `ecf1dc5834c125c9612be74128fc4ac34e65a046` applies the independently reviewed fix with app tree `9988d56611aaf331a9659c9364e0d648e0b52608`. The decoder is unchanged. The corrected-source host gate passes all 30 affected cases with no failures/errors/skips (3 new native-graphics consumers, 16 decoder API27/34, 4 gallery, 4 preview routing, 3 external actions), ktlint, Detekt, debug/AndroidTest APKs and debug Lint in **9m44s, exit 0**. Exact logs, copied XML and JSON are retained under `build/audit-tools/image-load-*`. The initial incomplete-input/IO diagnostics belong to intentional decoder fault tests; all test-result counters pass. Independent source and capture-fixture reviews find no blockers. Filter: `*ImageLoadRecoveryComposeTest`.

Also retain existing `GalleryTest`, `ImagePreviewHelperTest`, `PreviewTypeTest` and `PreviewExternalActionTest` coverage when validating. The existing native EXIF/BMP fixtures and gallery touch/zoom behavior remain useful phone gates. This host slice does not establish a complete decoder/API/device format matrix, a large-image memory benchmark, animated-image playback, high-resolution tiling, transform restoration through recreation, or crop/rotation/annotation saved copies.

## Verified physical checkpoint — 24 October

Exact host build source `ecf1dc5834c125c9612be74128fc4ac34e65a046`, app tree `9988d56611aaf331a9659c9364e0d648e0b52608`. Debug APK SHA256 `53C0A70984BD13DAD535963800852CDD140ED8497D8B03B4663C8805A6F68A17`; AndroidTest `8A039C2A5C99427625AEC358A9B5807EB72F8835E9B85995003DFCA8AA2D2BF6`.

All six selected API34 native cases pass in **26.134s**, wrapper and acceptance exit0: eight JPEG EXIF transforms and BMP decoding (two cache-only image methods), plus four Storage/Analysis/Browse/session surfaces. Image fixtures preserve source digests and remove only their uniquely owned cache files. The wrapper verifies the pulled installed base APK hash, unchanged all-files access, removal of its initially absent test runner and actual selected Home, with no cleanup failures. Evidence: `build/audit-tools/device-preview-tools/image-load-acceptance-1d5cb5d4-ca3e-410d-8446-1c4358fc385c/`. No root probes, measured benchmark, shared-file mutation or permission changes.

These native cases cover unchanged decoder and app surfaces; the new transient-provider Retry and source-replacement assertions are host Compose consumers, not a claim of physical gallery gestures/recreation or the full format matrix. Accepted MIME dependency PR #79 is integrated as `49dd78c`. [PR #80](https://github.com/DevBehindYou/Atomic-File-Manager/pull/80) is published with matching app tree. Final head `3ef374a50ce67bde39f33723938aa0eb18f343a8` passes both required jobs in CI `40106431841` (manual emulator skipped). Independent review, resolved-thread inspection and a fresh clean guarded trial preceded feature-integration merge `82e7e94ff7fcdbf8d7a84a121054f6d27658fe42`. U09 is partial and fixed milestone progress remains **15% host verified / 8% accepted**. The user also requested per-plugin context records and an initial v0.2 About label; that separate active increment does not establish advanced eight-plugin or main/release completion.