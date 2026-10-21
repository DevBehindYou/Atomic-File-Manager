# File-opening preferences and safe app handoff

Implementation slice for U06, based on `12f007d`. Host validation has passed for the current app source; the full device acceptance matrix remains open.

## Behavior

- Every existing `FilePreviewPane`/`FilePreviewDialog` caller now shares an app-specific open policy. The default remains the existing built-in viewer/file-details behavior.
- Settings → File opening supports ask, prefer built-in and prefer external policy, plus per-family, explicit extension and exact MIME rules. Extension rules override MIME, family and global rules in that order.
- Settings can add, change and reset rules, or confirm a reset of all opening preferences without resetting unrelated settings.
- The per-file chooser lists actual exported/enabled Android `ACTION_VIEW` components, with once/always consent. App-specific saved rules record a real component and label, never infer an app from an opaque system chooser result.
- Generic “Prefer external app” remains a policy that invokes Android's chooser. It does not claim to save whichever application the user picks there.
- A missing/incompatible saved component returns to visible choices and the built-in/file-details fallback. Failed launch/preparation does not save the selected app as a default.
- Gallery opening passes the currently displayed image into choices/export/rule saving. Legacy callbacks tied to the originally opened file retain their original visibility guard, and share callbacks are not reused for another image. Per-file asynchronous handoff is cancelled when the preview source changes.
- The existing external exporter still supplies content URIs and temporary read access. Remote export remains capped at 64 MiB; media preview staging remains capped at 512 MiB. No writable editing or automatic remote write-back is advertised.
- Installed-app discovery is performed on IO. Activity launch remains on Main after export. Visibility is scoped to content-URI view handlers; no all-packages permission is added.
- Versioned rule persistence tolerates corrupt, unsupported and oversized stored data. Rules are bounded to 128 and add controls disclose the limit. Existing settings and user files are preserved.

## Source boundaries

Domain `FileOpening.kt` owns identity-independent policy, normalization and precedence. The existing settings repository stores a versioned JSON field through `FileOpeningPreferencesCodec`. `ExternalFileApps` owns Android handler discovery and launch intents. `FileOpeningPane` coordinates actual opening; `FileOpeningSettingsSheet` manages preferences. MainActivity and startup access logic are untouched.

No new dependency, downloaded module, minimum-SDK change, native code or user-file mutation is introduced.

## Regression coverage

- `FileOpeningTest`: precedence, directory handling, known-MIME authority, key validation and rule reset.
- `FileOpeningPreferencesTest` on API27/34: persisted real component, corrupt/oversized/versioned data, malformed-row isolation and reset preservation.
- `ExternalFileAppsTest` on API27/34: handler filtering, actual component launch, read-only URI/ClipData grants and generic chooser identity.
- `FileOpeningSettingsComposeTest`: real rule change/reset and reset-all confirmation using native graphics.
- `PreviewExternalActionTest`: the real external-action button stays available for a newly displayed gallery image, passes its identity, and preserves original-file callback restrictions.
- Existing Settings, PreviewType, ExternalPreviewHelper and startup preferences regressions.
- ktlint, detekt, debug/instrumentation compilation and Android Lint.

## Recorded validation — 21 October 2026

- The serial combined host run at integration commit `2767f46` passed 42 file-opening tests, ktlint, detekt, debug Android Lint, debug APK and Android-test APK packaging, and the R8-minified benchmark target/benchmark harness packaging. The app source tree `6c80f40d1ab0693311133e7a7df99f8c674bcf44` exactly matches PR #64 head `2e9230b`. Log: `build/audit-tools/opening-benchmark-final-validation.log`, exit 0, `BUILD SUCCESSFUL in 18m 50s`.
- Debug Lint reported **0 errors, 154 warnings and 1 hint**. Passing the task does not mean those warnings were resolved.
- `ExternalFileAppsTest` separately passed six cases across API 27 and 34 with real registered component intent filters, including an incompatible-MIME exclusion. Log: `build/audit-tools/external-app-component-fixture-validation.log`, exit 0, `BUILD SUCCESSFUL in 1m 21s`.
- [PR #64 CI run 39612223345](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/39612223345) passed static analysis and unit-test/APK packaging at `2e9230b`. Its manual instrumented-test job was skipped; CI success is not device acceptance.
- A phone check exercised the file-opening Settings surface on the earlier debug APK with SHA-256 prefix `e84518`. That result applies only to that artifact and surface. The latest debug APK, SHA-256 `3F2A01163C9716F8D27CAB0361227A824FA97F547A91F96087A8F87CA945D364`, has not yet received the corresponding phone retest because the benchmark run owns the device.

Native OS app resolution, install/uninstall and missing-handler recovery, representative external editors/readers, provider grant loss, and large/slow remote handoff still require device acceptance. The completed host checks and earlier Settings surface check do not complete U06.

## Remaining U06 work

Bounded content sniffing for ambiguous/mislabelled file metadata is not added by this slice. Routing uses existing metadata plus explicit rules. Handler choices in Settings use a representative MIME for each broad family and are revalidated for the actual file when opened; not every handler supports every format in a family. App icons and a richer temporary-download progress display remain presentation follow-ups. No OS-wide defaults are changed.

Official APIs checked: [package visibility](https://developer.android.com/training/package-visibility/declaring), [content-URI sharing](https://developer.android.com/training/secure-file-sharing/share-file).
