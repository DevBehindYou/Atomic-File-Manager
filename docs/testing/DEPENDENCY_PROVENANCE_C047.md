# Dependency and build provenance audit — C047 slice 1

Claude Code task **C047** (M12/U13). Base: feature integration `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Recorded 7 November 2026 UTC on a Linux host with OpenJDK 21.0.12 and the Android SDK 36 platform. This is an audit record with one CI hardening change. No dependency version changed.

## Summary

| Area | Finding | Decision in this PR |
| --- | --- | --- |
| Release runtime graph | `:app:releaseRuntimeClasspath` resolves 205 distinct module versions, all of which resolved without failure. Every one has a licence in its POM or in a parent POM. | Recorded below. No change. |
| Runtime advisories | One advisory matches a shipped artifact: Guava `33.3.1-android` (brought in by Media3 1.8.0), GHSA-xxph-c9ww-hj94 / CVE-2026-102554, fixed in 33.7.2. The app makes no direct Java-deserialization call. | Not upgraded here. Recorded as decision **C047-D1** (next candidate cluster). |
| Build-time advisories | Eleven advisory matches across seven artifacts on the Gradle plugin classpath (AGP/Kotlin tooling). None ship in the APK. | Recorded as decision **C047-D2**. No change. |
| Gradle wrapper | `distributionSha256Sum` is already present and matches Gradle's official checksum. The checked-in `gradle-wrapper.jar` matches the official 9.3.1 wrapper checksum. CI validates the wrapper jar on every Gradle job, because `setup-gradle` defaults to `validate-wrappers: true`. | No change needed. Open PR #49 asked for the same pin, which base already contains (Codex reconciles #49). |
| GitHub Actions pinning | All 53 `uses:` lines in the 8 workflows reference floating major tags (`@v4`, `@v2`). The floating tags are moved by hand: `gradle/actions@v4` points at v4.4.3 while v4.4.4 also exists. | **Changed:** in `ci.yml` only, the two third-party actions are now pinned to the full commit SHAs their tags resolve to today, so the code that runs is the same. |
| Licences and notices | The release graph includes MIT, Bouncy Castle (MIT-style), 0BSD and UnRar-licensed code as well as Apache-2.0. The About sheet credits only the fonts and Material Icons. The catalog comment calls Shizuku "Apache 2.0", but its POM and upstream LICENSE are MIT. | Recorded as decision **C047-D3** (product/legal). No change. |

## Commands and evidence

All Gradle runs used the shared lock: `flock …/gradle.lock bash ./gradlew --no-daemon -q …`.

| Command | Result |
| --- | --- |
| `:app:dependencies --configuration releaseRuntimeClasspath` | Exit 0 in 16.6 s. 1,043 report lines, 0 `FAILED`. Report SHA-256 `8818fe7d49e2894ab2a538f187e8848e7c1a4d883df67f0f369874437176d618`. 205 distinct `group:artifact` keys, each with one selected version. Normalised list SHA-256 `2c9cb06fbe8d87a0ca14f511c45a665ee43b76bab787aa0b90153448d7d6ef0f`. |
| `:app:buildEnvironment` | Exit 0. 123 distinct plugin-classpath modules. Report SHA-256 `2a20ebddc2ee6e6aff1d1199dbfd2fff20af252a21a69d39f7cc30975fc17657`. |
| Licence extraction from cached POMs (`~/.gradle/caches/modules-2`), following `<parent>` up to 4 levels | 205/205 determined. 197 directly from the artifact's POM, 8 from a parent POM (Guava ×3, commons-codec, commons-io, commons-compress, commons-lang3, slf4j-api). |
| OSV Maven export `https://osv-vulnerabilities.storage.googleapis.com/Maven/all.zip` (Last-Modified Fri, 06 Nov 2026 18:17:58 GMT; 10,527,068 bytes; MD5 `tBTFwMmGoGB523xfV3bkzQ==` matched the `x-goog-hash` header; SHA-256 `6524cee5275c327d01e8ff51a2d3772baea5ec5ee27b807870834f1bbfc445a9`; 7,253 advisories) | Offline match of each resolved version against `ECOSYSTEM` ranges and explicit `versions` lists, skipping withdrawn entries. Runtime: 1 hit. Build classpath: 11 hits across 7 artifacts. |
| Positive control for the matcher (known-vulnerable versions: commons-compress 1.21, bcprov 1.77, guava 31.1-android, junrar 7.4.0) | 20 advisories reported, including the known commons-compress, Bouncy Castle and junrar GHSAs. So "0 hits" means the advisory data has no match, not that the matcher is blind. xz 1.9 produced no hit. |
| `curl https://services.gradle.org/versions/all` (official Gradle version API), entry `9.3.1` | `checksum` `b266d5ff6b90eada6dc3b20cb090e3731302e553a27c5d3e4df1f0d76beaff06` equals `distributionSha256Sum`. `wrapperChecksum` `b3a875ddc1f044746e1b1a55f645584505f4a10438c1afea9f15e92a7c42ec13` equals `sha256sum gradle/wrapper/gradle-wrapper.jar`. Build time 2026-01-29, commit `44f4e8d3`. The direct `.sha256` files redirect to `downloads.gradle.org`, which this session could not reach (HTTP 403 at the proxy). |
| `git ls-remote https://github.com/<owner>/<repo>.git refs/tags/<tag> 'refs/tags/<tag>^{}'` for each action | See the Actions table. Re-run after the edit: both pinned SHAs still equal the peeled tags (`MATCH`). |
| `raw.githubusercontent.com/gradle/actions/ed408507…/setup-gradle/action.yml` | `validate-wrappers: … default: true` ("automatically validate all wrapper jars found in the repository … If the wrapper checksums are not valid, the action will fail"). |
| `python3 -B scripts/release/test_beta_transport.py` | 12 tests OK. |
| `python3 -B scripts/release/test_verify_beta.py` | 16 tests OK. |
| `bash -n scripts/release/prepare_signed_beta.sh scripts/release/smoke_signed_beta.sh` | OK. |
| `yaml.safe_load(ci.yml)`, `git diff --check` | Parses (4 jobs). Clean. |
| `:app:ktlintCheck :app:detekt` (shared lock) | Exit 0. No Kotlin changed. |

