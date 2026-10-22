# Analysis cleanup action alignment candidate

Date: 22 October 2026. Based on accepted feature-integration commit `32a506eeda0c749b661b9885ce8a79d66eb01c4b`. The checkpoint below records completed host/device/CI checks and the feature-integration merge.

The unscanned or failed cleanup description and Analyze storage action now share a full-width column with `AtomicSpacing.s12` between them. The button's full-width outer box centers its existing intrinsic visual, matching the Analyze file types action. No shared button styling changes are required.

The existing `start_cleanup_analysis` and `cleanup_unscanned` tags, explicit scan callback, file-type-scan admission guard, error wording and scan lifecycle behavior are preserved. No dependency, permission, storage traversal or automatic scan is added.

Initial preparation covered source review and `git diff --check`; the completed affected Analysis and screenshot checks are recorded below. This focused UI change does not complete U05 or main/release acceptance.

## Verified checkpoint — 22 October

All 5 existing Analysis opt-in cases, ktlint/detekt and debug/AndroidTest packaging pass in `analysis-action-alignment-validation.log`, exit 0, 2m53s. Head `7975a2e` passes CI `39801109884`, including debug/release Lint and full unit/APK packaging; its manual emulator job is skipped. Review and a clean guarded trial after startup #72 preceded feature-integration merge `dbb43f075fd26e0deb41e0d9467e8b8c0d973b65`.

The final API34 app passes the four native cases in 25.425s on combined `bc6d9f0` (also including independent #71/#72). The idle Analysis screenshot was reviewed with no scan requested: cleanup label center and viewport center both equal 540px, with clear spacing beneath the description. `startup-analysis-final-visual.exit` is 0; Home is restored. Native finalization verifies unchanged access and removal of the test APK. Source app tree and APK hashes are recorded in the startup validation record and `startup-analysis-final-acceptance/apk-hashes.json`. This is combined-artifact evidence, not a full plugin or main/release acceptance claim.
