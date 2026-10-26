# Building and verifying the GitHub beta

Only owner-authorized dedicated-key artifacts may be published. Keep signing credentials outside the repository, GitHub releases, Actions and logs.

Set `ATOMIC_RELEASE_SIGNING_PROPERTIES` to a private Java properties file with `storeFile`, `storePassword`, `keyAlias`, `keyPassword` and optional `storeType=PKCS12`. Use a forward-slash absolute storeFile path. Build `:app:assembleRelease` on the reviewed source with JDK21. Never put passwords in command-line arguments or source control. The owner's private backup includes the keystore and credentials; it is not a public asset.

Without this environment variable, CI retains its existing development debug-signing fallback so secret-free PRs still exercise release packaging/R8. A CI-packaged development APK is not an authorized shipping APK: the beta verifier independently requires the public certificate fingerprint pinned in `beta-config.json`, correct version/package/API, non-debuggable/non-test-only metadata and matching public checksums/provenance.

The public draft contains exactly the configured APK, `BETA_MANIFEST.json`, `SHA256SUMS.txt` and `SIGNING_CERTIFICATE_SHA256.txt`. BETA_MANIFEST names the exact source revision, app tree, version/package/minSDK, APK SHA256, certificate SHA256 and minified release status. The local build record retains matching R8 mapping evidence. Keep mapping/source logs local for diagnostics; do not upload secrets or signing backups.

Dispatch the existing CI workflow at `codex/beta-0.2.0` only after creating the owner-authorized draft at that exact source SHA and uploading its explicit public assets. The branch-specific manual job resolves the configured draft by release ID with its existing read-only GitHub token, requires exactly four fully uploaded public assets and verifies source/app-tree/artifact identity, then installs those exact bytes in a disposable API34 emulator. It does not change phone permissions, uninstall the owner's app, run benchmarks or execute the legacy manual monkey job. Both normal required CI jobs still run.

Retain all failing/cancelled runs. Publish the prerelease only after applicable current-head checks, review concerns/dependencies and a clean guarded trial pass, plus the signed-runtime job and artifact checks. Main/stable release and the wider feature/plugin acceptance ledgers remain separate.

Draft retrieval by the existing contents-read job token must be demonstrated by the actual manual run. Do not increase token permissions automatically if GitHub refuses access; retain the failed evidence and resolve the runtime gate before publication.
