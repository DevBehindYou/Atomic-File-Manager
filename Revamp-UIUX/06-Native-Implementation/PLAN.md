# Atomic native UI/UX implementation plan

Owner direction recorded **15 November 2026**: reproduce the approved `04-Design-Prototypes/index.html` design across the entire native application. This supersedes the previous parity note's exceptions for content-width popups and retained old Settings/Storage structure. **A palette change is not a page implementation.** This plan is approved for execution by the owner's request; no additional phase approval is needed.

## 1. Source of truth and scope

The HTML entry point plus `tokens.css`, `styles.css`, `screens.js`, `core.js`, `app.js`, `viewers.css` and `viewers.js` jointly specify the design. [REFERENCE_MANIFEST.json](REFERENCE_MANIFEST.json) pins the audited contents. Saved `05-Visual-Previews` illustrate the reference; the source also defines pages without saved screenshots. Preserve original artwork and prototypes. Never replace the prototype to make a different native result appear compliant.

Implement native Kotlin/Compose using the existing manual container, API28 and navigation model. Use actual files, storage capacities, operations and plugin capabilities. Synthetic file names, 70% sample usage, simulated authentication and demo completion buttons are reference fixtures, not production behavior. Preserve permissions, provider identity, safe publication/cancellation, user data and signing materials. Native functionality not pictured by the prototype remains reachable in appropriate secondary pages; do not discard it to simplify a screenshot.

The 32 surfaces below include 31 product surfaces plus one test-only component catalogue. They are a new UI verification inventory, **not** additions to the original 13-feature or 42-group/eight-plugin denominators. Initially **0/32 visually accepted**: existing host/device evidence proves scoped behavior, not whole-page parity. Never equate task/PR count with completion. A surface closes only after all its acceptance rows pass.

## 2. Exact component contract

| Component | Reference contract | Native implementation and checks |
| --- | --- | --- |
| Palette | Final owner overrides in tokens.css: orange #FE6902, dark #010101/#1B1B1B/#262626, light #F6F6F6/white/#E5E5E5; derived text/lines | Semantic roles, no older purple or arbitrary per-screen colour substitution; compare both themes. Preserve explicit wallpaper/AMOLED preferences; reference tests use explicit reference settings. |
| Typography | Hanken throughout; JetBrains for source text; page 26sp mobile, section18sp, body16sp, metadata13–14sp; weights/line spacing from CSS | Central typography roles; no Bebas/uppercase tracking leaking into production revamp; large-text reflow, full accessible names. |
| Header/navigation | Root title; Search/Tasks/Commands (Files uses Options); four labelled destinations; orange wash selection; responsive rail | Shared root/pushed header contracts; no omitted toolbar actions; Android back and restored routes still work. |
| Surfaces | Soft 16px cards, 24px dialogs, restrained line borders; 20px mobile page/card padding; 24/26px section rhythm | Shared card/list group containers; remove old hard shadows and framed toggle cards from root pages. |
| Buttons/icons | Tabler outlines; 48px targets; 10px control radius; orange primary with black text; 120ms press scale | Pinned MIT native vectors; real callbacks; focus, disabled, pressed and accessible name states. |
| Rows | Leading accent icon, label + secondary text, trailing chevron; 14px gap; separators only inside grouped settings lists | Reusable navigation row and row group; whole row one accessible click; wrap secondary copy. |
| File list/grid | Quiet rows; type-coloured thumbnails; two columns mobile; 104px grid preview; selection/long press preserved | Same stable IDs/backends/thumbnail loaders; no per-file option box; selection bar and menu parity. |
| Storage | Real-data circular usage ring; prominent total; used/free legend; reference volume card hierarchy | Pure snapshot rendering; no implicit recursive scan; meaningful no-volume/unknown-capacity states. |
| Top popup | Centred modal overlay; dim backdrop; width min(420px, viewport-32px), 112px mobile top placement; content-driven height bounded by viewport | Shared top-dialog shell; 20px mobile padding; balanced 48px heading slots; outside/Back/close dismissal; focus capture/restore. Root and submenus use the same geometry. |
| Option row | Icon + label left, value/chevron right; Show hidden displays On/Off as in reference | Whole row toggles/opens; not the old trailing large switch; persisted state reflected on reopen. |
| Bottom sheet/commands | Mobile bottom sheet24px top radius; desktop centred; two-column action grid mobile; icon+label with destructive semantic colour | One shared shell/grid; no empty full-height panel; bounded scroll; safe actions retain confirmation. |
| Inputs/choices | Labelled filled inputs; consistent field error; range/checkbox/segmented styles; selected checkmark | Keyboard/insets/focus handled; invalid values cannot publish; no placeholders replacing labels. |
| Notices/empty/loading | Reference notices, empty illustration/icon/title/CTA, loading dots/progress and recoverable error | Real state models; no permanent placeholder values or unimplemented actionable controls. |
| Motion | 120ms press,180ms state,220ms enter; no overshoot; reduced motion disables transitions | Native state animations; keep task progress truthful; inspect navigation/popup reversal and rapid taps. |
| Viewer chrome | Shared title/source/back/options and action affordances; viewer-specific stages/transport | Use existing engines and owner reservations; do not implement mock playback/content. |

