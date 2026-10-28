# Cloud Connect feature matrix

27 October 2026. Target version v0.2 is unshipped. This matrix separates source facts from proposed behavior; see [Plan](PLAN.md).

| Feature | Current Atomic state | First useful target / boundary |
| --- | --- | --- |
| Android provider-app folders | Existing SAF linked folders | Preserve grants and provider-owned authentication |
| Network storage | Existing SFTP/SMB/FTP/FTPS/WebDAV backends | Preserve separate setup and credentials |
| Direct cloud accounts | Absent | One registered provider with account isolation |
| Cloud account shortcuts | Absent | Stable account label and Files root |
| Paginated cloud browsing | Absent | Explicit partial/complete/error, deduplicated stable IDs |
| Cloud file opening | No direct connector | Bounded downloads; explicit native-document export where supported |
| OAuth reconnect/revocation | No direct connector | Actionable state, safe refresh ownership, local disconnect cleanup |
| Cloud upload/copy/move | Absent | Explicit conflicts and revision conditions; source retained until publication verified |
| Tasks integration | Existing operation UI, no direct cloud tasks | Show actual transfer state; pause/resume only when implemented |
| Quota/storage use | No direct cloud quota | Unknown is distinct from zero; no implicit full-account scan |
| Persistent offline files / sync | Not implemented by this track | Separate later product, retention and recovery decision |
| Cloud Connect About | Not in eight-entry inventory | Add v0.2 only with shipped useful behavior |

Candidate research does not establish Atomic support. Drive selected-file access is not full-drive access. Do not describe all providers as interchangeable, all remote files as writable, or cloud document formats as ordinary downloadable byte streams.
