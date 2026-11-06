# Shared-storage permission delegation

Date: 2026-10-19. Focused extraction from source checkpoint 55540f3. Integrated Gradle and device validation remain pending.

SharedStorageAccess centralizes the existing shared-storage access check and settings request sequence. API 27–29 continues to check READ_EXTERNAL_STORAGE. API 30+ continues to query Environment.isExternalStorageManager and opens app-specific all-files settings, falling back to global all-files settings and finally the caller's existing READ/WRITE permission launcher when both activities fail. Returning from settings does not imply that access was granted.

The helper owns no Context, mutable state, launcher, lifecycle observer, coroutine scope or result callback. MainActivity retains its launcher registration, ON_RESUME/result refresh, hasStorageAccess state, pending-volume navigation, and runCatching fail-closed access check. StorageVolumes retains its existing uncaught permission-check failure behavior and directory readability/root selection. Minimum SDK remains 27; the API 30 calls remain guarded by the SDK check. No broader permission or background access is added.

Eight test methods provide 18 Robolectric API variants: legacy access checks, denied/granted changes and caller launcher delegation on API 27/29; modern app-op access, app settings, global settings fallback and legacy-launcher fallback on API 30/34; and propagation of a failing caller launcher without duplicate invocation on all four APIs. The existing permission-check exception identity test runs on API 27/29.

Review against the base confirmed that the extraction preserves branch order, fallback count, intent action/data, caller exception handling and caller lifetimes. Standalone ktlint and git diff --check passed. No Gradle process, phone operation or remote publication was run from this worktree. This is architecture cleanup; no measured runtime speedup is claimed.
