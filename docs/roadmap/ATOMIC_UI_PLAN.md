# Atomic UI/UX Reconstruction Plan

*Written 4 October 2026. Companion to [`ALL_IN_ONE_PLAN.md`](ALL_IN_ONE_PLAN.md). Source of truth for visuals: [`../design/ATOMIC_DESIGN_SYSTEM.md`](../design/ATOMIC_DESIGN_SYSTEM.md).*

This plan rebuilds Atomic File Manager's interface so it belongs to the DevBehindYou **Atomic** family ("Technical Editorial": ink on paper, one Signal accent, poster headlines, instrument labels, hard offset shadows). It is a reconstruction of the product experience, not a re-theme: information architecture, navigation, components, states, motion and copy all change.

**Status: plan only. Nothing in this document is implemented yet.**

---

## 0. Ground rules

1. **Precedence.** `ATOMIC_DESIGN_SYSTEM.md` wins over existing Atomic File Manager design docs when they conflict, unless this plan records a technical reason not to follow it (section 3). Conflicts that need a product call are listed for the owner in section 18.
2. **Platform translation.** The design system's code samples are Flutter and CSS. Atomic File Manager is **Kotlin + Jetpack Compose**. Every token and component in this plan is the Compose translation of the spec, not a copy of the Flutter code.
3. **Functionality is preserved.** View models, use cases, repositories, backends and the operations engine are not changed by this work except where a screen needs new state (for example, a "partial results" flag). UI work is split from business-logic work in separate pull requests.
4. **Coordination with the feature plan.** Atomic foundations land *before* the new Phase 1 screens in `ALL_IN_ONE_PLAN.md` (Trash, Search, Operations) so those screens are built once, in Atomic, not built in Material and then rebuilt (section 15).
5. **Superseded docs.** When the matching step ships, these documents are replaced, not kept in parallel: `docs/DESIGN_SYSTEM.md`, `docs/COMPONENT_LIBRARY.md` (glass primitives), `docs/UI_UX_GUIDELINES.md` §4 (glass rules), `docs/ANIMATION_SYSTEM.md` §1 (spring catalogue), `docs/LIQUID_GLASS_RESEARCH.md` (archive), and FR-10.3 (glass tier) in `FUNCTIONAL_REQUIREMENTS.md`.

---

## 1. What the design system says (extracted)

| Area | Atomic rule | Source § |
|---|---|---|
| Principles | Ink on paper; one Signal; hard edges, honest depth; labels read like instruments; headlines are posters; show the real state; calm by default, loud on purpose | 1 |
| Personality | Technical, trustworthy, editorial, slightly playful: an engineering notebook, not a SaaS dashboard | 2.1 |
| Core colours | ink `#15171B`, paper `#F4F5F1`, white `#FFFFFF`, surface `#EDEEE8`, signal `#3A2FF0` (> 95 % of every screen) | 3.1 |
| Accent budget | Signal under ~5 % of any screen; on ink use `signal-light #8F88FF`, never Signal text | 3.1, 3.6 |
| Semantic | error `#BA1A1A` family; energy-high `#EB7D00` = "nearly full"; energy-low `#601D49`; live `#3DDC84` only for "server is live"; no success green (confirm with ink text + Signal check) | 3.4 |
| Type | Display = Bebas Neue, always uppercase, line-height 0.95–1.05; Body = Hanken Grotesk, sentence case; Mono = JetBrains Mono, uppercase tracked labels, numbers, timestamps | 4 |
| App type scale | top title 40 sp, pushed title 30 sp, hero number 44–48, card title 22–24, row title 20–22, button 18–20, body 15–16, section label 12–13, counter 11–12 (raise to 12 minimum) | 4.3, 4.4.7 |
| Spacing | Bold steps 4 · 8 · 12 · 16 · 22 · 24 · 32 · 44 · 56 · 74; app margin 16 dp; card padding ≈ 16 dp | 5.1, 5.3 |
| Shape | Radius 4 dp default; 6 web cards; pill 999; 28 dp sheet tops; nothing in the 12–16 "soft" range | 6.1 |
| Borders | 1 dp line (hair), 1 dp ink (rule), 1.5 dp ink (structure), 2 dp ink (controls), 2 dp signal (selected), 2 dp error (danger) | 6.2 |
| Elevation | No blur. Solid offset shadows: 2/3/5/6/8 dp; ink by default, Signal for the featured one; press = move +2,+2 and drop shadow | 6.3 |
| Icons | Material Symbols **Outlined** only in the app; one style per surface; no emoji; "→" text arrows; 40 dp icon tiles with 1.5 ink border | 7 |
| Motion | press 120 ms, hover 150, toggle 200, enter 300–350, reveal 500; `ease` or linear; **no springs, bounce or overshoot**; respect reduced motion | 8 |
| Components | Buttons (Primary/Solid/Ghost/Destructive/Text; one Primary per view), chips, pills, cards, settings rows, danger zone, inputs (2 dp ink), toggles, checkboxes, steppers, segmented toggles, header with 1 dp ink divider, bottom bar with one ink pill, bottom sheets, data display (bars, counters, hero numbers, tiles), feedback states | 9 |
| States | Loading = mono "LOADING…" + ink indeterminate bar, no spinners on content; empty = surface module + one sentence + next step; destructive confirm states the effect with numbers; selected = 2 dp Signal border + checkbox + "1 SELECTED" header | 9.9 |
| Patterns | App anatomy: status bar on paper → header + ink divider → title row with counter → filter chips → content under mono section labels → bottom bar or full-width primary action | 10.3 |
| Flows | Numbered steps; explain the cost/effect before the action; "coming soon" info sheet; two-key danger | 10.4 |
| Accessibility | AA contrast table; 2 dp Signal focus ring; 48 dp targets; never colour alone; reduced motion; mono ≥ 12 sp; 200 % text; uppercase applied by style, not typed | 11 |
| Copy | Short declarative sentences; real numbers; caps verb + object buttons; mono caps noun labels; ISO dates; `·` `/` `→` separators | 12 |
| Don't copy | Signal on ink; sub-12 sp labels; untokenised colours; small hit areas; no dark theme | 13 |
| Family rules | Keep ink/paper, fonts, radii, borders, shadows, header/bottom-bar/sheet patterns, motion; a sibling may swap the accent (contrast rules) and mascot | 15 |

---

## 2. Translating Atomic to a file manager

The design system was extracted from a notes app. A file manager has different data and different risks. These are the translations:

