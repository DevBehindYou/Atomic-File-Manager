# Changes

## Current validation — 20 October 2026

Current source `4792db8` includes Files storage shortcuts, persisted List/Grid views, content-sized top options, responsive icon-card action menus and distinct theme-aware icons/colours for 15 file families. All 917 app JVM/Robolectric cases, 28 custom Lint-rule cases, ktlint/detekt and debug/instrumentation packaging pass. All 13 selected API34 phone cases pass; manual popup sizing, Commands/Select grids and coloured folder icons are verified. The installed debug APK matches the recorded build hash, and the settled app is back on Home. The current selected log contains no StrictMode or fatal errors; a prior InsetsSourceControl warning remains historical evidence. Debug/release Android Lint, R8 release and benchmark test-APK packaging passed in the complete Gradle run (exit 0). See [menu/icon validation](../testing/MENU_REFINEMENT_VALIDATION.md), [Files validation](../testing/FILES_WORKSPACE_VALIDATION.md) and [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md). Broader provider, hardware, recovery, performance and owner-signing acceptance remains open.

Grouped by plan area. Git and the PRs have the line-level detail; this file keeps the why and the result. A status of IMPLEMENTED means merged with CI green (unit tests, Robolectric, Lint and the R8 build); it does **not** mean tested on a device.

## Modernization checkpoint — 20 October 2026

The safety/architecture changes remain integrated locally with seventeen draft PRs and no merges. The previous complete host checkpoint `5705606` passed 712 app tests and 28 custom Lint-rule tests; startup's manual API34 retest recorded zero StrictMode disk warnings. Subsequent changes add comparison preference IO, Kotlin-plugin loading cleanup, shared-storage access, URI thumbnails and private bounded folder miniatures, removal of per-row More boxes, pane-owned contextual actions, secure read-only DOCX, bounded owned PDF/media sessions, all eight image EXIF orientations and editor draft retention across recreation. The current validation above supersedes that host/device checkpoint. Earlier release/Lint evidence remains checkpoint-specific; current packaging results are recorded above. See [IMPLEMENTATION_PROGRESS](../roadmap/IMPLEMENTATION_PROGRESS.md), [viewer audit](../testing/BUILT_IN_VIEWER_AUDIT.md) and [REVIEW_BRANCHES](../roadmap/REVIEW_BRANCHES.md); no remote CI or device speedup is claimed.

## Historical merged feature work

| ID | Plan | What | PRs | Status |
|---|---|---|---|---|
| CHG-001 | 0.1 | Durable operation queue: journal, resume prompt, notification, Operations screen | #1 | IMPLEMENTED |
| CHG-002 | 0.5 | R8 and resource shrinking; macrobenchmark module | #2, #18 | IMPLEMENTED |
| CHG-003 | 1.5, H7 | APK info, Coil thumbnails, image gallery, Media3 audio and video | #3, #4, #7, #15 | IMPLEMENTED |
| CHG-004 | 1.4 | Room `search_index` and search screen; updated after the app's own operations | #5 | IMPLEMENTED |
| CHG-005 | 1.1–1.3 | Trash with retention and undo, favourites and recents, selection power (on branch #1) | #1 | IMPLEMENTED |
| CHG-006 | 2.x | Checksum compare, batch rename, text editor (atomic save), smart cleanup, tabs, app manager | #6, #8–#13 | IMPLEMENTED |
| CHG-007 | 2.2 | Archives: TAR, GZ, BZ2, XZ, 7z, RAR extraction, password-protected ZIP | #12, #19, #20 | IMPLEMENTED |
| CHG-008 | 2.5 | Encrypted vault (Tink); Private Storage encrypts new and existing items; export of decrypted copies | #16, #17, #21, #35 | IMPLEMENTED |
| CHG-009 | §10 | The owner's scope decisions recorded in `PRODUCT_SCOPE.md` and FR-10.3 | #22 | VERIFIED (docs) |
| CHG-010 | 3.1 | Real SFTP (sshj) and SMB (smbj) backends with connection pooling and reconnect-once | #23, #28 | IMPLEMENTED (SFTP covered by an in-process sshd test; SMB has no live test) |
| CHG-011 | 3.2 | mDNS server discovery in the Add server dialog | #30 | IMPLEMENTED_UNTESTED on device |
| CHG-012 | 3.3 | Cloud through document providers ("Linked folders" on Storage) | #31 | IMPLEMENTED |
| CHG-013 | 3.4 | Guarded Wi-Fi share: PIN, read-only by default, same Wi-Fi only, stops on leave or after 15 minutes idle; Quick Settings tile | #32 | IMPLEMENTED_UNTESTED on device |
| CHG-014 | 3.5 | USB/SD names, errno-to-`StorageUnavailable` mapping, mount receiver, `USB_OTG_CHECKLIST.md` | #33 | IMPLEMENTED_UNTESTED on device |
| CHG-015 | 4.1 | Folder compare, one-way sync (copy new or mirror, with mirror deletes going to Trash), saved pairs | #27, #34 | IMPLEMENTED |
| CHG-016 | 4.2 | EXIF view, share clean copies, audio tags | #24, #29 | IMPLEMENTED |
| CHG-017 | 4.3 | Shizuku read-only backend for `Android/data` and `obb`, opt-in under Labs | #36 | IMPLEMENTED_UNTESTED on device |
| CHG-018 | 4.4 / 4.5 | Command palette (Ctrl+K), launcher shortcuts, pinned folders, storage widget | #25, #26 | IMPLEMENTED |
| CHG-019 | 0.1 / 16.8 | `ProcessKillTest`: simulated kills mid-write, before publish, while replacing, after publish | #37 | VERIFIED (JVM) |
| CHG-020 | 16 H8 | Copy buffer 64 KB → 256 KB (ADR-003) | #38 | IMPLEMENTED; speed not measured |
| CHG-021 | 16.1 / 16.8 | `profileinstaller`, `BaselineProfileGenerator`, debug JankStats (`AtomicJank`), monkey test in the emulator job | #39 | IMPLEMENTED; the profile itself is not generated yet |
| CHG-022 | 16 H5 / H6 | Categories show index files at once (ADR-004); the last analysis is reused for 10 minutes | #40 | IMPLEMENTED |
| CHG-023 | 16 H9 | `BrowseScreen.kt` split into five files, behaviour unchanged | #41 | IMPLEMENTED |
| CHG-024 | — | This handover (`docs/ai-handover/`); `docs/SESSION_HANDOFF.md` moved to `history/` | this PR | — |

Already done before this session (plan §16): H1 (volumes loaded off the main thread), H2 (SQLite repositories load on IO), StrictMode in debug builds, listing in chunks of 200, stable list keys.
