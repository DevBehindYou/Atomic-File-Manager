# Next agent continuation

Updated **4 November 2026**. Latest request is a documentation handover. No automation or new app/device run was started by this refresh. When the owner asks to resume, start with TASK-101; do not restart audits or duplicate existing PRs.

## Recover context and ownership

1. Read [STATE.yaml](STATE.yaml), [HANDOVER](HANDOVER.md), [CURRENT_STATE](CURRENT_STATE.md), [PENDING](PENDING.md), [TESTING](TESTING.md), then only the relevant scope-owner records. Historical snapshots are for unresolved provenance, not the default reading set.
2. Use an explicit working directory. The primary repository is on the published beta; the handover checkout contains documentation only. Accepted implementation is `codex/feature-upgrade-base` at the recorded `b995e23` snapshot. Re-read Git status, remote heads, worktrees, live agents and Java/Gradle processes before mutations. Environment-listed agent names are not live-work evidence.
3. Preserve user artwork, settings, permissions, ignored keys/tools/evidence and local candidate refs. Several old `build/audit-worktrees` directories are absent: recover committed refs into managed worktrees; do not prune or clean stale registrations before ownership is understood. No reset of the primary checkout.
4. Compare live PR heads/checks against the snapshot before continuing. Current #101 is `fb856a6`; host CI `41064609636` and native CI `41064609699` pass. Current #89 is `7d54483`; its source-trigger change was never applied. PDF/Document/Archive local heads differ from published PR heads (CURRENT_STATE).

## First continuation: GIF phone acceptance

Retain exact passing hosted results; do not rerun unchanged CI merely to recreate evidence. The current local full-build log lacks a completion/exit marker, so it cannot certify a local APK. Check phone availability and the installed signing identity before testing; the last idle confirmation is historical. Never uninstall/clear the app or change permissions to overcome a signature mismatch. CI APKs can have an ephemeral debug signer incompatible with the phone installation.

Prepare a compatible current-source artifact if needed. Use the pinned wrapper/JDK/SDK configuration and **one local Gradle process on this 8 GB host**. Capture stdout/stderr and the actual exit code in ignored evidence. Run applicable targeted host/static/packaging/Lint checks for changed code; incomplete output does not establish success. Derive test selectors from the current source/workflow rather than guessing stale class names. The native GIF suite comprises eight cases covering playback, finite completion/Replay, looping ownership and static image behavior on API28/API34; phone acceptance must exercise the current repair and retain source integrity.

Local branch `codex/gif-playback-recovery` publishes to the existing PR branch `codex/image-gif-playback`; check the exact remote head before pushing. Never force-push. After current artifact/phone acceptance, resolve review concerns and dependencies, fetch the target again and perform a clean merge trial. Check the current PR head has all applicable checks before any merge. `FETCH_HEAD` and a stale remote-tracking ref are not interchangeable evidence. Record integration completion separately from main/release.

## Then Markdown and prepared candidates

Use the existing recovered Markdown checkout at `7d54483`, currently detached/clean. Establish branch ownership before editing. Apply the four production workflow paths in PENDING and validate YAML. Integrate accepted dependencies; the known GIF/Markdown conflict is in `docs/plugins/modernization/PLUGIN_PROGRESS.md`. Preserve both partial records and fixed credit. Revalidate the actual combined source, not old standalone evidence.

Relevant host classes include `MarkdownPreparationRecoveryComposeTest`, `MarkdownBlocksTest`, `TextPreviewBoundsTest`, `PreviewTypeTest`, plus affected opening/Editor regressions. Native ownership class is `com.devbehindyou.atomicfilemanager.MarkdownOwnershipDeviceTest`, method `retryRevisionAndRawDisclosurePreserveOwnedSourceAndSentinel`. Check their current paths/signatures before use. Complete applicable full CI, native and packaging/Lint gates on the PR head. Existing cancelled runs are not passes.

Continue existing PDF #91, Archive #92 and Document #93 after reconciling newer retained local commits. Image-fit work overlaps GIF and has no PR yet. Parallel source/review agents are authorized on isolated branches; local Gradle remains serialized. Per-plugin records and N42 matrix govern full acceptance. Cloud Connect is separate ninth-plugin scope.

## Non-negotiable boundaries

- Measured benchmarks are owner-deferred. No `su`/root-manager probes, permission changes or larger corpora; do independent host work instead.
- Recurring continuation automation was permanently deleted. Do not recreate it.
- Keep all plugins bundled Kotlin/Compose; no executable downloads from GitHub/Drive.
- Do not expose signing files/passwords or put private backups in handover archives. Preserve immutable beta assets and existing signing identity.
- Never bypass failed/missing checks, fabricate logs, weaken integrity assertions or award whole-group progress to partial fixes. Current feature credit is 15% host / 8% accepted; full plugin-group credit is 0% with partial work recorded separately.
- A sandbox/approval failure, if encountered, must be reported accurately; historical failures do not establish a current blocker. Stop only dependent work and preserve other progress.
- Report meaningful verified changes, required input or actual completion. Do not claim stable v0.2.1 is done while acceptance remains open.
