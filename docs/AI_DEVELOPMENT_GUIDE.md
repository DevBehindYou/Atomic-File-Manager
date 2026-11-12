# AI Development Guide

**Current coordination:** Claude Code and Codex use the [Coordinator Dictionary](dictionary/coordinator/README.md) for ownership, execution order and review. The owner-approved current minimum is **API28**; older API27 instructions below are historical and do not override the current source baseline. Current accepted architecture and safe implementation gates remain authoritative.

**For Google AI Studio, Gemini, Claude, or any AI agent implementing this project.**
Read this file and `README.md` before writing a single line of code.

---

## 1. The thirteen rules

1. **Read the documentation before coding.** For any task, read `README.md`, then the
   specific docs that govern it. A task touching storage requires
   `ANDROID_STORAGE_RESEARCH.md` + `architecture/STORAGE_ARCHITECTURE.md`. A task touching UI
   requires `DESIGN_SYSTEM.md` + `COMPONENT_LIBRARY.md`.
2. **Never rewrite the architecture casually.** If you believe a design here is wrong, say so
   and explain why *before* changing it. Do not silently substitute your own approach.
3. **Build incrementally.** One phase from `roadmap/IMPLEMENTATION_ROADMAP.md` at a time. Do
   not generate the whole app in one pass; it will not compile and it will not be reviewable.
4. **Keep every increment compilable.** If a change leaves the project not building, either
   finish it or revert it. Never hand back broken code with "you'll need to add X".
5. **Never replace working functionality to simplify your implementation.** If an existing
   feature is inconvenient for your change, adapt your change.
6. **No placeholder implementations in production paths.** No `TODO()`, no
   `throw NotImplementedError()`, no returning empty lists to make it compile, no stub that
   pretends to succeed. If something cannot be implemented yet, do not wire it into the UI.
7. **Never fake unavailable Android capabilities.** If a feature requires API 33 and the
   device is on 27, implement the documented fallback or disable the feature — never
   simulate the result.
8. **Verify API compatibility for every call.** Check the API level of every platform API you
   use against `minSdk = 27`. Guard with the named constants from `CODING_RULES.md` §38.
9. **Preserve `minSdk = 27`.** Raising it is a product decision, not an implementation
   convenience.
10. **Check compilation after every significant change**, and run the affected tests.
11. **Add tests for important business logic** — every use case, every operation invariant,
    every API-level branch.
12. **No deprecated APIs** without a comment stating the compatibility reason and the
    replacement's minimum API level.
13. **Document architectural changes** in the relevant doc, in the same change. Code and docs
    drift the moment you allow them to.

---

## 2. Before you write code, state your plan

For each task, output this first:

```text
TASK:        <what you are implementing>
PHASE:       <which roadmap phase it belongs to>
DOCS READ:   <the files you consulted>
FILES:       <files you will create or modify>
API LEVELS:  <any version-gated behaviour and its fallback>
RISKS:       <what could break>
TESTS:       <what you will add>
```

If the task spans more than about six files, split it and do the first part.

---

## 3. Hard prohibitions

These will be rejected in review every time:

| Never | Why |
|---|---|
| `java.io.File` in `domain`, `feature.*`, or a ViewModel | Breaks the boundary that makes the app testable and multi-backend |
| Raw `SDK_INT` integer comparisons | Unsearchable, unmaintainable |
| `requestLegacyExternalStorage` | Does nothing at target 36 and triggers a Play warning |
| Hardcoded paths (`/sdcard`, `/storage/emulated/0`) | Wrong on many devices |
| `readBytes()` / `readText()` on a user file | Guaranteed OOM on large files |
| Deleting a source before verifying a copy | Data loss |
| Catching `CancellationException` without rethrowing | Silent cancellation failure |
| `runBlocking` in production code | ANR |
| Hardcoded colours/dp/durations outside the design system | Breaks theming and the glass tiers |
| A composable that performs I/O | Runs unpredictably and on the wrong thread |
| Glass on list items | Destroys scroll performance |
| Auto-selecting all duplicates | Destroys photo libraries |
| A permission request without a rationale screen | Play review risk and user hostility |
| `fallbackToDestructiveMigration()` | Data loss |

---

## 4. When the documentation is incomplete or wrong

You will find gaps. When you do:

1. **Say so explicitly.** "The docs do not specify X."
2. **Propose the smallest decision that unblocks you**, with a one-line rationale.
3. **Implement it and record it** in the relevant doc in the same change.
4. **Never invent Android behaviour to fill the gap.** If you are unsure whether an API
   behaves a certain way on a given version, say you are unsure and check the official
   documentation. A confident wrong claim about `MANAGE_EXTERNAL_STORAGE` costs days.

Things you must **not** decide unilaterally: the minimum SDK, the permission model, the
storage abstraction, the operations-engine invariants, the privacy policy, or the priority
order in `README.md` §2.

---

## 5. Order of implementation

Follow `roadmap/IMPLEMENTATION_ROADMAP.md`. In particular:

* **Storage and operations come before UI polish.** A beautiful app that loses files is a
  failure; a plain app that never loses a file is a product.
* **The Liquid Glass system is Phase 8, deliberately late.** Build the app in plain Material
  first. Glass must be addable — and removable — without touching feature code.
* **Do not build all five screens before any of them works end to end.** Home + Browse +
  operations is the vertical slice that proves the architecture.

---

## 6. Definition of done for any task

- [ ] Compiles with zero warnings
- [ ] ktlint + detekt clean
- [ ] Unit tests for new logic; they pass
- [ ] No new Lint suppressions (or each is justified in a comment)
- [ ] Previews render at light, dark, font 2×, and all three glass tiers where UI is involved
- [ ] Semantics present on every new interactive element
- [ ] Runs on an API 27 emulator **and** an API 36 emulator without a crash
- [ ] No new dependency, or the dependency is added to `TECH_STACK.md` with a justification
- [ ] Docs updated if behaviour changed

---

## 7. Prompt patterns that work with this repo

**Good:**
> Implement Phase 2 from the roadmap: the `FileNode` model, the `StorageBackend` interface,
> and `FileSystemBackend`. Follow `architecture/STORAGE_ARCHITECTURE.md`. Do not implement
> the SAF or MediaStore backends yet. Add unit tests for the id encoding/decoding round trip.

**Bad:**
> Build the file manager app.

**Good:**
> The Browse screen drops frames when scrolling a 5,000-item folder. Diagnose against
> `architecture/PERFORMANCE.md` §3 before changing anything, and tell me the cause first.

**Bad:**
> Make it faster.

---

## 8. If you are about to disagree with this document

Good. Say it plainly, give your reasoning, and propose the alternative. Silent divergence is
the failure mode; disagreement is not. But the priority order in `README.md` §2 —
file safety above everything, visual effects last — is not negotiable, and any proposal that
inverts it will be rejected.