## 3. Complete page inventory

| ID | Page | Native entry/area to inspect | Required layout and behavior | Current state |
| --- | --- | --- | --- | --- |
| P01 | Home | HomeScreen / MainActivity | Greeting; primary storage; six category tiles; Recently opened; Pinned locations; secondary volumes in responsive layout | Partial |
| P02 | Files | BrowseScreen / BrowseHeaders / BrowsePanes / FileListItem | Source strip; location context; title; view/select/count row; list/grid; selection bar; dual pane | Partial |
| P03 | Storage | StorageScreen / StorageUsageDashboard | All connected storage ring + totals/legend; Your storage; analysis CTA card; four tools rows | Pending |
| P04 | Analysis | StorageIntelligenceScreen / StorageUsageDashboard | Readable-file card; explicit scan/cancel; breakdown; cleanup card; inclusion disclosure | Pending |
| P05 | Category | CategoryScreen | Scope caption; list/grid items; loading/empty/error | Pending |
| P06 | Recently opened | Recent files collection / MainActivity | History list; clear-history action; empty state; no file deletion | Pending |
| P07 | Pinned locations | Favourites collection / MainActivity | Pinned location cards; manage/unpin; missing provider state | Pending |
| P08 | Search | SearchScreen | Search input; type chips; scope/index status; results; empty/error | Pending |
| P09 | Tasks | OperationsScreen | Real task cards; status/progress; safe cancel/retry/details; empty state | Pending |
| P10 | Destination | Folder picker / transfer flow | Source operation notice; independent destination/source strip; free capacity; confirm | Pending |
| P11 | Trash | TrashScreen | Origin-aware cards; restore/purge; conflict and confirmation; empty | Pending |
| P12 | Settings | SettingsScreen | Make it yours / Access & privacy / About; icon rows only; controls in secondary surfaces | Pending |
| P13 | Private files | HiddenFilesScreen / Private route | Locked zero-disclosure state; real authentication; unlocked content; lock action | Pending |
| P14 | Hidden files | HiddenFilesScreen / Browse hidden mode | Visibility notice; hidden toggle; real hidden entries; privacy separation | Pending |
| P15 | Folder compare | FolderCompareScreen / sync pairs | Two locations; comparison CTA; differences; direction review; saved pairs | Pending |
| P16 | Apps | AppManagerScreen | App icon/name/package; applicable app actions; empty/error | Pending |
| P17 | Wi-Fi share | WifiShareScreen | Start/stop state; real URL/session; access instructions; transfer status | Pending |
| P18 | Connections | Storage linked folders / network server controls | Separate routed page for linked locations and existing server CRUD; capability/error states | Pending |
| P19 | Cloud Connect | Cloud records / future inventory surface | Honest bundled/planned capability presentation; no fake sign-in/download controls | Pending |
| P20 | Storage access | Existing permission / SAF entry points | Status rows; real Android grant/link flows; revoked/disconnected states | Pending |
| P21 | File details | Details sheet/screen | Type/name; metadata definition list; real supported actions; unavailable values | Pending |
| P22 | About | Settings About / licenses | Version; licenses; help; runtime version and bundled notices | Pending |
| P23 | Plugins | InAppPluginsSheet / plugin inventory | Eight plugin cards; v0.2 About information; status/formats/limits; no downloader | Pending |
| P24 | Image viewer | ui/components/ImageGallery.kt + AnimatedGifImage.kt | Universal viewer chrome; image stage; fit/zoom; navigation; GIF Stop/Replay; details | Pending |
| P25 | Video player | ui/components/preview/VideoPreviewContent.kt + PreviewPlayer.kt | Media stage; play/seek/time; speed; metadata; lifecycle/error | Pending |
| P26 | Audio player | ui/components/preview/AudioPreviewContent.kt + PreviewPlayer.kt | Artwork/track stage; seek; transport; ±10s; metadata; focus states | Pending |
| P27 | PDF reader | ui/components/preview/PdfPreviewContent.kt + PdfPageController.kt | Page stage; jump/zoom; page count; restored position; errors; unsupported actions honest | Pending |
| P28 | Document reader | ui/components/preview/DocxPreviewContent.kt | Reading surface; Find/matches; text size; structured content; format limits | Pending |
| P29 | Text/code editor | ui/components/TextEditorDialog.kt + TextEditorSession.kt | Editor toolbar; text/source stage; Find; wrap; undo/redo; guarded Save and dirty exit | Pending |
| P30 | Markdown viewer | ui/components/preview/MarkdownPreviewContent.kt + MarkdownBlocks.kt | Rendered/Raw segments; reading surface; bounded native subset; link safety | Pending |
| P31 | Archive manager | ui/components/FilePreviewDialog.kt::ArchivePreviewContent | Summary; entry list/path; supported creation/extraction; encrypted/unsupported errors | Pending |
| P32 | Component catalogue | Debug/test-only Compose catalogue | All production component variants, tokens and states for visual comparison; not a fake production destination | Pending |

