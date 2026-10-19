# Launcher icon

Selected on 19 October 2026: **07 Stacked, light**, from the ten light/dark
directions in `icon-variations/`. The stacked folders represent managing multiple
folders; the Signal-purple atom retains the Atomic identity. The selected light
artwork is used for both normal and round launcher icons.

## Source and integration

- Supplied source: `icon-variations/png/atomic-folder-07-stacked-light-512.png`.
- Packaged copy: `app/src/main/res/drawable-nodpi/atomic_launcher_stacked_light.png`.
- Both are the same 512 × 512 PNG, SHA-256
  `9c5833f336bdb01e135f6bfb4d21c84754ae15d5feb2074fdf0a00eac5f817e0`.
- Existing `@mipmap/ic_launcher` manifest references remain the entry point.
- The adaptive foreground uses a 20% inset on each side and disables bitmap
  filtering to retain the supplied pixel edges. No artwork is redrawn or recolored.
- The adaptive background matches the image's paper color, `#F4F5F1`.
- The superseded 1 MB `refract_launcher_art.png` resource is removed.

Within the 108 dp adaptive layer, the image occupies 64.8 dp. The furthest
non-background pixel center measures 32.2 dp from the center, inside Android's
33 dp safe radius. See [Android adaptive icon guidance](https://developer.android.com/develop/ui/compose/system/icon_design_adaptive).

## Validation

- Final `:app:assembleDebug` and `:app:lintDebug` passed on 19 October 2026.
  Android Lint completed with zero errors; existing warnings remain.
- APK inspection confirmed the selected resource is packaged and the superseded
  `refract_launcher_art` resource is absent.
- The final debug APK was installed over the existing app on the authorized
  Android 14/API 34 phone. App Info displayed the new icon correctly, and the app
  opened successfully on Home.
- Final APK SHA-256:
  `d08150c08c79efe4d3cb86b59537e93776832164d7bd6db1be0b2b2e0586a40a`.

Build output is in `build/audit-tools/stacked-launcher-icon-final-build.log`;
phone captures are in `build/audit-tools/device-stacked-launcher-icon/`.
These generated outputs are ignored by Git. Icon validation does not imply full
release acceptance. The earlier full test checkpoint is recorded in
[implementation progress](../roadmap/IMPLEMENTATION_PROGRESS.md).