| Atomic concept | Atomic File Manager meaning |
|---|---|
| Atomic Energy bar (fill colour by value) | **Storage meter.** Used < 80 % = Signal; ≥ 80 % = `energy-high` orange with the mono label "NEARLY FULL" (never colour alone). Same component, same 350 ms width animation |
| "Show the real state" | Every list and screen shows numbers: `128 ITEMS · 4.2 GB`, `INTERNAL · 41.2 / 128 GB`, `SCANNED 2026-10-04 08:11` |
| Storage tiles (notes on device / in cloud) | `ON PHONE` / `ON SD CARD` / `IN TRASH` tiles on Home and Storage |
| Activity row with mono delta | Operations history: `COPIED 12 FILES · +1.2 GB`, cleanup `FREED · -3.4 GB` |
| Fact sheet | File info: mono labels (`PATH`, `SIZE`, `MODIFIED`, `SHA-256`) with values; hashes in a code well |
| Explain the cost before the action | **Explain the effect before the action**: "Moves 12 files (1.2 GB) to SD card. Same volume, so it's instant." |
| Danger zone + two-key danger | Permanent delete, empty trash, reset vault, clear index: separate surface with `error` border, each with its own EXECUTE |
| Recovery phrase list | Vault recovery/export confirmation |
| Coming soon info sheet | SFTP/SMB until Phase 3 ships: says what's missing, why, and the alternative |
| Live dot `#3DDC84` | Wi-Fi share running (Phase 3.4) and nothing else |
| Unread dot | New items in Downloads since last visit (optional) |
| Selected note card | Selected file row/card: 2 dp Signal border + Signal checkbox |
| Physics vocabulary | Use sparingly. The product is named "Atomic File Manager" (family naming, spec §2.2). Feature names stay plain (Trash, Vault, Transfer Bubbles, Quick Peek) |

**Hard domain rule — user data is never transformed.** File and folder names, paths, extensions and file contents are shown exactly as stored. Bebas Neue has no lowercase glyphs, so **file and folder names are never set in the Display face** and never uppercased: `README.md` must not read `README.MD`. Names use Body (Hanken Grotesk); paths and sizes use Mono in their original case. Display is for app-authored text only: screen titles, section headings, button labels, hero numbers.

---

## 3. Audit of the current UI (4 October 2026)

### 3.1 Map

```
Application (single :app module, Kotlin + Compose, manual DI in AtomicApp/AppContainer)
├── Navigation      MainActivity: 4 tabs (HOME, BROWSE, STORAGE, SETTINGS) + boolean overlays
│                   (private files, category, storage intelligence, more-categories popup); no route stack
├── Screens         Home, Browse (+dual pane ≥720 dp), Category, HiddenFiles, Storage,
│                   StorageIntelligence, Settings
├── Dialogs/sheets  15 AlertDialog call sites: NewFolder, Rename, FileDetails, FilePreview, HideFile,
│                   HiddenFolder, DropDecision, BubbleTransfer, AddNetworkServer, About, …;
│                   1 ModalBottomSheet (BubbleDetails)
├── Interaction     Transfer Bubble rail, drag and drop (FileDragController, DragFloatingPreview,
│                   EdgeAutoScroll), Quick Peek (GestureArbiter)
├── Components      BreadcrumbBar, CategoryGrid, FileListItem, StorageOverviewCard, FileActionDialogs,
│                   FilePreviewDialog, preview/* (audio, video, markdown, pdf, text, archive)
├── Theme           ui/theme/Theme.kt (M3 blue/teal schemes + dynamic colour), AtomicMotion.kt (springs)
├── State           Per-screen view models (BrowseViewModel), StateFlow; settings via SettingsRepository
├── Services        FileOperationService (declared, unused)
├── Data layer      Backends (FileSystem, SAF, MediaStore, FTP, WebDAV, SFTP/SMB stubs), SQLite helpers,
│                   SharedPreferences
└── Assets          Launcher icon from Refract-Icon.png; no bundled fonts; strings.xml has 1 entry
```

### 3.2 Measured inconsistencies

| Finding | Count / evidence | Atomic rule broken |
|---|---|---|
| Corner radii in use | 11 different values (4, 5, 6, 8, 10, 12 ×16, 14, 16, 18, 20, 24 dp) | Radius 4 default; nothing 12–16 |
| Hard-coded colours outside the theme | 17, in `FileListItem`, `CategoryGrid`, `HomeScreen` (category accents) | One Signal; tokenised colours |
| Theme | Blue `#0F6CBD` + teal secondary; dynamic colour on by default | One accent, ink/paper base |
| Elevation | Tonal elevation (3, 6) and `Modifier.shadow` (blurred) | No blur; hard offset shadows |
| Motion | `AtomicMotion.Quick` uses `DampingRatioMediumBouncy` | No bounce/overshoot |
| Icons | 73 `Icons.Default`, 29 `Icons.Filled`, 16 auto-mirrored filled; 0 outlined | Outlined only |
| Loading | 10 `CircularProgressIndicator` | No spinners on content |
| Overlays | 15 `AlertDialog`, 5 `Toast` | Bottom sheets; in-app feedback |
| Typography | Default M3 type scale (no custom fonts); `bodySmall` used 42×; 5 raw `fontSize` | Three-family system |
| Hard-coded `.dp` | 420 literals; `NoHardcodedDp` lint rule exists but is disabled in `app/build.gradle.kts` | One source of truth |
| Copy | UI strings typed inline in Kotlin (strings.xml has 1 entry) | Needed for localisation and render-time uppercasing |
| Arrows | "Open ›", "›" | "→" in mono |
| Breakpoints | Dual pane at `screenWidthDp >= 720`; `RESPONSIVE_DESIGN.md` says 840 | One responsive model |

### 3.3 Decide per area

| Category | Items |
|---|---|
| **Preserve** (behaviour) | All view models, use cases and backends; Transfer Bubbles behaviour and persistence; drag-and-drop controller and auto-scroll; Quick Peek gesture arbitration; Browse back handling and per-folder view-model keying; biometric gate; chunked listing and stable list keys |
| **Refactor** (keep logic, rebuild UI on Atomic components) | `FileListItem`, `BreadcrumbBar`, `CategoryGrid`, `StorageOverviewCard`, `TransferBubbleRail`, `BubbleDetailsSheet`, `QuickPeekOverlay` frame, preview contents, `HideOptionCard`, `NetworkServerCard`, `VolumeDetailCard` |
| **Redesign** (new layout and hierarchy) | Home, Browse header/selection/actions, Storage, Storage Intelligence, Settings, Hidden/Private files, Category screen |
| **Replace** | `Theme.kt` colour schemes and dynamic colour; `AtomicMotion` springs; all `AlertDialog`s → Atomic sheets; `Toast` → Atomic snackbar; spinners → Atomic loading; filled icons → outlined |
| **Duplicated** | Two view-model construction paths in `BrowseScreen` (primary + `secondaryViewModel`); repeated "card with title + subtitle + ›" patterns in Home/Storage/Settings; several near-identical confirmation dialogs |
| **Inconsistent** | Radii, colours, icons, arrows, dialog vs sheet usage, breakpoints (section 3.2) |
| **Technically fragile** | `MainActivity` boolean routing (640 lines); `BrowseScreen.kt` 1,324 lines; Hidden Files reachable only from the Sort menu; Robolectric tests that find nodes by visible text will break when copy changes (switch to `testTag`) |

---

## 4. Token architecture (Compose)

All tokens live in **`com.devbehindyou.atomicfilemanager.core.designsystem`**: the package the existing `NoHardcodedDp` lint rule already allows. One decision, one place.

