# Analysis cleanup action alignment candidate

Date: 22 October 2026. Based on accepted feature-integration commit `32a506eeda0c749b661b9885ce8a79d66eb01c4b`. This record describes a layout candidate; host and device validation are pending.

The unscanned or failed cleanup description and Analyze storage action now share a full-width column with `AtomicSpacing.s12` between them. The button's full-width outer box centers its existing intrinsic visual, matching the Analyze file types action. No shared button styling changes are required.

The existing `start_cleanup_analysis` and `cleanup_unscanned` tags, explicit scan callback, file-type-scan admission guard, error wording and scan lifecycle behavior are preserved. No dependency, permission, storage traversal or automatic scan is added.

Source review and `git diff --check` are the only checks performed for this candidate. The coordinating task must run existing affected Analysis opt-in checks and inspect a phone screenshot before claiming the alignment is verified. This focused UI change does not complete U05 or main/release acceptance.
