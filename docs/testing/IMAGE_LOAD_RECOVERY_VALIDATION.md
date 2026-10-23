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

Frozen CRLF checkout SHA256: `CC1AD6AF8D3689B772F3B4586616AF7F183135DCFF03EBE9C6B1F33CDC2F0341`.

Three API34 native-graphics consumers cover:

1. Decode a solid red image, replace its identity with a gated blue image, require no image under the replacement name while its decoder is waiting, then assert blue rendered pixels after release.
2. Change the known revision at the same identity, require a new decode and no previous image while waiting, then assert the revised blue rendered pixels.
3. Fail the first read with a typed unavailable-provider result, require a visible explicit retry action and no automatic retry, recover the provider, click the action and assert decoded red pixels and disappearance of Retry.

Generated PNGs exist only in memory. Test-controlled reads are released in `finally`, and composition disposal cancels outstanding decode work. The tests do not access real provider files or request grants.

## Validation status and limits

Parent-run unchanged-source RED, corrected-source GREEN, static checks, APK compilation, Android Lint and final-head CI are pending. No passing build, device run or percentage increase is claimed by this preparation record. Filter: `*ImageLoadRecoveryComposeTest`.

Also retain existing `GalleryTest`, `ImagePreviewHelperTest`, `PreviewTypeTest` and `PreviewExternalActionTest` coverage when validating. The existing native EXIF/BMP fixtures and gallery touch/zoom behavior remain useful phone gates. This host slice does not establish a complete decoder/API/device format matrix, a large-image memory benchmark, animated-image playback, high-resolution tiling, transform restoration through recreation, or crop/rotation/annotation saved copies.
