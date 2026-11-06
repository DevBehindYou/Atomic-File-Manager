> Source inventory checked 19 October 2026: see [Current architecture](architecture/CURRENT_ARCHITECTURE.md) and [Modernization validation](testing/MODERNIZATION_VALIDATION.md). The app uses manual `AppContainer` wiring, a custom `AtomicRoute` stack, Room schema v4 alongside SQLiteOpenHelper stores, and SharedPreferences. Coil 3 and Media3 are present and used. Hilt is declared, but no Hilt application/component graph is active. DataStore and Navigation Compose are not declared.

# Technology Stack

**Rule: every dependency must earn its place.** Each entry below states what it does, why
the platform alternative is insufficient, and what we would do if it were removed. Anything
that cannot answer those three questions is not added.

The dependency rationale below preserves the original **August 2026 design intent**;
it is not a list of installed libraries or a claim that each proposed integration exists.
In particular, Navigation Compose, DataStore, Haze, WorkManager, Kotlinx Serialization,
Turbine, MockK, Truth and LeakCanary remain proposals, not app declarations. The checked-in
`app/build.gradle.kts` and `gradle/libs.versions.toml` define the current dependencies;
update exact versions deliberately, not automatically. Current pins include Kotlin 2.2.10,
Compose BOM 2026.04.01, Room 2.7.2, Coil 3.3.0 and Media3 1.8.0.

---

## 1. Core platform

| Dependency | Why | If removed |
|---|---|---|
| **Kotlin** (2.x, K2) | Mandated. Coroutines, sealed types, null safety. | N/A |
| **Jetpack Compose** (BOM, 1.10.x line) | Declarative UI is required for the tier-swappable glass system and adaptive layouts. XML would make the material system unmaintainable. | N/A |
| **Compose Material 3** | Baseline components, theming, dynamic colour, motion specs. We override the *material*, not the *system*. | Rebuild sheets, dialogs, snackbars by hand |
| **AndroidX Activity Compose** | `ComponentActivity`, `enableEdgeToEdge()`, predictive back plumbing. | N/A |
| **Kotlin Coroutines + Flow** | The entire concurrency and streaming model. | N/A |
| **Navigation Compose** (proposed) | Original rationale: type-safe routes, predictive back integration and deep links. The app currently uses its own `AtomicRoute` stack. | Retain the current custom stack |

## 2. Data and persistence

| Dependency | Why | If removed |
|---|---|---|
| **Room** | Favourites, recents, search index, operation history. SQLite with compile-time verified queries and Flow observation. | Raw SQLite — more code, no compile-time checks |
| **DataStore (Preferences)** (proposed) | Coroutine/Flow-based settings persistence. The implemented settings repository wraps SharedPreferences. | Retain the current repository |
| **AndroidX DocumentFile** | SAF tree traversal. **Used carefully:** `DocumentFile.listFiles()` is notoriously slow because it re-queries per child. We use raw `DocumentsContract` + a single bulk `ContentResolver.query()` for listing, and `DocumentFile` only for one-off operations. | Write the `DocumentsContract` wrapper ourselves (we largely do anyway) |
| **Kotlinx Serialization** | Navigation type-safe routes, cached scan results. | Manual parcelling |

## 3. Dependency injection

| Dependency | Why | If removed |
|---|---|---|
| **Hilt** (declared, graph inactive) | Original proposal: generated ViewModel injection and scoped dependencies. Current composition and test substitution use manual `AppContainer` wiring. | Retain the manual container; no migration is required by this inventory |

The original design considered and rejected Koin in favor of Hilt's compile-time graph.
That rationale does not describe the implemented manual graph.

## 4. Images and media

| Dependency | Why | If removed |
|---|---|---|
| **Coil 3** | Thumbnail loading with memory + disk cache, Compose-native, coroutine-based, supports custom fetchers — we need one for SAF `content://` and one for APK icons. | Hand-rolled decode pipeline; loses caching and lifecycle correctness |
| **Media3 (ExoPlayer)** | Video and audio preview. The implemented player uses `media3-exoplayer` + `media3-ui`, with `PlayerView` hosted in Compose; no session/cast modules are declared. | Hand off everything to an external player — acceptable degradation but poor UX |
| **`ThumbnailUtils` / `ContentResolver.loadThumbnail`** (platform) | Preferred thumbnail source on API 29+; Coil fetches through it. | Full decode of every image — unacceptable |
| **`PdfRenderer`** (platform, API 21+) | V1 PDF preview. No dependency needed. | Drop PDF preview |

## 5. Graphics / Liquid Glass

