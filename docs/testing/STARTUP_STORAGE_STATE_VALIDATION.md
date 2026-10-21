# Startup storage readiness — U04 first slice

Implementation prepared 21 October 2026 against feature-plan checkpoint `12f007d`.

## Behavior

Previously the shell initialized storage access to `false` and volumes to an empty list before its asynchronous read. Home could briefly advertise denied access even though the phone's existing grant was valid.

The shell now owns a `StorageReadinessLoader` with explicit Loading, Ready and Failed states. The access check and volume enumeration run together on injected IO and publish one snapshot. Exceptions become a retryable failure rather than permission denial. A newer refresh cancels an older request; cancellation and revision checks prevent late publication.

Before the first successful read, Home/Storage show checking or failure/retry content. Files and unrelated Settings remain usable. Settings shows Checking access or Access check unavailable instead of an unverified denial; its access row retries the read until status is known. A confirmed denied snapshot retains the existing permission rationale and request behavior.

During later resume/media/permission refreshes, the last successful snapshot stays available, preserving screen composition and scroll state. Home labels refreshing/failure, hides its denial banner while the check is unresolved, and exposes Retry when needed. Settings also labels unresolved refresh state explicitly.

Existing mount broadcasts, permission result refreshes, pending volume navigation and backend permission behavior remain in place. No minimum SDK, permission model, user files, settings schema or journal was changed.

## Validation

Source regression coverage prepared:

- Five loader cases: unresolved initial state and atomic successful publication; failure versus actual denial and retry; previous-snapshot retention on refresh/failure; superseded queued reads and owner cancellation; blocking IO off the caller thread with an old cancelled result unable to replace a newer result.
- Home Compose regression: loading/failure does not offer Grant access, Retry and existing volume navigation still dispatch correctly, and a confirmed denial restores the permission action.
- Existing Home and Settings Compose cases remain applicable.

Root completed serialized host validation: `build/audit-tools/startup-storage-complete-validation.log` and its exit marker record success in 7m 45s. The 13 focused cases (loader 5, Home 4, Settings 4), ktlint, detekt, debug APK and debug Lint passed. Tasks:

```text
ktlintFormat ktlintCheck detekt :app:testDebugUnitTest
  --tests '*StorageReadinessLoaderTest'
  --tests '*HomeScreenComposeTest'
  --tests '*SettingsScreenComposeTest'
:app:assembleDebug :app:lintDebug
```

The debug APK SHA-256 was `d1fbe0825b71307db16244974e3d1e5190ac9f135ea5e75e809e4eece347279e`. The authorized API34 phone passed cold launch → Home → Settings (visible Granted status) → Home, with its existing all-files grant preserved. Captures are under `build/audit-tools/device-preview-tools/startup-storage-*`; the captured runtime-error log was empty. No real user files were changed. This is a surface check, not a comparable performance baseline.

PR #62 at `963a96b` passed full current-head CI, independent review and a clean trial merge, then merged into `codex/feature-upgrade-base` as `08541851c0ab7f8c32ed768e89558a791a93f321`. This is feature integration, not main/release acceptance.

Permission revocation/return, media mount/unmount and recreation checks remain pending. The broader U04 primary-tab ownership/refactor work is separate; this first slice addresses loading-state truthfulness.