APK size delta: not applicable, because no dependency or source file changed. The existing release tamper guards (the beta verifier tests above) pass unchanged. R8/native/unit checks are unaffected by a workflow `uses:` reference, and the hosted CI jobs on the PR head are the gate.

## Release runtime graph (direct dependencies, exact resolved versions)

| Declared (app/build.gradle.kts) | Resolved | Licence (POM) | Notable transitives |
| --- | --- | --- | --- |
| kotlin-stdlib | 2.2.10 | Apache-2.0 | annotations 23.0.0 (JetBrains) |
| profileinstaller 1.4.1, metrics-performance 1.0.0-beta01 | same | Apache-2.0 | — |
| compose-bom 2026.04.01 | ui/foundation/runtime 1.11.0, material3 1.4.0, material-icons-core/extended 1.7.8 | Apache-2.0 | org.jetbrains.compose.* 1.8.2/1.9.2 multiplatform artifacts (via Coil) |
| activity-compose 1.10.1, core-splashscreen 1.0.1, biometric 1.1.0, fragment-ktx 1.8.5 | same | Apache-2.0 | lifecycle 2.9.x, appcompat 1.7.1, core 1.16.0 |
| coil-compose 3.3.0 | 3.3.0 | Apache-2.0 | okio 3.15.0, accompanist-drawablepainter 0.37.3 |
| junrar 8.1.1 | 8.1.1 | **UnRar License** | slf4j-api 2.0.18 (MIT) |
| zip4j 2.11.6 | 2.11.6 | Apache-2.0 | — |
| sshj 0.41.1 | 0.41.1 | Apache-2.0 | asn-one 0.6.0; asks for bcprov/bcpkix 1.84, resolved 1.86 |
| smbj 0.15.0 | 0.15.0 | Apache-2.0 | mbassador 1.3.2 (MIT); asks for bcprov 1.85.2, resolved 1.86 |
| shizuku api/provider 13.1.5 | 13.1.5 | **MIT** | shizuku aidl/shared 13.1.5 (MIT) |
| bcprov/bcutil/bcpkix-jdk18on 1.86 | 1.86 | **Bouncy Castle Licence** | — |
| commons-compress 1.28.0 | 1.28.0 | Apache-2.0 | commons-codec 1.19.0, commons-io 2.20.0, commons-lang3 3.18.0 |
| xz 1.10 | 1.10 | **0BSD** | — |
| media3-exoplayer/ui 1.8.0 | 1.8.0 | Apache-2.0 | **guava 33.3.1-android**, failureaccess 1.0.2, jspecify 1.0.0, error_prone_annotations 2.41.0 |
| exifinterface 1.4.1 | 1.4.1 | Apache-2.0 | — |
| tink-android 1.23.0 | 1.23.0 | Apache-2.0 | gson 2.13.2, jsr305 3.0.2 |
| hilt-android 2.57.2 | 2.57.2 | Apache-2.0 | dagger 2.57.2, hilt-core, javax.inject 1, jakarta.inject-api 2.0.1 |
| kotlinx-coroutines-core/android **1.9.0** | **1.10.2** | Apache-2.0 | Raised by Coil 3.3.0 (`coil-core-android` requests 1.10.2) |
| room-runtime/ktx 2.7.2 | 2.7.2 | Apache-2.0 | sqlite/sqlite-framework 2.5.x |

