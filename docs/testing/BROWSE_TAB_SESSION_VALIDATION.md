# Browse tab session identity — U04 follow-up candidate

Prepared 22 October 2026 from `d8dca8b`, preserving the Storage/Analysis candidate and accepted primary visibility work. Source review and validation are pending; this record does not claim a build, test, device or CI pass.

## Problem and change

Browse's numeric tab ID is allocated from the largest currently open ID. Closing the highest tab and reopening the same root can therefore reuse the old Activity-owned model key. The retained closed model can bring back its navigation, clipboard, selection and search state because the newly opened tab starts with request 0 again.

Each newly opened tab now receives a UUID session identity, saved as an additional optional field in the existing tab-state string. Root changes, retitling and selection preserve that identity. Both the composition key and primary/secondary pane state prefix include the session. Numeric IDs and the four-tab limit remain unchanged.

Older four-field saves keep their existing tab IDs, requests, titles, roots and legacy model keys. The initial single tab retains its existing primary key, including the native recreation fixture's lookup. Missing or malformed optional session values fall back to the legacy key; canonical UUID values round-trip unchanged. New sessions are generated only by explicit open-tab actions, not during composition or decoding. No preferences, permissions or user files are changed, and no dependency or API-level branch is added.

This slice prevents closed-session reuse; it does **not** prune retained Activity stores. Closed models remain retained and hidden until the Activity store clears. Pruning/root ownership is a separate follow-up because clearing a model also affects direct create/rename jobs; accepted mutations, sort saves and the global queue must retain appropriate ownership. Existing per-pane clipboard behavior for still-open tabs is preserved.

## Regression candidates and next gates

- Existing `BrowseTabsTest` cases plus five session cases: close-highest/reopen-same-root, saved state/recreation, deterministic legacy decoding, malformed optional fields, and repeated close/reopen with unaffected siblings.
- Two `BrowseTabSessionModelTest` cases use the actual production model-key helper and retained real BrowseViewModels. They require fresh state on reopening a closed session even with a reused numeric ID/root, and reuse of surviving/restored open sessions without leaking clipboard/search/history.
- Parent-serialized ktlint/detekt, focused tab/session/primary lifecycle/state tests, APK packaging and Lint remain pending. A phone close/reopen and recreation check, independent review, exact-head CI and clean trial merge remain acceptance gates.

## Prepared native close/reopen case

`BrowseTabSessionDeviceTest.closingHighestTabAndReopeningSameFolderCreatesFreshStateAcrossRecreation` exercises the production `MainActivity` tab wiring. It opens Target with the actual long-press → More actions → New tab commands, stages an in-memory clipboard through Copy, enters Child, closes the highest active tab, and reopens the same Target folder through the same UI. Both Target child rows must return, its clipboard bar must be absent, and activity recreation must preserve that fresh Target state. One Back press must then reach Home, proving the closed tab's Child → Target history was not restored. The surviving primary tab must retain its model, clipboard, search and own parent history after selection and recreation.

The native case deliberately stages clipboard/navigation in the closed tab. Closed-tab search/selection isolation and numeric-ID reuse are proven by the focused host tests; this case does not claim to stage or inspect those fields independently on the phone. No restricted Activity store enumeration or reflection is used.

The fixture creates only six empty directories beneath a unique `context.cacheDir/browse-session-<UUID>` root. It records successful directory creation, verifies the exact canonical cache parent and containment before cleanup, and removes only those empty owned directories in reverse order. It never invokes Paste, touches shared storage, changes settings or grants permissions. Teardown attempts Home restoration and retains any original test/creation failure, attaching cleanup failures as suppressed exceptions. No recursive delete is used.

The native source and this section are prepared only: no native compilation, packaging or phone pass is claimed here. The parent owns serialized build, installation, execution, test-APK removal and exact-head evidence. After building and installing the matching app and test APKs, the narrow runner command is:

```powershell
adb shell am instrument -w -r -e class com.devbehindyou.atomicfilemanager.BrowseTabSessionDeviceTest com.devbehindyou.atomicfilemanager.test/androidx.test.runner.AndroidJUnitRunner
```

The fixed feature denominator remains 8% host verified / 8% accepted (1/13). This partial U04 source increment earns no full milestone credit and is not main/release completion. The deleted recurring automation remains deleted.
