# Architecture decisions — modernization

17 October 2026. Existing decisions remain in [handover DECISIONS](../ai-handover/DECISIONS.md); this ledger records this audit's additions without duplicating that history.

* **ADR-M01: preserve the modular monolith and manual DI.** Existing storage seams and tests justify incremental changes. No measured build reason for new Gradle modules and no benefit proven for Hilt migration.
* **ADR-M02: durable admission precedes mutation.** An asynchronous enqueue API does not justify running before Room acknowledges intent. A failed initial journal write fails closed. Keep optional journal-less testing mode explicit.
* **ADR-M03: stage output and never silently overwrite.** Archive/share code must preserve preexisting data on failure. Treat no-clobber publication and crash durability as separate guarantees. Generic StorageBackend does not yet promise atomic external-writer exclusion.
* **ADR-M04: lifecycle owns readers; application owns mutations.** Secondary browsing ViewModels must be cleared with their owner and are unnecessary in single-pane mode. Queued operations survive screen removal.
* **ADR-M05: bound work before increasing concurrency.** Keep serial mutations; remove unbounded progress-write amplification and share connection backlog. Real progress throttling is justified by source-level event count, not invented device speedups.
* **ADR-M06: source and current evidence supersede historical status.** Retain historical product plans, add authoritative current inventory and explicit evidence scope. No claim of production readiness until device/recovery/signing gates pass.

API guidance consulted: [Kotlin cancellation](https://kotlinlang.org/docs/cancellation-and-timeouts.html), [ViewModel lifetime](https://developer.android.com/topic/libraries/architecture/viewmodel), [Gradle wrapper integrity](https://docs.gradle.org/current/userguide/gradle_wrapper.html). The Context7 skill was inspected, but this session exposes no Context7 tools; official documentation is the fallback. Existing pinned versions remain unchanged.
