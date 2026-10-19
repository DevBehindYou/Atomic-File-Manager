# Modernization review branches

19 October 2026. All PRs below are drafts; none has been merged. Sources were integrated and checked together on `codex/architecture-audit`; each isolated PR still needs its own current-head CI and review. All created PRs are attached to the current Codex chat.

| Scope | Base | Draft PR |
|---|---|---|
| Wi-Fi upload integrity and connection lifetime | main | [#44](https://github.com/DevBehindYou/Atomic-File-Manager/pull/44) |
| Durable journal admission and visible persistence failures | main | [#45](https://github.com/DevBehindYou/Atomic-File-Manager/pull/45) |
| Storage read IO dispatch | main | [#46](https://github.com/DevBehindYou/Atomic-File-Manager/pull/46) |
| Filter indexed search before result limit | main | [#47](https://github.com/DevBehindYou/Atomic-File-Manager/pull/47) |
| Trash cancellation and retryable restore | main | [#48](https://github.com/DevBehindYou/Atomic-File-Manager/pull/48) |
| Verified Gradle distribution checksum | main | [#49](https://github.com/DevBehindYou/Atomic-File-Manager/pull/49) |
| Secondary browse lifecycle and compact loading | main | [#50](https://github.com/DevBehindYou/Atomic-File-Manager/pull/50) |
| SAF ancestry and pre-mutation directory guard | codex/review-journal-admission | [#51](https://github.com/DevBehindYou/Atomic-File-Manager/pull/51) |
| Archive staging, recursion guards and network root compatibility | codex/review-provider-ancestry | [#52](https://github.com/DevBehindYou/Atomic-File-Manager/pull/52) |
| Actual progress emission throttling | codex/review-provider-ancestry | [#53](https://github.com/DevBehindYou/Atomic-File-Manager/pull/53) |
| Actual-ID breadcrumbs and navigation history | main | [#54](https://github.com/DevBehindYou/Atomic-File-Manager/pull/54) |
| FTPS TLS, transfer status and bounded FTP metadata | main | [#55](https://github.com/DevBehindYou/Atomic-File-Manager/pull/55) |
| Bounded DAV metadata, conditional mutations and URI identity | main | [#56](https://github.com/DevBehindYou/Atomic-File-Manager/pull/56) |
| Portable Android storage fixtures on Windows | main | [#57](https://github.com/DevBehindYou/Atomic-File-Manager/pull/57) |

Review journal admission before provider ancestry, then archives/progress. When a base PR is integrated, retarget its children appropriately and rerun their current-head checks. Preserve branch history and do not force-push shared or protected branches.

Filesystem/provider/protocol changes require human review. No automatic merge is enabled. Remote CI diagnostics could not be read through the Code Review connector because its GitHub account is not connected; local success must not be described as remote CI success.

The local review manifest and exact branch hashes are preserved in `build/audit-tools/review-branches.json`; the isolated publication checkout is `build/audit-worktrees/publish`. The main integration checkout and all original worktrees are preserved. Do not discard them as routine cleanup before review and handover are complete.
