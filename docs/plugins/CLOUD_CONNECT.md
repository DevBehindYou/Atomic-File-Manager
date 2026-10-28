# Cloud Connect plugin — v0.2 target

Recorded 27 October 2026. **Research and planned work only; not shipped.** Cloud Connect is the requested ninth bundled plugin, separate from the existing eight-plugin modernization scope. Its target Atomic plugin version is **v0.2**, not an SDK version or a completed release.

The plugin will connect supported cloud accounts to Atomic's Files, opening and operation interfaces. Keep native Kotlin/Compose and ship connector code with the app. No executable plugins, installers or independent updates are downloaded from GitHub, Google Drive or other providers.

On 27 October 2026, the owner explicitly requested raising Atomic's global minimum SDK from API27 to API28 because of MEGA's documented requirement. This is the requested baseline; source integration and release validation are pending separately. It removes the planned API27 compatibility condition once integrated, but does not establish a working MEGA connector or change the first-provider decision.

Existing Android-provider linked folders and network connections remain distinct connection methods. A provider app owning an Android SAF grant is not a direct Atomic OAuth account. Do not add inactive Connect controls or imply support by displaying provider logos.

| Record | Purpose |
| --- | --- |
| [Plan](cloud-connect/PLAN.md) | Source audit, boundaries and ordered increments |
| [Features](cloud-connect/FEATURES.md) | Existing capabilities versus planned connector behavior |
| [Tasks](cloud-connect/TASKS.md) | Work items, dependencies and open decisions |
| [Bugs](cloud-connect/BUGS.md) | Reproduced bugs versus unverified risks |
| [Validation](cloud-connect/VALIDATION.md) | Evidence and acceptance requirements |
| [Research](cloud-connect/RESEARCH.md) | Dated official provider sources and uncertainties |

This track implements the cloud portions of [U07/U08/U12](../roadmap/FEATURE_UPGRADE_PLAN.md); it must not duplicate or inflate their credit. The existing [N42 eight-plugin ledger](modernization/PLUGIN_PROGRESS.md) is unchanged. Cloud documentation is not a new completed capability or a ninth entry in that denominator. Cloud implementation has no accepted features yet; freeze a separate measurable denominator when the first provider and scope are selected.

When a usable increment ships, add Cloud Connect to In-app plugins with version v0.2 and accurate About details, supported providers, connection management and limits. Retain per-provider evidence. No app inventory change is part of this documentation.