## 4. Secondary surfaces and state coverage

Every page must inventory its reference `data-action` handlers before implementation. The following families must be wired and checked, not just their closed-state screenshot:

1. File Options and View/Sort/Filter submenus, Show hidden On/Off, New folder, Manage hidden, expanded-width Dual pane. Persist selections and preserve clipboard/active pane.
2. Tasks popup, full task history/details, cancel/retry and operation errors. Never expose simulated Complete sample in production.
3. Commands and selection action grids: select all/invert/same type, copy/move, details, share/Open with, rename/batch rename, create archive, hide/private and destructive confirmation.
4. Destination picker, collision review (skip/replace/keep both where supported), cancellation, revoked destination and rollback/reporting states.
5. Trash confirmation/restore conflict/permanent purge, explicit review and durable error feedback.
6. Appearance choices and existing wallpaper/system options; opening preferences and app overrides; file-browsing preferences; Trash retention; hiding method/folder; private authentication. Move existing advanced controls into their relevant secondary surfaces rather than remove them.
7. Transfer bubble configuration, maximum seven, real enablement and limits. If a proposed preference has no persisted native field, implement and validate that field before presenting it as saved.
8. Connection add/edit/delete, linked-folder permission picker, existing secure protocol/credential handling; setup errors and disconnected states.
9. Storage access and optional Shizuku setup/read-only tools; never grant permissions or run root probes as part of design validation.
10. About/license/help, plugin About v0.2/format limits; Cloud Connect availability must match its separate approved implementation.
11. Search index status/rebuild/retry; scan idle/running/cancelled/partial/error/retained result; cleanup review separate from deletion.
12. Viewer Details, Open with/Share, page or position entry, Find/navigation, unsaved exit/conflict, unsupported/encrypted/oversized/revoked inputs. Preserve native engine limitations and safe errors.

Apply Default/Loading/Empty/Error/permission-denied/disconnected/long-name/large-text/selection states wherever applicable. Surface recovery must use the actual native action, not a generic success toast. Demo-only studio controls, stress generators and synthetic tutorial actions stay out of production.

