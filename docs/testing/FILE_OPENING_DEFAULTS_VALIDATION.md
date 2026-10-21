# File-opening preferences and safe app handoff

Implementation slice for U06, based on `12f007d`. Validation remains pending until the root agent records the serial build/test outcome.

## Behavior

- Every existing `FilePreviewPane`/`FilePreviewDialog` caller now shares an app-specific open policy. The default remains the existing built-in viewer/file-details behavior.
- Settings → File opening supports ask, prefer built-in and prefer external policy, plus per-family, explicit extension and exact MIME rules. Extension rules override MIME, family and global rules in that order.
- Settings can add, change and reset rules, or confirm a reset of all opening preferences without resetting unrelated settings.
- The per-file chooser lists actual exported/enabled Android `ACTION_VIEW` components, with once/always consent. App-specific saved rules record a real component and label, never infer an app from an opaque system chooser result.
- Generic “Prefer external app” remains a policy that invokes Android's chooser. It does not claim to save whichever application the user picks there.
- A missing/incompatible saved component returns to visible choices and the built-in/file-details fallback. Failed launch/preparation does not save the selected app as a default.
- The existing external exporter still supplies content URIs and temporary read access. Remote export remains capped at 64 MiB; media preview staging remains capped at 512 MiB. No writable editing or automatic remote write-back is advertised.
- Installed-app discovery is performed on IO. Activity launch remains on Main after export. Visibility is scoped to content-URI view handlers; no all-packages permission is added.
- Versioned rule persistence tolerates corrupt, unsupported and oversized stored data. Rules are bounded to 128 and add controls disclose the limit. Existing settings and user files are preserved.

## Source boundaries

Domain `FileOpening.kt` owns identity-independent policy, normalization and precedence. The existing settings repository stores a versioned JSON field through `FileOpeningPreferencesCodec`. `ExternalFileApps` owns Android handler discovery and launch intents. `FileOpeningPane` coordinates actual opening; `FileOpeningSettingsSheet` manages preferences. MainActivity and startup access logic are untouched.

No new dependency, downloaded module, minimum-SDK change, native code or user-file mutation is introduced.

## Required checks

- `FileOpeningTest`: precedence, directory handling, known-MIME authority, key validation and rule reset.
- `FileOpeningPreferencesTest` on API27/34: persisted real component, corrupt/oversized/versioned data, malformed-row isolation and reset preservation.
- `ExternalFileAppsTest` on API27/34: handler filtering, actual component launch, read-only URI/ClipData grants and generic chooser identity.
- `FileOpeningSettingsComposeTest`: real rule change/reset and reset-all confirmation using native graphics.
- Existing Settings, PreviewType, ExternalPreviewHelper and startup preferences regressions.
- ktlint, detekt, debug/instrumentation compilation and Android Lint.

No phone result or completion of the full U06 acceptance matrix is claimed. Native OS app-resolution/install/uninstall behavior, representative external editors/readers, provider grant loss and large/slow remote handoff still require device acceptance.

## Remaining U06 work

Bounded content sniffing for ambiguous/mislabelled file metadata is not added by this slice. Routing uses existing metadata plus explicit rules. Handler choices in Settings use a representative MIME for each broad family and are revalidated for the actual file when opened; not every handler supports every format in a family. App icons and a richer temporary-download progress display remain presentation follow-ups. No OS-wide defaults are changed.

Official APIs checked: [package visibility](https://developer.android.com/training/package-visibility/declaring), [content-URI sharing](https://developer.android.com/training/secure-file-sharing/share-file).
