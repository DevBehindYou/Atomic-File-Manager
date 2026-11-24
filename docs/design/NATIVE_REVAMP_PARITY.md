# Native revamp parity audit — 14 November 2026

Reference: owner-approved `Revamp-UIUX/04-Design-Prototypes/index.html`, its CSS and saved 05-Visual-Previews. Compared to PR #130 phone capture at 5d7647f. Browser preview refused local file URLs; source and saved previews provide the reference. No prototype files were changed.

## Why the first pass did not match

The palette and font changed but the previous information hierarchy and control geometry survived. Heavy outlined toolbar buttons, uppercase tracked section labels, hard offset shadows and left-aligned oversized category tiles still defined the appearance. The Home screen lacked the greeting, storage identity icon and separated usage hierarchy. File lists retained outlined thumbnail frames and row rules. Navigation used a bright filled pill instead of the quiet orange selection wash.

## Corrections and acceptance

| Surface | Correction | Acceptance |
| --- | --- | --- |
| Shared typography | Hanken headings, sentence case, compact sans metadata | Implemented; focused host/device evidence below |
| Icons | Pinned MIT Tabler outline vectors, lazy cached native ImageVectors, no network at runtime | Implemented; focused host/device evidence below |
| Controls | Filled quiet toolbar surfaces, 48dp hit targets, short press scale | Implemented; focused host/device evidence below |
| Home | Greeting, structured storage card, centered compact coloured categories | Implemented; focused host/device evidence below |
| Files | Pill sources, quiet type tiles, separated title/count/sort hierarchy | Implemented; focused host/device evidence below |
| Navigation | Four real destinations, animated orange wash | Implemented; focused host/device evidence below |
| Menus | Quiet surfaces and readable command cards | Implemented; focused host/device evidence below |

Native data remains authoritative. Prototype sample counts, fake files, cloud sessions and completion animations are not shipped. Mounted SD/USB volumes stay visible. Permission, operation, provider, thumbnail and history behavior remains unchanged. Press and selection motion uses 120/180ms tokens; shared enter/reveal timing is 220ms. These state animations snap when reduced motion is enabled. No artificial task-completion or transfer animation is added.

Full visual acceptance must cover light/dark, larger text, navigation, options and actual files on the connected phone. No overall revamp, plugin or stable-release completion credit is asserted by this record.

## Implementation details

- Home now has the prototype greeting and usage hierarchy, compact centered categories and Search / Tasks / Commands order. Internal storage is featured; additional mounted storage follows the category grid. Unlike the mobile prototype, real SD/USB volumes are not hidden.
- Files places source chips and breadcrumbs above the full-width folder name. List/grid controls share the existing saved preference; count and sort remain right-aligned beneath the title. Stable IDs, provider breadcrumb callbacks, drag targets, long press and accessible actions are retained. Dual-pane breadcrumbs remain local to each pane.
- Shared controls retain 48dp touch targets, visible focus, switch/radio/tab semantics and original action descriptions. The folder arrow is a vector decoration; its test now checks the affordance and its disappearance during selection rather than a literal arrow character.
- The bundled Tabler catalogue has 73 semantic mappings to 71 unique source SVGs, plus legacy aliases. Native vectors are initialized lazily and reused. The pinned commit and individual hashes are in [the manifest](tabler-icons-manifest.json); the MIT notice is bundled and listed in About. No runtime download, web icon rendering or dependency upgrade is involved.
- Menus retain bounded intrinsic sizing and scroll for long content. Their quiet surfaces and centered headings replace hard offset shadows. Settings group surfaces and shared cards adopt the same typography and geometry.
- The phone's existing dark/AMOLED preference is preserved, explaining its black canvas compared with the reference's dark-grey canvas. File-type colours remain separate from the orange action accent.

## Retained validation history