```
core/designsystem/
├── foundation/
│   ├── AtomicPalette.kt      raw colours from §3 of the spec (internal; screens never use it)
│   ├── AtomicColors.kt       semantic roles (below), light + dark instances
│   ├── AtomicTypography.kt   display / body / mono roles
│   ├── AtomicSpacing.kt      s4 s8 s12 s16 s22 s24 s32 s44 s56 s74
│   ├── AtomicShape.kt        radiusXs 3, radiusSm 4, radiusMd 6, radiusLg 8, pill, sheetTop 28
│   ├── AtomicBorder.kt       hair 1 line, rule 1 ink, structure 1.5 ink, control 2 ink, selected 2 signal, danger 2 error
│   ├── AtomicElevation.kt    hard offsets 2/3/5/6/8 dp + colour (ink | signal)
│   ├── AtomicMotion.kt       press 120, hover 150, toggle 200, enter 350, reveal 500; Ease = CubicBezier(.25,.1,.25,1)
│   ├── AtomicSize.kt         touch 48, iconSm 20, icon 24, iconTile 40, button 52/44, header 56–64, chip 32
│   ├── AtomicBreakpoints.kt  compact < 600, medium 600–839, expanded ≥ 840
│   └── AtomicTheme.kt        CompositionLocals + mapping onto MaterialTheme (below)
├── modifiers/                hardShadow, atomicPressable, focusRing, atomicBorder
├── icons/                    AtomicIcons (outlined set used by the app), atom mark ImageVector
├── atoms/ molecules/ organisms/   (section 5)
└── preview/                  @Preview catalogue for every component and state
```

### 4.1 Semantic colour roles

| Role | Light | Dark (proposal, contrast-test in the token PR) |
|---|---|---|
| `background` | paper `#F4F5F1` | ink `#15171B` |
| `surfaceCard` (things the user owns or acts on) | white | `#1E2026` (spec §13.9) |
| `surfaceInset` (settings groups, tools) | surface `#EDEEE8` | paper at 4–6 % over ink (spec §3.5 inner panels) |
| `surfaceRaised` | raised `#F9FAF4` | paper at 8 % over ink |
| `content` | ink | paper |
| `contentSecondary` | slate `#4A4D55` | paper at 78 % |
| `contentMuted` | slate | paper at 62 % |
| `accent` | signal `#3A2FF0` | signal-light `#8F88FF` for text and icons; Signal fills keep white text |
| `accentPressed` | signal-hover `#2A20C9` | signal-hover |
| `borderStrong` (controls, cards) | ink | paper at a level that reaches ≥ 3:1 on ink (to be measured) |
| `borderHair` | line `#C6C6CB` | paper at 16 % |
| `shadow` | ink | Signal (spec: on dark backgrounds the shadow is Signal) |
| `error`, `onError`, `errorContainer`, `onErrorContainer` | spec §3.4 | to be derived and contrast-tested |
| `meterNormal` / `meterHigh` | signal / energy-high | signal-light / energy-high |
| `live` | `#3DDC84` (dot only) | same |

Screens use **roles only**. Raw palette values are `internal` to `foundation`.

### 4.2 Typography roles

| Role | Face | Size / line-height | Case | Use in Atomic File Manager |
|---|---|---|---|---|
| `displayHero` | Bebas Neue | 44–48 sp / 0.95 | upper | Free-space hero number on Storage |
| `displayTitle` | Bebas Neue | 40 sp / 0.95 | upper | Top-level screen titles: HOME, STORAGE, SETTINGS |
| `displayPushed` | Bebas Neue | 30 sp / 0.95 | upper | Pushed screens: TRASH, STORAGE ANALYSIS, FILE INFO |
| `displayCard` | Bebas Neue | 22–24 sp / 1.0 | upper | Card and settings-row titles authored by the app |
| `displayButton` | Bebas Neue | 18–20 sp | upper | Primary/Solid/Ghost button labels |
| `nameLarge` | Hanken Grotesk 700 | 22 sp / 1.2 | **as stored** | Current folder name in the Browse title row |
| `name` | Hanken Grotesk 500 | 16 sp / 1.35 | **as stored** | File and folder names in rows and cards |
| `body` | Hanken Grotesk 400 | 16 sp / 1.5 | sentence | Descriptions, explanations |
| `bodySecondary` | Hanken Grotesk 400 | 15 sp / 1.5, slate | sentence | Supporting text |
| `monoLabel` | JetBrains Mono 500 | 12–13 sp, tracking ≈ 1.5 sp | upper | Section labels, eyebrows, chips |
| `monoMeta` | JetBrains Mono 400 | 12 sp | as is | Sizes, ISO dates, counters, paths, hashes |

- **Fonts are bundled** in `res/font` (offline-first app; no downloadable-fonts dependency). Use variable fonts for Hanken Grotesk and JetBrains Mono to keep size down; include the SIL OFL licence text in the About screen's licences list.
- **Glyph coverage:** these families cover Latin scripts. Android's font fallback renders other scripts (for example Devanagari or CJK file names) in the system font automatically; verify with test fixtures before localising.
- **Uppercase is applied at render time**, never typed in strings: an `AtomicText` atom takes the sentence-case string resource, displays `text.uppercase(locale)` and sets the accessibility text to the original case, so TalkBack reads "Storage", not letter by letter.

### 4.3 Bridging to Material 3

`AtomicTheme {}` provides the Atomic locals **and** a `MaterialTheme` whose `colorScheme`, `typography` and `shapes` are mapped from the roles (primary = accent, surface = background, outline = borderStrong, outlineVariant = borderHair, shapes all 4 dp, etc.). Result: on day one, every remaining Material widget already uses ink/paper/Signal and the new fonts. That gives one immediate, app-wide change while screens are migrated one by one. The current `ui/theme/Theme.kt` (`AtomicTheme`, renamed from `RefractTheme` on 4 October 2026, still the old blue/teal schemes) is replaced by this one, so only one theme exists.

### 4.5 Wallpaper colours (owner decision 1)

Design mockups for every screen (light, dark, sheets, states, tablet, component kit) are on the Claude design canvas "Atomic File Manager — UI/UX"; use them together with section 7.

