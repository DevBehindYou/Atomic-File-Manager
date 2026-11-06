# Files workspace controls — 20 October 2026

Source checkpoint: `9f75b3e`, based on `f04ab8f`. The requested Files storage
switcher, List/Grid views and toolbar options are installed and surface-tested.
See [preview validation](PREVIEW_TOOLS_VALIDATION.md) for the preceding increment.

The following ledger applies to `9f75b3e`. The subsequent `4792db8` refinement
adds content-sized top options, icon grids for bottom action menus and file-type
colours; its separate [validation report](MENU_REFINEMENT_VALIDATION.md) records
917 host cases and the current 13-case phone result.

## Behavior

- Files shows mounted storage shortcuts and the friendly current-storage label
  below them. Roots come from Android's existing volume enumeration on IO;
  matching uses known identities and FILE path component boundaries. It does not
  probe paths or decode opaque provider identifiers on the UI thread.
- Switching a drive navigates the current pane's existing view model. Copy/Move
  clipboards survive, Back returns through visited locations, and search/type
  filters reset for the newly selected drive. Access requests refresh the shortcuts;
  the user chooses the storage after granting access. No denied request is retained
  for automatic replay, and switching tabs clears old Home navigation requests.
  Unmounted or unavailable storage is not guessed.
- Files has Search, Tasks and a three-dot options button. New folder, Show hidden
  files, hidden-file management, sorting, View and Filter are available in the
  anchored popup. Existing per-row More boxes remain removed.
- List/Grid is persisted as an app preference, with LIST as the invalid/absent
  preference fallback. Actual adaptive lazy grid cells retain selection,
  accessibility actions, previews, thumbnail ownership and drag/drop behavior.
  Both layouts retain their own scroll position when toggled.
- Category filters retain folders for navigation. Files-only and folders-only
  are explicit choices; an empty filtered result offers Clear filter instead of
  claiming that a nonempty folder is empty. Filtering remains off the main thread,
  and stale work cannot publish into a different folder.
- Grid thumbnails request their larger displayed size; local folder miniatures
  remain small and bounded. Tasks and options popups use Atomic sizing tokens and
  the host app window's bounds.

## Validation

| Gate | Result |
| --- | --- |
| Formatting/static analysis | ktlint and detekt passed |
| Full host suite / custom Lint rules | 902 app cases and 28 custom-rule cases passed; zero failures/errors/skips |
| Debug/instrumentation APKs | Packaged and installed as updates |
| Selected API34 device suite | All 13 cases passed, including the storage/view/toolbar journey |
| Physical Files UI review | Internal/SD switching, friendly labels, actual Grid/List rendering, options and Tasks popup verified; List restored and app returned to Home |
| Android Lint | Debug and release passed; each report has zero errors, 153 warnings and one hint |
| Release / benchmark packaging | R8 release and benchmark test APK assembled; complete Gradle command exited 0 |

New host cases cover mounted-volume identity, path-boundary and opaque-provider
matching; shortcut selection and grant routing; popup choices and placement;
filter categories; grid geometry, selection, thumbnails and scrolling; preference
reload/fallback; Copy/Move clipboard retention and cross-storage Back navigation;
and popup resizing. No tests are skipped or disabled.

The authorized phone run opens storage, changes/restores view and hidden-file
preferences, opens Tasks, and opens/cancels New folder. It does not copy, move,
delete or rename user files. Broader provider mutation, USB/network hardware,
process recovery and performance acceptance remains separate.

The initial integrated host run had two fixture failures: a raw TCP admission
setup hit Windows' kernel backlog, and a popup test's default 320dp host capped
its simulated larger window. The socket helper now retries refused connections
within the original two-second deadline; it still obtains all 20 clients and
asserts at least eight application rejections. The popup fixture provides a large
enough actual host. The first 13-case phone run passed 12 and failed an exact-text
assertion that omitted the current-storage label prefix. It now asserts the full
label. The final full 902-case and 13-case runs pass without removing assertions,
increasing deadlines or skipping cases.

The selected phone log contains one StrictMode `LeakedClosableViolation` with
callsite `InsetsSourceControl` and an Android `SurfaceControl.finalize` stack.
It contains no app acquisition frame or fatal exception. This diagnostic is
retained unresolved; the run is not described as zero-warning. The earlier
03:05 native media-retriever crash remains historical evidence in the device
crash buffer and preceded the provider-thumbnail fix.

Final debug APK SHA256:
`2060e100702bb15a0cd0cc1b3f3e10d731091eaad9480089c7a3aaaa9848eada`.
Instrumentation APK SHA256:
`8f109777d72ced7cc44ecca8d0ebfd33eb4dadeac0d13fe9d6f3cbe70893fd9e`.
Release APK SHA256:
`72e65c64fef030f9052aaa57c99dafc426d4716791f26104c4d571804b80cc9e`.
Benchmark test APK SHA256:
`ab5a166565cd7be6f79fc273eb388fc450ed42c39970516a7dd7320b23ec894c`.
These are debug-key-signed test artifacts. Packaging the benchmark test APK does
not run benchmarks, generate a profile or establish measured performance.

Host log: `build/audit-tools/files-workspace-final-validation.log` and matching
exit marker. Earlier failed runs remain in `files-workspace-integrated.log` and
`files-workspace-validation*`. Phone artifacts are under
`build/audit-tools/device-preview-tools/`: `selected-*`, `apk-sha256.txt`,
`files-first-13-*`, `files-physical-*` and `files-final-home.*`.
