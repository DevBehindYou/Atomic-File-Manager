# Cloud Connect implementation plan

27 October 2026. Status: source audit and research recorded; implementation unstarted. See [index](../CLOUD_CONNECT.md), [tasks](TASKS.md) and [research](RESEARCH.md).

## Source of truth

Inspected accepted feature integration `863f78702de1066f6d7fc3c7f8dddef9788ffb6d`; the initial audit was also checked on beta `5a72341`, whose relevant production source is unchanged. Paths below are relative to `app/src/main/kotlin/com/devbehindyou/atomicfilemanager/`.

| Source | Observed contract | Consequence for first increment |
| --- | --- | --- |
| `domain/repository/StorageBackend.kt` | Device/network backend types; listing is `Flow<FileResult<List<FileNode>>>` | Add explicit page/completion semantics at a narrow cloud boundary; preserve current backends |
| `domain/model/FileNodeId.kt` | No cloud prefix or cloud-account identity codec | Versioned provider/account-qualified opaque IDs; do not derive identity from display paths |
| `domain/model/FileNode.kt` | Size, modification time, parent, access flags; no opaque remote revision | Preserve ETag/revision separately; timestamps cannot authorize overwrite |
| `domain/model/StorageCapabilities.kt` | Capability flags exist, including resume and server-side copy | Project actual account/item capabilities; do not copy generic REMOTE_NETWORK flags |
| `data/backend/network/NetworkCredentialsStore.kt` | Keystore-protected network passwords and server configurations | Reuse protection ideas, not the password schema for OAuth expiry/scopes/refresh ownership |
| `data/volume/LinkedFolders.kt` | User-linked SAF folders with provider-app labels | Keep working device-app access separate from direct accounts |
| `data/backend/StorageBackendSelector.kt`, `AtomicApp.kt` | Device/network dispatch and manual container registration | Register a connector only with usable capabilities and tests |
| `ui/screens/InAppPluginsSheet.kt` | Eight shipped entries and per-plugin versions | Add ninth entry only when a useful Cloud increment ships |

## Product boundaries

Reuse the existing U07 three-way Add storage design: Device apps, Cloud accounts and Network. Give each connected account a stable label and root shortcut. Keep available methods separate from connected accounts. Cloud engines load on demand; opening the inventory or Storage must not scan remote accounts.

Start with one provider. Drive remains the existing conditional first candidate; its selected-file product and broad-drive browser require different scopes. Dropbox or OneDrive can become first if registration/scope feasibility makes Drive unsuitable, then a second provider tests the abstraction. Do not silently choose broader permissions. Box and MEGA need the specific feasibility work in Research.

No automatic sync, persistent offline copy, universal resume, cross-provider atomic move or background transfer is promised by this plan. Metadata/content caching requires explicit ownership, bounds, expiry and disconnect cleanup. Do not recursively scan private cloud content to populate storage statistics.

## Ordered PR-sized increments

1. **Account and identity contracts:** typed provider/account/remote IDs, availability, revisions, capabilities and bounded page results. Keep adapters small and avoid refactoring every storage backend for hypothetical future APIs.
2. **Credential lifecycle:** provider-supported native authorization, request ownership and redirect/state validation, protected token persistence where the SDK does not own it, single-flight refresh, account-generation checks, reconnect and disconnect. No embedded confidential-client secret.
3. **One read-only connector:** real sign-in, account-root navigation, paginated listings, cancellation and bounded downloads through the existing opening path. Retain partial/error distinction and native-document export limits.
4. **Mutating transfers:** explicit conflict choice and provider revision preconditions, verified publication, bounded upload/download, Tasks progress and cancellation. Failed/ambiguous writes require reconciliation; never replay blindly or delete the source prematurely.
5. **Acceptance and inventory:** disposable-account native cases, release-like size/performance evidence, exact-head CI/review and clean integration; expose only verified capabilities in Cloud Connect About v0.2.
6. **Second provider:** run the same contract suite with provider-specific adaptations before claiming a unified cloud implementation.

Existing beta release work retains priority. Independent documentation/mock contracts may proceed while registration is unavailable; native authentication and transfer acceptance cannot be simulated into completion.
