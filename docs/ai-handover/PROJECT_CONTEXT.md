# Project context and owner constraints

Updated **4 November 2026**. Native Android **Atomic File Manager**, repository `DevBehindYou/Atomic-File-Manager`. Old Refract naming and agent-specific commit/session trailers are obsolete.

## Objective and scope

Deliver **v0.2.1 stable** with eight bundled Image, Video, Audio, PDF, Document, Code/Text Editor, Markdown and Archive plugins functional, tested and usable without known release-blocking defects. Maintain per-plugin features/tasks/bugs/validation and independent About version **v0.2**, separate from app version.

The [advanced intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md), [42-group matrix](../plugins/modernization/PLUGIN_FEATURE_MATRIX.md), [feature plan](../roadmap/FEATURE_UPGRADE_PLAN.md) and acceptance gates remain authoritative. Later owner instructions permit parallel plugin implementation; the intake's blanket deferral is historical. Keep native Kotlin/Compose; do not interpret Flutter examples as a migration request or format names as working decoders.

Cloud Connect is the requested ninth bundled plugin. [Cloud records](../plugins/CLOUD_CONNECT.md) and `cloud-connect/` own research/tasks. Direct accounts are requested, superseding the old provider-only restriction. First provider/scopes/registration and real account evidence remain unresolved. Owner-approved API28 is integrated to accommodate MEGA's baseline; this does not implement MEGA. Existing SAF-linked folders are distinct from direct OAuth accounts. Bundling alone is not proof of Google Play policy compliance.

## Preserve product corrections

Files: storage shortcuts and friendly current-storage names; title on its own row, count/sort on the next right-aligned row; ascending/descending Name/Date/Size; List/Grid; hidden files and folder creation in compact options. Tasks replaces About/Add-folder header buttons and shows actual copy/move/upload/download operations. Keep balanced popup titles, content-sized top menus, bottom action icon grids, long-press actions without per-row More boxes, typed icon colours and media/folder thumbnails. Preserve user launcher artwork. Readable-shared-file scanning belongs in on-demand Analysis, with centered Analyze actions. Transfer bubble maximum is seven. Existing validation records own evidence; do not gratuitously redesign accepted decisions.

## Execution constraints

- Owner authorizes isolated parallel agents, focused `codex/` PRs and automatic merging only after applicable current-head checks, resolved reviews, integrated dependencies and clean fresh-target dry-run. No force pushes or bypassing missing/failing checks.
- Integration is not main/release completion. Reconcile old #44–60 individually; do not duplicate existing candidate PRs.
- Serialize local Gradle on this 8 GB host. Preserve files/preferences/permissions, artwork and ignored tools/evidence/signing backups. No broad cleanup or `gradle clean` against evidence-bearing checkouts.
- Bundle plugins with app updates. Never download executable plugins from GitHub/Drive or show fake installation controls.
- Phone authorization covers the specific surface/owned-fixture tests. Benchmarking is explicitly deferred: unchanged permissions, no `su` probes or larger corpora. Do not infer permission for destructive user-file tests, grants, clearing data or changing root access.
- Recurring continuation automation was permanently deleted. Do not recreate it. Handover does not authorize a replacement schedule.
- Preserve signing identities. Private root copies and `C:/Users/temp/Documents/Atomic-File-Manager-Signing` are present. Never print credentials, commit/upload keys, rotate them for convenience or overwrite the immutable beta. TESTING records public identities only.
- Do not fabricate attribution/session links, validation, percentages or zero-bug guarantees. Whole-group credit requires all group gates.

## Architecture essentials

Kotlin 2.2.10, Compose/Atomic design system, AGP 9.1.1, Gradle 9.3.1, JDK 21; min28/compile36/target36. Manual `AppContainer`/`DefaultAppContainer` is active DI; declared Hilt is not an active graph. Existing Room/SQLiteOpenHelper/SharedPreferences and custom routes remain source of truth. Mutations preserve operation-engine/journal, trash and verification policy. Read source-linked architecture/dependency records before adding frameworks or engines.