| Dependency | Why | If removed |
|---|---|---|
| **Haze** (`dev.chrisbanes.haze`, Apache-2.0) | Backdrop capture and blur that already picks the right implementation per API level (scrim on old versions, layered graphics layers around API 32, `RuntimeShader` on 33+). Reimplementing this correctly is weeks of work and a permanent maintenance burden. | Implement backdrop capture with `rememberGraphicsLayer()` on API 34+, `RenderEffect` on 31–33, scrim below. Roughly 600 lines, and worse on the versions we care least about. |
| **AGSL / `RuntimeShader`** (platform, API 33+) | Our own refraction shader sits on top of the captured backdrop. This is the part that must be ours. | Tier A collapses into Tier B |
| **`androidx.graphics.shapes`** | Optional: rounded polygon morphing for the nav indicator. Only if the shape morph is kept. | Use `RoundedCornerShape` + scale animation |

**Rejected:** any WebView-based glass, any OpenGL/Vulkan custom renderer, RenderScript
(deprecated since API 31).

## 6. Background work

| Dependency | Why | If removed |
|---|---|---|
| **Foreground `Service` + coroutines** (platform) | User-initiated file operations must run now, report byte-level progress, and be cancellable instantly. `foregroundServiceType="dataSync"`. | Operations die when the app backgrounds — unacceptable |
| **WorkManager** | Only for deferrable maintenance: search index refresh, thumbnail cache trim, storage rescan. Survives reboot, respects Doze. | Run maintenance on next app open — acceptable but worse |

The design proposed both for different jobs; the implemented operation runner uses a
foreground service and coroutines. WorkManager is not declared. Historical rationale is in
`architecture/FILE_OPERATIONS.md` §2.

## 7. Archives

| Dependency | Why | If removed |
|---|---|---|
| **`java.util.zip`** (platform) | MVP ZIP create/extract. Streaming, zero dependency, adequate. | N/A |
| **Apache Commons Compress** (implemented) | TAR, TAR.GZ, and other supported archive formats, with XZ declared separately. Pure Java. | Drop formats beyond the platform ZIP implementation |
| **zip4j** (implemented) | Password-protected ZIP support. | Drop encrypted ZIP |
| **7-Zip-JBinding / native p7zip** | **Rejected** for MVP and V1: native libs, ABI splits, 16 KB page-size work, and a large attack surface for a niche format. |

## 8. Testing

| Dependency | Why |
|---|---|
| **JUnit 5** + **Turbine** | Unit tests and Flow assertions |
| **`de.mannodermaus:android-junit`** (Gradle plugin) | AGP's Android unit-test task doesn't run the JUnit Platform natively — this plugin bridges it. **Added in Phase 1**, not part of the original spec; see roadmap/PHASE_1_NOTES.md for the version chosen and why. |
| **MockK** | Kotlin-native mocking |
| **Robolectric** | Fast JVM tests for anything touching light Android classes |
| **Compose UI Test** | Semantics-based UI tests |
| **AndroidX Test / Espresso** | Instrumented flows and permission grants |
| **Truth** | Readable assertions |
| **`androidx.benchmark` (macro)** | Start-up and scroll benchmarks; also generates the baseline profile |
| **JankStats** | Runtime frame timing, feeds the glass tier watcher |

## 9. Tooling

* **Gradle version catalogs** (`libs.versions.toml`) — single source of dependency truth.
* **ktlint + detekt** — style and complexity gates in CI.
* **Android Lint** with custom rules: no `java.io.File` in `domain`/`ui`, no raw API-level
  integers, no `runBlocking` outside tests.
* **R8** and resource shrinking are enabled for release. ProfileInstaller and a benchmark/profile generator are present; no measured app baseline profile is committed.
* **LeakCanary** was proposed for debug builds; it is not currently declared.

## 10. Explicitly rejected

| Rejected | Reason |
|---|---|
| RxJava | Coroutines cover everything; two async models is a maintenance tax |
| Retrofit / OkHttp / Ktor | Historical MVP choice: no general HTTP client. Current user-directed networking uses platform HTTP/sockets, sshj and smbj; Wi-Fi sharing runs a local server |
| Firebase Analytics / Crashlytics | Not declared. An opt-in crash-reporting flavour was proposed but is not implemented; see `PRIVACY.md` for current behavior |
| Timber | Not declared. The original proposal favored a structured logger with operation IDs and redaction; no universal redaction layer has been verified |
| Glide / Picasso | Coil is Compose-native and Kotlin-first |
| Accompanist (most) | Almost all of it has graduated into Compose proper |
| RenderScript | Deprecated from API 31 |
| Any theme/icon-pack framework | Dilutes the design identity |

## 11. Current Gradle configuration summary

The Android convention plugin sets Java source/target compatibility to 21. CI also uses
JDK 21. This excerpt summarizes that configuration; it is not a proposed Java 17 migration.

```kotlin
android {
    compileSdk = 36
    defaultConfig {
        minSdk = 27
        targetSdk = 36
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
```

The checked-in CI builds against SDK 36 and has a manual API 34 emulator job. A job
compiling against the newest SDK was proposed but is not configured.
