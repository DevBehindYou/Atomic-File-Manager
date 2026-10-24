# Bundled plugin records

Recorded 24 October 2026. These records preserve tasks, supported features, limitations, bugs, evidence and decisions separately for all eight built-in plugins. Start here when resuming a plugin change, then read that plugin's record and linked sources. This is a maintained source checkpoint, not the completed eight-plugin audit or competitor/dependency research requested by the [advanced intake](../roadmap/ADVANCED_BUILT_IN_PLUGINS_INTAKE.md).

| Plugin | Record | Starting version | Current work boundary |
| --- | --- | --- | --- |
| Image viewer | [Image](IMAGE_VIEWER.md) | v0.2 | Existing U09 recovery/replacement integrated through PR #80; wider acceptance open |
| Video player | [Video](VIDEO_PLAYER.md) | v0.2 | Existing U09 preparation Retry passes host/app-surface checks; CI/integration pending |
| Audio player | [Audio](AUDIO_PLAYER.md) | v0.2 | Existing foreground player; background/queue modernization deferred |
| PDF reader | [PDF](PDF_READER.md) | v0.2 | Bounded raster viewer; engine evaluation deferred |
| Document reader | [Document](DOCUMENT_READER.md) | v0.2 | DOCX structured text; Office page fidelity not claimed |
| Code and text editor | [Editor](CODE_TEXT_EDITOR.md) | v0.2 | Bounded editor; persistent recovery/richer editing open |
| Markdown viewer | [Markdown](MARKDOWN_VIEWER.md) | v0.2 | Basic raw/rendered subset; CommonMark/GFM expansion deferred |
| Archive manager | [Archive](ARCHIVE_MANAGER.md) | v0.2 | Format-specific operations; broader safety/device acceptance open |

## Version meaning and ownership

`v0.2` is the user-requested starting **Atomic plugin version**, displayed in each selected plugin's About/details page under Settings → In-app plugins. Each entry has its own value in [InAppPluginsSheet.kt](../../app/src/main/kotlin/com/devbehindyou/atomicfilemanager/ui/screens/InAppPluginsSheet.kt), so a later increment can change that plugin alone. The localized label lives in [plugins.xml](../../app/src/main/res/values/plugins.xml). These versions are distinct from app/dependency/codec versions and release acceptance. They do not retrospectively label old binaries v0.2 or imply the future modernization is complete.

All eight plugins remain native Kotlin/Compose features bundled with Atomic and updated through the app. No installer, executable download, independent update feed, extra permission or settings migration is introduced. About/details must describe real capabilities and limits rather than planned functions.

## Status and evidence rules

- **Source present** means the implementation exists; it does not establish a format/device/performance pass.
- **Historical verified checkpoint** names the earlier source and record that passed. Do not apply its count or APK hash to a changed tree.
- **Pending** means implementation or validation is active and unaccepted. Retain failures and corrections separately.
- **Deferred task** is a future capability, not a bug or current feature. Audit and research precede engine/dependency choices.
- **Known bug** needs a reproducible failure and evidence. Source observations and untested scenarios remain risks/open checks until reproduced. Unsupported features are limitations, not invented defects.

The [existing plan](../roadmap/FEATURE_UPGRADE_PLAN.md) and [fixed progress ledger](../roadmap/FEATURE_UPGRADE_PROGRESS.md) retain U01–U13. This version/documentation slice adds no milestone credit. The deferred eight-plugin program needs its own audited scope and evidence-based tracking later. Device benchmarking remains owner-deferred; preserve current phone permissions.

## Update protocol

For every plugin increment:

1. Record its task/bug ID, priority, source/PR and state before changing behavior. Inspect active worktrees/PRs to avoid duplication.
2. Update supported features and explicit format/size/provider limits only after inspecting source. Link shared components affected across plugins.
3. Record exact tested source/app tree, filters/counts/result, retained negative evidence, device/build/artifact identity and remaining gates. Distinguish host, native, manual touch, benchmark, integration and main/release acceptance.
4. Add a dated changelog entry. Assign later versions deliberately and update only the affected plugin metadata/record/consumer expectations; a dependency bump alone is not a plugin version decision.
5. Preserve unresolved tasks, bugs, owner decisions and limits. Mark resolved items with fixing PR/checkpoint rather than deleting context.

## Validation boundary

The accepted inventory/navigation increment is [U01 / PR #61](../testing/BUILT_IN_PLUGINS_VALIDATION.md). Its historical tests/phone flow do not verify this addition. The version slice extends `SettingsScreenComposeTest` to visit all eight plugins, require visible **Version v0.2**, preserve Back/Close and limitations, and leave preferences/cache/notifications unchanged. The focused source passes seven affected host cases, ktlint/Detekt and both APK builds in3m06s. All eight phone detail/version/features/limits/Back/Close checks pass, with installed hash, Home, permissions and runner state verified. Independent review, current-head full CI/Lint and guarded feature integration pass through PR #81 (head6a8fff4, CI40108734849, merge377a378). See [the exact version validation record](../testing/PLUGIN_RECORDS_VERSION_VALIDATION.md), including retained test-format and phone-harness failures. Accepted Image Viewer recovery is separate from the incomplete whole U09 milestone.

The common historical capability snapshot is [BUILT_IN_VIEWER_AUDIT](../testing/BUILT_IN_VIEWER_AUDIT.md). Requested future audit, research, architecture, matrix, dependency, test, performance and handover reports remain governed by the intake; this index does not claim those reports are complete.