# Cloud Connect tasks

27 October 2026. No implementation task is accepted. These IDs are a work breakdown, not a weighted completion denominator. Existing U07/U08/U12 credit and eight-plugin N42 remain unchanged.

| ID | Priority / state | Task | Exit evidence / dependency |
| --- | --- | --- | --- |
| CC01 | P0 / research recorded | Source audit and provider feasibility | Dated sources, observed contracts and unresolved decisions; parent review pending |
| CC02 | P0 / planned | Provider/account identity and paging contract | Host cases for isolation, malformed IDs, rename stability, continuation, partial failure and cancellation |
| CC03 | P0 / planned | Choose first provider and access scope | Registration feasibility and concrete scope rationale; Drive selected-file versus full-drive explicit |
| CC04 | P0 / planned | Native auth and token lifecycle | Redirect/state checks, decline, expiry, serialized refresh, disconnect races; provider registration required |
| CC05 | P0 / planned | Read-only browse/download adapter | Pagination complete without omissions; no stale account publication; bounded streams and safe file opening |
| CC06 | P1 / planned | Safe transfer mutations | Conflict/precondition/ambiguous-outcome cases; disposable-account verification; existing operation ownership reused |
| CC07 | P1 / planned | Account UI, Files shortcut and plugin details | Working connection actions, accurate v0.2 About, accessibility and recreation evidence |
| CC08 | P1 / planned | Native/release acceptance and second provider | Exact artifacts, auth/transfer fixtures, isolation, size/performance evidence and shared contract reuse |

## Decisions needed only when dependent work is ready

- First provider and selected-file/app-folder versus broader browsing product. Preserve least privilege and explain the actual tradeoff.
- Provider app registration, package/signing identity, allowed redirects and a disposable acceptance account. Public client IDs can be configuration; no confidential secret belongs in the APK.
- Persistent offline retention and background transfers are separate decisions; foreground read-only browsing does not require inventing them.
- A Box public-native authentication method needs verification before choosing it.
- Owner decision, 27 October 2026: raise Atomic's global minimum SDK to API28, matching MEGA's [documented Android 9.0/API28 floor](https://github.com/meganz/sdk/blob/master/README.md#minimum-supported-os-versions). Separate source integration and release validation remain pending. No API27 fallback or optional API28 gate is currently required; reviewed SDK revision, session/auth integration, dependency/licence/ABI/R8/size checks and first-provider selection are still open.

Do independent contract fixtures before asking for registration. Do not request production passwords, reset phone permissions or reopen owner-deferred benchmarks. Update task state with source/PR, exact checks and limitations; retain failed evidence.
