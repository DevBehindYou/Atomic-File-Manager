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

The first two consumers wait for either the old visible preparation error or the requested action, then assert the action. They should fail on unchanged production through a missing-action assertion, rather than an unrelated UI-capture or generic wait failure. Parent-run RED is still required to confirm that expectation.

Each test creates a uniquely owned directory under application cache and supplies it through a `ContextWrapper`. Teardown disposes the preview, waits for structured cleanup and deletes that empty directory. Pending acquisition is cancellable. No shared-storage content, preferences or grants are touched. Staged byte fixtures and supplied metadata verify preparation and ownership only; host ExoPlayer creation or a player error is not decoded video-playback acceptance.

## Pending validation and limits

No build, test or phone run has been performed for this preparation. Parent-run unchanged-source RED, corrected-source GREEN, static checks, APK compilation, Lint, independent review and final-head CI remain pending. Filter: `*VideoPreparationRecoveryComposeTest`.

Retain existing `MediaPreviewOwnershipTest`, `MediaPreviewHelperTest`, `PreviewPlayerLifecycleTest`, `PlaybackSpeedsTest` and preview routing/external-action coverage for the affected host gate. Explicit source/revision replacement, successful-source recomposition, real playback, lifecycle/recreation, source disconnects, corrupt/unsupported media and decoded-frame acceptance still require appropriate checks. This change does not claim saved playback position, focus/Bluetooth/call handling, subtitles/audio-track controls, a complete format matrix, measured resource improvements or release completion.
