# Active decisions

Updated **4 November 2026**. [Earlier ADR details](history/2026-11-04-pre-refresh/DECISIONS.md) remain historical context.

| ID | Decision and reason | Revisit when |
| --- | --- | --- |
| ADR-001 | Keep manual AppContainer DI; don't infer migration from Hilt declarations | A scoped migration is justified |
| ADR-002 | Preserve custom routes and existing callback ownership | Actual navigation requirements change |
| ADR-003 | Preserve streaming verification/journal; current 256 KB buffer is not a measured speedup | Integrity and measurement justify a change |
| ADR-004 | Keep category walk until index/MediaStore freshness is correct | Generation-aware freshness is proven |
| ADR-005 | Keep BouncyCastle family versions consistent | Researched/tested dependency upgrade |
| ADR-006 | Review receivers against current source/policy; historical API27 workaround is not a new security proof | Receiver/permission changes |
| ADR-007 | Existing Shizuku is optional/read-only; preserve phone permissions | Explicit scoped decision |
| ADR-008 | Bundled native Kotlin/Compose plugins; independent About version starts v0.2 | Explicit architecture decision |
| ADR-009 | App target stable 0.2.1; published beta immutable; preserve release key | Validated dedicated release increment |
| ADR-010 | API28 accepted; Cloud research differs from functional direct accounts | New platform/provider evidence |
| ADR-011 | Auto-merge requires current applicable checks, review, integrated dependencies and clean fresh-target trial | Owner changes authorization |
| ADR-012 | Separate fixed U13/N42 credit; audit and partial slices earn no full-group credit | Logged owner scope/denominator change |
| ADR-013 | GIF direct-buffer/callback-free lifecycle ownership; Stop/Replay labels reflect reset semantics | New controller/decoder with real native evidence |

Use current official sources for platform/dependency decisions; source inspection is not runtime acceptance.
