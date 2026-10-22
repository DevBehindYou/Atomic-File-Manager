# Startup first-frame availability — priority fix candidate

Prepared 22 October 2026 from accepted feature integration commit `32a506eeda0c749b661b9885ce8a79d66eb01c4b`. This record describes source and pending validation; it does not claim a build, host, phone or CI pass.

## Observed failure and bounded diagnosis

The four-case device run remained in its first Storage capacity case while `MainActivity` had reached RESUMED. The visible hierarchy contained Loading saved settings and Close. The captured successful socket JDWP dump showed Main idle in its Looper, all three DefaultDispatcher workers parked, and the instrumentation thread waiting inside `Instrumentation.startActivitySync` → `ActivityScenario.launch` → `ActivityScenarioRule.before`, under the Compose test scheduler. No active SharedPreferences read or settings-lazy lock appeared in those captured stacks. This snapshot does not establish the exact startup StateFlow value or explain every independently reported black screen.

The old splash condition nevertheless contains a circular first-frame dependency: it prevents drawing while settings load and, after Ready, until a `SideEffect` inside the Ready composition runs. When a Compose test scheduler defers that composition while the instrumentation thread synchronously waits for first draw, neither side can advance. Holding the splash during a slow or failed-to-complete read also prevents the existing Loading/Close surface from becoming visible.

Adding another V1 Compose rule is not established as the cause. The earlier passing three-case run already mixed the V1 primary-Browse rule with V2 Storage. Cached Compose bytecode places its global window-recomposer replacement inside rule evaluation with finally restoration, rather than in the rule factory.

## Small production change

Keep `installSplashScreen()` and remove only the custom keep-on-screen predicate, the `appContentReady` variable and its `SideEffect`. Android may draw the existing startup surface while settings load. `AtomicAppContent` remains behind the unchanged `SettingsStartupState.Ready` gate, and the existing IO loader still returns the fully seeded process-shared repository. Loading, failure, retry, Close, theme selection and pending external intents retain their current behavior.

There is no default-settings fallback, timeout, new dependency, permission change, preference write or user-data mutation. This fix does not clear app data or replace the persisted settings repository.

## Actual Activity regression and next gates

`MainActivityFirstFrameTest` runs the real `MainActivity` at API 27 and 34 in a paused Looper. It creates the Activity without entering STARTED or advancing Compose, verifies the actual Compose content container exists, and dispatches pre-draw through its real `android.R.id.content` observer. Cached `core-splashscreen` 1.0.1 bytecode confirms both the legacy implementation and API 31+ implementation register their keep predicate on that content observer. `dispatchOnPreDraw()` reports cancellation, so the test requires false before Ready composition or any Ready `SideEffect` has run. The old source is expected to cancel that draw whether its loader is still Loading or has reached Ready; a red/green execution remains required.

The parent owns serialized validation: prove the new regression fails against the old MainActivity, then passes with the fix; rerun settings-loader, preference-preservation, loading/failure UI and pending-intent recreation tests; run static checks, APK packaging and Lint; perform a native cold launch and repeat the four-case device run against recorded matching APK hashes. Independent review, exact-current-head CI and a clean trial merge remain acceptance gates.

The existing fixed feature ledger remains 8% host verified / 8% accepted (1/13). This priority correction adds no completed feature milestone and does not complete main/release acceptance. The deleted recurring automation remains deleted.

## Reproduction checkpoint

Before applying the fix, the actual Activity regression failed at its expected draw-cancellation assertion on both API27 and API34 (2 failures, 0 errors/skips; `startup-first-frame-red-validation.log`, exit1, 1m5s). This used the combined checkout with the unchanged old startup gate, not an isolated phone artifact. Corrected-source host/static/APK/Lint checks and a native cold launch are still pending. The original four-case native runner was deliberately stopped during its first Activity launch after diagnostics; that interruption is not a spontaneous crash or a test pass. The phone recovered to Home, its test handler was removed and all-files access stayed allowed.
