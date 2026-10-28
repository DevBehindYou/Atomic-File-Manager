# Building and verifying the GitHub beta

Only owner-authorized dedicated-key artifacts may be published. Keep signing credentials outside the repository, GitHub releases, Actions and logs.

Set `ATOMIC_RELEASE_SIGNING_PROPERTIES` to a private Java properties file with `storeFile`, `storePassword`, `keyAlias`, `keyPassword` and optional `storeType=PKCS12`. Use a forward-slash absolute storeFile path. Build `:app:assembleRelease` on the reviewed source with JDK21. Never put passwords in command-line arguments or source control. The owner's private backup includes the keystore and credentials; it is not a public asset.

Without this environment variable, CI retains its existing development debug-signing fallback so secret-free PRs still exercise release packaging/R8. A CI-packaged development APK is not an authorized shipping APK: the beta verifier independently requires the public certificate fingerprint pinned in `beta-config.json`, correct version/package/API, non-debuggable/non-test-only metadata and matching public checksums/provenance.

The public draft contains exactly the configured APK, `BETA_MANIFEST.json`, `SHA256SUMS.txt` and `SIGNING_CERTIFICATE_SHA256.txt`. BETA_MANIFEST names the exact source revision, app tree, version/package/minSDK, APK SHA256, certificate SHA256 and minified release status. The local build record retains matching R8 mapping evidence. Keep mapping/source logs local for diagnostics; do not upload secrets or signing backups.

Dispatch the existing CI workflow at `codex/beta-0.2.0` only after creating the owner-authorized draft at that exact source SHA and uploading its explicit public assets. The branch-specific manual job consumes the four owner-resolved public-asset capabilities described below, verifies source/app-tree/artifact identity and installs those exact bytes in disposable API28 and API34 emulators. Its legacy empty-input path still tries the draft API with the existing read-only token; that path has a retained HTTP403 and is not a runtime pass. It does not change phone permissions, uninstall the owner's app, run benchmarks or execute the legacy manual monkey job. Both normal required CI jobs still run for every PR and main push. A manual beta dispatch with nonempty asset URLs runs only the signed-runtime gate; publication also requires both normal jobs to pass separately on the same current PR head. Other manual dispatches retain normal checks.

Retain all failing/cancelled runs. Publish the prerelease only after applicable current-head checks, review concerns/dependencies and a clean guarded trial pass, plus the signed-runtime job and artifact checks. Main/stable release and the wider feature/plugin acceptance ledgers remain separate.

Direct draft retrieval with the contents-read token is unavailable in the retained preflight. Do not increase token permissions automatically; use the bounded artifact-only transport below and retain an actual signed-runtime pass before publication.

## Read-only draft transport after the confirmed HTTP403

GitHub's existing contents-read token could not retrieve this private draft. Keep that permission unchanged. After the owner has uploaded exactly the four intended-public assets to the configured unpublished draft and set its exact source SHA, run this locally on the published beta branch:

```text
python scripts/release/beta_transport.py dispatch
```

This helper uses the owner's existing local `gh` authentication only to inspect the draft and resolve actual HTTP302 release-asset redirects. It then dispatches `ci.yml` at `codex/beta-0.2.0`, passing only four temporary asset-specific download capabilities, sizes and SHA256 digests through stdin. No owner token, signing key, password or private backup enters CI. An HTTP200 direct-body response is not a usable narrow capability and fails closed; the helper never substitutes the owner token. The [official release-asset API](https://docs.github.com/en/rest/releases/assets) supports both200 and302 responses; no fixed redirect lifetime is assumed.

The read-only job reads the descriptor directly from the event file, avoiding shell interpolation or a step environment that prints it. It requires the expected source/release/tag, exact asset names, HTTPS release-assets host, no redirects and bounded bytes with matching size/digest. URLs are masked before use and are never saved under retained `beta-smoke` evidence. The existing independently pinned signer, source/app-tree, version/package, non-debuggable and checksum checks still run before installation. Checkout does not persist credentials.

Workflow inputs are observable event data, and temporary links may grant download access before publication. This transport is only for those four files already authorized for public beta distribution. Never use it for signing backups, credentials or private data. Resolve fresh links immediately before dispatch; expiry or queue delay fails the runtime gate and requires a new owner dispatch, without increasing repository permissions. Publish only after current-head CI/review/clean-trial and the exact signed APK runtime pass.

Pure transport tests (no network or credentials): `python scripts/release/test_beta_transport.py`.

Signer-report compatibility tests: `python scripts/release/test_verify_beta.py`. The public fixtures retain actual SDK36 and SDK37 reports from the same signed APK. Require exactly one signer and the pinned certificate in every recognized signing-scheme entry; missing, malformed, unknown or mismatching certificate entries fail closed. The caller must also require the actual apksigner verify process to succeed.
