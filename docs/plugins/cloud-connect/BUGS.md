# Cloud Connect bugs and risks

27 October 2026. No direct Cloud Connect implementation exists, so no runtime Cloud bug has been reproduced. Missing features below are design gaps or pending checks, not invented defects.

| ID | State | Observation / risk | Required control or evidence |
| --- | --- | --- | --- |
| CCR01 | Open design gap | Generic network-password storage has no OAuth lifecycle | Dedicated account token state, protected persistence and refresh-generation tests |
| CCR02 | Open design gap | Existing file metadata has no provider revision | Opaque revision preconditions; never infer safety from size/time |
| CCR03 | Open design gap | Existing listing type does not expose cloud continuation/completion | Bounded cloud paging and partial failure semantics |
| CCR04 | Pending auth feasibility | Box documentation shows confidential secret exchange | Verify supported public-native flow or separately approved architecture; never ship a secret |
| CCR05 | Platform-floor decision made; integration and adoption pending | [MEGA README](https://github.com/meganz/sdk/blob/master/README.md#minimum-supported-os-versions) requires Android 9.0/API28; audited Atomic sources target API27. Owner explicitly requested global API28 on 27 October 2026 | Verify separate minimum-SDK integration and release artifacts; no current API27 fallback requirement. Matching the floor does not close native SDK/session/auth, transitive licence/ABI/R8/size or provider-selection gates |
| CCR06 | Pending acceptance | Unknown upload outcome after disconnect can cause duplicate mutation | Provider-specific reconciliation and idempotency; preserve source |

For each reproduced bug add source/PR, precise fixture and expected/actual result, retained failing evidence, fix and current-head verification. Never record account tokens, authorization URLs or private file contents in examples/logs.
