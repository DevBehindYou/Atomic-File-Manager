# Plugin modernization — parent host evidence

Recorded 26 October 2026. This record tracks executed parent validation separately from source-author proposals. The [42-group ledger](../plugins/modernization/PLUGIN_PROGRESS.md) and the existing U01–U13 ledger remain separate. No whole group or plugin credit follows from a failing baseline.

## First Image, Audio and shared-media baselines

Source `768657588db844a53df6581da2b8ae9578f28f40`, app tree `8590f723b3d60dd32793e4a882434bb11795245d`, main tree `252211496965a4389d42277378636eeb8a17b0f0`. Image and Audio production files are unchanged from accepted integration #85; the shared player has only the behavior-preserving factory extraction, without focus/noisy opt-ins. The root architecture branch also contains unrelated work and deferred benchmarks: never publish it wholesale.

Configured ktlint/Detekt pass. The selected test task runs **23 cases: 19 intended failures, four controls passing, zero errors/skips**, in **4m33s**, exit 1. Evidence in the primary workspace's ignored `build/audit-tools/`: `plugin-first-baselines-retry-source.json`, `plugin-first-baselines-retry-validation.log/.exit`, copied `plugin-first-baselines-results/` XML and `plugin-first-baselines-results.json`.

| Scope | Cases / result | Actual observation |
| --- | --- | --- |
| Image gesture consumers | 4 failures, suite 25.906s | Off-centre double-tap/pinch replace the expected green marker with red; wide/tall short-axis panning moves the image away from the viewport centre, leaving transparent pixels. Initial marker rendering succeeds before the double-tap assertion. |
| Audio preparation consumers | 3 failures, 2 controls pass, suite 20.811s | Two actual preparation failures have no Retry action; same-ID revision does not acquire the replacement. Pending-source switch and disposal cleanup controls pass. Staging bytes and metadata are controlled; this does not establish decoded audio. |
| Shared media control policy | 12 failures, 2 paused controls pass, suite 12.323s | API 27/34 playback never requests platform focus and never registers the noisy-output receiver. Expected focus-control checks cannot proceed; both initially paused controls pass. Actual ExoPlayer control state is exercised through platform shadows, not audible hardware. |

The preceding attempt on source `6682733` stopped at Detekt's unused private Image `imageNode` helper in 2m34s, exit 1; no test ran. Source `7686575` removes only that obsolete helper/import. Preserve `plugin-first-baselines-validation.log/.exit` and its source manifest; do not relabel the static failure as a behavioral reproduction.

## Candidates and outstanding acceptance

Image: test proposal `3dcf2a7` / viewport correction `6886fcb`; source `56298bf`, finite-centroid guard `e37f8278`, native proposal `3de0873`. Ten pure geometry cases and four pointer/pixel consumers are prepared. Four native real-FILE/gesture consumers remain uncompiled and unrun. Native proposal adds no activity, manifest, permission or preference change.

Shared player: baseline `8bdd603`, focus/noisy policy `94703bb`. Audio: test-first `3ad2987`, typed revision/attempt-owned Retry candidate `accb8f0`. Independent source/fixture review is clear; corrected host/static/APK/Lint, applicable native evidence, current-head CI and guarded integration are still required. Plugin versions remain v0.2.

Whole-plugin modernization remains **0/8 (0%)**; host-verified and accepted capability groups remain **0/42 (0%)**. Image geometry is only part of IMG1; media control/recovery is only part of VID1/AUD1. Full formats, restoration, gestures, physical interruptions, provider/security/performance and end-to-end journeys remain open. The benchmark permission policy stays unchanged and measurements remain owner-deferred.