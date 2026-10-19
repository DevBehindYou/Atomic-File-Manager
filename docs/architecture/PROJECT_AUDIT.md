# Project audit

Baseline `7782660`, 17 October 2026. Static review of application, domain/data/UI, build logic, tests, CI, manifests, version catalog and the documentation/handover families. Read-only workstreams independently examined storage, UI/search/performance, and infrastructure/security. This is an evidence-based audit, not release certification or proof that every possible defect was found.

Paths below are relative to `app/src/main/kotlin/com/devbehindyou/atomicfilemanager/` unless specified. Severity describes consequence; completion is tracked separately.

| ID / severity | Verified problem and root cause | Evidence at baseline | Remedy / acceptance |
|---|---|---|---|
| A01 / P0 | Extraction writes final destination before validation; failure can delete an existing file | `domain/usecase/ArchiveOperationsHelper.kt:224-255`; `data/backend/FileSystemBackend.kt:244,252` | Stage uniquely, fail safely on collisions, publish only completed output; original survives corrupt/cancelled archive |
| A02 / P0 | Queue starts mutation independently of initial journal write; DB exceptions swallowed | `data/operations/OperationQueue.kt:120-142` | Await durable intent before engine; failed persistence must prevent mutation |
| A03 / P0 | Wi-Fi upload uses predictable temporary file, truncation and check/rename publication | `data/share/ShareServer.kt:213-220` | Unique exclusively created staging and no-clobber publication; concurrent same-name uploads cannot corrupt data |
| A04 / P1 | Compression/extraction ignores input/list/output errors; nested extraction invents opaque IDs | `domain/usecase/ArchiveOperationsHelper.kt:47,91,123,225,284-296` | Propagate failures and resolve backend-returned child IDs |
| A05 / P1 | Secondary ViewModel directly constructed and never cleared; unnecessary on single pane | `ui/screens/BrowseScreen.kt:127-141`; `BrowseViewModel.kt:88-94` | Owned lifecycle, create only when needed, verify collectors stop |
| A06 / P1 | Listing/stat suspend signatures do not dispatch IO; browsing invokes them on Main | `BrowseViewModel.kt:111-133`; FileSystemBackend/SafBackend listing/stat implementations | Inject IO dispatcher at listing/metadata use-case boundary; verify flow and suspend paths |
| A07 / P1 | Every buffer emits progress; only speed arithmetic is throttled; unlimited DB-write queue | `FileOperationsEngine.kt:125-138`; `OperationQueue.kt:58,213` | Bound progress persistence and throttle actual emissions; keep terminal states and cancellation |
| A08 / P1 | Trash insert cancellation can leave unrecorded moved file; restore collision rename can stale persisted ID | `domain/usecase/TrashManager.kt:84-87,112-120` | Durable transaction/recovery design and fault tests before altering trash lifecycle |
| A09 / P1 | SAF getNode parent null defeats ancestor guard; recursive destination can consume own output | `data/backend/SafBackend.kt:66`; `FileOperationsEngine.kt:498-552` | Provider-aware ancestry validation before mutation; opaque-ID regression fixture |
| A10 / P1 | Wi-Fi stop leaves active sockets; executor has unbounded backlog | `data/share/ShareServer.kt:56,76-87` | Bounded connections, close accepted/queued sockets on stop |
| A11 / P1 | Category filtering after SQL result cap hides matches | `data/database/room/SearchIndexTable.kt`, `ui/screens/SearchScreen.kt` | Filter before limit; adversarial >2,000 matching-directory fixture |
| A12 / P1 | Breadcrumb parsing treats provider/network IDs like local paths | `BrowseViewModel.kt:247-264` | Backend parent/history navigation; never manufacture opaque IDs |
| A13 / P1 | FTPS protects control channel only: PROT P requested but data socket plain | `data/backend/network/FtpBackend.kt:555,609-612` | TLS data-channel and hostname verification with protocol integration tests |
| A14 / P2 | FTP rejected LIST becomes successful empty folder | `FtpBackend.kt:421-424` | Typed failure; test denied/disconnected listings |
| A15 / P1 review | WebDAV XML parser lacks explicit DTD/entity restrictions; response fully buffered; no no-overwrite preconditions | `WebDavBackend.kt:130,172,472-475` and PUT/MOVE implementation | Bound response, disable external XML entities, test server preconditions. XXE exploitability not established on Android |
| A16 / P2 | Build/dependency integrity gaps | wrapper lacks checksum; no dependency verification/lock or vulnerability CI | Verify upstream artifacts, pin checksum, add deliberate dependency review; do not fabricate CVE clearance |
| A17 / release gate | Debug-signed release, limited device evidence | `app/build.gradle.kts`, handover ISS-001/002 | Owner signing and actual supported-device acceptance |
| A18 / P2 | Large shell, UI/data coupling, promised diagnostics absent | MainActivity, direct screen orchestration; LOGGING_DIAGNOSTICS specification | Incremental orchestration seams and privacy-conscious diagnostics after integrity fixes |

## Verification and risk interpretation

The UI audit reproduced A11 with the production SQL on host SQLite: 2,000 matching folders plus one matching PDF produced zero PDF results after UI filtering, despite one true match. This is query correctness evidence, not Android latency measurement.

The current environment has Java21 and an Android SDK; initial Gradle bootstrap failed with a distribution download timeout after cache access was authorized. The official distribution was subsequently downloaded and SHA256 verified. GitHub authentication succeeds outside the sandbox; the initial invalid-token report was a sandbox false negative. Historical CI passes do not establish the patched revision's status. See [baseline](../testing/PERFORMANCE_BASELINE.md) for subsequent attempts.

The manifest FileProvider uses a broad root-path but is unexported: reduce exposed roots after checking legitimate sharing flows; do not label this alone a remotely exploitable vulnerability. SFTP pins known hosts and credentials use Keystore encryption. Keep these safeguards. The `requestLegacyExternalStorage` attribute is stale against project rules. Privacy text requires correction to describe intentional sharing, without inventing a new privacy policy.
