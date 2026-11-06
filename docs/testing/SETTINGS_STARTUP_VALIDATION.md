# Settings startup IO — 19 October 2026

This follows the three remaining cold-start disk-read warnings found during the
[preference IO retest](PREFERENCE_IO_VALIDATION.md). The existing settings repository
was constructed from Compose on Main. Implementation checkpoint: `5705606`.

## Behavior

The activity now initializes the existing process-shared settings repository on an
injected IO dispatcher. Normal app content is composed only after the repository is
fully loaded; the splash remains until that themed composition commits. There is no
temporary default `AppSettings` snapshot and no late startup write that can replace
an early user change.

Activity destruction cancels its wait. Explicit cancellation checks reject late IO
success and failure. An ordinary load failure releases the splash and shows a
separate retry/close screen, without reading or editing default settings or exposing
raw exception details. Repeated retries cannot start overlapping loads.

Pending notification/shortcut requests are saved across recreation, including the
explicit null after consumption. The app acknowledges the actual handled request,
so an older acknowledgement cannot clear a newer target. Repository keys, formats,
normalization, update semantics and headless consumers remain unchanged.

## Verification

At checkpoint `5705606`, all **712 app JVM/Robolectric tests** and **28 custom
Lint-rule tests** passed, along with formatting, detekt, debug APK and debug
instrumentation APK packaging. A manual API34 Home → Files → Storage → Settings →
Home run recorded no StrictMode disk-read warnings, fatal exceptions or ANRs.
The notification/recreation and saved-theme device tests subsequently passed in the 13-case API34 suite on `9f75b3e`; see [current evidence](FILES_WORKSPACE_VALIDATION.md). Seventeen new host cases cover IO
construction and preference reads, saved theme/color/authentication on the first
ready value, updates before first composition, shared initialization, cancellation,
retry, failure controls and pending-intent restoration. They do not by themselves
prove the first rendered phone frame or every possible incoming-intent interleaving.

Initial checks caught a long line and an invalid Compose assertion import; both were
corrected. No tests were skipped or disabled. Logs are retained under ignored
`build/audit-tools/settings-startup-*.log` with exit markers.

The broader provider, mutation, recovery, benchmark and production-signing gates
remain separate work. Existing settings updates still use `SharedPreferences.apply()`;
this change does not add cross-process transactions or synchronous disk durability.

API contracts consulted: [splash readiness](https://developer.android.com/reference/androidx/core/splashscreen/SplashScreen)
and [coroutine cancellation on dispatcher return](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines/with-context.html).
