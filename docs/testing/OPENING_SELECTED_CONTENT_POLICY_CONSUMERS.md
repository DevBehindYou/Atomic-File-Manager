# Selected-content policy and lifecycle — proposed pane consumers

Prepared 25 October 2026. Test-only proposal based on accepted feature integration `4d7157f528fae3ea5ef81de80c6f9db7bdf7aeea` (PR85), production main tree `cc00d88b`. Follows [the selected-content design](OPENING_SELECTED_CONTENT_DESIGN.md) at proposal commit `1c1d7b5`. No production file or existing frozen consumer is modified. Parent owns scoped formatting, freezing, compilation, genuine unchanged-production baseline and later source validation.

## Four actual-pane consumers

OpeningSelectedContentPolicyComposeTest uses actual FilePreviewPane calls, real package-manager discovery, existing exporter and Intent construction. It introduces no production API or test seam.

- Initial unknown generated PNG resolves a saved exact image/png external target and hands off automatically exactly once. It checks intended component, MIME, label/ClipData, exact exported and resolver-readback bytes, read-only flags, no preference writes, stream closure, and no second launch after a same-session recomposition.
- The equivalent IMAGE-family target independently requires detected family policy to apply once, preserving the existing domain precedence implementation.
- Actual parent state changes A to B to C with A's first prefix read blocked. B is admitted through its own composition before C replaces it. Only A may read until its stream drains; B must never read. C is separately blocked so the test can observe C's checking/header with no stale A reader, launch, preference write or export. After C releases, its reader and one manual handoff must use C's distinct blue PNG bytes. Requests must be A probe, C probe, C export; A and C close their streams.
- Explicit Close occurs while A's first read is blocked and a saved PNG target could otherwise launch later. The caller intentionally keeps the pane mounted. After releasing A and forcing same-ID recomposition, no late reader, checking UI, choices, activity, preference update, export or new source request may appear.

Public semantics are existing Close label `Close` and proposed user-visible `Checking file type…` text, confirmed with the source author. These are real user controls/status, not internal coordinator hooks. The original four unknown-content and six metadata/gallery consumers remain untouched. Expected filter `*OpeningSelectedContentPolicyComposeTest`, count4; all are **unrun**. Current metadata-only production is expected to fail at missing automatic handoff or missing prefix-read admission, but no confirmed RED is claimed until parent execution distinguishes target assertions from compilation/fixture errors.

## Ownership and limits

One test sandbox container dispatches UUID-qualified FTP-shaped identities to an explicitly FILE-typed controlled backend returning real tiny private-cache file streams. This exercises supported local stream ownership and remote-style export copying without claiming FTP/network prefix-read safety. Three generated PNG payloads have distinct colors. Streams and requests are tracked; each gated read has a 30-second failure bound, and teardown releases all gates before disposing composition even on assertion failure. No root, ADB, network, permission or real account is touched.

Each consumer performs at most one actual handoff. A unique sentinel and preexisting export snapshot are verified, sources remain byte-identical, and cleanup deletes only the captured new export and exact owned sentinel/source files. Original container and launch validation are restored. Settings and recents are sandbox implementations. No recursive deletion or user/shared-storage mutation is performed.

OwnedExportFileProviderShadow is explicitly scoped to this test. It serves actual owned copied bytes while replacing host URI mapping/grants. Read flags in host Intents are not proof of real Android roots, cross-UID access, real provider/network behavior or revoked grants.

Tiny PNGs and latch ordering do not establish a byte budget, backend read-ahead bound, wall-clock performance or a device cancellation result. Separate helper/coordinator contracts owned by the source author must cover prefix limits, short/zero reads, eager acquisition, failure, cancellation and resource cleanup. Existing opening/viewer/static/Lint/artifact and appropriate native gates remain required. No Gradle, formatter, device, GREEN, CI, merge, whole-milestone or release claim follows. Progress and all eight plugin v0.2 records remain unchanged.

## Frozen policy/lifecycle baseline — 25 October

Parent imports the independently reviewed four policy consumers, then applies only scoped line wrapping. Original pre-format bytes are retained in `build/audit-tools/opening-selected-policy-preformat.kt`. Frozen source `52096175b8cd1a08866809487c2bb620f536dfc8`, app tree `d283a118128c71589e3b46032f3c6682e78b4697`, production main tree `cc00d88bcb8bdfa117bf28e830319a90270deef8` is identical to accepted PR85. Working test SHA256 `5116B6A591F193C6F2B56A61661308965DCE4D6C47B5AFD793728B70E4D31270` remains unchanged during the run.

All four execute in3m21s, exit1, suite98.056s: four expected ComposeTimeout assertions, no errors/skips. The two saved-policy cases cannot reach automatic typed handoff; the two blocked-source lifecycle cases cannot reach initial prefix-read admission because no detection path exists yet. Thus the baseline confirms missing detection, not an independently observed old-source leak or stale cancellation bug. Logs/exit `build/audit-tools/opening-selected-policy-red-validation.*`, source manifest `opening-selected-policy-red-source.json`, copied XML `opening-selected-policy-red-results.xml` retain exact evidence. New helper/coordinator increments are subsequently compiled separately; no UI GREEN follows until integration is reviewed and these same assertions pass.
