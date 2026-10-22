# Startup first-frame availability — priority fix candidate

Prepared 22 October 2026 from accepted feature integration commit `32a506eeda0c749b661b9885ce8a79d66eb01c4b`. The verified checkpoint below records host/native passes and the guarded feature-integration merge; main/release acceptance remains separate.

## Observed failure and bounded diagnosis

The four-case device run remained in its first Storage capacity case while `MainActivity` had reached RESUMED. The visible hierarchy contained Loading saved settings and Close. The captured successful socket JDWP dump showed Main idle in its Looper, all three DefaultDispatcher workers parked, and the instrumentation thread waiting inside `Instrumentation.startActivitySync` → `ActivityScenario.launch` → `ActivityScenarioRule.before`, under the Compose test scheduler. No active SharedPreferences read or settings-lazy lock appeared in those captured stacks. This snapshot does not establish the exact startup StateFlow value or explain every independently reported black screen.

The old splash condition nevertheless contains a circular first-frame dependency: it prevents drawing while settings load and, after Ready, until a `SideEffect` inside the Ready composition runs. When a Compose test scheduler defers that composition while the instrumentation thread synchronously waits for first draw, neither side can advance. Holding the splash during a slow or failed-to-complete read also prevents the existing Loading/Close surface from becoming visible.

Adding another V1 Compose rule is not established as the cause. The earlier passing three-case run already mixed the V1 primary-Browse rule with V2 Storage. Cached Compose bytecode places its global window-recomposer replacement inside rule evaluation with finally restoration, rather than in the rule factory.

## Small production change

Keep `installSplashScreen()` and remove only the custom keep-on-screen predicate, the `appContentReady` variable and its `SideEffect`. Android may draw the existing startup surface while settings load. `AtomicAppContent` remains behind the unchanged `SettingsStartupState.Ready` gate, and the existing IO loader still returns the fully seeded process-shared repository. Loading, failure, retry, Close, theme selection and pending external intents retain their current behavior.

There is no default-settings fallback, timeout, new dependency, permission change, preference write or user-data mutation. This fix does not clear app data or replace the persisted settings repository.

## Actual Activity regression and next gates

`MainActivityFirstFrameTest` runs the real `MainActivity` at API 27 and 34 in a paused Looper. It creates the Activity without entering STARTED or advancing Compose, verifies the actual Compose content container exists, and dispatches pre-draw through its real `android.R.id.content` observer. Cached `core-splashscreen` 1.0.1 bytecode confirms both the legacy implementation and API 31+ implementation register their keep predicate on that content observer. `dispatchOnPreDraw()` reports cancellation, so the test requires false before Ready composition or any Ready `SideEffect` has run. The old source is expected to cancel that draw whether its loader is still Loading or has reached Ready; the verified red/green execution is recorded below.

The parent owns serialized validation: prove the new regression fails against the old MainActivity, then passes with the fix; rerun settings-loader, preference-preservation, loading/failure UI and pending-intent recreation tests; run static checks, APK packaging and Lint; perform a native cold launch and repeat the four-case device run against recorded matching APK hashes. Independent review, exact-current-head CI and a clean trial merge remain acceptance gates.

The existing fixed feature ledger remains 8% host verified / 8% accepted (1/13). This priority correction adds no completed feature milestone and does not complete main/release acceptance. The deleted recurring automation remains deleted.

## Verified checkpoint — 22 October

The real Activity regression first fails at its expected cancellation assertion on both API27/34 against the unchanged old gate (2 failures, 0 errors/skips; red log exit 1 in 1m5s). With this fix both cases pass. The complete corrected run passes all 31 selected cases, static checks, debug/AndroidTest packaging and Android Lint in 7m12s, exit 0 (`startup-first-frame-green-validation.log` and its saved JSON results). Lint reports 0 errors, 154 existing warnings and 1 hint. The 31 cases include 2 first-frame, 2 preferences, 6 pending-open, 2 Activity smoke, 5 Analysis opt-in, 7 settings-loader, 2 startup-screen and 5 storage-readiness cases.

Final source head `6ca7b2e` passes full CI `39801041577` (including debug/release Lint and unit/APK packaging; manual emulator skipped). Review and a clean guarded trial preceded feature-integration merge `952ba19222abc1c68afb27d255161468abd0399a`.

A fresh physical API34 cold launch now advances and all four native cases pass in 25.425s on combined `bc6d9f0`, app tree `9ec9b8dc8acda01fe5e37fb9e72eaf95876b1c64`. This also includes independent tab-session #71 and centered-button #73; it is not isolated-artifact evidence. Debug SHA-256: `6C3ACEF1142BDAEC5B53B9B90F288F06C837E6666F210247DC4B088AE1C34ABA`; test SHA-256: `BED18771E468D770764FB3AD9DDA1742D9ED6A55381E23C79E298858E589ACC8`. The initial hung runner was deliberately stopped, not relabeled or diagnosed as a spontaneous crash. Final native/visual wrappers exit 0, preserve access and remove the test handler, with Home restored. Evidence is under `build/audit-tools/device-preview-tools/startup-analysis-final-acceptance/` and `startup-analysis-final-visual/`. No preferences or user files were cleared, and no SharedPreferences lock was inferred from idle thread stacks.
