# Storage card header — 19 October 2026

The shared `AtomicStorageCard` header now gives its volume name and usage text equal
shares of the available width. Both stay on one line, align vertically at the center,
and use ellipsis when constrained. Capacity details align to the trailing edge.
Original full strings remain in accessibility semantics; typography and meters are unchanged.

Validation on the integrated audit checkout:

- `ktlintCheck`, `:app:assembleDebug` and `:app:assembleDebugAndroidTest` passed.
- Debug APK SHA-256: `5e5230462cee2a69400eda03c1f9b742e4434bf234d88e6a9622e3c08f5ab4ca`.
- Installed over the existing app on the authorized Android 14/API 34 phone.
- Visually checked internal and SD-card headers on Home and Storage in portrait.
- All four selected navigation/recreation instrumentation tests passed in 5.883 seconds.

Captures and logs are in ignored `build/audit-tools/device-storage-card-layout/`;
build output is `build/audit-tools/storage-card-layout-build.log`.
This is a focused layout/surface check, not full release acceptance.
