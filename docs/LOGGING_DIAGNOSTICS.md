# Logging and Diagnostics

Source review: 19 October 2026. This document separates implemented diagnostics from future requirements. The earlier structured-logger specification was a design target, not a description of the running app.

## Implemented logging

| Source | Behavior | Scope |
|---|---|---|
| `ui/util/DebugJank.kt` | `Log.w` under tag `AtomicJank` reports janky-frame duration and `frame.states`. | Enabled only when `ApplicationInfo.FLAG_DEBUGGABLE` is set. No app calls adding file names or paths to performance states were found. |
| `AtomicApp.kt` | StrictMode logs main-thread disk/network access and leaked closeable/SQLite objects through `penaltyLog()`. | Enabled only for a debuggable application. Platform-generated diagnostic details are not passed through an app redactor. |
| `service/FileOperationService.kt` | `Log.w` reports an `IllegalStateException` when the foreground operation service cannot be started. | Not guarded by a debug flag. The call includes the exception and stack trace; the start Intent contains only the service component. |

These were the direct logging sites found in `app/src/main` by searching for Android Log calls, print/stack-trace calls, and logger APIs. No direct call logging a password, session token, file contents, or full user path was demonstrated. This is a bounded source review, not proof that platform or transitive-library logs can never contain sensitive data.

There is no implemented structured `Logger` interface, automatic name/path hashing, release redaction test suite, 500-event ring buffer, Settings diagnostics export, crash-report consent flow, or crash-upload build flavour. The main manifest includes network permissions for the storage and sharing features described in [Privacy](PRIVACY.md).

## Persistent operation and recovery records

The Room operation journal is application data, not an anonymized diagnostic log. It records full source and destination node IDs, options, status, timestamps, progress, and error text. `OperationQueue.journalStateOf` persists `FileError.toString()` for failures; error fields may contain names and paths. Hidden-file recovery journals also retain original/current locations.

`OperationJournalDao.pruneFinishedBefore` defines a deletion query, but no production invocation was found. There is no implemented 24-hour path redaction or seven-day operation-history retention policy. Recovery metadata must not be described as anonymous or included wholesale in a future support export.

## Performance evidence

- Debug JankStats supplies logcat observations; no production glass-tier demotion based on those observations was found.
- The `:benchmark` module is enabled and contains macrobenchmark code. Device results and performance gates must be reported from actual runs, not inferred from the presence of that module.
- File-operation progress exposes bytes, counts, and speed to the UI. This does not establish a bucketed throughput log or device-performance improvement.
- Debug StrictMode is implemented. The previously proposed developer backend inspector, recomposition overlay, LeakCanary integration, and verbose SAF logger were not found.

See [Performance Baseline](testing/PERFORMANCE_BASELINE.md) for recorded validation and outstanding measurements.

## Requirements for future diagnostics

These requirements are proposed work, not current capabilities:

1. Centralize event logging before adding more call sites. Explicitly classify every field, including exception messages, causes, stack traces, provider IDs, URLs, and network error strings.
2. Exclude passwords, session tokens, file contents, and credentials from all logs. Minimize file names and paths in both debug and release builds. Truncated unsalted hashes of names or paths are not a guarantee of anonymity.
3. Bound retained events and message lengths. Keep logging out of per-buffer copy loops, and avoid eager formatting when a log level is disabled.
4. If a user-controlled support export is implemented, define its exact contents and retention, test it with recognizable secret/path fixtures, and allow inspection before sharing. Persistent recovery records require a separate redaction step.
5. Treat any crash-upload SDK or automatic reporting endpoint as a new feature requiring a documented data flow and an updated privacy review. No such opt-in reporter is currently implemented.

## Review boundaries

This pass inspected application source, manifest declarations, declared dependencies, and component wiring. It did not capture network traffic, inspect every dependency's implementation, certify release logcat output on a device, or verify a store privacy submission. No new telemetry, logging, or production behavior was added by this documentation update.

Kotlin paths above are relative to `app/src/main/kotlin/com/devbehindyou/atomicfilemanager/`.
