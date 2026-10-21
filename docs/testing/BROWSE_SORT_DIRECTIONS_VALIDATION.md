# Browse sort directions and folder header

Prepared against PR #66 source `80f98ea` on 21 October 2026. Candidate source only; no Gradle or device checks have run for this slice.

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

Root coordinates formatting, static analysis, selected host tests, APK compilation, Android Lint and device checks. No pass, measured speedup or completion of the broader optimization plan is claimed here.