- Off by default; available on API 31+. When on, only the `accent` family changes: `accent`, `accentPressed` (≈ 15 % darker), the accent shadow colour (≈ the spec's `signal-deep` role) and the accent-on-dark variant. Ink, paper, surfaces, borders, semantic colours and typography never change.
- Source: the primary colour of `dynamicLightColorScheme` (light) and `dynamicDarkColorScheme` (dark).
- **Guard rails from the spec's §15** (checked whenever the wallpaper changes):
  1. accent on paper ≥ 4.5:1 and white text on the accent ≥ 4.5:1 (light); accent-on-dark variant on ink ≥ 4.5:1 (dark);
  2. the hue must not read as red, orange or plum, which already mean error, nearly full and low;
  3. if a check fails, the app tries darker/lighter tones of the same hue, and otherwise falls back to Signal Blue and says so under the setting ("Your wallpaper colour is too light to read, so Signal Blue is used.").
- A unit test feeds sample wallpaper colours (pale yellow, red, mid blue, dark green) through the guard rails.
- `AppSettings.dynamicColor` currently defaults to `true`; it becomes the wallpaper-colours flag and defaults to `false`.

### 4.4 Modifiers

- `hardShadow(offset, color, shape)`: draws a solid copy of the shape behind the content with `drawBehind`. No blur, no `RenderEffect`, so it is cheap on every API level.
- `atomicPressable(interactionSource)`: on press, translates the content by the shadow offset and collapses the shadow over 120 ms (a key being pressed); with reduced motion, switches colour only. Replaces ripple on Atomic buttons and cards.
- `focusRing()`: 2 dp Signal outline with a 2–3 dp offset when focused by keyboard or D-pad (`signal-light`/white on dark and Signal fills).

---

## 5. Component library (Atomic layers)

Each item names the existing code it replaces. "New" means nothing equivalent exists. Every component ships with `@Preview`s and screenshot tests for default, pressed, focused, disabled, selected, error, dark and 200 % font (section 14).

### 5.1 Atoms

| Component | Spec summary | Replaces |
|---|---|---|
| `AtomicText` | Role-based text with render-time uppercase and correct semantics | Raw `Text` + `MaterialTheme.typography.*` |
| `AtomicButton` | Variants: Primary (Signal, 2 ink border, 3 dp ink shadow), Solid (ink), Ghost (2 ink border), GhostOnDark, LightOnSignal, Destructive (`errorContainer` + error border), Text action (Signal mono caps). Heights 52/44 dp; one Primary per view; disabled 40 % no shadow | 18 `Button`, 8 `OutlinedButton`, 30 `TextButton`, 1 `FilledTonalButton` |
| `AtomicIconButton` | Back (36 dp ink square, white arrow, 48 dp hit area), Signal action (48×40), Toolbar (40 ink + 2 dp shadow), Destructive (error fill) | 32 `IconButton` |
| `AtomicIconTile` | 40 dp, 1.5 ink border, 4 radius, paper fill, outlined icon | Category/volume icon circles |
| `AtomicChip` | 32 dp pill, mono 12 sp; off = paper + line border + slate; on = ink fill + paper text; 48 dp hit area | 4 `FilterChip` |
| `AtomicStatusPill` / `AtomicBadge` | Signal fill + white mono ("ON", "LOCKED", "RUNNING"); count badge | New |
| `AtomicCheckbox` | 20 dp square, 2 dp radius; on = Signal fill + white check | 6 `Checkbox` |
| `AtomicSwitch` | On: Signal track, white thumb. Off: paper track, 2 dp ink outline, ink thumb | 1 `Switch` |
| `AtomicTextField` | White, 2 dp ink border, 4 radius; mono caps label above; error border + error helper text below | 11 `OutlinedTextField` |
| `AtomicDivider` | `hair` (line) or `rule` (ink) | `HorizontalDivider` |
| `AtomicMeter` | 8 dp bar, value-coloured fill (Signal / energy-high), 350 ms width animation, mono caption | `LinearProgressIndicator` for storage |
| `AtomicProgress` | Indeterminate 2 dp ink bar + mono "LOADING…" label; determinate ink bar for operations | 10 `CircularProgressIndicator`, 5 `LinearProgressIndicator` |
| `AtomicCounter` | Mono "128 ITEMS" / "3 / 50", baseline-aligned | New |

### 5.2 Molecules

| Component | Spec summary | Replaces |
|---|---|---|
| `AtomicHeader` | Home variant (wordmark + mono label, Ghost icon buttons) and Pushed variant (ink back square + `displayPushed` title); full-width 1 dp ink divider | 7 `TopAppBar`, 1 `CenterAlignedTopAppBar` |
| `AtomicTitleRow` | Title left, mono counter right on the baseline, 1 dp ink rule below | New |
| `AtomicSectionLabel` | Mono caps label + optional status pill or text action + ink rule | Ad hoc "Storage Locations", "Quick Access" headings |
| `AtomicFileRow` | Icon tile or thumbnail → name (`name`, as stored, 2 lines max) → mono meta `4.2 MB · 2026-10-04 08:11`; selected = 2 dp Signal border + checkbox; hidden files at 60 % content colour with a mono `HIDDEN` tag | `FileListItem` |
| `AtomicFileCard` | Grid variant: thumbnail, name, mono meta; same selection treatment | Grid cells |
| `AtomicBreadcrumb` | Mono segments in original case separated by `/`, last segment ink, others slate; horizontally scrollable | `BreadcrumbBar` |
| `AtomicSearchField` | 2 dp ink field with leading search icon, mono scope chip ("THIS FOLDER" / "ALL STORAGE") | Browse search |
| `AtomicSettingsRow` | 56 dp, `displayCard` title (app-authored), Signal "→", hair divider | Settings rows |
| `AtomicToggleCard` | Surface fill; 2 dp Signal border when on; title + body + switch | Settings toggles |
| `AtomicFactSheet` | Mono label column + value column, hair rows, hashes in a code well; stacks at 200 % font | `FileDetailsDialog` metadata rows |
| `AtomicStatTile` | Display number + mono caption ("ON PHONE", "IN TRASH"); featured tile gets Signal number and border | Storage summary text |
| `AtomicEmptyState` | Surface module: one plain sentence + one next-step button | Ad hoc empty texts |
| `AtomicErrorState` | What happened + how to fix + recovery button + optional "DETAILS" disclosure (mono) | Ad hoc error texts |
| `AtomicWarningBox` | `errorContainer`, 1.5 line border, mono caps title, `onErrorContainer` body | Permission warnings |
| `AtomicActivityRow` | Icon tile → body title + mono timestamp → mono delta right ("+1.2 GB") | New (Operations history) |

### 5.3 Organisms

| Component | Spec summary | Replaces |
|---|---|---|
| `AtomicBottomBar` | Paper, 1 dp ink top border; **one** active destination as an ink pill (icon + mono caps label in paper), others icon-only ink with labels for TalkBack | `NavigationBar` |
| `AtomicNavRail` | Same language vertically for medium/expanded widths | `NavigationRail` |
| `AtomicSelectionBar` | Header switches to "3 SELECTED · ALL · CANCEL"; bottom action strip with ≤ 4 actions + overflow; destructive action uses error styling | Browse selection top bar |
| `AtomicSheet` | Paper, 28 dp top corners, drag handle `#45474B`, 54 % scrim; Signal mono label + ink rule → content → full-width Primary. On medium/expanded widths renders as a centred 560 dp panel | 15 `AlertDialog`, `ModalBottomSheet` |
| `AtomicConfirmSheet` | States the effect with target and numbers; destructive variant uses Destructive button; "TRASH" vs "DELETE FOREVER" wording | Delete/overwrite dialogs |
| `AtomicInfoSheet` | Label → 2-line Display headline → body → Solid "GOT IT" (coming soon, explanations) | About dialog, unsupported protocol notices |
| `AtomicSnackbar` | Ink bar, paper body text, Signal-light text action ("UNDO"), mono count; enter 350 ms; announced to TalkBack | 5 `Toast`, 4 `Snackbar` |
| `AtomicDangerZone` | Section label → warning sentence → surface container with 2 dp error border → rows with mono action name + EXECUTE | New (Settings, Trash, Vault) |
| `AtomicStorageCard` | Volume name (Display, app-authored label like INTERNAL / SD CARD) + mono "41.2 / 128 GB" + `AtomicMeter` + "BROWSE →" | `StorageOverviewCard`, `VolumeDetailCard`, `VolumeItem` |
| `AtomicOperationCard` | Mono "ITEM 12 / 48 · 1.2 / 3.4 GB · 2 MIN LEFT", determinate ink bar, CANCEL ghost; success flips to an ink-text confirmation with a Signal check | New (operation queue) |
| `AtomicBubbleRail` | Transfer Bubbles as ink-bordered square tiles with hard shadows and a Signal count badge; snapping behaviour unchanged | `TransferBubbleRail` visuals |
| `AtomicFabStack` | Primary "+ NEW FOLDER" (ink fill, mono label, 52 dp) with secondary "NEW FILE" above | `FloatingActionButton` |
| `AtomicPeekFrame` | Quick Peek card: 2 dp ink border, 6 dp hard shadow, mono caption bar "IMG_2041.jpg · 3 / 15" (name as stored) | `QuickPeekOverlay` frame |
| `AtomicCommandPanel` | Phase 4.4 command palette in the same sheet language | New (later) |

Storage card headers keep the volume name and capacity details beside each other in a single
row. Both fields share the available width and use one-line ellipsis when constrained; capacity
details align to the trailing edge. Full strings remain available to accessibility services.

---

## 6. Information architecture and navigation

### 6.1 Problems today

- Navigation is a set of booleans in `MainActivity`; screens appear as overlays with inconsistent Back behaviour.
- Hidden files are reachable only from the Sort menu.
- 15 modal dialogs interrupt flows that should be sheets or inline.
- Storage and Storage Intelligence overlap; tools have no home.

### 6.2 New structure

```
Bottom bar (compact) / rail (medium+): HOME · FILES · STORAGE · SETTINGS   (one ink pill)

HOME            search entry · storage tiles · categories · recent · favourites · private & hidden
FILES           browse (title row, breadcrumb, chips, list/grid, FAB stack, selection bar)
  └ pushed      file info · preview/viewer · archive view · search results
STORAGE         volumes (meters) · analysis · tools (trash, cleanup, duplicates, batch rename,
                compare, vault, Wi-Fi share, app manager) · network servers · operations
SETTINGS        appearance · files · hiding & privacy · storage access · about · danger zone
Sheets          every create/rename/confirm/choose flow (no AlertDialogs)
```

- "Browse" is renamed **FILES** in the bottom bar (decided; clearer for the non-technical persona; code names stay `Browse*` until the screen is rebuilt in U5).
- **Private & hidden** moves out of the Sort menu to Home and to Storage > Tools; the "show hidden files" toggle stays in Settings and in the Files overflow.
- Navigation uses an in-house saved route stack (`core/navigation/AtomicRoute.kt`, `RouteStack`) over the four tabs, in the style of Navigation 3: routes are plain Kotlin values encoded to strings in saved state, one `BackHandler` pops the stack, and predictive back works through the platform handler on Android 14+. The Atomic shell (U4 below) and the route migration are **one piece of work**, not two.
- **Decision (5 October 2026): no Navigation Compose.** It would add a library plus the Kotlin serialization plugin for type-safe routes, at versions that cannot be checked against AGP 9.1 / Kotlin 2.2 from the build machine, for a graph of four tabs and three pushed screens. The route stack is about 80 lines, unit-tested (`RouteStackTest`), and drops unknown entries from an old saved state instead of crashing. Revisit if deep links or more than roughly ten pushed screens arrive.
- Deep links: open a folder (`atomic://files?path=…`) and the operations screen from the notification.

---

## 7. Screen specifications

Every screen implements the full state set. Abbreviations: **L** loading, **E** empty, **Er** error, **P** partial, **O** offline (network screens), **S** success, **F** first use.

### 7.1 Home

| Field | Spec |
|---|---|
| Purpose | Get to a file in one or two taps, and see storage health at a glance |
| Primary user | Priya (non-technical) |
| Primary action | Search all files (Phase 1.4); before search ships, open a category |
| Secondary | Open a volume, open Downloads, recent, favourites, private & hidden |
| Layout | Header (spec §9.6 home header): mono "ATOMIC" label over Display "FILE MANAGER" + mono "2 VOLUMES · 41 % USED", ghost search icon → search field → `AtomicStatTile` row (ON PHONE / ON SD / IN TRASH) → section CATEGORIES (3×n icon-tile grid; 2 columns narrow/large-font, 4 wide) → section RECENT → section FAVOURITES → PRIVATE & HIDDEN card (status pill LOCKED) |
| States | L: tiles show mono "SCANNING…" with ink bar. E: no favourites → `AtomicEmptyState` "Star a folder to keep it here." P: categories partial "2 FOLDERS UNREADABLE →". Er: no storage access → `AtomicWarningBox` + Primary "GRANT ACCESS". F: one-time numbered steps "STEP 1 OF 2 / ALLOW ACCESS" |
| Motion | Tiles' numbers fade in (350 ms); meters animate width once |
| Accessibility | Each tile announces "On phone, 41.2 of 128 gigabytes used"; category tiles are buttons with names |

### 7.2 Files (Browse)

| Field | Spec |
|---|---|
| Purpose | Navigate and act on files |
| Primary user | Arjun (prosumer), Dev (developer) |
| Primary action | Open a file or folder |
| Secondary | Select, sort, view mode, search in folder, new folder/file, add to bubble, actions via long press |
| Layout | Pushed header (back square + mono eyebrow `INTERNAL`) → title row: folder name (`nameLarge`, as stored) + counter `128 ITEMS` → breadcrumb (mono) → chips row: sort (`NAME ↑`), segmented `LIST / GRID`, `HIDDEN` toggle chip when shown → content → `AtomicFabStack` → `AtomicBubbleRail` |
| Selection | Header becomes "3 SELECTED · ALL · CANCEL"; bottom action strip: COPY · MOVE · SHARE · TRASH + overflow (rename, compress, info, hide, add to bubble) |
| States | L: first chunk pending → ink bar under header + "LOADING…"; rows stream in. E: "This folder is empty." + "NEW FOLDER". Er: access denied → "Atomic File Manager can't read this folder." + "GRANT ACCESS" or "GO UP". Volume removed → return to volume list with explanation (FR-1.7) |
| Dual pane | Expanded widths (≥ 840 dp; aligns the current 720 dp threshold with `RESPONSIVE_DESIGN.md`): two panes, each with its own title row; active pane has a 2 dp Signal top rule |
| Motion | Folder change: 350 ms fade + 16 dp slide in the direction of travel; reduced motion = fade only |
| Accessibility | Rows expose custom actions (open, select, info, add to bubble); selected state announced; drag has a non-drag alternative (Move to…) |

### 7.3 Category collection

Title row `IMAGES` + counter `1,204 FILES · 3.1 GB`; search field; sort chips; grid for images/videos, list otherwise. States: L "SCANNING · 1,204 FOUND SO FAR" (partial results stream), P "2 FOLDERS UNREADABLE", E "No videos found on this phone.", refresh as a text action "RESCAN →" with "SCANNED 2026-10-04 08:11".

### 7.4 Search (new, Phase 1.4)

Search field with scope chips (THIS FOLDER / ALL STORAGE / volume) and filter chips (TYPE, SIZE, DATE). Results: `AtomicFileRow` with the containing folder in mono and "OPEN FOLDER →". States: L "SEARCHING…" with results streaming; E "Nothing named "invoice" on this phone." + suggestion to widen scope; index not ready → `AtomicInfoSheet`-style inline note "INDEX BUILDING · RESULTS MAY BE INCOMPLETE".

### 7.5 File info and preview

- **File info** becomes a pushed screen (compact) or side panel (expanded) using `AtomicFactSheet`: NAME, PATH, SIZE (exact bytes + human), TYPE, MODIFIED, CREATED, PERMISSIONS, then CHECKSUMS with "CALCULATE" text action and hashes in code wells (copy button each). Replaces `FileDetailsDialog`.
- **Preview/viewer**: ink full-screen for images/video (dark section rules: paper text, `signal-light` accents); mono caption "IMG_2041.jpg · 3 / 15"; Ghost-on-dark actions. Text/markdown on paper with Body type, code in mono.
- **Quick Peek**: `AtomicPeekFrame`; behaviour unchanged.

### 7.6 Private & hidden (and vault later)

Pushed title "PRIVATE & HIDDEN"; segmented control `FAST OBSCURE / GALLERY / PRIVATE` (mono caps), each with a one-line explanation of what the mode does and does not protect. Rows show original location in mono. Lock: status pill `LOCKED`/`UNLOCKED`, biometric gate as an `AtomicSheet`. Restore is the primary action per item; delete forever lives behind the confirm sheet. Vault (Phase 2.5) adds a dark module explaining encryption and the "cannot be recovered after reset" warning in an `AtomicWarningBox`.

### 7.7 Storage

Title "STORAGE"; hero number free space (`displayHero` "86.8" + mono "GB FREE ON PHONE"); `AtomicStorageCard` per volume; section ANALYSIS (Storage Intelligence entry with last scan time); section TOOLS (2-column grid of icon tiles: TRASH, CLEANUP, DUPLICATES, BATCH RENAME, COMPARE, VAULT, WI-FI SHARE, APPS; unbuilt tools hidden, not disabled); section NETWORK (servers; SFTP/SMB show the coming-soon info sheet until Phase 3); section OPERATIONS (active and recent `AtomicActivityRow`s).

### 7.8 Storage analysis and cleanup

Pushed title "STORAGE ANALYSIS"; eyebrow "SCANNED 2026-10-04 08:11 · 41,203 FILES"; tabs as chips (LARGE FILES · DUPLICATES · TEMP & CACHE · OLD DOWNLOADS); each suggestion card says the reason and the space ("APKs for apps already installed · 1.3 GB"). Selection uses the standard selection bar; the confirm sheet says "Moves 14 files (1.3 GB) to Trash. You can restore them for 30 days." States: L progress bar with mono "FOLDER 812 / 3,400"; E "Nothing to clean up."; P unreadable folders count.

### 7.9 Settings

Title "SETTINGS"; groups under mono labels with ink rules: APPEARANCE (theme: segmented SYSTEM / LIGHT / DARK; WALLPAPER COLOURS toggle card with the fallback note from section 4.5), FILES (show hidden files, default view, confirmations), HIDING & PRIVACY (default hiding method, hidden folder location, lock), STORAGE ACCESS (status pill GRANTED / NEEDED + fix button: the permission doctor), ABOUT (version in mono, licences), DANGER ZONE (clear scan cache, reset settings; each with EXECUTE). "On" settings use `AtomicToggleCard` with Signal border.

### 7.10 Sheets that replace dialogs

| Old dialog | New sheet | Primary action |
|---|---|---|
| NewFolderDialog | NEW FOLDER sheet with `AtomicTextField` and validation text | CREATE FOLDER |
| RenameDialog | RENAME sheet; extension shown separately and protected by default | RENAME |
| HideFileDialog / HideOptionCard | HIDE sheet: three option cards with honest one-line explanations | HIDE FILE |
| HiddenFolderDialog | HIDDEN FOLDER sheet with path preview in mono | SAVE FOLDER |
| DropDecisionDialog | MOVE OR COPY sheet stating counts, size and same-volume speed | MOVE / COPY (one Primary, one Ghost) |
| BubbleTransferDialog | TRANSFER sheet: destination, counts, conflict policy | TRANSFER FILES |
| AddNetworkServerDialog | ADD SERVER pushed screen (long form) with numbered steps | SAVE SERVER |
| Delete confirmations | `AtomicConfirmSheet` | MOVE TO TRASH / DELETE FOREVER |
| About dialog | `AtomicInfoSheet` or Settings > About | GOT IT |

### 7.11 New screens from the feature plan (built Atomic from the start)

Trash (stat tiles, rows with "DELETED 2026-10-01 · RESTORES TO /Download", danger zone EMPTY TRASH), Operations (`AtomicOperationCard` list, history with undo text actions), Search (7.4), Vault, Batch rename (preview table old → new in mono, conflicts in error), Compare/sync (plan preview), Wi-Fi share (live dot, URL and PIN in a code well, STOP SHARING primary).

---

## 8. Responsive behaviour

| Width class | Navigation | Layout transformations |
|---|---|---|
| Compact < 600 dp | Bottom bar | Single column; sheets from bottom; categories 3 columns (2 at large font); file info as pushed screen |
| Medium 600–839 dp | Nav rail | Two-column card grids (Storage tools, Settings groups); sheets as centred 560 dp panels; categories 4 columns |
| Expanded ≥ 840 dp | Nav rail | Dual-pane Files; list–detail for Settings and Storage; file info as a right side panel; Trash/Operations side by side |
| Landscape phone | Bottom bar kept; header compresses to 48 dp content height | Title row and breadcrumb share one line; FAB stack collapses to the primary only |
| Foldables | Rail when unfolded | Panes avoid the hinge (`WindowLayoutInfo`); dual pane splits on the fold |
| Font scale 200 % | Unchanged | Display titles wrap to 2 lines; counters move under titles; fact sheets stack label above value; chips wrap |

Breakpoints come from `AtomicBreakpoints` only. `BrowseScreen`'s hard-coded 720 dp is replaced.

---

## 9. Motion

| Token | Use in Atomic File Manager |
|---|---|
| press 120 ms | Button and card press translate + shadow collapse |
| hover 150 ms | Pointer hover (tablets, Chromebooks), colour changes |
| toggle 200 ms | Chips, switches, checkbox, segmented control |
| enter 350 ms | Screen/folder transitions (fade + 16 dp slide), sheet enter, snackbar, meter width |
| reveal 500 ms | First-run and empty-state content only; **never on file lists** (noise and cost) |
| ambient (34 s linear) | Optional atom-mark orbit on first-run/empty states only; paused with reduced motion |

- **Gesture exception (technical reason):** drag-and-drop, bubble fling-to-edge and Quick Peek follow the finger and must keep fling velocity, which a fixed-duration tween cannot. These use **critically damped springs only** (`DampingRatioNoBouncy`): no overshoot, so they still obey the spec's "no bounce" intent. `AtomicMotion.Quick` (`MediumBouncy`) is deleted.
- **Reduced motion:** when the system animator scale is 0 or "Remove animations" is on, all translates are dropped and only short opacity fades remain. This extends the existing `ACCESSIBILITY.md` §3 behaviour.
- Haptics stay as in `ANIMATION_SYSTEM.md` §8.

---

## 10. Iconography and assets

- One style: **outlined**. The 118 filled/default icon references are migrated to outlined equivalents through `AtomicIcons`, so the set is defined in one file.
- Google's Compose `material-icons` artifacts are no longer the recommended route for new icons (Material Symbols are distributed as vector drawables); check current guidance, and if needed import only the Material Symbols Outlined icons the app uses as vector drawables behind `AtomicIcons`. Either way, screens reference `AtomicIcons.*`, never an icon library directly.
- Icons sit in 40 dp `AtomicIconTile`s in lists; 20–24 dp; ink, or white on ink/Signal.
- Category identity comes from **icon + label**, not colour: the 17 hard-coded category accent colours are removed.
- Text arrows "→" (mono) replace "›".
- Custom Atomic assets as `ImageVector` in `core/designsystem/icons`: atom mark (for empty/first-run states). Launcher icon: the owner is supplying a new icon; until then the existing artwork stays (section 18).
- No emoji in UI (none today; keep it that way).

---

## 11. Copy and content

- Move every UI string to `strings.xml` in sentence case (required for localisation and for render-time uppercasing). This is a prerequisite step, done screen by screen.
- Buttons: caps verb + object (`CREATE FOLDER`, `MOVE TO TRASH`, `GRANT ACCESS`).
- Labels: mono caps nouns (`STORAGE ACCESS`, `CHECKSUMS`, `RECENT`).
- Numbers and dates: mono; ISO `2026-10-04 08:11` in UI; sizes with one decimal (`4.2 MB`) and exact bytes in file info.
- Destructive copy says what happens and whether it can be undone: "Moves 3 files to Trash. You can restore them for 30 days." vs "Deletes 3 files permanently. This can't be undone."
- Unavailable features explain why and the alternative (SFTP/SMB coming-soon sheet).
- Consistent nouns, capitalised: Transfer Bubbles, Quick Peek, Trash, Vault, Private Storage, Fast Obscure, Hide from Gallery.

---

## 12. Accessibility

| Requirement | Implementation |
|---|---|
| Contrast | Only role pairs from the spec's §3.6 table; dark-theme roles contrast-tested in the token PR; a unit test computes contrast for every role pair |
| Never Signal text on ink | Enforced by role design (`accent` resolves to `signal-light` on dark surfaces) |
| Focus | `focusRing()` on every interactive atom; keyboard/D-pad traversal order checked per screen |
| Touch targets | 48 dp minimum hit area even for 36 dp back squares and 32 dp chips (`minimumInteractiveComponentSize`) |
| Not colour alone | Meter shows "NEARLY FULL" text; selection shows checkbox; status pills carry words |
| Uppercase | Applied at render; semantics carry the original-case text |
| Text scaling | 200 % font: layouts per section 8; mono minimum 12 sp |
| Screen readers | Decorative icons have null descriptions; icon-only buttons labelled; snackbars announced; custom actions on file rows; sheets trap focus and restore it |
| Reduced motion | Section 9 |

---

## 13. Performance

- Hard shadows are drawn with `drawBehind` (a filled shape): cheaper than the blurred `Modifier.shadow` and tonal elevation used today. No blur, `RenderEffect` or gradients anywhere (consistent with the spec and with dropping Liquid Glass).
- Tokens are provided through `staticCompositionLocalOf`: they only change on theme switch, so reading them does not cause recomposition.
- Fonts are bundled and loaded once; variable fonts keep APK growth small. Record the APK size change in the foundation PR.
- Atomic components take stable, immutable parameters; file rows receive preformatted strings from the view model (`ALL_IN_ONE_PLAN.md` §16.3).
- No scroll-reveal animations on lists; list item enter animations limited to folder transitions.
- Splitting `BrowseScreen.kt` into title row, chips, list, selection bar and FAB components limits recomposition to the part that changed.

---

## 14. Code architecture, enforcement and testing

### 14.1 Package layout

```
core/designsystem/   foundation · modifiers · icons · atoms · molecules · organisms · preview
ui/screens/<feature>/  one folder per screen: Screen.kt (composition only), ViewModel.kt, components/
```

Screens compose components; they do not define colours, sizes, shapes or animation specs.

### 14.2 Lint enforcement (custom `lint-rules` module)

| Rule | Status today | Plan |
|---|---|---|
| `NoHardcodedDp` | Exists, **disabled** in `app/build.gradle.kts` (420 literals) | Re-enable with a lint **baseline** of today's literals: existing ones are tolerated while screens migrate, new ones fail CI. Shrink the baseline with each screen PR; delete it at the end |
| `NoHardcodedColor` | New | `Color(0x…)` outside `core.designsystem.foundation` |
| `NoHardcodedSp` / `NoRawTextStyle` | New | `fontSize =` literals and `MaterialTheme.typography` use outside `core.designsystem` |
| `NoFilledIcons` | New | `Icons.Filled` / `Icons.Default` outside `AtomicIcons` |
| `NoBouncySpring` | New | `spring(dampingRatio < 1)` anywhere |
| `NoToast` / `NoAlertDialog` | New | `Toast.makeText`, `AlertDialog(` outside `core.designsystem` |
| `NoRoundedCornerLiteral` | New | `RoundedCornerShape(<literal>)` outside `foundation` |

Each new rule ships with lint unit tests, like the existing detectors.

### 14.3 Visual regression in CI

The developer has no local Android SDK, so visual checks must run in CI. Add **Roborazzi** (Robolectric-based screenshot testing; the project already uses Robolectric): every component in the preview catalogue is captured in light, dark and 200 % font. Screenshots are uploaded as a CI artifact for review, and compared against recorded baselines once the design settles.

### 14.4 Existing tests

Robolectric Compose tests that find nodes by visible text will break when copy changes. Before each screen migration, switch its tests to `testTag`s and semantic roles, then migrate the UI.

---

## 15. Implementation phases

Mapped to the ten-phase process requested, and to `ALL_IN_ONE_PLAN.md`. Effort: S ≈ a day, M ≈ a few days, L ≈ a week+.

| Phase | Work | Output | Effort | Runs alongside |
|---|---|---|---|---|
| **U0 Decide** | Owner answers section 18 | **Done 4 October 2026** (section 18); product rename done in code | S | — |
| **U1 Understand + audit** | This document; design system copied into `docs/design/` | Done (plan) | — | — |
| **U2 Foundations** | Tokens (4.1–4.2), bundled fonts, `AtomicTheme` bridged onto `MaterialTheme`, modifiers, `AtomicIcons`, motion tokens, breakpoints; dynamic colour becomes the accent-only wallpaper option (4.5); lint baseline + new lint rules (as warnings) | Whole app switches to ink/paper/Signal and Atomic fonts in one PR; no screen restructuring yet | M | Phase 0.1/0.2 (different files) |
| ↳ U2 status (4 Oct 2026) | **Part 1 done:** colour roles light/dark, wallpaper-accent guard rails, bundled fonts, type/spacing/shape/border/elevation/size/breakpoint/motion tokens, `hardShadow`, `AtomicTheme` bridged onto Material 3 (old theme and bouncy motion deleted). **Part 2 done:** `AtomicIcons` (outlined set), `AtomicDesignDetector` with NoHardcodedColor, NoFilledIcons, NoToast, NoAlertDialog and NoBouncySpring (warnings), and `NoHardcodedDp` re-enabled as a warning. Warnings instead of a lint baseline: a baseline file has to be generated by a lint run, and warnings give the same "report, don't block" effect; all become errors at U8 | | | |
| **U3 Atoms** | Section 5.1 with previews + Roborazzi | Component catalogue | M | Phase 0 |
| ↳ U3 status (4 Oct 2026) | **Done in code:** `AtomicText` (render-time uppercase, original-case semantics), `AtomicButton` (7 variants, key-press shadow), `AtomicIconButton` (5 variants), `AtomicIconTile`, `AtomicChip`, `AtomicStatusPill`, `AtomicBadge`, `AtomicCheckbox`, `AtomicSwitch`, `AtomicTextField`, `AtomicDivider`, `AtomicCounter`, `AtomicMeter`, `AtomicProgressBar`, `AtomicLoading`, `Modifier.focusRing`; preview catalogue (light, dark, 200 % font) and Robolectric behaviour tests. Roborazzi screenshots still to add | | | |
| **U4 Molecules + organisms + shell** | Sections 5.2–5.3; `AtomicHeader`, `AtomicBottomBar`/rail, `AtomicSheet`, `AtomicSnackbar`; **merged with the route-stack migration (Phase 0.3)** | New shell and route stack | L | Phase 0.3 (same work) |
| ↳ U4 status (5 Oct 2026) | **Done in code:** molecules (headers, breadcrumb, file/settings/toggle/activity rows, fact sheet, empty/error/warning states, stat tile), organisms (bottom bar, rail, sheet/confirm/info sheets, snackbar, storage/operation cards, danger zone, action strip, FAB stack); `MainActivity` on the Atomic shell; saved route stack replaces the overlay variables (section 6 decision). Predictive-back animation and the Operations entry point come with their screens | | | |
| **U5 Screens** | One PR per screen, in order: Settings → Home → Files (split `BrowseScreen`) → Category → Storage → Storage analysis → Private & hidden → previews/file info → sheets replacing each dialog | Every existing screen redesigned | L | Phase 1 starts after Files is done |
| ↳ U5 status (5 Oct 2026) | **Done in code:** Settings, Home, Files (header, breadcrumb, rows, sort/selection/clipboard bars, states, every Files dialog as a sheet, preview), Category, Storage, Storage analysis, Private & hidden, lock screen. Fixes found on the way: cleanup delete crashed on `file:` ids; hidden files were deleted on one tap (now confirmed); checksum failures were silent; ISO dates everywhere. **Left for U6–U8:** Quick Peek, drag preview, bubble rail and the Markdown/video/audio viewer bodies still use Material containers (themed, outlined icons); Search, Trash, Operations and favourites/recents screens come with their Phase 1 features | | | |
| **U6 States pass** | Every screen against the state list (L/E/Er/P/O/S/F) | No happy-path-only screens | M | — |
| **U7 Responsive + accessibility pass** | Section 8 and 12 on phone, tablet emulator, 200 % font, TalkBack, keyboard | Verification report entries | M | — |
| **U8 Cleanup** | Delete old `Theme.kt` schemes, `AtomicMotion` springs, unused components; lint rules from warning → error; delete lint baseline; supersede docs (section 0.5) | One design system in the codebase | S | — |
| **U9 Final review** | Check against the spec's §15 checklist and the definition of done below; write the final report (section 17) | Report | S | — |

**Order relative to the feature plan:** Phase 0 (reliability) and U2–U3 run in parallel (no shared files). U4 *is* Phase 0.3. Phase 1 feature screens (Trash, Search, Operations, favourites/recents on Home) start only after U4, and are built with Atomic components directly.

---

## 16. Definition of done (per screen and component)

- [ ] Only role tokens and Atomic components; no literals (lint clean, baseline shrunk)
- [ ] One accent, under ~5 % of the screen; one Primary button per view
- [ ] No blurred shadows, gradients or bouncy springs
- [ ] App-authored headings and buttons uppercase via `AtomicText`; user data shown as stored
- [ ] Mono labels ≥ 12 sp; touch targets ≥ 48 dp; focus ring visible
- [ ] States L/E/Er/P/O/S/F designed and implemented where applicable
- [ ] Destructive actions state the effect with numbers and use the confirm sheet
- [ ] Reduced-motion variant for every animation
- [ ] Light, dark and 200 % font screenshots in CI; TalkBack pass recorded
- [ ] Functionality unchanged: existing unit, Robolectric and device tests green
- [ ] Old component deleted in the same PR once it has no callers

---

## 17. Final report template (filled in at U9, not before)

Design system extracted · Architecture · Components created/refactored · Screens redesigned · UX improvements · Responsive behaviour · Accessibility · Performance · Removed/deprecated · Remaining work (only genuinely incomplete items). Nothing is reported as complete unless it is implemented and verified.

---

## 18. Owner decisions (recorded 4 October 2026)

| # | Decision | Owner's answer | How it is implemented |
|---|---|---|---|
| 1 | Dynamic colour (Material You) | Keep, as a wallpaper-colour option | Signal Blue is the default accent. A **WALLPAPER COLOURS** setting (API 31+, off by default) swaps **only the accent** for the Material You colour; ink, paper and every other token stay Atomic. Guard rails in section 4.5 |
| 2 | Dark theme | Ship both light and dark | Atomic Light and Atomic Dark (section 4.1); theme setting SYSTEM / LIGHT / DARK |
| 3 | Product name | **Atomic File Manager**, including the package name | Done in code on 4 October 2026: app label "Atomic File Manager", `applicationId`/namespace/Kotlin package `com.devbehindyou.atomicfilemanager`. See section 18.1 |
| 4 | Launcher icon | Owner selected 07 Stacked light on 19 October 2026 | Supplied PNG wired into adaptive layers; paper background matched and previous Refract artwork retired. See [launcher icon integration](../design/LAUNCHER_ICON.md) |
| 5 | Accent | Signal Blue `#3A2FF0` | Default accent; see decision 1 for the wallpaper option |
| 6 | "Browse" tab label | Left to the plan | **FILES** |
| 7 | Mascot | No mascot | No Atomi anywhere; empty and first-run states use the atom mark or icons only |
| 8 | Dates | ISO | `2026-10-04 08:11` in the UI for everyone |

### 18.1 Rename notes

- Kept on purpose (on-disk formats, must never change): the Fast Obscure footer `REFRACT_OBSCURE_V1`, the extension `.refract_obscured`, and the credential key-derivation string in `NetworkCredentialsStore` (replaced anyway by Phase 0.4).
- Default hidden folder for new installs is `Atomic File Manager/Hidden`. Items hidden earlier keep their stored paths, so restore still works.
- Database, settings and notification-channel names are now `atomic_*`.
- Because the `applicationId` changed, Android treats this as a **new app**. It installs next to the old Refract app and starts with empty settings, hidden-file records and bubbles. Anything hidden with the old app (especially Private Storage, which lives inside the old app's private folder) must be restored **in the old app** before it is uninstalled.
- The GitHub repository is still named `Refract-Full-File-Manager`; renaming it is the owner's call.

---

## 19. Risks

| Risk | Mitigation |
|---|---|
| Superficial restyle instead of reconstruction | Screen specs in section 7 change hierarchy and flows; review each PR against them |
| Regressions in working features | UI-only PRs; tests switched to tags first; device checks per screen |
| Two design systems living side by side for too long | Bridged `MaterialTheme` from U2 makes old widgets look Atomic immediately; lint baseline must shrink every PR; U8 deadline |
| Uppercase Display hurting readability or TalkBack | Display only for short app-authored text; semantics carry original case |
| Font size and glyph coverage | Variable fonts; fallback verified with non-Latin fixtures |
| Contrast failures in dark theme | Contrast unit test on every role pair; Signal never used as text on ink |
| Work blocked by no local SDK | Roborazzi screenshots and all checks run in CI |
