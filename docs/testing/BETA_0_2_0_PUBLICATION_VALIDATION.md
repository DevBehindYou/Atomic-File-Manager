# 0.2.0 Beta 1 publication validation

Published 28 October 2026 at 17:46 UTC (23:16 IST). [Public prerelease](https://github.com/DevBehindYou/Atomic-File-Manager/releases/tag/v0.2.0-beta.1) and [APK download](https://github.com/DevBehindYou/Atomic-File-Manager/releases/download/v0.2.0-beta.1/Atomic-File-Manager-0.2.0-beta.1.apk).

## Frozen release identity

| Field | Verified value |
| --- | --- |
| Tag / app version / code | `v0.2.0-beta.1` / `0.2.0-beta.1` / `2` |
| Package / platform floor | `com.devbehindyou.atomicfilemanager` / Android 9, API 28 |
| Public source | `02605684b2b0bab41f2546f69b859c542a375ac6` |
| App tree | `f017871f9bc4576798d65c53d4de7c5d9aaccb43` |
| Feature-integration merge | [PR #97](https://github.com/DevBehindYou/Atomic-File-Manager/pull/97), `3fb922d2afde773f9e1060602a3eb2a781f07b9f` |
| APK bytes | `15075706` |
| APK SHA-256 | `BBD5B14FA3B29F15B22756A3BBBCE2EEB620AA696AA4ED98F5EB419FB97572FB` |
| Dedicated certificate SHA-256 | `976B82A67F452019371416A4151CE36D3AAD1D56FDD833242F7ED072D05198B7` |

The release includes the seven-transfer-bubble cap and bounded scrolling rail on compact screens. Its minified APK contains the actual seven-slot guard; the old three-slot message is absent. Tag/source identity was independently checked. An unauthenticated public download at 23:17 IST matched the exact APK size and SHA-256 above.

## Accepted gates and limits

- The complete serialized source gate passed in 29m 7s: 1,278 app cases in 214 suites and 28 custom Lint-rule cases, with zero failures/errors/skips, plus static checks, signed R8 packaging and release Lint.
- Both required normal jobs passed in [current-source CI 40705484240](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/40705484240). Separately, both exact dedicated-key APK startup jobs passed on disposable API 28 and API 34 emulators in [run 40736625106](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/40736625106). Downloaded artifacts independently matched source, app tree, signer, package/version and hashes; reviewed screenshots showed meaningful Home/access UI without fatal AndroidRuntime output. Skipped jobs are not passes.
- Alignment, signer/source/app-tree checks and 13 tamper rejections passed. Independent review and a clean dependency-aware merge trial preceded PR #97 integration.
- Four host Compose cases used actual pointer gestures. The Native SQLite fixture retained eight simultaneous requests through two helpers, seven successes, one capacity rejection and seven persisted unique IDs. These scoped host checks do not establish physical-touch or broader provider/transfer acceptance. See the retained [seven-bubble candidate evidence](SEVEN_TRANSFER_BUBBLES_CANDIDATE.md).

The earlier overflow failures, Legacy SQLite fixture failure, first static failure, beta host failure, signer-parser failure, interrupted packaging and cancelled CI remain historical evidence. The dated pending gates in the [preparation record](BETA_0_2_0_PREPARATION.md) and candidate record describe their earlier checkpoints; the passing gates above apply to the frozen public source.

## Continuing scope

Main/stable is unchanged. Original feature credit remains **15% host verified / 8% accepted**; advanced eight-plugin modernization remains **0/42 complete groups, 0/8 fully modernized (0%)**. This publication does not complete either plan. Broader provider, recovery, format and performance gates remain open; benchmark deferral, phone permissions and the removed continuation automation are unchanged.

The dedicated release key cannot update an existing debug-signed installation of the same package. The phone was untouched. The original private backup and owner-requested root signing copies remain ignored and outside public assets. Public assets are limited to the named APK, provenance manifest, checksums and certificate fingerprint. Keep this public tag and these assets frozen; future plugin work must ship under a new release version.

Retained local evidence: `build/audit-tools/beta-publication-verified.json`, `beta-public-download-verification.json`, `beta-seven-final-acceptance.json`, `beta-host-test-counts.json` and `beta-artifacts/BETA_MANIFEST.json` under the same audit-tools directory. Only public provenance is recorded here.