## 5. Dependency-ordered execution

| Slice | Scope and output | Dependencies / ownership | Acceptance |
| --- | --- | --- | --- |
| R01 | Freeze reference, inventory pages/actions/components, record gaps and plan | UI coordinator; this document | Source hashes, complete page map, explicit remaining work |
| R02 | Shared exact top-dialog, row group, cards, header/nav and typography contracts | Existing PR130; area-ui-shell | Light/dark, narrow/large-font, focus/back/outside, callback/state tests; phone captures |
| R03 | File Options and all submenus; Commands/Tasks and selection shell alignment | R02 | Reference geometry, On/Off row, saved changes, menu transitions and no lost selection |
| R04 | Settings root with three exact groups; move controls into working secondary surfaces | R02; route additions before links | All ten reference rows work; old settings still reachable and preserved; authentication gates intact |
| R05 | Storage root with capacity ring, volume cards, analysis CTA and tools group; move connections/access to secondary pages | R02/R04 route contracts; area-index-analysis as well as UI | Snapshot totals correct; no background scan; every tool row reaches real flow |
| R06 | Analysis, Search, collections, Home lower sections, Files selection/dual layouts | R02/R05; index/source ownership | Real data, state matrix, responsive parity and no permission/provider regressions |
| R07 | Tasks, destination, Trash, compare, apps, sharing, private/hidden, details/About/plugins/Cloud surfaces | R02/R04; respective backend owners reviewed | Full interaction/error matrix; no simulated functionality; no inaccessible old features |
| R08 | Shared viewer chrome, then all eight viewer-specific layouts | R02; acquire per-plugin leases; coordinate Claude reserved implementations | Existing actual-engine fixtures plus visual evidence per viewer; version/docs intact |
| R09 | Whole-app device/host visual audit, accessibility, transitions and release review | R03–R08 | All32 surfaces accepted or explicit owner-approved scope change; current-head CI/review/trial; no stable claim before release gates |

Keep focused dependent PRs rather than one uncontrolled rewrite. No shared-file parallel edits. Independent audits and isolated area branches may proceed in parallel; one local Gradle run at a time on this host. Acquire UI plus feature-area leases for cross-area files. Coordinate Claude through repository docs and PRs; do not duplicate active engine work. Update this matrix and the canonical progress records after each accepted slice.

## 6. Visual acceptance protocol (required per surface)

- Record exact reference hashes, app commit/tree, APK hash, theme, viewport/density/font scale and state. Compare the same content/state; variable real filenames/capacities cannot justify a different hierarchy or component.
- Compare layout top-to-bottom: visible elements/order, page and card margins, widths/heights, radius, font size/weight/line spacing, icon shape/size/colour, separators, alignment, backdrop, scroll reachability and animations. No self-approved “close enough” geometry substitutions.
- Minimum sizes: compact phone ~390dp, narrow320dp, expanded/tablet >=760dp. Light and dark; normal and increased text. Verify landscape/insets and keyboard for inputs. Preserve device settings after testing.
- Host Compose assertions verify bounds, semantics, grouping, click targets and state; saved rendered captures support visual comparison. Phone touch tests verify the actual installed artifact and real routes. Use device-side `screencap` then pull on this phone; direct streamed captures have omitted unchanged regions.
- Each action must have a destination/state transition and Back/close path. Verify persistent preferences, rotation/restoration and cancellation when the changed surface owns them. Never create/delete real user files for a visual smoke test.
- Current normal CI (ktlint/Detekt/Lint, unit tests/APK packaging), appropriate native/provider/engine checks and resolved review concerns are mandatory. Clean merge trial and tested combined tree precede merging. No force-push or check bypass.
- Record failures and their fixes. Source-present, host-tested, physically tested, visually accepted, merged-to-integration and released are different states. Screenshots with real filenames remain local unless explicitly sanitized/approved for publication.

## 7. Definition of done and reporting

A page is **implemented** when all its visible and secondary components use the reference design and connect to real native behavior. It is **host verified** only after its applicable tests pass. It is **visually accepted** only after the required comparison matrix has no unexplained mismatch and associated review is resolved. Report `accepted surfaces / 32` for this new inventory, and itemize partial implementation rather than awarding fractional visual credit. The component catalogue is test-only and does not pretend to be an end-user feature.

