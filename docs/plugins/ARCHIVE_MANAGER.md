# Archive manager — v0.2

Starting Atomic plugin version: **v0.2**, recorded 24 October 2026. See [record/version rules](README.md). It does not rename library versions or certify every format/provider.

Source inspected: `ecf1dc5834c125c9612be74128fc4ac34e65a046` (24 October 2026). The version-only increment changes inventory metadata, not the linked viewer engines.

## Implementation, features and limits

[ArchiveFormats.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/domain/usecase/ArchiveFormats.kt) recognizes ZIP, TAR, TAR.GZ/BZ2/XZ, GZ/BZ2/XZ, 7z and RAR. Listing streams headers; 7z uses an owned seekable spool. Recognition is separate from successful/encrypted/multipart support. [InspectArchiveUseCase.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/domain/usecase/InspectArchiveUseCase.kt) inspects entries; [ArchiveOperationsHelper.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/domain/usecase/ArchiveOperationsHelper.kt) creates ZIPs/extracts through existing progress and staged publication.

Password ZIP reads use the existing zip4j path and memory-consumed passwords. This does not add password handling to other formats. RAR is read-only here; valid variants need representative fixtures. Code bounds include 100,000 entries, a 10 GiB extraction ceiling and ZIP-creation depth/cycle guards. Unsafe paths, collisions, cancellation and corruption have rejection/cleanup paths preserving unrelated files.

Creation is ZIP, not arbitrary format creation/update. Encryption/multipart, symlink/case-collision semantics, provider behavior and genuine pause/resume remain unaccepted. Guard ceilings are not complete bomb/security certification. A dependency declaration is not valid-RAR/encryption acceptance or a license review.

## Tasks

| ID | Priority / state | Task and exit evidence |
| --- | --- | --- |
| ARC-T01 | P0 / open acceptance | Valid/corrupt/encrypted formats across backends; traversal/absolute paths/symlinks/duplicates/case collisions/bombs/low space/interruption/overwrite |
| ARC-T02 | P0 / open acceptance | Actual process-kill journal repair/publication/cleanup preserving unrelated files; host simulations and physical acceptance separately |
| ARC-T03 | P1 / deferred audit/research | Browse/extract/create/modify/encrypted-read/write/multipart matrix; engine/API/size/license/security evaluation |
| ARC-T04 | P1 / deferred | Supported entry search/sort/selection and creation controls; pause/resume/ETA only when real and meaningful |

## Bugs and risks

Retain existing integrity/ancestry fixes; do not duplicate completed work because the future intake requests similar protection. No new archive bug is reproduced here; assign `ARC-Bnn` to a future confirmed failure with evidence. RAR recognition plus corrupt-input rejection is not valid-RAR acceptance. Process death, provider publication/aliases/collisions and malicious formats remain open gates.

## Validation and continuation

`ArchiveFormatsTest`, `ArchiveOperationsTest` and `ArchiveIntegrityTest` include dispatch, TAR/compressor/7z paths, password ZIP/AES, traversal/collision/corrupt/cancel cleanup, opaque IDs and recursive destinations. Read the [existing plan](../roadmap/FEATURE_UPGRADE_PLAN.md), [progress](../roadmap/FEATURE_UPGRADE_PROGRESS.md) and exact PR records for accepted source. This baseline runs no new compression/extraction/device mutation and grants no milestone credit. U11/U13 and the [advanced intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md) remain incomplete. New evidence must name exact source/fixtures/providers.

## Changelog

- **v0.2 — 24 October 2026:** establish independent version and maintained format/task/bug/risk/evidence record. No new engine, encrypted writes or complete archive-safety claim is introduced.