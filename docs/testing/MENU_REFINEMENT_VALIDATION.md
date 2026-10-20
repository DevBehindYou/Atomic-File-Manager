# Popup, action-card and file-icon refinement — 20 October 2026

Source checkpoint: `4792db8`; base: `9f75b3e`. This increment responds to the supplied popup/menu
screenshots; its results are separate from the base's passing 902 host cases,
13 selected phone cases and complete release/benchmark packaging.

## Behavior

- Files options uses the intrinsic width of its actual content, bounded by the
  host window and Atomic popup tokens. Short View/Sort pages shrink; a taller page
  scrolls within the window cap. Changing pages resets that page's scroll offset.
  The top popup remains a list with its existing choices and callbacks.
- Bottom action menus use a shared responsive one-to-three-column icon-card grid.
  Cards contain an outlined semantic icon, short label and arrow, with optional
  concise context. Rows grow for wrapped labels and font scale; height follows
  actual rows, with the existing sheet supplying the screen cap and scrolling.
- The shared grid is used by Home Commands, More categories, Files Select,
  Add to bubble, contextual More, drop decisions, app actions and Trash actions.
  Search/filtering, callback ownership, busy/disabled states and destructive
  confirmations retain their existing behavior. Forms, readers, conflict details
  and explanatory hide-method choices keep their content-specific layouts.
- Semantic icons distinguish Download, Theme, Rename, Favourite, Extract,
  Invert selection and Select same type, in the existing outlined icon family.
  No image-generation service or bitmap icon dependency is needed.
- Fifteen file families have distinct symbols and stable light/dark icon colours:
  folder, image, video, audio, PDF, APK, archive, code, text, document, spreadsheet,
  presentation, e-book, font and generic. PDF is red, APK green, archive orange,
  audio rose, video purple and text blue/cyan, with deeper tones on light surfaces.
  Classification uses MIME, known MediaStore identity and filename metadata;
  it does not inspect file contents or change launch routing.
- Actual image/video preview pixels remain unchanged. Type colours apply to
  fallback icons and the small folder type marker; bounded miniature scans,
  cancellation, access checks and hidden-file privacy are retained.

## Validation

| Gate | Result |
| --- | --- |
| Formatting / detekt | Passed in the final integrated run |
| App / custom-rule host suites | 917 app cases and 28 custom-rule cases passed; zero failures/errors/skips |
| Debug / instrumentation APKs | Packaged and installed as updates; current debug APK matches the phone-tested hash |
| Android debug/release Lint | Passed; each report has zero errors, 153 warnings and one hint |
| Release / benchmark test APKs | R8 release and benchmark test APK assembled; complete 14m36s Gradle run exited 0 |
| Selected API34 device suite | All 13 selected cases passed on the latest debug APK; no captured StrictMode or fatal errors |
| Physical layout/icon review | Content-sized Files main/View popups, Commands and Select icon grids, coloured folder icons verified; selection cancelled and Home restored |

New regression coverage checks content-sized options/submenus, command-grid
reflow and shrinking row height, callbacks and disabled/destructive semantics,
file-family classification on API27/34, and every type colour's contrast against
the light/dark Atomic file surfaces. Existing browse selection, clipboard,
thumbnail, command filtering and viewer coverage remains enabled.

The first refinement validation was intentionally superseded before Kotlin
compilation to include the final app/Trash and semantic-icon changes. It is not
a passing run. Its log and superseded marker are retained. Final host evidence
belongs in `build/audit-tools/menu-refinement-complete-validation.*`; phone artifacts
remain under `build/audit-tools/device-preview-tools/`.

The first integrated refinement run caught a geometry-test API mismatch: DpRect
sizes are now computed from its edges. The next full run passed 916 of 917 cases;
only the new sizing assertion failed. A bounded diagnostic measured both host
pages at the minimum width (176dp inside the 24dp padding), despite increased
font scale. The legacy graphics shadow did not reproduce the bundled font
metrics. The same strict width/height assertions pass with native Android font
measurement (`GraphicsMode.NATIVE`); no assertion or test was removed and no
deadline was increased. The subsequent complete 917-case suite passed, including
all four options cases in that rendering mode. New icon calls use the current
AutoMirrored APIs.

Actual API34 popup window bounds were 573×1032px for the main options and
550×465px for View, on the 1080×2460px / density440 phone. These confirm natural
width and height reduction in the app. Commands and Select use icon cards;
the Select menu occupies its two card rows and introductory text. Generated
private preview fixtures remain the only file content written by the device
suite. No user file was copied, moved, renamed or deleted.

Phone evidence: `menu-final-13-instrumentation.log`,
`menu-final-13-logcat.txt`, `menu-final-13-apk-sha256.txt`,
`menu-options-compact.*`, `menu-view-compact.*`, `menu-select-grid.*`,
`menu-commands-grid.*` and `menu-home-settled.*`. The initial visual candidate
preceded three RTL-aware icon API substitutions; the final 13-case suite used
the latest APK below.

An immediate Home capture preceded the asynchronous volume refresh and briefly
showed the access-off state. A read-only app-ops check still reported
`MANAGE_EXTERNAL_STORAGE: allow`; the settled capture contains both Internal and
Samsung SD storage. No permission was changed. Loading-state polish for this
first access/volume frame remains separate startup work.

Debug APK SHA256:
`1f99a406ec0e75ffc5bcf65022e8c373a39b3a8095ade89725c87d9d832b23f0`.
Instrumentation APK SHA256:
`8f109777d72ced7cc44ecca8d0ebfd33eb4dadeac0d13fe9d6f3cbe70893fd9e`.
Release APK SHA256:
`74dd33db211bb7641dd7f9d17a611a5692168dd8e32f86b4cfb31155164fe27c`.
Benchmark test APK SHA256:
`ab5a166565cd7be6f79fc273eb388fc450ed42c39970516a7dd7320b23ec894c`.
The complete command finished successfully in 14m36s, exit 0. Hashes are also
retained in `build/audit-tools/menu-refinement-final-apk-sha256.txt`. Release
remains debug-key-signed; this packaging result is not production signing or
a benchmark measurement.

This is UI/surface acceptance. It does not establish measured performance,
provider mutations, process-death recovery or production signing.
