# Browse sort directions and folder header

Prepared against PR #66 source `80f98ea`; validation record updated 22 October 2026. Focused host and physical API34 checks pass. This branch remains stacked on the unmerged visibility slice; isolated current-head CI and integration are separate gates.

Files offers six explicit choices in this order: Name A–Z, Name Z–A, Date ascending, Date descending, Size ascending and Size descending. The existing persisted names `NAME`, `DATE_MODIFIED` and `SIZE` still mean ascending name, descending date and descending size. Three additional names represent the inverse choices; no stored preference is renamed or migrated.

Folders remain before files in every direction. A direction affects its chosen field within each group. Unknown dates (`0`) and sizes (`-1`) follow known values in either direction; a zero-byte file remains a known size. Equal values use case-insensitive name, exact name and node identity to produce a deterministic order across listing refreshes. Sorting does not alter the input or the existing query/hidden/category filters.

The shared list/grid folder header now gives the stored folder/storage name its own full-width row, using the existing smaller `Name` typography role. It remains one line and ellipsizes only when it cannot fit; semantics retain the complete name. A separate row underneath aligns the item count and sort label to the right and can wrap on unusually narrow windows. Its accessible description remains `$count items · ${sort.label}`, preserving the benchmark selector contract. Other title rows in the app are unchanged.

The Files options and Tasks anchored popups share a balanced heading: equal touch-sized Back/Close areas, with an empty leading area when Back is absent, center the title between the controls. Popup positioning, content sizing and scrolling are unchanged. Home Commands opens the existing bottom command sheet, not an anchored popup with this asymmetric header, so it needs no change in this slice.

Prepared checks:

- `BrowseItemOrderingTest`: all six orders using mixed folder/file fixtures; deterministic ties; unknown metadata and zero-byte/maximum values; query, hidden and category filtering.
- `BrowseSortLoadingTest`: all six persisted names restore through the real ViewModel loader and save unchanged, including the three legacy names. Existing preference-read/save races remain covered.
- `BrowseOptionsComposeTest`: actual popup reports all six selections, dismisses after choosing and restores the selected radio mark on reopening.
- Popup heading geometry checks cover main/subpages at larger text and both Tasks panel widths, with a one-pixel centering tolerance and equal control areas.
- `BrowseFolderTitleComposeTest`: a typical storage name fits; a long name at narrow width/large font stays one line with ellipsis and full semantics; metadata is below the full-width title, inside its row and aligned right.
- `MobileNavigationDeviceTest`: existing menu check uses the explicit size-direction label and scrolls to it.

## Recorded validation

The combined root source `66d7451` passed `build/audit-tools/browse-layout-sort-validation.log` with terminal exit 0 in 11m46s: formatting/ktlint, detekt, 44 selected cases (no failures, errors or skips), debug and instrumentation APK packaging, and debug Android Lint. The selected classes cover ordering, persisted sort loading, Files menu geometry/selection, folder-title geometry, Tasks geometry, primary visibility and navigation identity. Orthogonal opening and index changes are present in that combined source; isolated PR CI still validates this branch's own complete head.

Physical API34 checks passed all four selected cases: primary folder/clipboard/Back retention across actual Activity recreation, real external test-APK read-only PNG handoff, Files storage shortcuts/view/menu controls, and Tasks dismissal/Operations navigation. The UI cases exercise the updated size-direction label. Generated PNG/cache fixtures are private to these tests; no real user file mutation was performed. The test-only external handler APK was uninstalled afterward and existing all-files access remains allowed.

Installed debug APK SHA256: `7AB8B77A5564787F23180AB3CE4E437FA2BC74F87752849EE12728294970B350`; instrumentation APK: `7B1FD4C1D30F905E9A89BDDE154632E6612A2B3B4334CC9D1CCAD23E3A442B6E`. Logs, hashes, finalization JSON and phone captures are under ignored `build/audit-tools/device-preview-tools/browse-layout-acceptance/`. Visual inspection confirms the full-width storage name, right-aligned next-row metadata and centered View heading.

The wrapper originally returned exit 1 after all four cases passed because its final reporting assigned PowerShell's reserved `$HOME` variable. The helper was corrected without rerunning unchanged app cases. Read-only finalization separately verifies the Home capture, retained access and removed handler (`browse-layout-phone-finalization.exit=0`); the original wrapper error log is retained.

No measured speedup, font-scale-2/RTL/short-landscape phone acceptance, or completion of the broader optimization plan is claimed. New current-head CI, resolved review and a clean dependent merge remain required before integration.
