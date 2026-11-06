> **28 October 2026 platform decision:** the owner requested Android 9 / API 28 as the application and benchmark minimum. Android 8.1 / API 27 is no longer supported by new builds. Earlier dated API27 audit and test evidence remains historical; this decision does not establish MEGA integration or new runtime acceptance.

# Atomic File Manager

> **17 October 2026 modernization audit:** start with the [verified current architecture](docs/architecture/CURRENT_ARCHITECTURE.md), [audit findings](docs/architecture/PROJECT_AUDIT.md), [implementation roadmap](docs/roadmap/OPTIMIZATION_ROADMAP.md) and [live verification status](docs/roadmap/IMPLEMENTATION_PROGRESS.md). The September first-build notes below are historical: the UI is implemented, all three Gradle modules are enabled, Java 21 is required, and manual AppContainer wiring is active. Historical test counts do not certify the audit branch. Production signing and device acceptance remain open.

> Formerly **Refract**. Renamed on 4 October 2026 (app name, `applicationId` and Kotlin package `com.devbehindyou.atomicfilemanager`). The repository keeps its old name. Part of the DevBehindYou Atomic family; see `docs/design/ATOMIC_DESIGN_SYSTEM.md`.

A native Android file manager (Kotlin, Jetpack Compose, Hilt, coroutines). Built by an AI
assistant against the specification in `docs/`, phase by phase, following
`docs/roadmap/IMPLEMENTATION_ROADMAP.md`.

**Open this in Android Studio**, not Google's "AI Studio" (a separate, web-based
generative-AI tool that can't open or build a Gradle project at all).

## Current status — 15 September 2026

Start with [the AI handover](docs/ai-handover/INDEX.md) (protocol AI-HANDOVER/V2) for the current state, verification evidence and pending work.

The app has compiled successfully. Earlier verification passed 238 app tests and 22 custom lint tests, debug/release assembly and Android Lint with zero errors. A later mobile build passed 12 physical-device tests. These passes apply to the versions identified in [the verification report](docs/testing/VERIFICATION_REPORT.md) and [mobile verification](docs/testing/MOBILE_LAYOUT_VERIFICATION.md).

The latest category collections, private-files routing, edge-snapping bubble and further layout changes are in the working tree but still await a completed build and device verification. Disk/RAM pressure interrupted the latest build attempt. The app is not fully verified or release-ready.

The runtime graph is assembled by AppContainer; Hilt is declared but is not the active wiring mechanism. The benchmark module is disabled. Roadmap documents describe intended scope and must not be read as proof of implementation or acceptance.

## Historical first-build notes

The notes below were written before the successful verification sessions. Version guesses and phase-status statements are historical; consult the current version catalog and linked verification documents first.
## First build: check these, in this order

Ranked by how likely each one is to actually break something, not by how interesting it is.

1. **`gradle/libs.versions.toml`** — every version is tagged `[CONFIRMED]` or
   `[BEST-EFFORT]` inline. If the build fails on dependency resolution, this file is the
   first place to look, before anything else. Two specific entries are the most likely
   culprits:
   - `lintApi` (`32.3.0`) — extrapolated from a historical "AGP version + 23 major"
     pattern confirmed only through AGP 8.x, not independently verified for AGP 9.x.
   - The `androidJunit5Plugin` **plugin ID itself**
     (`de.mannodermaus.android-junit`, not the older `-android-junit5`) — a plugin
     rename is exactly the kind of thing that produces a clean, easy-to-fix
     "plugin not found" error.
2. **AGP 9's built-in Kotlin support**, used in
   `build-logic/convention/src/main/kotlin/atomic.android.application.gradle.kts`. This
   project targets AGP 9.3.0, which folds Kotlin support into `com.android.application`
   directly rather than needing a separate `org.jetbrains.kotlin.android` plugin — a
   change that happened after this assistant's reliable knowledge cutoff (Jan 2026) and
   was confirmed only via a live search, not direct experience. If
   `kotlin { jvmToolchain(17) }` fails to resolve there, `compileOptions` in the same file
   sets Java 17 as a fallback regardless.
3. **The Gradle wrapper jar** (`gradle/wrapper/gradle-wrapper.jar`) was generated locally
   using an old (2017-era) Gradle 4.4.1 install, since this sandbox couldn't reach
   `services.gradle.org`. It should still correctly bootstrap-download the real Gradle
   9.3.0 distribution — the wrapper protocol has stayed compatible for years — but run
   `./gradlew wrapper --gradle-version 9.3.0` once you have real network access, to
   regenerate it from a genuine current template.
4. **The `:benchmark` module** (`com.android.test`) is the newest, least-certain piece of
   tooling in the project — deliberately *not* using the same convention plugin as `:app`
   (see `docs/roadmap/HARDENING_PASS_1_NOTES.md` §3 for why), and its
   `experimentalProperties["android.experimental.self-instrumenting"]` flag is included
   from general recollection, not confirmed against current tooling. If anything fails to
   sync, this module is a reasonable first suspect.

