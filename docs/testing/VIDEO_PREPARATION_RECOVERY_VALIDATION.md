# Video preparation recovery

Existing U09 video-viewer increment, prepared 24 October 2026 from checkpoint `12f60c0`. This is a narrow essential reliability change, not completion of U09 or the separate advanced eight-plugin program.

## Behavior and ownership

- A failure before player creation exposes the existing Atomic **Try again** action when the actual `FileError` is recoverable. Every retry is explicit; there is no automatic retry loop.
- A source identity, known revision (`modifiedAt` and `size`), helper identity and retry attempt own a keyed composition. A new owner starts in loading with fresh failure, prepared-media and player state. Ordinary recomposition with unchanged keys preserves a successful source instead of preparing it again. A source whose identity and revision are unchanged is not polled for byte changes.
- The viewer retains the typed error returned by the existing helper. Its message is intentionally general: `MediaPreviewHelper` currently collapses several preparation failures into `IoFailure`, so the UI does not invent a provider-disconnection, permission, format or quota diagnosis.
- Loading and failure use localized strings and Atomic surfaces, keeping text legible over the black video viewport in either theme. Existing external opening remains in the surrounding preview header.
- `MediaPreviewHelper`, `withMedia`, direct local/SAF/MediaStore URIs, 512 MiB staging cap, IO dispatch, structured cancellation and staging cleanup remain unchanged. Player lifecycle/STOP policy, release, Media3 controls, playback-error Retry and metadata badge remain unchanged. No dependency, permission, root-policy change or user-file write is introduced.

## Frozen unchanged-API consumers

Test-only commit `cdccaf5a7e17e306c708055fb4f8ff0a2731db0c` precedes the production change. `VideoPreparationRecoveryComposeTest` uses the existing public `VideoPreviewContent`/`AppContainer` boundary and real `MediaPreviewHelper`, with a controlled remote backend and the helper's existing metadata-reader seam. It adds no production injection seam or mock framework.

Frozen CRLF checkout SHA256: `6F421DB2222111FFC1D0BAEB360C7EB31036A66DCC3725717358E61D46681565`.

Three API34 consumers cover:

1. Transient acquisition failure stays idle until explicit Retry; recovery stages the exact supplied bytes and disposal removes the staged copy.
2. Repeated acquisition failure requires another explicit Retry and does not reach metadata/player preparation or loop automatically.
3. Disposal cancels a pending remote acquisition and removes its partial staging file. This is a positive ownership control on unchanged source.

On unchanged video production, the first two consumers fail specifically because Try again is not displayed after the actual old preparation error; the pending-acquisition cancellation/cleanup control passes. Baseline: three cases, two expected assertion failures, zero errors/skips, 1m17s, exit1. The frozen test hash is unchanged through GREEN. Logs/XML: `build/audit-tools/video-preparation-red-validation.log/.exit` and `video-preparation-red-test-results.xml`. Root test-only freeze is `6b5d285`; no production test seam was added.

Each test creates a uniquely owned directory under application cache and supplies it through a `ContextWrapper`. Teardown disposes the preview, waits for structured cleanup and deletes that empty directory. Pending acquisition is cancellable. No shared-storage content, preferences or grants are touched. Staged byte fixtures and supplied metadata verify preparation and ownership only; host ExoPlayer creation or a player error is not decoded video-playback acceptance.

## Verified host checkpoint and remaining limits

Actual source `3b0ede60f1804d5277b43810a6c42c2611cc77a5`, app tree `aa40f2ab57da681e0e9e3135bb30ff8e26092948`, passes all 33 affected cases without failures/errors/skips: three new video consumers, 12 media-ownership API27/34, three media-helper, four player-lifecycle, three Image recovery, four preview routing and four Settings consumers. ktlint, Detekt, both APKs and debug Lint pass in **11m26s, exit0**. Independent source/consumer review finds no blocker. Exact logs, copied XML, JSON and manifest are retained under `build/audit-tools/video-preparation-*`. Filter for the new consumers: `*VideoPreparationRecoveryComposeTest`.

Retain existing `MediaPreviewOwnershipTest`, `MediaPreviewHelperTest`, `PreviewPlayerLifecycleTest`, `PlaybackSpeedsTest` and preview routing/external-action coverage for the affected host gate. Explicit source/revision replacement, successful-source recomposition, real playback, lifecycle/recreation, source disconnects, corrupt/unsupported media and decoded-frame acceptance still require appropriate checks. This change does not claim saved playback position, focus/Bluetooth/call handling, subtitles/audio-track controls, a complete format matrix, measured resource improvements or release completion.

## Current phone artifact and publication

Debug APK SHA256 `E4568676F1705B0D6441C1BF39E1BECF0945846CD6D01FF13136DB4495B78DFD`; AndroidTest `8A039C2A5C99427625AEC358A9B5807EB72F8835E9B85995003DFCA8AA2D2BF6`. The exact debug APK is installed on the API34 phone and passes four existing Storage/Analysis/Browse/session native cases in **23.58s**, wrapper and acceptance exit0. Installed base hash, actual selected Home, unchanged all-files access and removal of the initially absent owned test runner are verified with no cleanup failure. Evidence: `build/audit-tools/device-preview-tools/video-preparation-acceptance-011b3278-8557-43bf-9e0f-6304ca3dea7d/`. No root probe, measured benchmark, user-file mutation or permission change.

Those four native cases establish app-surface compatibility on this artifact. New preparation Retry, staged bytes and cancellation assertions are host consumers; no decoded-video, seek/frame or device Retry acceptance is claimed. Unchanged playback-speed and external-action suites were not part of the 33-case local filter; full current-head CI must run the complete suites before integration.

The focused branch matches tested app tree and excludes owner-deferred benchmark #63. Plugin records/version dependency PR #81 is accepted as `377a378b95d9bcd0334d3ef57ca5844e6db82335`, final head `6a8fff403a7f4492f1d4d7e19724b8b983cde716`, CI `40108734849`. [PR #82](https://github.com/DevBehindYou/Atomic-File-Manager/pull/82) is published with matching app tree. Final head `de85c8830a18722ec521a545410039012859807c` passes both required jobs in CI `40115420936`, including the complete unit/APK and debug/release Lint gates (manual emulator skipped). Independent review, resolved-thread inspection and a fresh clean guarded trial preceded feature-integration merge `338f37ddd54bb679d448124a054e42148d360fb9`. VID-T01 is partial existing U09 work, not completion of U09 or advanced eight-plugin modernization. Fixed milestone credit remains **15% host verified / 8% accepted**; main/release acceptance and owner-deferred benchmarking remain separate.