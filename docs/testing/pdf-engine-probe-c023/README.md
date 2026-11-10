# C023 AndroidX PDF probe — reproducible evidence bundle

Supports the "Isolated build probe" section of [PDF_ENGINE_EVALUATION.md](../../plugins/modernization/PDF_ENGINE_EVALUATION.md). Recorded by Claude Code (Claude cloud), 7–8 November 2026. **Provisional measurements**: one probe run per configuration, a keep rule standing in for real use, and an ephemeral cloud container. This was never committed to app source.

## Reproduce

1. Check out `89140e3f3d4a746e6a907912042911cb10c8f6a3` (base for both builds).
2. Apply [probe.patch](probe.patch). It contains three probe-only lines:
   - `implementation("androidx.pdf:pdf-viewer-fragment:1.0.0-beta01")` in `app/build.gradle.kts`;
   - `compileSdkExtension = 19` in `build-logic/convention/src/main/kotlin/atomic.android.application.gradle.kts`;
   - `-keep class androidx.pdf.viewer.fragment.PdfViewerFragment { *; }` in `app/proguard-rules.pro`. This is the keep rule that makes the fragment reachable in place of real use. Without it, R8 strips the unused library and the delta is meaningless.
3. Install SDK packages `platforms;android-36` and `platforms;android-36-ext19`. Build tools: 36.0.0.
4. Run, one Gradle invocation at a time:
   - `./gradlew :app:dependencies --configuration releaseRuntimeClasspath`
   - `./gradlew :app:checkReleaseAarMetadata`, without and with the extension line
   - `./gradlew :app:assembleRelease`

All builds were local release builds signed with the debug key; no release signing properties were present. They are for comparison only and were not published.

## Files

| File | Content |
| --- | --- |
| `probe.patch` | Exact uncommitted probe change (dependency, extension, keep rule) |
| `release-runtime-graph-base.txt` / `-probe.txt` | Raw `releaseRuntimeClasspath` dependency trees |
| `release-modules-base.txt` / `-probe.txt` | Normalized resolved `group:artifact:version` lists (205 and 213 entries) |
| `release-modules.diff` | Resolved-version diff |
| `check-aar-metadata-no-extension.log` | AAR metadata failure: four artifacts require compile SDK extension ≥ 19 |
| `check-aar-metadata-extension-19.log` | Same check passing with `compileSdkExtension = 19` |
| `assemble-release-heap-1536m-excerpt.log` | Failure excerpt: `lintVitalAnalyzeRelease` and `minifyReleaseWithR8` both hit `OutOfMemoryError` under the repository default `-Xmx1536m`; build failed after 28m29s |
| `assemble-release-heap-8g.log` | Same probe passing with `-Dorg.gradle.jvmargs=-Xmx8g` (quiet log) |

Environment proxy settings and local paths were stripped from the logs.

## Artifact hashes (SHA-256)

| Artifact | Bytes | SHA-256 |
| --- | --- | --- |
| Base `app-release.apk` (89140e3, default heap) | 15,074,414 | `6c7c00e349b9f0c311d0627393b749798c611f08c69dc8dff13f6c1b8ead2347` |
| Probe `app-release.apk` (89140e3 + probe.patch, 8 GB heap) | 16,268,942 | `a5d4e328204409a35575618d5a1abf34215650db0a05568c766c713adf9120e6` |
| `pdf-viewer-1.0.0-beta01.aar` | 1,290,390 | `2c94f047f9c2e9bd453180404a102447572c757b09309d69d40482db2caa9c03` |
| `pdf-viewer-fragment-1.0.0-beta01.aar` | 124,652 | `ec71af1b43d2ae8bcfff26946983c602e4dcce85e037efbb82bc39219259b36e` |
| `pdf-core-1.0.0-beta01.aar` | 104,554 | `f18ecee10523dfd9100689c3459e5cb8a161a9416fd3b4ccff6aea43f35beda7` |
| `pdf-document-service-1.0.0-beta01.aar` | 255,612 | `9a89a9fb5f49cea94f7c3daeca0a4a5801dc793f392213ac1663882c84210d8d` |
| `pdf-compose-1.0.0-beta01.aar` | 57,409 | `e8f187c6c40dcaaae980b1d4374bb474a7355eca4c14bdfea7435aca2ac077a1` |

The AARs were fetched from `dl.google.com/dl/android/maven2/androidx/pdf/<artifact>/1.0.0-beta01/`.

## Limits

- The APK delta (+1,194,528 B) is a single provisional measurement with a keep-rule stand-in. A real integration, with the used surface, resource shrinking and a scoped theme, will differ. Re-measure before any adoption decision.
- **Build memory is inconclusive.**
  - Only two heaps were tried: 1.5 GB failed and 8 GB passed. Nothing in between was tried.
  - The 1.5 GB run was serialized with other Gradle builds but ran on a host shared with other agent activity.
  - The unchanged base release build passed at the 1.5 GB default.
  - A practical heap, such as the 3 GB used by hosted workflows, has not been tested.
  - 8 GB is not a recommendation and must not become a default, particularly on the 8 GB owner host.
