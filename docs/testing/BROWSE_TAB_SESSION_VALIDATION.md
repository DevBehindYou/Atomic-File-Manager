# Browse tab session identity — U04 follow-up candidate

Prepared 22 October 2026 from `d8dca8b`, preserving the Storage/Analysis candidate and accepted primary visibility work. The verified checkpoint below records completed source/host/device checks and the remaining current-head merge gate.

## Problem and change

Browse's numeric tab ID is allocated from the largest currently open ID. Closing the highest tab and reopening the same root can therefore reuse the old Activity-owned model key. The retained closed model can bring back its navigation, clipboard, selection and search state because the newly opened tab starts with request 0 again.

Each newly opened tab now receives a UUID session identity, saved as an additional optional field in the existing tab-state string. Root changes, retitling and selection preserve that identity. Both the composition key and primary/secondary pane state prefix include the session. Numeric IDs and the four-tab limit remain unchanged.

Older four-field saves keep their existing tab IDs, requests, titles, roots and legacy model keys. The initial single tab retains its existing primary key, including the native recreation fixture's lookup. Missing or malformed optional session values fall back to the legacy key; canonical UUID values round-trip unchanged. New sessions are generated only by explicit open-tab actions, not during composition or decoding. No preferences, permissions or user files are changed, and no dependency or API-level branch is added.

This slice prevents closed-session reuse; it does **not** prune retained Activity stores. Closed models remain retained and hidden until the Activity store clears. Pruning/root ownership is a separate follow-up because clearing a model also affects direct create/rename jobs; accepted mutations, sort saves and the global queue must retain appropriate ownership. Existing per-pane clipboard behavior for still-open tabs is preserved.

## Regression candidates and next gates

- Existing `BrowseTabsTest` cases plus five session cases: close-highest/reopen-same-root, saved state/recreation, deterministic legacy decoding, malformed optional fields, and repeated close/reopen with unaffected siblings.
- Two `BrowseTabSessionModelTest` cases use the actual production model-key helper and retained real BrowseViewModels. They require fresh state on reopening a closed session even with a reused numeric ID/root, and reuse of surviving/restored open sessions without leaking clipboard/search/history.
- The prepared gates are parent-serialized ktlint/detekt, focused tab/session/primary lifecycle/state tests, APK packaging, Lint and phone close/reopen/recreation. The checkpoint below records execution; exact-head CI/review/clean trial still govern integration.

## Prepared native close/reopen case

`BrowseTabSessionDeviceTest.closingHighestTabAndReopeningSameFolderCreatesFreshStateAcrossRecreation` exercises the production `MainActivity` tab wiring. It opens Target with the actual long-press → More actions → New tab commands, stages an in-memory clipboard through Copy, enters Child, closes the highest active tab, and reopens the same Target folder through the same UI. Both Target child rows must return, its clipboard bar must be absent, and activity recreation must preserve that fresh Target state. One Back press must then reach Home, proving the closed tab's Child → Target history was not restored. The surviving primary tab must retain its model, clipboard, search and own parent history after selection and recreation.

The native case deliberately stages clipboard/navigation in the closed tab. Closed-tab search/selection isolation and numeric-ID reuse are proven by the focused host tests; this case does not claim to stage or inspect those fields independently on the phone. No restricted Activity store enumeration or reflection is used.

The fixture creates only six empty directories beneath a unique `context.cacheDir/browse-session-<UUID>` root. It records successful directory creation, verifies the exact canonical cache parent and containment before cleanup, and removes only those empty owned directories in reverse order. It never invokes Paste, touches shared storage, changes settings or grants permissions. Teardown attempts Home restoration and retains any original test/creation failure, attaching cleanup failures as suppressed exceptions. No recursive delete is used.

This section describes the prepared native fixture; its actual compilation, packaging and phone results are recorded in the verified checkpoint below. The parent owns serialized installation, test-APK removal and exact-head evidence. After building and installing the matching app and test APKs, the narrow runner command is:

```powershell
adb shell am instrument -w -r -e class com.devbehindyou.atomicfilemanager.BrowseTabSessionDeviceTest com.devbehindyou.atomicfilemanager.test/androidx.test.runner.AndroidJUnitRunner
```

The fixed feature denominator remains 8% host verified / 8% accepted (1/13). This partial U04 source increment earns no full milestone credit and is not main/release completion. The deleted recurring automation remains deleted.

## Verified source and device checkpoint — 22 October

`browse-tab-sessions-validation.log` now exits 0 in 6m1s: all 42 focused cases pass (9 tab-state, 2 actual session-model, 8 STARTED lifecycle, 3 visibility, 1 primary state, 5 pane lifecycle, 3 navigation identity and 11 sort-loading cases), plus static checks, APK packaging and Lint. No failures/errors/skips. Native-fixture compilation/static/APK/Lint passes separately in 2m17s. The earlier interrupted Lint log is retained, not a pass.

The first four-case runner stalled at the pre-existing startup splash gate before any tab fixture began; it was deliberately stopped after diagnostics. Startup repair #72 reproduces the old cancellation on API27/34, passes with the fix, and is integrated. With #72 and centered UI #73 present, all four API34 cases now pass in 25.425s, including actual close-highest/reopen-same-folder, fresh Back behavior, activity recreation and surviving-tab state. Final app tree `9ec9b8dc8acda01fe5e37fb9e72eaf95876b1c64` exactly matches this synchronized source. Debug SHA-256: `6C3ACEF1142BDAEC5B53B9B90F288F06C837E6666F210247DC4B088AE1C34ABA`; test SHA-256: `BED18771E468D770764FB3AD9DDA1742D9ED6A55381E23C79E298858E589ACC8`. Native/visual wrappers exit 0 with selected Home, unchanged access and removed test handler; exact owned cache cleanup passes. Evidence is under `startup-analysis-final-acceptance/` and `startup-analysis-final-visual/`.

The older published `8c80870` passes historical CI `39752514936`. Final synchronized head `67adad8422df455368ae42ca92eb393628bfcabd` passes both required jobs in CI `39804269314`; manual emulator execution is skipped, with physical results recorded separately above. Review-thread inspection and a fresh clean guarded trial preceded feature-integration merge `e60d8e78a9eb7f932461c0b5dd65a9911afe9a59`. Gate snapshots and trial results are retained under `build/audit-tools/pr71-acceptance/`. Retained-store pruning, mutation ownership and broader U04 provider/process-death gates remain open. Consult PR #71 for the live merge gate; no full milestone credit is added here.