1. Initial offline formatter run lacked cached reporter artifacts; an online retry obtained them. That retry found overlong generated SVG strings and the About notice; both were formatted without changing the vector paths.
2. The first 39-test run reported three failures: duplicated PDF/video colours and two missing Settings accessibility labels. The palette and label semantics were fixed, not the assertions.
3. Static analysis found an oversized vector object, a 15-parameter listing function and unused category inversion. Vectors are split into three internal source-data objects; listing presentation has a small value object; category inversion remains functional.
4. A later build reached APK assembly but its session ended before test completion. It is not counted as a test pass.
5. The next focused run passed 47/48. The only failure was the old literal text-arrow assertion, now updated to verify the Tabler affordance while retaining selection checks. The first attempted assertion edit did not match the UTF-8 arrow and the same failure recurred; the subsequent explicit UTF-8 edit was verified in source. Final evidence is recorded below.

## Physical comparison

The authorized USB test used an in-place debug update after verifying the installed/candidate signer matched. No uninstall, data clear, root probe or permission change was performed. Home, Files list/grid, the View submenu, SD/internal switching, the idle Tasks popup and Home command grid were exercised. List and internal storage were restored. No real-file mutation or transfer was initiated.

Local, ignored evidence lives in `build/parity-phone/`. `adb exec-out screencap` on this phone omitted unchanged portions of several frames. Saving with device-side `screencap -p` and pulling the result produced complete frames, including all four navigation tabs and status icons. Do not diagnose a missing-tab bug from the partial captures. Device screenshots include real storage labels and filenames and are not published with the PR.

## Remaining acceptance and follow-up

This is a correction to the shared shell and Home/Files surfaces, not whole-prototype completion. Storage's capacity presentation, Analysis-specific charts and the eight viewer-specific layouts still need dedicated comparison/implementation. The bounded content-sized popup is intentionally retained from the earlier owner requirement; the prototype's fixed-width dimmed overlay has not been copied blindly. Light theme and narrow/large-text behavior have focused host coverage. Home and Settings were also checked physically in light mode and the original Dark preference restored; physical large-font and tablet/dual-pane visual review remain open. Active-transfer progress is not covered by an idle-popup smoke test. Full CI must pass on the new PR head before integration. Main/stable publication and plugin acceptance are separate gates.

## Final scoped validation

- `gradlew.bat --offline --no-daemon :app:ktlintCheck :app:detekt :app:testDebugUnitTest --tests 'com.devbehindyou.atomicfilemanager.core.designsystem.*' --tests '*HomeScreenComposeTest' --tests '*SettingsScreenComposeTest' --tests '*BrowseFolderTitleComposeTest' --tests '*BrowseOptionsComposeTest' --tests '*BrowseStorageShortcutsComposeTest' --tests '*FileThumbnailComposeTest' :app:assembleDebug`: **PASS**, 48 tests, 0 failures/errors/skips. Local log `build/revamp-parity-validation-7.log`. Includes all bundled vector getters parsing/caching, colour contrast/distinction, menus, narrow/large-text titles, source shortcuts, thumbnails and selection behavior.
- `python scripts/coordination/validate.py`: PASS, 50 task records, ordering/generated roadmap and 43 local links. `git diff --check`: PASS.
- Final debug APK SHA-256: `74094D1417509363A7E1C333861DF7058BD7E6EB6C163B24BE1D3C2AF36313D9`. In-place install succeeded; cold launch returned `Status: ok`. `home-validated.png` captures the final Private-icon contrast correction. Earlier interaction captures used candidate `007C7505B5C28EE82D4065E23E1DAEB78887F3B36CD4320654D9C86141236F1F`; subsequent UI differences were that icon tint and the decorative folder-arrow test tag.
- Physical checks: default dark/AMOLED Home, Files list to grid to list, View submenu, SD to internal source navigation, idle Tasks, command grid, light Home/Settings then restored Dark. Final artifact reinstalled after these scoped corrections. Screenshots and XML stay ignored/local because they contain real filenames.
- Hosted checks must be read from the current PR head. The earlier green run on `5d7647f` is not evidence for this correction. This record claims neither full application regression nor stable-release acceptance.