Observations, not changes:

- The documented coroutine pin (1.9.0) is not the shipped version (1.10.2). Gradle conflict resolution raises it to the 1.10.2 that Coil 3.3.0 requests.
- Hilt/Dagger runtime jars are on the release classpath even though the manual `AppContainer` owns wiring. Whether R8 strips them was not measured here.
- The catalog has `coil-video`, but no module declares it. It does not reach the APK.

The full resolved list with licences is in the appendix.

## Advisories checked

Source: the OSV.dev Maven data export described above. It aggregates the GitHub Advisory Database (GHSA, the `github_reviewed` field) and other feeds. Absence from OSV is not proof of safety, and Google Maven AndroidX artifacts have little advisory coverage there.

### Shipped (release runtime)

| Artifact | Advisory | Severity (GHSA) | Published | Fixed in | Assessment |
| --- | --- | --- | --- | --- | --- |
| com.google.guava:guava 33.3.1-android (via androidx.media3:media3-common 1.8.0) | [GHSA-xxph-c9ww-hj94](https://github.com/google/guava/security/advisories/GHSA-xxph-c9ww-hj94) / [CVE-2026-102554](https://nvd.nist.gov/vuln/detail/CVE-2026-102554): `CompactHashMap`/`CompactHashSet`/`MapMakerInternalMap` Java deserialization makes allocations sized by an attacker-controlled count (memory exhaustion DoS) | HIGH (advisory text gives CVSS 3.1 5.9) | GHSA 2026-10-27, NVD 2026-10-09 | [33.7.2](https://github.com/google/guava/releases/tag/v33.7.2) (Maven Central `33.7.2-android` POM Last-Modified 29 Sep 2026) | Only reachable through `ObjectInputStream.readObject` on attacker data. `grep -rn "ObjectInputStream\|readObject(" app/src/main` finds nothing. The remaining theoretical path is a hostile local app sending a `Serializable` Intent/Bundle extra. That would crash the app process (OOM), not leak data. Low practical impact. See C047-D1. |

No advisory matches Bouncy Castle 1.86, commons-compress 1.28.0, commons-lang3 3.18.0, junrar 8.1.1, zip4j 2.11.6, sshj 0.41.1, smbj 0.15.0, tink-android 1.23.0, xz 1.10, gson 2.13.2, okio 3.15.0 or any other shipped version in this export. The Bouncy Castle 2026 advisories (GHSA-574f-3g2m-x479, -9pwp-9qqc-pr26, -p93r-85wp-75v3, -qp49-qgx5-5m26, -c3fc-8qff-9hwx) are fixed at or below 1.85, so the 1.86 pin is clear of them.

### Build-time only (Gradle plugin classpath, not packaged)

| Artifact (who brings it) | Advisories | Fixed in |
| --- | --- | --- |
| kotlin-gradle-plugin 2.2.10 | GHSA-r937-wjx7-w2jp / CVE-2026-53914 (unsafe deserialization in the Kotlin build cache), MODERATE, 2026-06-26 | 2.4.20-Beta1 by the OSV range. There is no fixed 2.2.x/2.3.x stable in the data. |
| bcprov-jdk18on 1.79 / bcpkix 1.79 (AGP tooling) | GHSA-574f-3g2m-x479, GHSA-9pwp-9qqc-pr26 (CRITICAL); GHSA-p93r-85wp-75v3, GHSA-qp49-qgx5-5m26 (HIGH); GHSA-c3fc-8qff-9hwx, GHSA-wg6q-6289-32hp (MODERATE) | 1.84–1.85 |
| guava 33.3.1-jre | GHSA-xxph-c9ww-hj94 (as above) | 33.7.2 |
| jose4j 0.9.5 | GHSA-3677-xxcr-wjqv / CVE-2024-29371, HIGH | 0.9.6 |
| jdom2 2.0.6 | GHSA-2363-cqg2-863c / CVE-2021-33813 (XXE), HIGH | 2.0.6.1 |
| commons-lang3 3.16.0 | GHSA-j288-q9x7-2f5v / CVE-2025-48924, MODERATE | 3.18.0 |

These run only inside Gradle on the developer host or an ephemeral CI runner, against this repository's own inputs. CI runners are disposable, and the local build cache is not shared with untrusted parties.

## Gradle wrapper integrity

| Check | Result |
| --- | --- |
| `distributionUrl` | `https://services.gradle.org/distributions/gradle-9.3.1-bin.zip`, with `validateDistributionUrl=true` |
| `distributionSha256Sum` | `b266d5ff…beaff06`, equal to the official `checksum` for 9.3.1 |
| `gradle/wrapper/gradle-wrapper.jar` SHA-256 | `b3a875dd…a7c42ec13`, equal to the official `wrapperChecksum` for 9.3.1. Last changed in `e204e9fd` (19 Oct 2026). |
| CI wrapper-jar validation | Implicit in every job that uses `gradle/actions/setup-gradle@v4.4.3`, since `validate-wrappers` defaults to true. The `beta-signed-runtime` job does not run Gradle. |

The wrapper needs no change. A separate `wrapper-validation` action would repeat what `setup-gradle` already does.

## GitHub Actions pin table (resolved 7 Nov 2026 with `git ls-remote`)

| Action | Ref used | Tag resolves to (commit) | Same commit as | Runtime | Uses (all workflows / ci.yml) | After this PR (ci.yml) |
| --- | --- | --- | --- | --- | --- | --- |
| actions/checkout | `v4` | `11d5960a326750d5838078e36cf38b85af677262` | v4.4.0 | node20 | 11 / 4 | unchanged (GitHub-owned) |
| actions/setup-java | `v4` | `cf277c60eb25467037889841efdb72551f06f6c3` | v4.9.1 | node20 | 11 / 4 | unchanged (GitHub-owned) |
| actions/upload-artifact | `v4` | `ea165f8d65b6e75b540449e92b4886f43607fa02` | v4.6.2 | node20 | 13 / 6 | unchanged (GitHub-owned) |
| gradle/actions/setup-gradle | `v4` | `ed408507eac070d1f99cc633dbcf757c94c7933a` (annotated tag object `0b6dd653`) | v4.4.3 (not v4.4.4 `748248dd`) | node20 | 10 / 3 | **`@ed408507eac070d1f99cc633dbcf757c94c7933a # v4.4.3`** |
| reactivecircus/android-emulator-runner | `v2` | `a421e43855164a8197daf9d8d40fe71c6996bb0d` (annotated tag object `4c44018e`) | v2.38.0 | node24 | 8 / 2 | **`@a421e43855164a8197daf9d8d40fe71c6996bb0d # v2.38.0`** |

Why only these two, and only in `ci.yml`:

- Third-party actions are outside GitHub's own trust boundary, and a moved tag runs new code in every CI job without a diff in this repository. The March 2025 `tj-actions/changed-files` incident (CVE-2025-30066) worked by retargeting tags. `setup-gradle` runs on the full checkout with the Gradle cache. The emulator runner executes the instrumented test and signed-beta smoke scripts.
- The pinned SHAs are exactly what the floating tags run today, so behaviour is unchanged. Each SHA came from the action's own upstream repository, not a fork.
- `gif-playback-native.yml` and `audio-wav-native.yml` belong to open Codex PRs #103/#105 and are excluded. The other five native workflows are left for one consistent follow-up rather than mixing them into this change.
- GitHub's hardening guide (https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions) recommends full-length SHA pins. **That page and github.blog could not be reached from this session (DNS blocked).** This rationale therefore rests on the `git ls-remote` evidence above, not on a quote fetched today.
- Trade-off: a SHA pin does not pick up upstream fixes on its own. Without an update bot (recurring automation is out of scope here), each bump is a reviewed PR.

Node 20: four of the five actions declare `node20`. A web search returned GitHub changelog entries titled "Deprecation of Node 20 on GitHub Actions runners" (for example https://github.blog/changelog/2025-09-19-deprecation-of-node-20-on-github-actions-runners/ and https://github.blog/changelog/2026-09-23-deprecation-of-node-20-on-github-actions-runners/). The snippets give a Node 24 default from 16 June 2026 and Node 20 removal on 23 September 2026. **The pages themselves could not be fetched, so these dates are unverified.** Current CI runs these node20 actions successfully. Moving to newer action majors is a version change, so it goes to C047-D4 rather than into this PR.

## Decisions for Codex

| ID | Decision needed | Recommendation |
| --- | --- | --- |
| C047-D1 | Guava 33.3.1-android ships through Media3 1.8.0 with GHSA-xxph-c9ww-hj94. | Next single-cluster PR: add a strict constraint `com.google.guava:guava:33.7.2-android` (or move Media3 to a release that already depends on a fixed Guava, if one exists for compileSdk 36/Kotlin 2.2). Gate it on release R8 build, APK size delta, unit/native media checks and the beta tamper guards. Low practical risk, so this is not urgent. |
| C047-D2 | The build-time classpath has known advisories, including Kotlin Gradle Plugin CVE-2026-53914, fixed only at 2.4.20-Beta1. | Do not churn the toolchain for build-only findings. Re-evaluate when the Kotlin/AGP/Coil cluster is next raised (Coil 3.4+ needs Kotlin 2.3+, per the catalog). Keep CI runners ephemeral and the Gradle build cache unshared. |
| C047-D3 | Third-party notices. Shipped code under MIT (Shizuku, mbassador, slf4j), the Bouncy Castle Licence, 0BSD (xz), the UnRar License (junrar, which forbids creating RAR) and Apache-2.0 is not acknowledged in-app. The About sheet lists only fonts and Material Icons. The catalog comment mislabels Shizuku as Apache 2.0. | Owner/product decision on an open-source notices screen or bundled notices file. Correct the Shizuku catalog comment when the catalog is next touched. This record is not legal certification. |
| C047-D4 | Remaining tag pins: GitHub-owned actions everywhere, plus the third-party actions in the five other native workflows. All except the emulator runner are node20 majors. | One follow-up PR pins the five non-forbidden native workflows to the same SHAs. A separate reviewed PR moves to current majors (checkout v6/v7, setup-java v5/v6, upload-artifact v6/v7, gradle/actions v6), one cluster at a time. The two Codex-owned workflows follow after #103/#105 merge. Consider the repository setting that requires SHA-pinned actions only once every workflow is pinned. |
| C047-D5 | No Gradle dependency verification (`gradle/verification-metadata.xml`) or dependency locking. Resolution is reproducible only through exact catalog pins and Gradle conflict rules. | Evaluate `--write-verification-metadata sha256` as its own PR. It affects every future dependency change and needs owner agreement on the maintenance cost. |

## Remaining

- Hosted CI (Static analysis; Unit tests and APK packaging) on the PR head. The emulator jobs that use the pinned runner are manual or workflow_dispatch only and do not run on the PR.
- C047-D1 to D5 above. No SBOM file (CycloneDX/SPDX) is produced. Generating one would add a build plugin and needs a decision.
- The official GitHub docs/changelog pages and the `downloads.gradle.org` checksum files were unreachable from this session. Gradle checksums were verified through Gradle's official versions API instead.

## Appendix — resolved `releaseRuntimeClasspath` (205 module versions) with POM licences

| Group | Licence (POM) | Artifacts:versions |
| --- | --- | --- |
| `androidx.activity` | Apache-2.0 | activity:1.10.1, activity-compose:1.10.1, activity-ktx:1.10.1 |
| `androidx.annotation` | Apache-2.0 | annotation:1.9.1, annotation-experimental:1.4.1, annotation-jvm:1.9.1 |
| `androidx.appcompat` | Apache-2.0 | appcompat:1.7.1, appcompat-resources:1.7.1 |
| `androidx.arch.core` | Apache-2.0 | core-common:2.2.0, core-runtime:2.2.0 |
| `androidx.autofill` | Apache-2.0 | autofill:1.0.0 |
| `androidx.biometric` | Apache-2.0 | biometric:1.1.0 |
| `androidx.collection` | Apache-2.0 | collection:1.5.0, collection-jvm:1.5.0, collection-ktx:1.5.0 |
| `androidx.compose` | Apache-2.0 | compose-bom:2026.04.01 |
| `androidx.compose.animation` | Apache-2.0 | animation:1.11.0, animation-android:1.11.0, animation-core:1.11.0, animation-core-android:1.11.0 |
| `androidx.compose.foundation` | Apache-2.0 | foundation:1.11.0, foundation-android:1.11.0, foundation-layout:1.11.0, foundation-layout-android:1.11.0 |
| `androidx.compose.material` | Apache-2.0 | material-icons-core:1.7.8, material-icons-core-android:1.7.8, material-icons-extended:1.7.8, material-icons-extended-android:1.7.8, material-ripple:1.11.0, material-ripple-android:1.11.0 |
| `androidx.compose.material3` | Apache-2.0 | material3:1.4.0, material3-android:1.4.0 |
| `androidx.compose.runtime` | Apache-2.0 | runtime:1.11.0, runtime-android:1.11.0, runtime-annotation:1.11.0, runtime-annotation-android:1.11.0, runtime-retain:1.11.0, runtime-retain-android:1.11.0, runtime-saveable:1.11.0, runtime-saveable-android:1.11.0 |
| `androidx.compose.ui` | Apache-2.0 | ui:1.11.0, ui-android:1.11.0, ui-geometry:1.11.0, ui-geometry-android:1.11.0, ui-graphics:1.11.0, ui-graphics-android:1.11.0, ui-text:1.11.0, ui-text-android:1.11.0, ui-tooling-preview:1.11.0, ui-tooling-preview-android:1.11.0, ui-unit:1.11.0, ui-unit-android:1.11.0, ui-util:1.11.0, ui-util-android:1.11.0 |
| `androidx.concurrent` | Apache-2.0 | concurrent-futures:1.1.0 |
| `androidx.core` | Apache-2.0 | core:1.16.0, core-ktx:1.16.0, core-splashscreen:1.0.1, core-viewtree:1.0.0 |
| `androidx.cursoradapter` | Apache-2.0 | cursoradapter:1.0.0 |
| `androidx.customview` | Apache-2.0 | customview:1.0.0, customview-poolingcontainer:1.0.0 |
| `androidx.documentfile` | Apache-2.0 | documentfile:1.0.0 |
| `androidx.drawerlayout` | Apache-2.0 | drawerlayout:1.0.0 |
| `androidx.dynamicanimation` | Apache-2.0 | dynamicanimation:1.0.0 |
| `androidx.emoji2` | Apache-2.0 | emoji2:1.4.0, emoji2-views-helper:1.4.0 |
| `androidx.exifinterface` | Apache-2.0 | exifinterface:1.4.1 |
| `androidx.fragment` | Apache-2.0 | fragment:1.8.5, fragment-ktx:1.8.5 |
| `androidx.graphics` | Apache-2.0 | graphics-path:1.0.1 |
| `androidx.interpolator` | Apache-2.0 | interpolator:1.0.0 |
| `androidx.legacy` | Apache-2.0 | legacy-support-core-utils:1.0.0 |
| `androidx.lifecycle` | Apache-2.0 | lifecycle-common:2.9.4, lifecycle-common-java8:2.9.4, lifecycle-common-jvm:2.9.4, lifecycle-livedata:2.9.4, lifecycle-livedata-core:2.9.4, lifecycle-livedata-core-ktx:2.9.4, lifecycle-process:2.9.4, lifecycle-runtime:2.9.4, lifecycle-runtime-android:2.9.4, lifecycle-runtime-compose:2.9.4, lifecycle-runtime-compose-android:2.9.4, lifecycle-runtime-ktx:2.9.4, lifecycle-runtime-ktx-android:2.9.4, lifecycle-viewmodel:2.9.4, lifecycle-viewmodel-android:2.9.4, lifecycle-viewmodel-ktx:2.9.4, lifecycle-viewmodel-savedstate:2.9.4, lifecycle-viewmodel-savedstate-android:2.9.4 |
| `androidx.loader` | Apache-2.0 | loader:1.0.0 |
| `androidx.localbroadcastmanager` | Apache-2.0 | localbroadcastmanager:1.0.0 |
| `androidx.media3` | Apache-2.0 | media3-common:1.8.0, media3-container:1.8.0, media3-database:1.8.0, media3-datasource:1.8.0, media3-decoder:1.8.0, media3-exoplayer:1.8.0, media3-extractor:1.8.0, media3-ui:1.8.0 |
| `androidx.metrics` | Apache-2.0 | metrics-performance:1.0.0-beta01 |
| `androidx.print` | Apache-2.0 | print:1.0.0 |
| `androidx.profileinstaller` | Apache-2.0 | profileinstaller:1.4.1 |
| `androidx.recyclerview` | Apache-2.0 | recyclerview:1.3.0 |
| `androidx.resourceinspection` | Apache-2.0 | resourceinspection-annotation:1.0.1 |
| `androidx.room` | Apache-2.0 | room-common:2.7.2, room-common-jvm:2.7.2, room-ktx:2.7.2, room-runtime:2.7.2, room-runtime-android:2.7.2 |
| `androidx.savedstate` | Apache-2.0 | savedstate:1.3.3, savedstate-android:1.3.3, savedstate-compose:1.3.3, savedstate-compose-android:1.3.3, savedstate-ktx:1.3.3 |
| `androidx.sqlite` | Apache-2.0 | sqlite:2.5.1, sqlite-android:2.5.1, sqlite-framework:2.5.1, sqlite-framework-android:2.5.1 |
| `androidx.startup` | Apache-2.0 | startup-runtime:1.1.1 |
| `androidx.tracing` | Apache-2.0 | tracing:1.2.0 |
| `androidx.transition` | Apache-2.0 | transition:1.6.0 |
| `androidx.vectordrawable` | Apache-2.0 | vectordrawable:1.1.0, vectordrawable-animated:1.1.0 |
| `androidx.versionedparcelable` | Apache-2.0 | versionedparcelable:1.1.1 |
| `androidx.viewpager` | Apache-2.0 | viewpager:1.0.0 |
| `androidx.window` | Apache-2.0 | window:1.5.0, window-core:1.5.0, window-core-android:1.5.0 |
| `com.github.junrar` | UnRar License | junrar:8.1.1 |
| `com.google.accompanist` | Apache-2.0 | accompanist-drawablepainter:0.37.3 |
| `com.google.code.findbugs` | Apache-2.0 | jsr305:3.0.2 |
| `com.google.code.gson` | Apache-2.0 | gson:2.13.2 |
| `com.google.crypto.tink` | Apache-2.0 | tink-android:1.23.0 |
| `com.google.dagger` | Apache-2.0 | dagger:2.57.2, dagger-lint-aar:2.57.2, hilt-android:2.57.2, hilt-core:2.57.2 |
| `com.google.errorprone` | Apache-2.0 | error_prone_annotations:2.41.0 |
| `com.google.guava` | Apache-2.0 | failureaccess:1.0.2, guava:33.3.1-android, listenablefuture:9999.0-empty-to-avoid-conflict-with-guava |
| `com.hierynomus` | Apache-2.0 | asn-one:0.6.0, smbj:0.15.0, sshj:0.41.1 |
| `com.squareup.okio` | Apache-2.0 | okio:3.15.0, okio-jvm:3.15.0 |
| `commons-codec` | Apache-2.0 | commons-codec:1.19.0 |
| `commons-io` | Apache-2.0 | commons-io:2.20.0 |
| `dev.rikka.shizuku` | MIT | aidl:13.1.5, api:13.1.5, provider:13.1.5, shared:13.1.5 |
| `io.coil-kt.coil3` | Apache-2.0 | coil:3.3.0, coil-android:3.3.0, coil-compose:3.3.0, coil-compose-android:3.3.0, coil-compose-core:3.3.0, coil-compose-core-android:3.3.0, coil-core:3.3.0, coil-core-android:3.3.0 |
| `jakarta.inject` | Apache-2.0 | jakarta.inject-api:2.0.1 |
| `javax.inject` | Apache-2.0 | javax.inject:1 |
| `net.engio` | MIT | mbassador:1.3.2 |
| `net.lingala.zip4j` | Apache-2.0 | zip4j:2.11.6 |
| `org.apache.commons` | Apache-2.0 | commons-compress:1.28.0, commons-lang3:3.18.0 |
| `org.bouncycastle` | Bouncy Castle Licence | bcpkix-jdk18on:1.86, bcprov-jdk18on:1.86, bcutil-jdk18on:1.86 |
| `org.jetbrains` | Apache-2.0 | annotations:23.0.0 |
| `org.jetbrains.androidx.lifecycle` | Apache-2.0 | lifecycle-common:2.9.5, lifecycle-runtime:2.9.5, lifecycle-runtime-compose:2.9.5, lifecycle-viewmodel:2.8.4 |
| `org.jetbrains.androidx.savedstate` | Apache-2.0 | savedstate:1.3.5, savedstate-compose:1.3.5 |
| `org.jetbrains.compose.animation` | Apache-2.0 | animation:1.8.2, animation-core:1.8.2 |
| `org.jetbrains.compose.annotation-internal` | Apache-2.0 | annotation:1.8.2 |
| `org.jetbrains.compose.collection-internal` | Apache-2.0 | collection:1.8.2 |
| `org.jetbrains.compose.foundation` | Apache-2.0 | foundation:1.8.2, foundation-layout:1.8.2 |
| `org.jetbrains.compose.runtime` | Apache-2.0 | runtime:1.9.2, runtime-saveable:1.9.2 |
| `org.jetbrains.compose.ui` | Apache-2.0 | ui:1.8.2, ui-geometry:1.8.2, ui-graphics:1.8.2, ui-text:1.8.2, ui-unit:1.8.2, ui-util:1.8.2 |
| `org.jetbrains.kotlin` | Apache-2.0 | kotlin-bom:1.8.0, kotlin-stdlib:2.2.10, kotlin-stdlib-common:2.2.10 |
| `org.jetbrains.kotlinx` | Apache-2.0 | kotlinx-coroutines-android:1.10.2, kotlinx-coroutines-bom:1.10.2, kotlinx-coroutines-core:1.10.2, kotlinx-coroutines-core-jvm:1.10.2, kotlinx-serialization-bom:1.7.3, kotlinx-serialization-core:1.7.3, kotlinx-serialization-core-jvm:1.7.3 |
| `org.jspecify` | Apache-2.0 | jspecify:1.0.0 |
| `org.slf4j` | MIT | slf4j-api:2.0.18 |
| `org.tukaani` | 0BSD | xz:1.10 |