Maintain a per-slice log containing changed files, routes/actions preserved, design differences closed, exact evidence and remaining blockers. Preserve the existing plugin task/features/bugs/changelog records when viewer work changes them. Original U13 credit remains 2/13 host and1/13 accepted; N42 remains0/42 complete groups and0/8 fully modernized until their separate gates close. Never use this UI plan to claim v0.2.1 stable completion.

## 8. Current checkpoint

PR130 b5319d1 contains useful shared/Home/Files groundwork and 48 passing focused host tests, but the owner rejected treating the remaining layout gaps as exceptions. Its screenshots and evidence remain historical. R01 is documented here; execute R02/R03 next, then exact Settings and Storage restructuring before expanding to other pages. No surface is promoted to full visual acceptance by this planning document.

## 9. Source-audit findings and mandatory integration precautions

Source cross-check completed on 15 November against PR130 base `b5319d1595fb37f200f4823a9190591967ec25ab` and the pinned reference manifest. The following are functional dependencies, not optional visual exceptions:

- Settings currently exposes Appearance/Files/Trash/Hiding/Storage controls inline. Its replacement root must be three grouped row surfaces. Existing Trash retention, hiding method/folder, authentication and cache controls move into secondary surfaces and remain usable.
- Storage currently embeds linked folders, network CRUD, sync pairs and Labs. Its replacement root must contain the ring, storage cards, Analysis call-to-action and four navigation rows. Separate Connections/Compare/Access/Labs surfaces and restorable routes must be wired before removing the inline controls.
- Plugin inventory currently uses a bottom command sheet. The reference is a library of eight information cards with separate About v0.2 actions. No sample-open button may pretend to open a real file; use native file selection or an actual bundled fixture.
- Shared viewer entry is `ui/components/FileOpeningPane.kt::FilePreviewPane` to `FilePreviewDialog.kt::BuiltInFilePreviewPane`. Add shared title/source/About and universal Open with/Details/Share chrome once. Preserve `PreviewSourceSelection`, `resolvePreviewExternalAction` and `actsOnShown`: gallery navigation must never cause an original-source callback to operate on a different displayed file.
- Image: match the 4:3 stage, count/filmstrip and zoom controls using the real pager and bounded transforms. GIF controls operate the decoder; inactive pages stop. Preserve accepted image/GIF fixes and coordinate #112.
- Video/audio: replace presentation/transport around the existing Media3 controller, not player lifetime or revision ownership. Match 16:9 video, 260dp audio stage/140dp artwork, labelled seek and 68dp circular transport. Preserve focus, noisy/lifecycle and restored-position behavior; coordinate #119.
- PDF: frame the actual raster and bind page labels to the displayed bitmap. Smaller/larger means raster zoom, not unsupported text reflow. Coordinate #117 and keep jump/retry/source-change/resource tests.
- DOCX: reading card and Find/text-size controls wrap actual structured text, without inventing original pagination or embedded elements. Coordinate #111.
- Editor: reference dirty-state/encoding/editor/toolbar structure must preserve bytes, BOM/line endings, bounded history and staged save. Do not simulate Keep draft or Save copy while #121/#126 publication gates remain unresolved.
- Markdown: native Reading/Raw presentation and styled blocks retain parser bounds and external-asset policy. Do not introduce a browser or implicit network fetches.
- Archive: existing flat entries contain no-op row clicks and limited extraction routing. Reference hierarchy and destination review require real entry navigation and queue integration, not decorative rows. Preserve traversal/collision/staging/cancellation protections, coordinate #110, and require actual archive fixtures.

Existing engine validation suites are reused: ImageViewModes/FocusedGesture/GifPlayback; Video/AudioPreparationRecovery and focus/lifecycle/WAV; PdfSourceSession/Reader; DocxPreviewContent/SourceSession; MarkdownPreparationRecovery/Ownership; TextEditorSession/UndoRedo/History; ArchiveInspectionSafety/Integrity/Device. Layout tests supplement them. No visual change grants functional-modernization credit automatically.

