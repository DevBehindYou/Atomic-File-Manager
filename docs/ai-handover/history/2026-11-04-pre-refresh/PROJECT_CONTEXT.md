# Project Context

Last updated: 2026-10-08
Confidence: HIGH

## Project

Atomic File Manager. The repository is `DevBehindYou/Refract-Full-File-Manager`, and the launcher name and icon are "Refract". It is a native Android file manager whose goal is "all-in-one without clutter": the power features of Solid Explorer, MiXplorer and Total Commander behind a calm, plain-language UI.

## User goal

Implement `docs/roadmap/ALL_IN_ONE_PLAN.md`: Phases 0 to 4, plus the §16 reliability and optimization plan. The work is driven autonomously:

- one PR per task, on `claude/<topic>` branches off `main`;
- PRs run in parallel;
- a PR is auto-merged when CI is green and it has no merge conflict;
- the user gets regular progress reports as a percentage.

When the work reaches about 99%, hand over with the AI-HANDOVER/V2 protocol (this folder).

## Functional scope (decided)

- Product requirements: `docs/PRODUCT_REQUIREMENTS.md`, `docs/FUNCTIONAL_REQUIREMENTS.md`.
- Scope: `docs/PRODUCT_SCOPE.md`, updated by plan §10.
- Plan §5 is the feature matrix, and §11 lists what is deliberately skipped: root explorer, skin editor, disk images, EncFS, ads, media library.

## Non-functional

- Performance budgets are in plan §7.5 and §16.1. Examples: cold start under 1 s, first rows under 300 ms, 0 frames over 32 ms.
- Safety:
  - every mutation goes through `FileOperationsEngine` and the operation journal;
  - Trash is the default;
  - verified copies (SHA-256 while streaming, then a re-read);
  - atomic writes (stage, `sync()`, rename).
- Accessibility rules: `docs/ACCESSIBILITY.md`. Plain language: plan §7.4.

## Stack

- Kotlin 2.2.10, Jetpack Compose (BOM 2026.04.01) and the custom Atomic design system (`core/designsystem`).
- AGP 9.1.1, Gradle 9.3.1, Java 21. minSdk 27, target and compile SDK 36.
- Modules:
  - `:app`
  - `:benchmark` (macrobenchmarks and the baseline-profile generator)
  - `:lint-rules` (custom Lint checks)
  - `build-logic` (convention plugins)
- Hilt is declared, but the active dependency graph is **manual**: `AppContainer` / `DefaultAppContainer` in `AtomicApp.kt`.
- Data:
  - Room `AtomicDatabase` v4: operation journal, trash, shortcuts, search index.
  - SQLiteOpenHelper for transfer bubbles and hidden files.
  - SharedPreferences for settings and network servers.
- Network:
  - sshj (SFTP)
  - smbj 0.15.0 (SMB)
  - FTP and WebDAV backends
  - BouncyCastle 1.86, with prov, util and pkix all on the same version
- Optional Shizuku 13.1.5, read-only, under Labs.

## Platforms

Phone first, plus tablet and foldable layouts. Android 8.1 (API 27) up to API 36.

## Explicit constraints (user and owner)

- Owner decisions in plan §10 (7 Oct 2026), all approved:
  - finish the FTP, SFTP, SMB and WebDAV clients;
  - a guarded Wi-Fi share is allowed, but no general FTP or HTTP server;
  - cloud only through Android document providers and WebDAV, with no accounts inside the app;
  - no root; Shizuku is optional;
  - plain-text editor only;
  - a Media3 preview player only;
  - the Liquid Glass tier is dropped;
  - the Atomic design system replaces plain Material 3.
- Rules for commits and PRs:
  - No model identifiers in commits, PRs or code.
  - Commits end with the `Co-Authored-By:` and `Claude-Session:` trailer lines the session specifies.
  - PR bodies end with the "Generated with Claude Code" line and the session link.
  - GitHub comments end with the Claude Code footer.
- Never skip, disable or quarantine a test to get CI green. Never rewrite history on a shared branch.
- No secrets in the repo. Release builds are currently **debug-signed**, and the production key is not in the repo.

## Out of scope

The items in plan §11. Native OAuth cloud accounts are future work.
