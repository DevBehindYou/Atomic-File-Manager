# Preview details ownership and clean-copy cancellation — C002 slice 1

Claude Code task **C002** (U06, U09), first focused slice. Base: feature integration `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Recorded 6 November 2026 UTC (7 November, Asia/Calcutta). Host-verified only. No device run and no N42 group credit are claimed.

## Defects and reproductions

| ID | Defect | Reproduction on unchanged logic | Fix |
| --- | --- | --- | --- |
| C002-B1 | **Share without location / metadata** writes a clean copy into `cacheDir/clean`. If the File info sheet closes while the copy is being written, the caller is cancelled and `withContext` discards the copy. The verified copy, which still contains the image pixels, stayed in the cache with no owner. | `ExifMetadataReaderTest.cancellationAfterTheCopyIsWrittenDeletesItAndPropagates` fails with "The unshared copy is left in the cache" when the cancellation cleanup is removed. | `ExifMetadataReader.cleanCopy` deletes the target under `NonCancellable` and rethrows. Non-cancellation failures still return `Result.failure` and delete the target, as before. |
| C002-B2 | The photo details in File info used `produceState(null, file)`. Restarting only the producer keeps the previous value, so a replaced image showed the previous image's camera and location until its own read finished. A rewritten file at the same path (new `modifiedAt`/size) was never read again. | `PreviewDetailsRevisionComposeTest`: the two photo cases fail without the revision key. | `ImageMetadataSection` keys its state by `node.id`, `modifiedAt`, `size` and file, following the existing `ImageGallery` idiom. |
| C002-B3 | The APK summary used `produceState(false to null, node.id)`. A replaced APK showed the previous file's verdict ("can't be read as an app") or details instead of "Reading app details…". | `PreviewDetailsRevisionComposeTest.replacedApkStartsFromReadingInsteadOfThePreviousVerdict` fails without the key. | `ApkSummary` keys its state by `node.id`, `modifiedAt` and `size`. |

`ExifMetadataReader` and `ApkInfoReader` now take an injectable `CoroutineDispatcher` (default `Dispatchers.IO`). The production wiring is unchanged.

The first version of the cancellation test ran IO inline inside `dispatch`. `withContext` then returned without suspending, so no prompt-cancellation check ran, and the test failed even with the fix applied. The final test defers the IO block to the test scheduler, the same way a worker pool runs while the caller is suspended. That failure was a test-harness artifact; it is kept here for the record.

## Host validation (Windows 8 GB host, `host-gradle` lease held)

| Command | Result |
| --- | --- |
| `./gradlew ktlintCheck detekt :app:testDebugUnitTest --tests '*ExifMetadataReaderTest' --tests '*PreviewDetailsRevisionComposeTest' --tests '*MetadataTextTest' --tests '*ApkFactsTest' --tests '*ImageLoadRecoveryComposeTest' --tests '*FileOpeningRecoveryComposeTest'` | BUILD SUCCESSFUL in 1m 54s. 25 tests: Exif 5, revision 3, MetadataText 7, ApkFacts 2, ImageLoadRecovery 3, FileOpeningRecovery 5. 0 failed, 0 skipped. ktlint and detekt passed. |
| Control: same Exif test with the cancellation cleanup removed | 5 run, 1 failed (`cancellationAfterTheCopyIsWrittenDeletesItAndPropagates`). |
| Control: revision tests with the previous `produceState` keys | 3 run, 3 failed. |

The full unit suite, Lint and APK packaging are left to hosted CI on the PR head.

## Remaining C002 scope

- `MediaPreviewHelper.readMetadata` runs a blocking `MediaMetadataRetriever` call. No defect was reproduced in this slice. Existing ownership tests cover staged-copy cleanup.
- `CompareSheet` and `DeleteSheet` use the same unkeyed `produceState` pattern, but they are outside preview. They are recorded for a later task and not changed here.
- No device run. EXIF share on a physical device is unchanged apart from cancellation cleanup.
