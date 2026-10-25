# Native dependency evaluation

Observed26October2026, base4d7157f. No dependency changed. Online current API documentation is not an upgrade instruction. Exact artifact/transitive SBOM/notices/vulnerability/native ABI review remains before adoption/release; source or vendor license is not legal/security certification.

| Existing/candidate | Official evidence and fit | Decision/gates |
| --- | --- | --- |
| Coil3.3.0/ExifInterface1.4.1/current Android bitmap helper | [Coil](https://coil-kt.github.io/coil/) documents downsampling/cache and Apache2license; current viewer uses bounded still bitmap path. | Reuse for geometry, separate animation adapter evaluation/API27fallback/timing/memory. |
| Media3ExoPlayer/UI1.8.0 | [Formats](https://developer.android.com/media/media3/exoplayer/supported-formats) separates format layers; [focus guidance](https://developer.android.com/media/optimize/audio-focus) recommends attributes with focus handling. | Retain engine, verify pinned API/noisy. No new VLC/native codecs; size/ABI/licenses/device/legal costs if later proposed. |
| Platform PdfRenderer/candidate AndroidX PDF | [Platform API](https://developer.android.com/reference/android/graphics/pdf/PdfRenderer), [AndroidX releases](https://developer.android.com/jetpack/androidx/releases/pdf) show API/version evolution. | Preserve raster fallback; no adoption, verify API27search/password and dependency maturity/size/resources. |
| Candidate commonmark-java | [Maintainer AST project](https://github.com/commonmark/commonmark-java), [license](https://github.com/commonmark/commonmark-java/blob/main/LICENSE.txt). | Candidate only; verify exact release Java/Android/BSDnotice/transitives/size and bounded native AST rendering/conformance. |
| Commons Compress1.28.0/XZ1.10 | [Overview](https://commons.apache.org/proper/commons-compress/), [security history](https://commons.apache.org/proper/commons-compress/security.html). | Keep wired adapters/guards; review exact resolved artifacts/advisories/notices. No universal format/security clearance. |
| zip4j2.11.6 | [Maintainer](https://github.com/srikanth-lingala/zip4j). Library capabilities exceed wired passwordZIPread. | Staged-publication/encryption/API27/size/license fixtures before new writes. |
| junrar8.1.1 | [Maintainer](https://github.com/junrar/junrar). | Read-only, actual pinned RAR/RAR5 fixtures and licence restrictions; recognition does not prove variants. |
| Office/RTF/EPUB, grammar/tag engines | No engine selected. Flutter/Dart leads are desired capabilities, not native selections. | Compare maintained native/JVM API27feature/security/license/size costs before addition. |

APK/installed-size/RAM/CPU/battery not measured. Next Image geometry adds no dependency.