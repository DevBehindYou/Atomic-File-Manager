# Logging and Diagnostics

> **Status, 17 October 2026:** design specification. The structured Logger, redaction layer, ring buffer and export flow described below were not found in the current implementation. Debug StrictMode and JankStats exist; the benchmark module is enabled but measured device results are pending. Network permissions are present for user-directed storage/sharing, so the offline-flavour statement below is historical. Do not claim the listed diagnostics or privacy guarantees as implemented.

Goal: diagnose a user's problem from a bug report **without** learning anything about their
files.

## 1. Structured logger

```kotlin
interface Logger {
    fun d(tag: LogTag, event: String, vararg fields: Pair<String, Any?>)
    fun i(tag: LogTag, event: String, vararg fields: Pair<String, Any?>)
    fun w(tag: LogTag, event: String, error: FileError? = null, vararg fields: Pair<String, Any?>)
    fun e(tag: LogTag, event: String, throwable: Throwable? = null, vararg fields: Pair<String, Any?>)
}

enum class LogTag { STORAGE, OPERATION, PERMISSION, SEARCH, GLASS, PREVIEW, ARCHIVE, NAV }
```

Log lines are events with fields, never prose:

```text
2026-08-27T11:04:12.881Z  I  OPERATION  op.started
    opId=7c1f  type=COPY  items=340  bytes=2147483648
    srcVolume=internal  dstVolume=sdcard  backend=FILE→SAF
```

Prose logs ("copying files now…") cannot be filtered, aggregated, or diffed. Events can.

## 2. Operation IDs

* Every `FileOperation` carries an 8-char `opId` from creation to completion.
* Every log line touching that operation includes it.
* The `opId` is shown in the operation card's detail view and in the exported diagnostics,
  so a user's report can be tied to a single trace.
* Search sessions and storage scans get the same treatment (`searchId`, `scanId`).

## 3. Redaction — the hard rule

| Data | Debug build | Release build |
|---|---|---|
| File name | plaintext | `sha256(name).take(8)` |
| Full path | plaintext | `sha256(path).take(8)` + depth + volume type |
| Volume label | plaintext | volume **type** only (`internal` / `sdcard` / `usb`) |
| MIME type | plaintext | plaintext (not identifying) |
| File size | plaintext | bucketed (`<1MB`, `1-10MB`, `10-100MB`, `>100MB`) |
| Extension | plaintext | plaintext (not identifying) |
| File contents | never | never |
| SAF URI | plaintext | authority only |

Redaction is applied by the logger itself, not by call sites — a call site that forgets is a
leak, and there are hundreds of call sites.

```kotlin
// enforced: the logger's field encoder redacts by type
@JvmInline value class FileName(val value: String)   // always redacted in release
@JvmInline value class FilePath(val value: String)   // always redacted in release
```

A unit test feeds known paths and names through the release logger and asserts they do not
appear in the output.

## 4. In-memory ring buffer

* The last **500 events** are held in memory (roughly 60 KB) regardless of build type.
* Exported by the user from Settings → About → Export diagnostics, producing a `.txt` in
  Downloads that the user can inspect before sharing. **Nothing is sent automatically.**
* The export includes: app version, Android version, device model, glass tier, access level,
  volume types and their mount state, and the ring buffer.
* The export explicitly does **not** include: file names, paths, or the contents of anything.

## 5. Crash reporting

* Opt-in, off by default, and only present in the crash-reporting build flavour. The
  base/F-Droid flavour has no `INTERNET` permission, so the guarantee is structural.
* A crash report contains: stack trace, app/OS/device version, glass tier, `opId` of any
  in-flight operation, and the last 50 redacted events.
* Custom keys attached to every report: `accessLevel`, `glassTier`, `activeOperationType`,
  `volumeTypes`, `apiLevel`.
* The reporter runs the same redaction pass as the logger.

## 6. Performance metrics

Collected locally, never transmitted:

| Metric | Source | Use |
|---|---|---|
| Frame timing | `JankStats` | Feeds `GlassCapabilityManager` tier demotion |
| Cold start | `reportFullyDrawn()` + macrobenchmark | Regression gate in CI |
| Folder open latency | Manual trace around `observeDirectory` | Regression gate |
| Copy throughput | Executor, bytes/second | Shown to the user; logged bucketed |
| Shader compile time | First `RuntimeShader` construction | Tier A eligibility on unknown GPUs |
| Scan duration | Storage analysis | Cached with the result |

## 7. Debug-only tools

Compiled out of release via `BuildConfig.DEBUG` and R8, not a runtime flag:

* A developer screen showing the resolved glass tier and the inputs that produced it.
* A backend inspector listing each volume, its selected backend, and its `AccessFlags`.
* A recomposition counter overlay for the hot components.
* LeakCanary.
* Verbose SAF query logging.

## 8. Rules

1. No `Log.d` calls anywhere. Use the `Logger` interface, always.
2. No user-facing string is ever logged (they are localised and unstable).
3. No `printStackTrace()`.
4. Never log inside a tight loop — the copy loop logs at start, at completion, and on error
   only.
5. Logging must never allocate on a hot path; field varargs are only evaluated when the level
   is enabled.
6. Any new field added to a log line must be classified in the redaction table above.
