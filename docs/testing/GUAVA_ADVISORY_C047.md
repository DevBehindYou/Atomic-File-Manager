# C047 slice 2 — constrain transitive Guava to the fixed release (decision D1)

7 November 2026 (UTC). Author: Claude Code (Claude cloud). Reviewer: Codex. Base `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Branch `codex/claude-c047-guava-advisory`. Follows [C047 slice 1](DEPENDENCY_PROVENANCE_C047.md) (#114, decision D1).

## Advisory

- **GHSA-xxph-c9ww-hj94 / CVE-2026-102554.** Deserializing Guava `CompactHashMap`, `CompactHashSet` or `MapMakerInternalMap` triggers uncontrolled memory allocation.
- **Affected:** `com.google.guava:guava` from 4.0 up to (not including) 33.7.2.
- **Source:** the OSV record, fetched 7 November 2026 from `https://osv-vulnerabilities.storage.googleapis.com/Maven/GHSA-xxph-c9ww-hj94.json` (published 2026-10-27T14:27:53Z). `api.osv.dev` itself was blocked from the cloud session.
- **Fix release:** `33.7.2-android` is on Maven Central (`guava-33.7.2-android.pom` Last-Modified 29 September 2026). `33.8.0-android` also exists. The minimum fixed version was chosen to keep the change to one patch-level cluster.
- **Exposure:** the app never uses `ObjectInputStream` (slice 1 audit). The residual risk is a local out-of-memory crash if any library deserialized attacker-controlled Guava collections. The upgrade removes the advisory from the shipped runtime rather than relying on that reasoning.

## Change

The catalog adds `guava = "33.7.2-android"` with a comment. `app/build.gradle.kts` adds one `constraints { implementation(libs.guava) { because(…) } }` entry next to Media3.

Guava is not a direct dependency: no app code imports it. The constraint only raises the version Media3 1.8.0 resolves. Media3 stays at 1.8.0, and no other catalog version changed.

## Resolved release runtime graph (`:app:dependencies --configuration releaseRuntimeClasspath`)

Both before and after have 205 distinct modules. Exactly three resolved versions change:

| Module | Before | After |
| --- | --- | --- |
| `com.google.guava:guava` | 33.3.1-android | **33.7.2-android** (`33.3.1-android -> 33.7.2-android`, constraint `(c)`) |
| `com.google.guava:failureaccess` | 1.0.2 | 1.0.3 |
| `org.jspecify:jspecify` (annotations) | 1.0.0 | 1.0.1 |

`listenablefuture` stays the `9999.0-empty-to-avoid-conflict-with-guava` placeholder.

## Release packaging (R8)

Both builds used `:app:assembleRelease` with R8 minify and resource shrinking, signed with the local debug key because no release signing properties are present in the cloud session.

| | Before (`89140e3`) | After |
| --- | --- | --- |
| `app-release.apk` bytes | 15,074,414 | 15,074,414 (**0 B delta**) |
| SHA-256 | `6c7c00e349b9f0c311d0627393b749798c611f08c69dc8dff13f6c1b8ead2347` | `a849d00f9fe14c073902fab4120ce79600e19dbe03a9901291b38ff251860439` |
| `classes.dex` / `classes2.dex` | 9,384,388 / 4,279,300 | 9,384,120 / 4,279,328 (−240 B combined) |

R8 completed with no new keep rules and no warnings promoted to errors. These are local debug-signed artifacts for size comparison only. They are not release or beta assets, and they are not published.

## Validation

All runs were local in the cloud container with SDK 36, Gradle serialized under the shared lock.

| Command | Result |
| --- | --- |
| `:app:dependencies --configuration releaseRuntimeClasspath`, before and after | 205 modules each; 3 versions changed (table above) |
| `:app:assembleRelease`, before and after | both pass with R8; 0 B APK delta |
| `:app:ktlintCheck :app:detekt :app:lintDebug` | pass |
| `:app:testDebugUnitTest`, media host suites: `*MediaPreview*`, `*PreviewPlayer*`, `*VideoPreparation*`, `*AudioPreview*`, `*Audio*`, `*Video*` | 53 run, 0 failed, 0 skipped |
| `python3 -m unittest test_beta_transport test_verify_beta` (release tamper guards) | 28 run, OK |
| `bash -n` on `prepare_signed_beta.sh` and `smoke_signed_beta.sh` | OK |

The 53 media tests break down as MediaPreviewHelper 3, MediaPreviewOwnership 12, ProviderVideoThumbnailReader 8, AudioPreparationRecoveryCompose 5, AudioTagText 4, PreviewPlayerAudioPolicy 14, PreviewPlayerLifecycle 4 and VideoPreparationRecoveryCompose 3. They use real Media3 players under Robolectric, which exercise Media3's Guava-backed code paths on the new version. This is not decoded-playback acceptance.

## Remaining

- Hosted CI and Codex review.
- Native audio and video acceptance runs on the media workflows owned by #105/#109/#119 once those merge. No media native workflow exists on base, so this PR is gated on hosted CI (unit tests, Lint, R8 packaging) and the host media suites.
- D2–D5 from slice 1 are unchanged.
