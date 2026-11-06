# Ownership and conflict prevention

Observed 7 November 2026. A reservation protects active work; it does not claim an agent process is running. No other live Codex subagent was reported during this inspection. Claude's runtime is not observed and is not launched by Codex. Check live status on every resume.

| Work | Owner | Status | Boundary / next action |
| --- | --- | --- | --- |
| Coordinator setup | Codex | Preparing this documentation PR | Dictionary, agent entry points, validation/lease scripts |
| Image #103 | Codex | Green CI; review/guarded merge outstanding | Image fit modes, GIF/image regression records and associated workflows |
| Audio #105 | Codex | Green CI; review/guarded merge outstanding | Actual WAV fixture/workflow, acceptance records |
| Deferred #63 | Codex | Owner-deferred measurements | No benchmark invocation, root probes or permission changes |
| Old #44–#60 stack | Codex | Main/integration reconciliation required | No duplicate reimplementation or blind closure |
| C001–C050 | Claude Code | Assigned, all unclaimed | Claim one eligible task at a time; Codex reviews |
| Stable release/main promotion | Codex | Acceptance incomplete | Preserve public beta tag and private signing backups |

## Area locks

All Kotlin paths below are relative to `app/src/main/kotlin/com/devbehindyou/atomicfilemanager/`. Lock the whole applicable area; if a change spans areas, acquire all locks in sorted order before editing. Existing reservations above still apply even before their owners use the lease helper.

| Lock | Shared edit surface |
| --- | --- |
| area-opening | AtomicApp.kt, opening router/pane, MIME/preparation, common backend/domain contracts |
| area-image | ImageGallery, image/GIF/EXIF helpers, image strings/tests/native workflow |
| area-media | Audio/Video/PreviewPlayer, media helpers, services, media tests/workflows |
| area-pdf | PDF helpers/controllers/UI, PDF tests/workflow |
| area-editor-markdown | Text editor/drafts/history and Markdown parsers/UI/asset adapters/tests |
| area-documents | DOCX/document parsers/routing/UI/tests |
| area-archive-operations | Archive helpers/UI, queue/engine/recovery/journal and all mutating transfer invariants |
| area-index-analysis | Search/index, Analysis/usage caches, generation/checkpoint persistence |
| area-storage | StorageBackend implementations, account identity/capabilities, provider contracts |
| area-schema | Room schema/migrations, other database formats and preference migrations |
| area-build-release | Catalog, Gradle/conventions, manifest, CI/release scripts and signing configuration |
| area-ui-shell | MainActivity, route stack, common design system, Browse, Settings/plugin inventory |
| area-coordinator | Dictionary task/state/log/decisions, generated roadmap and entry-point docs |
| area-progress | Feature/plugin global progress, handover, shared release/architecture summary docs |

Feature-specific docs and tests travel with their feature area. Unknown shared paths require a new agreed area entry before editing. Path exclusions cannot be used to evade overlap. Multiple claims on `area-coordinator` are short updates only; no agent holds it for an entire implementation. Global ledger edits use `area-progress` briefly after source work is ready.

## Claim and handoff record

Update the task's status, branch, PR when created and remaining dependencies, then append an event in COORDINATION_LOG.md with owner, UTC timestamp, source base, task IDs, file scope, acquired area names, next action and evidence pointers. Token values belong in local session state, not Git. Declare exact handoff scope if Codex releases #103/#105 work to Claude.

Both agents fetch and inspect remote PR claims before starting. Within these linked worktrees the lease script is authoritative for local exclusion. For another clone/machine, obtain the documented acknowledgment described in README before shared edits; Git branches alone do not prevent duplicate ownership.

After tests or review fail, retain the branch and evidence, update remaining work and continue that owner’s repair. Do not reset another worktree, prune old branches, alter artwork, overwrite local settings or sweep ignored signing/tools files. Release source areas only after merge or explicit handoff. A stopped session retains a durable reservation until the resume check reconciles it.