## 10. Execution log

- **R01 complete:** reference manifest pinned; 32-surface inventory, component contract, dialog/state families, dependencies, ownership and acceptance protocol documented; native viewer mapping and functional gaps cross-checked.
- **R02/R03 in progress:** shared native dialog and File Options use the reference width, centered header and content-driven height. Ten focused host tests passed, including pointer dismissal and all six sort directions. Detekt, formatting and debug APK packaging passed. The phone confirmed header Back returns to File Options and Android Back dismisses it. APK SHA256: `ACA5DE15902A6C2D6DA65F5874EC604453E4BBE29798E64466361BE210E30AE9`. Wide-layout Dual pane, Commands/Tasks parity and the full visual matrix remain open.
- **R03 updated:** [Tasks #132](https://github.com/DevBehindYou/Atomic-File-Manager/pull/132) merged as `5902f8c` and [Commands #133](https://github.com/DevBehindYou/Atomic-File-Manager/pull/133) merged as `32baa1f` into feature integration. The combined Commands run initially failed before executing an API 28 test because the downloaded Robolectric POM failed SHA-512 verification. The retry passed with checksum verification enabled. [Dual pane #134](https://github.com/DevBehindYou/Atomic-File-Manager/pull/134) passed 20 host tests at the wide-layout boundary and in pane lifecycle cases. [Commands destinations #135](https://github.com/DevBehindYou/Atomic-File-Manager/pull/135) passed 16 host tests and four phone route checks, including New folder cancellation and no replay.
- **R04 host verified:** Settings uses the three reference groups and ten rows. Secondary controls retain the saved preferences and guarded private-files route. Full Plugin/About/Cloud surfaces remain in R07.
- **R05 host verified:** Storage uses real combined capacity, volume cards, an explicit Analysis action and four restorable tool routes. New comparisons use the existing two-folder selection in Files. The combined APK passed 20 focused tests, Detekt, formatting and packaging after five line-length corrections. APK SHA256: `721B15A4AC7A0327960C3FE8C20832E7EE62A30D949A7C6F9ACD83CB2E2F69A0`. The full responsive visual matrix remains open.
- **Device surface checks:** [PR #130](https://github.com/DevBehindYou/Atomic-File-Manager/pull/130) confirmed the File Options submenu and both Back actions. [PR #131](https://github.com/DevBehindYou/Atomic-File-Manager/pull/131) was exercised on the same 1080 × 2460 USB phone at 440 dpi, dark theme, normal text size. The combined APK cold-launched after a signer-matched in-place install. Touch checks opened Settings Appearance and returned to its groups, opened Storage, and reached Analysis, Connections and Storage access. The first Settings script matched both the label and its row description. The rerun selected the exact description and completed. Additional tool paths, light theme, increased text and expanded layouts remain in the matrix.
- **Feature integration:** [#130](https://github.com/DevBehindYou/Atomic-File-Manager/pull/130) merged as `0ab3c363`; [#131](https://github.com/DevBehindYou/Atomic-File-Manager/pull/131) merged as `db8e292d`. The current PR heads passed standard CI and both dry-run merges reproduced the tested trees. The visual acceptance and release checkpoints remain open.
- **R06 Analysis host verified:** separate Analysis cards preserve both opt-in scan actions, expose cancellation and retain earlier results. The corrected build passed all 20 focused tests for opt-in, cancellation, lifecycle, result restoration and cleanup selection. APK SHA256: `7391FCFA2A590068DBCB5B09787AA6AB01B32E434A4FB35815B611C65222E24B`.
- **Visual acceptance: 0/32.** This intentionally stays zero until complete per-surface gates pass; the plan itself is complete, implementation is not.
---
Current R06 evidence
- Home shortcuts (#137): 14 focused tests passed. The phone verified Recently opened, Pinned locations and Explore storage. View all and Manage are separate route work.
- Category: 4 focused tests passed. The phone verified Documents, collection search and Back. Pushed-screen global toolbar parity and the full visual matrix remain open.
