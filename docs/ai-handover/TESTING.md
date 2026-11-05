# Verification evidence

Refreshed **4 November 2026**. Hosted conclusions/logs were read; this refresh ran no Gradle, instrumentation, benchmark or release command.

| ID | Source / check | Status and result |
| --- | --- | --- |
| E-GIF-CI | fb856a6; [41064609636](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/41064609636) | VERIFIED: unit/APK and static/Detekt/Lint jobs SUCCESS |
| E-GIF-NATIVE | Same PR head; [41064609699](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/41064609699) | VERIFIED: 8 native cases pass on API28 and 8 on API34; finite/looping controls, active-page/lifecycle/disposal, static gesture/EXIF/BMP |
| E-GIF-LOCAL | Managed GIF `.local-evidence/gif-fb856a6-validation.log` | UNKNOWN completion: ends after Java compile, `.exit` absent, no Java build observed |
| E-GIF-PHONE | Latest repair fb856a6 | UNKNOWN: no retained current-repair phone result found; older phone run failed callback disposal |
| E-GIF-PREV | 60031fa; CI40931415358/native40931415466 | Historical 1306 host cases pass; API34 native 7/8; API28 process abort. Recovered artifacts below |
| E-MD-CI | 7d54483; [40915350269](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/40915350269), [40915350076](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/40915350076) | PARTIAL: static SUCCESS, unit/APK/native CANCELLED |
| E-MD-LOCAL | Earlier e217005/4c8fc5f | Historical recorded 16 host cases and packaging pass; combined 7d54483 validation interrupted. Missing old raw files cannot establish a current pass |
| E-PDF | Published dd800b8, CI40356491147 | BROKEN unit/APK; static SUCCESS. Not evidence for local 579d1be |
| E-ARCHIVE | Published 5942082, CI40356811778 | VERIFIED host/static only. Not evidence for newer local 61af2e8 |
| E-DOC | Published 09ec9e5, CI40356891772 | BROKEN unit/APK; static SUCCESS. Not evidence for local eb2d15a |
| E-EDITOR | #90 source 331a3fc, merge b995e23 | Scoped accepted record: 43 focused host cases, full CI, API34 emulator/phone. No complete EDT credit |
| E-SQLITE | ac15738; [40909140527](https://github.com/DevBehindYou/Atomic-File-Manager/actions/runs/40909140527), CI40909140613 | VERIFIED check conclusions; 49 cases per normal/isolated fork recorded. Root cause remains unknown |

Skipped manual full-device/signed-runtime jobs are not successful executions. GIF native success does not establish all providers, huge/long GIFs, true pause/resume, accessibility, performance or complete Image modernization.

## Retained evidence and provenance

GIF root: `C:/Users/temp/ChatGPT-Accounts/KAYA/codex/worktrees/gif-playback-recovery/Atomic-File-Manager`. Ignored `.local-evidence/ci-60031fa/{api28,api34,host}` contains recovered old CI reports, native logs, exact APKs and source/app-tree/hash identities. Latest native artifacts remain on GitHub with 14-day retention: capture identities before phone installation. CI debug signing is ephemeral; do not clear app data or uninstall to install an incompatible signature. Rebuild with existing local debug identity if needed.

Old primary `build/audit-tools` paths are absent. A historical description in Git is not recovery of raw local evidence. Live phone/APK/permission state was not read during this handover.

## Beta and signing boundary

[Published prerelease](https://github.com/DevBehindYou/Atomic-File-Manager/releases/tag/v0.2.0-beta.1), verified live 4 November: tag `v0.2.0-beta.1`, source `02605684b2b0bab41f2546f69b859c542a375ac6`, versionCode 2, published 28 October at 17:46 UTC. APK `Atomic-File-Manager-0.2.0-beta.1.apk`: 15,075,706 bytes; SHA256 `bbd5b14fa3b29f15b22756a3bbbce2eeb620aa696aa4ed98f5eb419fb97572fb`. Manifest/checksum/public certificate-fingerprint assets are present. [Publication record](../testing/BETA_0_2_0_PUBLICATION_VALIDATION.md) owns original signature/runtime verification; beta was not reinstalled here.

Private key/backup location: `C:/Users/temp/Documents/Atomic-File-Manager-Signing`. Owner-requested ignored root copies include `atomic-file-manager-release.keystore`, `signing.properties`, `Atomic-File-Manager-release-signing-backup-2026-10-26-verified.zip`, debug keystore and public certificate. Record locations/presence only, never values. Backup ZIP is not encrypted. Never publish key material, rotate keys, rewrite beta tag/assets or substitute debug signing for stable release.
