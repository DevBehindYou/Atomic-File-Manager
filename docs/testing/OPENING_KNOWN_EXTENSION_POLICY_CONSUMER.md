# Known-extension opening policy — additive proposed consumer

Prepared25 October2026. This is one new actual-pane consumer for existing U06 metadata-first routing, not a reproduced defect, source fix or accepted milestone. The test branch starts from accepted PR85 source4d7157f; the original four policy/lifecycle method bodies and fixture helpers are preserved, with parent formatting5209617 imported separately. No production file changes.

## Source observation and proposed behavior

For a known .png filename with generic binary MIME, externalMimeType already resolves image/png from the platform extension map. However, targetFor on the raw node cannot match a saved exact image/png rule while raw MIME remains application/octet-stream. This is a source-observed policy gap. The parent must compile and execute the new case against unchanged opening UI before claiming a reproduced failure.

The additive method knownPngExtensionAppliesSavedMimeTargetWithoutAContentProbe seeds a saved exact image/png external target with global Ask. It registers the established Robolectric png extension mapping, sets this run's first source to a known .png name with explicit generic MIME, then opens the actual FilePreviewPane. Automatic handoff must use the intended PNG component/MIME, current ClipData label and URI, exact copied and URI-readback PNG bytes and temporary read-only flags through the existing assertHandoff helper. Exactly one backend openInput request is permitted: the real export. Any content probe would introduce an additional request and fail. A same-session display-name recomposition must not trigger a second launch/Close/request or mutate preferences.

Expected filter: *OpeningSelectedContentPolicyComposeTest.knownPngExtensionAppliesSavedMimeTargetWithoutAContentProbe, count1. The source author has not run Gradle, formatter, device or CI. The expected missing-automatic-handoff baseline remains unconfirmed. Parent owns formatting, freezing, genuine baseline, implementation validation and acceptance.

## Ownership and limits

The existing fixture dispatches UUID-qualified FTP-shaped identities to a controlled actual FILE backend over generated tiny private-cache PNGs. It preserves all streams/source bytes, sandbox settings/container/recents, owned sentinel and unrelated exports; it deletes only captured owned fixtures, with at most one handoff. No user/shared file, root, permission, account or network is involved. The scoped OwnedExportFileProviderShadow serves actual owned exported bytes while replacing host URI mapping; these Intent assertions do not prove native provider roots, cross-UID access or real FTP behavior.

The test proves the intended effective metadata route and no content probe for this known-extension case if it passes. It does not prove decoding, null-MIME variants independently, broad format detection, provider revocation or all metadata precedence combinations. Existing valid-MIME authority and explicit extension-rule controls remain separate required regressions. All eight plugin versions and fixed milestone percentages remain unchanged.