## Once it compiles: real open gaps, not just "unverified"

These aren't compilation risks — they're places where a deliberate, honest limitation or
an actual undecided design question remains, even assuming everything above resolves
cleanly.

- **`MediaStoreBackend` cannot fully satisfy the shared `StorageBackendContractTest`.**
  MediaStore has no concept of an empty, explicitly-creatable directory — `createDirectory`
  honestly fails rather than fake one. This is a real, undecided conflict with Phase 3's
  AC1, found while implementing, not resolved. See `docs/roadmap/PHASE_3_NOTES.md` §3.
- **No `SafBackendContractTest` or `MediaStoreBackendContractTest` exist.** Building a
  reliable fake `ContentProvider`/`DocumentsProvider` for Robolectric was judged too
  large and too uncertain to execute correctly without being able to test it. What exists
  instead (`SafUriMappingTest`, `MediaStoreIdMappingTest`) tests the riskiest logic — URI
  and id parsing — directly, but full read/write/list/delete behavior against a real
  provider is unverified. See `docs/roadmap/PHASE_3_NOTES.md` §4.
- **Two acceptance criteria are structurally outside what any AI assistant working in a
  sandbox could verify:** Phase 3 AC2 (instrumented tests across 5 real API levels) and
  AC4 (a measured <100ms streaming latency). Both need a real device or emulator.
  Scaffolds exist for both; neither has ever actually run.
- **`RecoverableSecurityException`'s `IntentSender` has no representation in the
  `FileError` model.** `MediaStoreBackend` maps it to
  `FileError.PermissionDenied(AccessTarget)` as the closest existing signal, which loses
  the actual `IntentSender` a caller would need to launch to resolve it.
- **`OutputTarget.sync()` is a no-op in all three backends** — no durable-write guarantee
  is actually provided despite the interface exposing the method.
- **`StorageVolumes.enumerate()`'s pre-API-30 path** is a positional-match heuristic
  between `StorageManager.storageVolumes` and `Context.getExternalFilesDirs()`, not a
  documented contract — `ANDROID_STORAGE_RESEARCH.md` §10 lists exactly this kind of OEM
  variability as still open pending Phase 0.
- **`FileError`'s per-variant `severity`/`recoverable`/`action` mapping** (22 variants) is
  this project's own judgment call in roughly 15 of them — `ERROR_MODEL.md` gives a
  message-mapping table but doesn't assign these three properties per variant explicitly.
  Worth a real review once the screens that consume it exist.
- **Five supporting domain types have no concrete spec anywhere in `/docs`:**
  `AccessTarget`, `NameProblem`, `FailedItem`, `UndoToken`, `Conflict`. Each has a
  minimal, honestly-`Provisional`-marked definition, sufficient to compile and test now,
  not intended as settled design.

## Module map

```
:app            Single application module — domain/, data/, UI (empty beyond MainActivity)
:lint-rules     7 custom Lint checks enforcing the architecture's layer boundaries
:benchmark      Macrobenchmark module (com.android.test) — scaffolded, unrun
build-logic     Convention plugin (compileSdk/minSdk/targetSdk/Compose baseline for :app)
```

Inside `:app/src/main/kotlin/com/devbehindyou/atomicfilemanager/`:

```
domain/model/       FileNode, FileNodeId, FileError, FileResult, Operation, SortSpec, ...
domain/repository/  StorageBackend interface (the abstraction all three real backends implement)
data/backend/       FileSystemBackend, SafBackend, MediaStoreBackend, StorageBackendSelector
data/mapping/       Throwable -> FileError exception boundary
data/volume/        StorageManager-based volume enumeration
data/di/            Hilt @Module wiring the three backends into a BackendType-keyed map
```

Test fakes (`InMemoryBackend`, four fault-injecting decorators) live under
`:app/src/test/.../domain/testing/` — test-only, never shipped.

## Further reading

Each phase's full reasoning, every smaller decision, and everything flagged above in more
depth:

- `docs/roadmap/PHASE_1_NOTES.md` — Gradle/tooling setup, the 7 Lint rules
- `docs/roadmap/PHASE_2_NOTES.md` — the domain model
- `docs/roadmap/HARDENING_PASS_1_NOTES.md` — decorator tests, locale-aware sort, `:benchmark`
- `docs/roadmap/PHASE_3_NOTES.md` — the three real backends, including real bugs caught
  during review (worth reading even just for what *not* to trust yet)

The full original specification this project was built against is in `docs/` — start with
`docs/README.md` and `docs/roadmap/IMPLEMENTATION_ROADMAP.md` if you want the source of
truth for what each phase was actually asked to deliver.
