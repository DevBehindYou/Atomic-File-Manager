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

The fixed feature denominator remains 8% host verified / 8% accepted (1/13). This partial U04 source increment earns no full milestone credit and is not main/release completion. The deleted recurring automation remains deleted.
