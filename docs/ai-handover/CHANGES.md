# Changes

Grouped by plan area. Git and the PRs have the line-level detail; this file keeps the why and the result. A status of IMPLEMENTED means merged with CI green (unit tests, Robolectric, Lint and the R8 build); it does **not** mean tested on a device.

## Modernization checkpoint — 19 October 2026

These changes are integrated locally and published in fourteen implementation draft PRs plus the audit/documentation draft, with 663 app tests and 28 custom Lint-rule tests passing: archive staging/failure propagation; durable queue admission and visible journal errors; exclusive Wi-Fi uploads and bounded sockets; owned secondary browse lifecycle; IO dispatch; actual progress throttling; cancellation-safe trash reconciliation; filtering before search limits; provider-aware SAF ancestry; actual-ID breadcrumbs; FTPS TLS and bounded replies; bounded complete DAV XML and conditional mutations. ktlint/detekt, debug/release Lint and debug/release/instrumentation APK assembly pass. Four automated API34 UI tests and manual surface checks pass; the [device report](../testing/DEVICE_SURFACE_TEST_2026-10-19.md) records existing main-thread disk reads requiring follow-up. Benchmark packaging remains. See [IMPLEMENTATION_PROGRESS](../roadmap/IMPLEMENTATION_PROGRESS.md) and [REVIEW_BRANCHES](../roadmap/REVIEW_BRANCHES.md); no remote CI or device speedup is claimed.

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
