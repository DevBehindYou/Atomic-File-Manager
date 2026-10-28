# All-in-One File Manager: Competitor Research and Integration Plan

*Written 4 October 2026. Read this before adding any feature borrowed from another file manager.*

This document does three things:

1. Lists what the leading Android file managers do (Solid Explorer, MiXplorer, Files by Google, Total Commander, FX, Material Files, Cx, Amaze, Owlfiles, Samsung My Files).
2. Audits what Atomic File Manager has today, from the source, not from the older planning docs.
3. Gives a phased plan for adding the missing features. It also sets the rules every feature has to follow so that adding features doesn't make the app cluttered or unreliable.

The goal is the one stated by the owner: **one app that covers what people now need two or three file managers for, with a minimal surface, advanced tools one layer down, and operations people can trust.**

---

## 1. Executive summary

**Where the market is.** Each popular file manager is strong on one axis and weak on another:

| App | What people pick it for | What people complain about |
|---|---|---|
| Files by Google | Simple, cleanup suggestions, Quick Share, Safe folder | No network storage, weak search, no checksums, no bookmarks, no exact sizes, no recycle bin integration |
| Solid Explorer | Polished dual-pane, cloud + network, encryption | Paid after a 14-day trial, slow with big folders, slow SMB connects, licence problems, battery use |
| MiXplorer | Does everything: archives, editors, servers, tags, themes | Cluttered UI, steep learning curve, free build not on Play |
| Total Commander | Plugins, folder compare, multi-rename | Dated UI, needs manual plugins for FTP/SFTP |
| Material Files | Open source, private, clean, Linux-aware | Few tools beyond browsing |
| Cx File Explorer | Free, recycle bin, network, storage analysis | Fewer power tools |

**The gap Atomic File Manager can own:** no app combines *Files-by-Google simplicity*, *MiXplorer depth* and *a guarantee that file operations will not lose data*. The last point is the one nobody markets, and Atomic File Manager already has the start of it (`VerifiedFileTransfer`, the error model, Fast Obscure safeguards).

**Positioning line:** *Simple on top, powerful underneath, and it never loses your files.*

**Atomic File Manager's existing differentiators (keep and promote them):**

- Transfer Bubbles: staging files across folders and volumes. No competitor has an equivalent.
- Verified copies (checksum-verified transfer in `domain/usecase/VerifiedFileTransfer.kt`).
- Three hiding modes (Fast Obscure, Hide from Gallery, Private Storage) with biometric lock.
- Storage Intelligence (large files, duplicates, temp and cache) built in, not a separate "Clean" app.
- Quick Peek (hold to preview).
- No ads, no account, no tracking.

**The plan in one line per phase:**

| Phase | Theme | Why first |
|---|---|---|
| 0 | Reliability foundations | Every later feature depends on durable, background-safe, undoable operations |
| 1 | Everyday essentials | Favourites, recents, trash, global search, proper media viewing: what Files by Google users expect |
| 2 | Power layer | Batch rename, more archive formats, text editor, tabs, encrypted vault, app manager, cleaner |
| 3 | Connectivity | Working SFTP/SMB, secure credentials, Wi-Fi share, cloud through Android providers, USB OTG |
| 4 | Pro tools | Folder compare and sync, metadata tools, Android/data via Shizuku, command palette |
| U | Atomic UI/UX reconstruction (parallel track) | The whole interface rebuilt on the DevBehindYou Atomic design system; foundations land before Phase 1 screens so nothing is built twice. Full plan: [`ATOMIC_UI_PLAN.md`](ATOMIC_UI_PLAN.md) |

Section 10 lists decisions that conflict with `PRODUCT_SCOPE.md` and need the owner's call before Phase 3 and Phase 4 start.

Section 15 lists the signature features that put Atomic File Manager ahead of the other apps. Section 16 is the reliability and optimization plan, including hotspots found in the current code.

---

## 2. Competitor research

The sources are store listings, vendor pages, reviews and community threads (listed in section 14). Feature claims are the vendors' own unless marked as user reports.

### 2.1 Solid Explorer (NeatBytes)

**Model:** paid, 14-day free trial, then a purchase. Rated about 4.4 on Google Play.

| Area | Features |
|---|---|
| Layout | Dual-pane with drag and drop between panes; Material design; light/dark; accent colours; downloadable icon sets |
| Organisation | Automatic collections (Downloads, Photos, Videos, Music, Documents, Apps); indexed search; bookmarks |
| Operations | Batch rename with patterns and regular expressions |
| Archives | Create and extract ZIP, 7z, RAR, TAR |
| Network | FTP, SFTP, SMB/CIFS, WebDAV |
| Cloud | Google Drive, OneDrive, Dropbox, Box, Nextcloud, SugarSync, MediaFire, Yandex, Mega |
| Security | AES-256 encryption of files and folders, password or fingerprint |
| Storage | Analyzer for duplicates, large files and junk |
| Built-ins | Image viewer, music player, video player, text editor |
| Advanced | Root explorer; FTP server mode for PC access; plugins on Google Play |

**User-reported weaknesses:** thumbnails stop loading while scrolling; folders with many files are slow; SMB connects can take 30 s or longer; occasional crashes; higher battery and memory use; licence recognition problems for paying users.

**Lesson for Atomic File Manager:** the features are right, but performance and trust problems are the opening. Big-folder speed and network connection speed must be measured, not assumed.

### 2.2 MiXplorer (Hootan Parsa)

**Model:** free on XDA (not on Google Play); MiXplorer Silver is about $4.99 on Google Play and bundles the add-ons.

| Area | Features |
|---|---|
| Layout | Unlimited tabs; dual panel in landscape with drag and drop; customisable bookmarks drawer with file-type categories |
| Archives | Pack and unpack 7z, ZIP/ZIP64 (split and encrypted), TAR, TAR.GZ, TAR.BZ2, GZIP, BZIP2, XZ, WIM, LZ4, Zstandard and more; unpack-only for RAR/RAR5, ISO, CAB, DMG, MSI and many disk images; edit ZIP in place |
| Viewers and editors | Image viewer (GIF, large images); media player with VLC codec add-on; text editor; PDF add-on; database reader |
| Metadata | XMP/EXIF tag editor for JPEG, PNG, TIFF, JXL, WEBP; MP3 tag editor |
| Network | SMB, FTP, SFTP, WebDAV; LAN |
| Cloud | About 19 providers (Mega, Dropbox, Box, Yandex, HiDrive and others) plus any WebDAV cloud (ownCloud and others) |
| Servers | FTP, HTTP and TCP servers to receive files |
| Security | AES Crypt file encryption; EncFS volumes on any storage |
| Customisation | Full skin editor, any colour, icon theme packages |
| Protected folders | Internal bypasses plus native Shizuku support for Android/data and Android/obb |

**User-reported weaknesses:** cluttered and not intuitive for non-experts; steep learning curve; risk of accidental mistakes in a dense UI; manual APK install and updates for the free build.

**Lesson for Atomic File Manager:** MiXplorer proves the demand for depth. Its weakness is that all of that depth sits on the surface. Atomic File Manager should match the useful depth and hide it behind progressive disclosure (section 7).

### 2.3 Files by Google

**Model:** free, preinstalled on many phones.

| Area | Features |
|---|---|
| Structure | Three areas: Clean, Browse, Share |
| Clean | Personalised suggestions: unused apps, large files, duplicates, low-resolution videos, old screenshots, junk |
| Browse | Smart filters by type; recent files; storage devices |
| Share | Quick Share from the home page, offline, up to about 480 Mbps |
| Security | Safe folder protected by PIN or pattern |

**User-reported weaknesses (2025):** no exact byte sizes; search cannot be limited to the current folder and can't jump to a result's parent; doesn't remember sort order; no range-select; no full path in file info; no checksums; doesn't use Android's trash; no bookmarks; no SMB/NAS (only Google Drive); search often fails to find folders.

**Lesson for Atomic File Manager:** this list is free product research. Every item on it is cheap for Atomic File Manager to do well and costs no surface complexity.

### 2.4 Total Commander (Android)

**Model:** free; plugins as separate free apps.

| Area | Features |
|---|---|
| Layout | Two side-by-side panels |
| Tools | Multi-rename tool; compare by content (two files); directory synchronisation; search |
| Plugins | FTP, SFTP, WebDAV, LAN, Wi-Fi Direct transfer, Google Drive, OneDrive |

**Weaknesses:** dated UI; basic network features need separate plugin installs.

**Lesson for Atomic File Manager:** compare and sync are features that only the "pro" tools have. They are valuable for prosumers (persona Arjun) and fit Phase 4.

### 2.5 FX File Explorer

Free core; FX Plus (one-time, about $4.99) unlocks root and extras. Tab navigation; FTP, SFTP, SMB and WebDAV; Google Drive, Dropbox, OneDrive and Box; root with detailed controls.

### 2.6 Material Files (open source, GPLv3)

Material Design with attention to detail; root support; archive view, extract and create; FTP, SFTP, SMB and WebDAV; colour themes with true black; Linux-aware (symlinks, permissions, SELinux context); built on Java NIO2 with real system calls. No internet permission in some builds.

**Lesson for Atomic File Manager:** proof that a privacy-first, well-designed file manager can still support network protocols.

### 2.7 Cx File Explorer

Free. Recycle bin; SMB, SFTP, FTPS, FTP, LAN, WebDAV; "access from network" (browse the phone from a PC over Wi-Fi); visual storage analysis by category; app manager.

### 2.8 Amaze File Manager (open source)

Tabs; themes; navigation drawer; app manager (open, back up, uninstall); history and bookmarks; root explorer; AES encryption and decryption; cloud; database, ZIP/RAR, APK and text readers; FTP and SMB server and client; no ads or in-app purchases.

### 2.9 Owlfiles

SMB, NFS, WebDAV, FTP, SFTP; Google Drive, OneDrive, Dropbox, Box, ownCloud, Amazon S3 and S3-compatible storage, Mega, pCloud; streaming from computer, NAS or cloud; FTP and HTTP server; "Nearby Drop" to nearby Android and iOS devices.

### 2.10 Samsung My Files (preinstalled on Galaxy)

Recycle bin covering My Files, Gallery and Voice Recorder with 30-day retention; storage analysis across internal, SD and cloud; network storage (browse PC shares, stream video); SD card, USB and cloud in one place. Reviewers call it the Samsung app they miss most on a Pixel.

### 2.11 Platform facts that affect every competitor

- **All files access (`MANAGE_EXTERNAL_STORAGE`).** Google Play permits it for apps whose core purpose is file management. Atomic File Manager qualifies, but must declare it at every Play review.
- **`/Android/data` and `/Android/obb`.** Blocked on API 30+. Competitors that reach these folders do it through Shizuku, which uses shell-level privileges without root (works on Android 11–16; the original Shizuku project is reported as unmaintained, with forks such as ShizukuPlus).
- **Quick Share.** Reached through the standard Android share sheet; a file manager doesn't need to implement it, only to share correctly.

---

## 3. Market gaps Atomic File Manager can fill

| Gap | Evidence | Atomic File Manager answer |
|---|---|---|
| Power apps are cluttered, simple apps are shallow | MiXplorer UI complaints; Files by Google feature gaps | Three-layer progressive disclosure (section 7) |
| Nobody promises file safety | No competitor markets verified copies, operation journals or crash recovery | "Safe Operations": verified copy, trash by default, undo, resume after process death |
| Slow big folders and slow network connects | Solid Explorer user reports | Chunked listing (already present), performance budgets in CI, connection reuse |
| Search is weak or slow | Files by Google reports | Indexed search with scope, filters, jump to folder |
| Moving many files between distant folders is tedious on phones | Dual pane only works on wide screens | Transfer Bubbles (unique) |
| Cleanup without fear | Cleaners delete in bulk | Suggestions with reasons, everything goes to trash, undo |
| Paid or ad-funded | Solid trial, ads in many free apps | Free, no ads, no account (current business context) |

---

## 4. Atomic File Manager today: honest audit

Status from the source on 4 October 2026 (commit after `677e333`). "Partial" means code exists but is incomplete or not verified on a device; see `testing/VERIFICATION_REPORT.md`.

| Capability | Status | Where |
|---|---|---|
| Browse internal and SD, breadcrumbs, sort, hidden toggle | Done | `ui/screens/BrowseScreen.kt`, `BrowseViewModel.kt` |
| Categories across all volumes, More categories | Done (phone check pending) | `domain/model/FileCollection.kt`, `data/volume/PhoneFileIndex.kt`, `ui/screens/CategoryScreen.kt` |
| Copy, move, rename, delete, new folder; collision policies | Done | `domain/usecase/FileOperationsEngine.kt` |
| Verified copy (checksum after write) | Done | `domain/usecase/VerifiedFileTransfer.kt` |
| Background operations | **Missing**: `FileOperationService` exists but is never started; work runs in `viewModelScope` | `service/FileOperationService.kt` |
| Parallel operations | **Bug**: starting a second operation cancels the first | `BrowseViewModel.runOperation` |
| Undo | Model only (`UndoToken`), not wired | `domain/model/Operation.kt` |
| Trash / recycle bin | **Missing** (capability flag only) | `StorageCapabilities.supportsTrash` |
| Search | Current folder filter only | `BrowseViewModel.refilter` |
| Favourites, recents, bookmarks | **Missing** | none |
| ZIP create/extract, Zip Slip protection | Done | `domain/usecase/ArchiveOperationsHelper.kt`, `InspectArchiveUseCase.kt` |
| Other archive formats | **Missing** | none |
| Image, audio, video, PDF, text, markdown preview | Partial: `MediaPlayer` and `VideoView`, no swipe between images | `ui/components/preview/`, `data/preview/` |
| Checksums (MD5 and others) in file info | Done | `ui/components/FilePreviewDialog.kt` |
| Storage Intelligence: large files, duplicates, temp/cache | Done | `domain/usecase/StorageAnalyzerUseCase.kt`, `ui/screens/StorageIntelligenceScreen.kt` |
| Hiding: three modes, biometric lock | Done | `data/repository/HiddenFilesRepositoryImpl.kt`, `ui/security/BiometricAuth.kt` |
| Transfer Bubbles, drag and drop, Quick Peek | Done | `ui/interaction/` |
| Dual pane | Done at ≥720 dp | `BrowseScreen.kt` |
| FTP / FTPS | Partial (listing and transfer not fully verified) | `data/backend/network/FtpBackend.kt` |
| WebDAV | Partial | `WebDavBackend.kt` |
| SFTP / SMB | **Stubs** that fail with `ProviderUnavailable` | `SftpBackend.kt`, `SmbBackend.kt` |
| Credential storage | **Insecure**: fixed key and IV, plaintext fallback | `NetworkCredentialsStore.kt` |
| Settings tab | Done | `ui/screens/SettingsScreen.kt` |
| Navigation | State flags in a 640-line `MainActivity`; no route stack | `MainActivity.kt` |
| Persistence | SharedPreferences and two `SQLiteOpenHelper` databases; repositories read on the main thread in `init` | `data/database/` |
| Dependencies | Compose, Material 3, biometric, fragment, coroutines. No Room, Media3, image loader, WorkManager | `app/build.gradle.kts` |
| Release | Debug-key signing, R8 off | `VERIFICATION_REPORT.md` |

**Takeaway:** the browsing and organising surface is strong and distinctive. The weak spots are exactly the ones that decide whether people trust a file manager: background execution, trash, undo, and credential security. That is why Phase 0 comes first.

---

## 5. Feature matrix and decisions

Legend: ✓ has it, ~ partial, — missing. **Decision:** Adopt = build as competitors do; Adapt = build a safer or simpler version; Skip = do not build (section 11).

| Feature | Solid | MiX | Files | TC | Cx | Amaze | Atomic File Manager now | Decision | Phase |
|---|---|---|---|---|---|---|---|---|---|
| Background operations with notification | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — | Adopt | 0 |
| Trash / recycle bin | — | ~ | — | — | ✓ | — | — | Adopt (Samsung-style, 30 days) | 1 |
| Undo for move, rename, delete | — | — | — | — | — | — | — | Adapt (unique) | 1 |
| Favourites / bookmarks | ✓ | ✓ | — | ✓ | ✓ | ✓ | — | Adopt | 1 |
| Recent files | ✓ | ✓ | ✓ | — | ✓ | ✓ | — | Adopt | 1 |
| Indexed global search with filters | ✓ | ✓ | ~ | ✓ | ✓ | ✓ | ~ | Adopt | 1 |
| Image viewer with swipe and zoom | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~ | Adopt | 1 |
| Media playback (Media3) | ✓ | ✓ | ✓ | ✓ | ✓ | ~ | ~ | Adapt (preview player, not a media app) | 1 |
| APK info and install | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | — | Adopt | 1 |
| Range select, select all, invert | ✓ | ✓ | ~ | ✓ | ✓ | ✓ | ~ | Adopt | 1 |
| Batch rename with preview | ✓ | ✓ | — | ✓ | — | — | — | Adopt | 2 |
| 7z, TAR, GZ, XZ, encrypted ZIP; RAR extract | ✓ | ✓ | ~ | ✓ | ✓ | ~ | ZIP only | Adopt | 2 |
| Text editor | ✓ | ✓ | — | ✓ | ✓ | ~ | — | Adapt (plain text, atomic save) | 2 |
| Tabs | — | ✓ | — | — | — | ✓ | — | Adapt (up to 4, hidden when one) | 2 |
| Encrypted vault | ✓ | ✓ | ~ | — | — | ✓ | — | Adapt (extends Private Storage) | 2 |
| App manager (extract APK, uninstall) | — | ✓ | ~ | — | ✓ | ✓ | — | Adapt (launchable apps only) | 2 |
| Smart cleanup suggestions | ~ | — | ✓ | — | ✓ | — | ~ | Adopt (extend Storage Intelligence) | 2 |
| SFTP, SMB that work | ✓ | ✓ | — | ✓ | ✓ | ✓ | stub | Adopt | 3 |
| Network discovery (find NAS/PC) | ~ | ✓ | — | ✓ | ✓ | ~ | — | Adopt | 3 |
| Wi-Fi share to a PC browser | ✓ | ✓ | — | ~ | ✓ | ✓ | — | Adapt (guarded HTTP, section 9.3.4) | 3 |
| Cloud drives | ✓ | ✓ | ~ | ✓ | ~ | ✓ | — | Adapt (Android providers + WebDAV) | 3 |
| USB OTG | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ | ~ | Adopt (verify) | 3 |
| Folder compare and sync | — | ~ | — | ✓ | — | — | — | Adopt | 4 |
| EXIF view, strip location, audio tags | ~ | ✓ | — | — | — | — | — | Adapt (privacy first) | 4 |
| `/Android/data` via Shizuku | — | ✓ | — | — | — | — | — | Adapt (optional) | 4 |
| Command palette | — | — | — | — | — | — | — | Adopt (planned V1) | 4 |
| Root explorer | ✓ | ✓ | — | ✓ | — | ✓ | — | Skip | — |
| Full theming / skin editor | ~ | ✓ | — | ~ | — | ✓ | — | Skip (dynamic colour + accent only) | — |

---

## 6. Why the order is load-bearing

Each phase depends on the one before:

- Trash, undo, batch rename, sync and network transfers all create long or risky operations. Without a **durable operation queue running in a foreground service** (Phase 0), every one of them inherits the current bugs: work dies when the app is swiped away, and a second operation cancels the first.
- Search, recents, favourites and cleanup need a **database that is not read on the main thread** and that can grow (Phase 0).
- New screens (trash, search, vault, servers, tools) need a **real navigation stack** (Phase 0). Adding them as more booleans in `MainActivity` would make it unmaintainable.
- Network features (Phase 3) must not ship until **credentials are protected by the Android Keystore** (Phase 0).
- New screens must be **built from Atomic components** (`ATOMIC_UI_PLAN.md`). Atomic foundations and atoms (U2–U3) run in parallel with Phase 0; the Atomic shell (U4) is the same piece of work as the navigation migration (0.3); Phase 1 screens start after U4.

Do not start a phase until the previous phase is green in CI and checked on the phone.

---

## 7. Product rules for all-in-one without clutter

These rules apply to every feature in this plan. A feature that cannot meet them waits.

### 7.1 Three layers of disclosure

| Layer | What lives there | Rule |
|---|---|---|
| **Surface** | Home, Browse, the four tabs, selection bar with at most 4 actions + overflow | Only what Priya (the non-technical persona) needs daily. Nothing new is added here without removing something |
| **One tap deeper** | Long-press action sheet, overflow menu, file info sheet, sort sheet | Context-sensitive: an action appears only when it applies (Extract only on archives, Install only on APKs) |
| **Tools** | A "Tools" section on the Storage tab: Cleanup, Duplicates, Batch rename, Compare folders, Vault, Wi-Fi share, App manager, Trash | Power features live here. Each is a full screen with its own explanation |

### 7.2 Tap budget

From `PRODUCT_SCOPE.md` §5: any feature must be reachable in **3 taps or fewer from Home** without adding a permanent element. The Tools section on Storage makes every tool exactly 2 taps away (Storage tab → tool).

### 7.3 Defaults that protect people

- Delete goes to trash by default. Permanent delete needs a second, explicit confirmation.
- Bulk actions show a preview before running (batch rename, cleanup, sync).
- Nothing auto-selects all copies of a duplicate.
- Network features and servers are off until the user turns them on.

### 7.4 Plain language

Errors use the existing error model (`ERROR_MODEL.md`) and say what happened and what to do. Never show "URI", "SAF" or exception names on the surface.

### 7.5 Performance budgets (checked, not assumed)

| Budget | Target |
|---|---|
| Open a 10,000-entry folder | First rows in < 300 ms, no frame over 32 ms while scrolling |
| Indexed search | First results < 300 ms |
| Network folder (LAN) | Connect < 2 s, reuse connection between folders |
| Cold start | < 1 s to interactive Home on a mid-range phone |

Enable the disabled `benchmark` module (Phase 0) so these become measurable.

---

## 8. Integration contract: how every feature plugs in

Follow this for every feature. It is what makes the plan safe to execute by any developer or agent.

### 8.1 Layers and placement

```
ui/screens/<Feature>Screen.kt + <Feature>ViewModel.kt   (Compose, state only)
        │
domain/usecase/<Feature>UseCase.kt                       (pure Kotlin, no android.*; NoAndroidInDomain lint rule)
        │
domain/repository/<Feature>Repository.kt (interface)
        │
data/<area>/<Feature>RepositoryImpl.kt                   (Android, IO dispatcher)
        │
AtomicApp.kt → AppContainer (lazy val)                  (manual DI; Hilt is declared but unused)
```

### 8.2 Mutations go through one pipeline

Every change to files (including trash, restore, batch rename, sync, extract, encrypt) is a `FileOperation` with an `OperationType`, executed by `FileOperationsEngine` inside the operation service (Phase 0). No screen writes files directly. This gives every feature progress, cancel, the conflict dialog, failure summaries, undo and crash recovery for free.

To add a mutation: add an `OperationType`, implement it in the engine (or a helper the engine calls), and add an undo inverse where one exists.

### 8.3 Capabilities gate actions

Use `StorageCapabilities` on the node's backend to decide whether an action is shown. Example: hide "Move to trash" on a backend without `supportsTrash` and offer permanent delete with a clear warning instead. Never show an action that will fail on that storage.

### 8.4 Storage backends

New storage (SFTP, SMB, Android providers, Shizuku) is a new `StorageBackend` implementation registered in `AppContainer.backends` with a `BackendType`. Browse, search, operations and bubbles then work with it unchanged.

### 8.5 Settings and feature flags

Each new feature gets an `AppSettings` field only if the user needs to choose something. Features that are risky (servers, Shizuku, sync) also get a "Labs" flag, default off, until verified on a device.

### 8.6 Definition of done (every feature)

- [ ] Domain logic has JUnit 5 tests; Android code has Robolectric tests; file mutations are tested with disposable fixtures.
- [ ] Works when the app is backgrounded and when the process is killed mid-operation (Phase 0 onward).
- [ ] TalkBack labels on every control; layout intact at 200 % font and in landscape.
- [ ] UI built only from `core.designsystem` Atomic components and tokens, meeting `ATOMIC_UI_PLAN.md` §16.
- [ ] Reachable in ≤ 3 taps; no new permanent surface element unless one was removed.
- [ ] ktlint, detekt, Lint and unit tests green in CI.
- [ ] Phone check recorded in `testing/VERIFICATION_REPORT.md` with what was and was not verified.
- [ ] `ai-handover/` updated (`STATE.yaml`, `CHANGES.md`, `PENDING.md`).

---

## 9. Phased plan with feature specs

Effort is relative: **S** about a day, **M** a few days, **L** a week or more, for one developer with CI-only builds.

### Phase 0: Reliability foundations

#### 0.1 Durable operation queue in a foreground service (L)

- **Value:** copies keep going when the app is closed; several operations can run or queue; nothing is lost if Android kills the app.
- **Today:** `FileOperationService` is declared with `foregroundServiceType="dataSync"` but never started; `BrowseViewModel.runOperation` cancels a running operation when a new one starts.
- **Build:**
  - `domain/usecase/OperationQueue.kt`: queue of `FileOperation`, configurable concurrency (default 1 per volume pair, 2 total), exposes `StateFlow<List<OperationSnapshot>>`.
  - `data/operations/OperationJournal.kt`: SQLite table recording each operation and each completed item **before** moving to the next one. On start-up, unfinished operations are offered as "Resume or discard".
  - Start the service when the queue becomes non-empty and stop it when empty. Request `POST_NOTIFICATIONS` (API 33+) before the first background operation, not at launch (FR-2.6).
  - Notification: current file, n of m, cancel action.
  - Replace `runOperation` in `BrowseViewModel` with `queue.enqueue(...)`.
  - Operations screen (FR-4.13): active and recent operations, reachable from the notification and from a small progress chip in Browse.
- **Platform constraints:** Android 15 limits `dataSync` foreground services to about 6 hours per 24 hours. For API 34+ also evaluate user-initiated data transfer jobs (`JobInfo.Builder.setUserInitiated`, needs `RUN_USER_INITIATED_JOBS`), which are designed for user-started long transfers. Decide in the design review; the journal makes either approach resumable.
- **Tests:** queue ordering and concurrency; a second operation no longer cancels the first; journal replay after a simulated crash at each item boundary; instrumented test that kills the process mid-copy with disposable files.
- **Accept when:** a 2 GB copy completes with the app swiped away, and a forced stop mid-copy offers resume on next launch with no partial file left unreported.

#### 0.2 Database foundation (M)

- **Value:** search, favourites, recents, trash and the journal need one consistent store, read off the main thread.
- **Decision:** adopt **Room** (with KSP, already in the version catalog) for new tables. Keep the existing `SQLiteOpenHelper` databases working and migrate them later. Move the `init` reads in the bubble and hidden-file repositories to `Dispatchers.IO`.
- **Tables (added by the features that need them):** `operation_journal`, `trash_entry`, `favourite`, `recent_item`, `search_index` (FTS4), `saved_connection`.
- **Tests:** migration tests for each schema version (Room `MigrationTestHelper`).

#### 0.3 Navigation stack (M)

- **Value:** new screens without growing `MainActivity`; correct Back everywhere.
- **Decision:** adopt Navigation Compose with type-safe routes (as `architecture/NAVIGATION.md` proposes), keeping the four tabs. Move existing overlays (Private files, Category, Storage Intelligence, Hidden files) into routes one at a time, verifying Back on the phone after each.
- **Keep:** the per-folder Browse view model and the `browseOpenRequest` behaviour added on 4 October 2026.

#### 0.4 Keystore-backed credentials (M)

- **Value:** passwords for FTP/SFTP/SMB/WebDAV are actually protected.
- **Build:** replace the fixed-key scheme in `NetworkCredentialsStore` with an AES-GCM key held in the Android Keystore (random IV per value, versioned format). Migrate existing values once, then delete the plaintext fallback. Consider Google **Tink** (Apache 2.0, maintained) so the same keyset can drive the vault in 2.5.
- **Tests:** round trip; migration from the old format; tampered ciphertext fails safely.

#### 0.5 Release and measurement hygiene (S)

- Enable R8 for release builds with keep rules for reflection users; check the APK still works.
- Re-enable the `benchmark` module with startup and big-folder scroll benchmarks for the budgets in 7.5.
- Add an image loader (**Coil 3**) for thumbnails with a disk cache: it fixes the "thumbnails stop loading" problem competitors have and is needed by Phase 1 viewers.

### Phase 1: Everyday essentials

#### 1.1 Trash with 30-day retention and undo (M)

- **Competitors:** Samsung My Files (30 days), Cx. Files by Google users complain that it lacks one.
- **Design:**
  - Each volume gets a hidden trash folder at its root (for example `/storage/emulated/0/.Atomic File Manager/Trash`), so moving to trash is a **same-volume rename**: instant, no copying.
  - `trash_entry` table: original path, trashed path, size, date, volume.
  - On API 30+, media files can alternatively use MediaStore's trash (`MediaStore.createTrashRequest`), which also shows in Gallery apps. Decide per file type in the design review; the app-managed trash works on every API level and for every file type.
  - SAF-only volumes without rename support: warn and offer permanent delete.
  - Daily cleanup of entries older than the retention period (setting: 7/30/60 days, default 30).
- **Surface:** Delete moves to trash with an Undo snackbar (10 s). Trash screen in Tools: restore, delete forever, empty.
- **Undo:** wire `UndoToken` for move, rename and trash (FR-4.14).
- **Safety:** the trash folder is excluded from categories, search and storage "junk" suggestions; Phase 1.4 search must also exclude it.
- **Accept when:** delete → app killed → reopen → restore puts the file back byte-identical in its original folder.

#### 1.2 Favourites and recents (S)

- **Favourites:** star any file or folder (action sheet). Shown as a row on Home and in a Browse drawer. Missing targets are shown greyed with a "Locate or remove" option (FR-6.5).
- **Recents:** two sources merged: files Atomic File Manager opened or created (`recent_item` table), and recently modified media from MediaStore (`DATE_MODIFIED`, last 7 or 30 days). Home row "Recent".
- **Accept when:** both survive restart and handle deleted targets without crashing.

#### 1.3 Selection power (S)

- Select range (long-press first, then long-press last), select all, invert selection, "select same type".
- Exact byte size in file info (Files by Google gap) and full path with a copy button.
- Remember sort per folder (FR-3.3).

#### 1.4 Indexed global search (L)

- **Value:** fixes the most common complaint about Files by Google.
- **Build:**
  - `search_index` Room FTS4 table: name, path, extension, size, modified, volume, is_directory.
  - Indexer reuses the `PhoneFileIndex` walk (it already skips hidden folders and resolves canonical paths), writing in batches on IO.
  - Freshness: on API 30+ check `MediaStore.getGeneration` to detect media changes cheaply; re-walk folders whose modified time changed; full rescan on demand and when charging and idle.
  - Search screen: one box; scope chips (This folder, All storage, a volume); filter chips (type, size, date); results stream; each result shows its folder and has "Open folder".
  - Falls back to a live walk when the index is not ready, saying so.
- **Accept when:** first results in under 300 ms on an indexed phone with 100,000 files, and a file created by another app is found after a refresh.

#### 1.5 Better viewers (M)

- **Images:** full-screen viewer with pinch zoom, pan, and swipe between images in the same folder or category (FR-8.1). Uses Coil for decoding with downsampling to avoid out-of-memory errors on huge images.
- **Video and audio:** replace `VideoView` and `MediaPlayer` with **AndroidX Media3** (ExoPlayer) for wider format support, seeking and lifecycle-safe release. Keep it a *preview* player: play, pause, seek, speed, "Open in another app". No playlists or library.
- **APK:** info sheet with icon, label, package, version, size, permission count (FR-8.7) via `PackageManager.getPackageArchiveInfo`; "Install" opens the system installer through a `FileProvider` URI. Needs `REQUEST_INSTALL_PACKAGES`, which needs a Play declaration: check the current policy text at submission time.
- **Unknown types:** info plus "Open with" (already the rule in FR-8.5).

### Phase 2: Power layer (lives in Tools or one tap deeper)

#### 2.1 Batch rename (M)

- **Competitors:** Solid Explorer (patterns, regex), Total Commander multi-rename.
- **Design:** select files → Rename shows a builder. Simple mode: find/replace, add prefix/suffix, number sequence with padding, change case, insert date taken (EXIF) or modified date. Advanced toggle: regular expressions with capture groups.
- **Safety:** live preview table of old → new names; conflicts and invalid characters are highlighted and block Apply; runs as one `FileOperation`; Undo restores all names.
- **Tests:** conflict detection, cyclic renames (a→b, b→a) done through temporary names, undo.

#### 2.2 More archive formats (M)

- **Libraries:** Apache **Commons Compress** (Apache 2.0) for TAR, GZ, BZ2, XZ, 7z read and write; **zip4j** (Apache 2.0) for AES-encrypted ZIP read and write; **junrar** for RAR extract only (its licence forbids creating RAR, which matches what competitors do).
- **Design:** extend `ArchiveOperationsHelper` and `InspectArchiveUseCase` through a `ArchiveFormat` interface so each format is one class. Browse inside an archive without extracting (FR-9.1); extract all or selected entries.
- **Safety:** keep Zip Slip checks for every format; cap total extracted size and entry count (zip-bomb protection) with a clear message; password prompts never stored unless the user opts in.

#### 2.3 Plain text editor (M)

- Scope is **plain text** (txt, md, json, xml, csv, log, code), not office documents (still out per `PRODUCT_SCOPE.md`).
- Size cap (for example 5 MB; larger files open read-only).
- Detect encoding and line endings and preserve them on save.
- **Atomic save:** write to a temporary file in the same folder, sync, then rename over the original; keep the previous version in trash so the edit can be undone.
- Find and replace, line numbers, word wrap toggle, monospace font.

#### 2.4 Tabs (S)

- Up to 4 Browse tabs. The tab strip appears only when more than one tab is open, so the default surface does not change.
- "Open in new tab" in the folder action sheet. Each tab keeps its own `BrowseViewModel` (the per-folder keying already exists).
- On wide screens the second pane can show any tab.

#### 2.5 Encrypted vault (L)

- **Competitors:** Solid (AES-256), MiXplorer (AES Crypt, EncFS), Files by Google (Safe folder).
- **Design:** an evolution of the existing Private Storage hiding mode, not a fourth mode. Files moved into the vault are encrypted with **Tink StreamingAead (AES-256-GCM-HKDF, 1 MB segments)**, which allows large files and seeking. The keyset is wrapped by a Keystore key that requires user authentication (biometric or device credential), reusing `ui/security/BiometricAuth.kt`.
- Thumbnails of vault files are generated in memory only, never cached to disk.
- **Hard warning, shown before first use:** if the device is reset or the app is uninstalled, vault files cannot be recovered. Offer an export (decrypt to a chosen folder) at any time.
- Migration: existing Private Storage items can be converted into the vault on request.
- **Tests:** encrypt/decrypt round trip on large fixtures; wrong key fails safely; interruption mid-encryption leaves the original intact (encrypt to a temporary file, verify, then delete the original through trash).

#### 2.6 Smart cleanup (M)

- **Competitor:** Files by Google's Clean tab.
- Extend Storage Intelligence with cards, each with a *reason*: duplicate files, large files not opened in 90 days, APKs for apps already installed, old screenshots, empty folders, temp and cache files, big downloads older than N days.
- Every card opens a review list. Nothing is pre-selected except safe items (empty folders, temp files). Deletion goes to trash.
- Show "space you will get back" before confirming.

#### 2.7 App manager (M)

- List launchable apps with size and install date; actions: open, app info (system screen), extract APK to a folder, uninstall (system dialog), share APK.
- Use a `<queries>` element with the launcher intent to see launchable apps. **Do not** request `QUERY_ALL_PACKAGES`, which Play restricts.
- Split APKs (app bundles) are exported as `.apks`/ZIP with a note that they need a split-aware installer.

#### 2.8 Checksum compare (S)

- Checksums exist in file info. Add: paste an expected hash and show match or mismatch; compare two selected files by content (Total Commander feature).

### Phase 3: Connectivity

Requires Phase 0.4 (secure credentials, done) and owner approval of the scope change in section 10 (approved 7 October 2026).

#### 3.1 Real SFTP and SMB backends (L)

- **Libraries:** **sshj** (Apache 2.0) for SFTP; **smbj** (Apache 2.0) for SMB 2/3. Keep SMB1 disabled.
- Implement `StorageBackend` fully (list, read, write, rename, delete, mkdir) replacing the `UnimplementedProtocolBackend` stubs.
- **Performance:** connection pool per server (reuse between folders, fixing the slow-connect problem users report in Solid Explorer); listing in chunks; read-ahead buffers for streaming.
- **Security:** SFTP host-key verification with trust-on-first-use and a clear warning when the key changes; FTPS and WebDAV must validate certificates (user-approved exceptions only, stored per server).
- **Tests:** CI integration tests against disposable containers (OpenSSH and Samba services in GitHub Actions) for list, upload, download, rename, delete, disconnect mid-transfer.

#### 3.2 Network discovery (M)

- Find SMB servers and NAS devices on the local network using mDNS/DNS-SD (`NsdManager` for `_smb._tcp`, `_sftp-ssh._tcp`, `_webdav._tcp`) so users do not type IP addresses.
- "Add server" flow: discovered list first, manual entry second.

#### 3.3 Cloud through Android, without accounts in Atomic File Manager (M)

- **Principle:** keep the no-account promise. Many cloud apps (Google Drive, OneDrive, Dropbox, Nextcloud and others) publish Android `DocumentsProvider`s. Atomic File Manager can list provider roots and open them through the system picker, using the existing `SafBackend`.
- Folder (tree) access depends on each provider; some only allow picking single files. Test each major provider and show what works, honestly, in the UI.
- WebDAV covers Nextcloud, ownCloud and many other services directly.
- Native OAuth integrations stay in `roadmap/FUTURE.md` unless the owner decides otherwise.

#### 3.4 Wi-Fi share (guarded) (M)

- **Competitors:** Solid FTP server, MiXplorer FTP/HTTP servers, Cx "access from network".
- `PRODUCT_SCOPE.md` currently lists FTP/HTTP servers as **out** for security. A guarded design addresses the reasons:
  - HTTP only on the local Wi-Fi interface, never on mobile data.
  - Session-based: started by the user, shows a URL and a random one-time PIN; stops automatically after 15 minutes idle or when the user leaves the screen.
  - Scope is one folder the user chose, **read-only by default**; upload is a separate opt-in.
  - The notification shows the session is running, with Stop.
- **Approved** by the owner on 7 October 2026 (section 10).

#### 3.5 USB OTG (S to M)

- `StorageVolumes` already enumerates volumes. Verify OTG drives on a device: mount detection within 2 s, safe removal mid-operation (journal marks the operation failed with a clear message), and FAT/exFAT name limits.

### Phase 4: Pro tools

#### 4.1 Folder compare and sync (L)

- **Competitor:** Total Commander directory sync.
- Compare two folders (any two backends, such as phone and NAS): show only-left, only-right, newer-left, newer-right, same. Compare by size and date, optional checksum.
- One-way **copy-new** and **mirror** modes. Mirror deletes go to trash, and the plan is always shown before running.
- Saved sync pairs can be re-run with one tap (no hidden background syncing in the first version).

#### 4.2 Metadata tools (M)

- View EXIF (camera, date, location) in file info.
- **Strip location / all metadata** before sharing: a privacy feature that matches Atomic File Manager's positioning (uses `androidx.exifinterface`).
- Audio tag view; editing tags is optional and later.

#### 4.3 `/Android/data` through Shizuku (M, optional)

- Opt-in Labs feature: if Shizuku (or a maintained fork) is installed and authorised, add a `ShizukuBackend` that can list and copy within `/Android/data` and `/Android/obb`.
- Clearly explain risks; read-only by default; never required for core features. Re-check Play policy before release.

#### 4.4 Command palette (M)

- Planned for V1 (FR-10.10). Search box on Home that also matches actions ("compress", "trash", "Wi-Fi share", "settings: theme"). Keyboard shortcut Ctrl+K on tablets and Chromebooks.

#### 4.5 Shortcuts and widgets (S)

- Pin a folder or favourite to the launcher (dynamic shortcuts); Quick Settings tile for Wi-Fi share; a storage widget.

---

## 10. Decisions the owner needs to make

These conflict with `PRODUCT_SCOPE.md` or `PRODUCT_CONTEXT.md`. Recommendations are given; nothing in Phase 3 or 4 that depends on them should start before the owner decides.

**Decided 7 October 2026: the owner approved every recommendation below.** `PRODUCT_SCOPE.md` and FR-10.3 are updated to match; Phases 3 and 4 may start.

| Topic | Current scope | Recommendation |
|---|---|---|
| FTP/SFTP/SMB/WebDAV client | "Future" | Move to Phase 3. Code already exists; finishing it is cheaper than leaving broken stubs |
| FTP/HTTP server | "Out (security liability)" | Allow the guarded Wi-Fi share in 3.4 only; no general FTP server |
| Cloud accounts | "Future" | Use Android providers and WebDAV (no accounts in Atomic File Manager); keep native OAuth in Future |
| Root browsing | "Out" | Keep out; offer Shizuku as optional in 4.3 |
| Document editing | "Out" | Keep office formats out; allow the plain-text editor in 2.3 |
| Built-in media player | "Out" | Allow a preview player with Media3; no media library |
| Liquid Glass tier in FR-10.3 | Requirement | Drop it. `PRODUCT_SCOPE.md` §4 already rules out glass, and the Atomic design system forbids blur. Update FR-10.3 |
| Visual design language | "Clean plain Material 3, dynamic colour" (`PRODUCT_SCOPE.md` §1) | Replace with the Atomic design system (`../design/ATOMIC_DESIGN_SYSTEM.md`). UI-specific decisions (dynamic colour, dark theme, name, icon, accent, tab label, mascot, date format) are in `ATOMIC_UI_PLAN.md` §18 |

---

## 11. What we deliberately skip

| Feature | Who has it | Why we skip |
|---|---|---|
| Root explorer | Solid, MiX, TC, Amaze | Play policy and safety risk; small audience; Shizuku covers the main use |
| Skin editor, icon packs, dynamic colour | MiX, Solid, Files | Dilutes the Atomic identity; users choose light or dark only |
| Dozens of disk-image formats (ISO, DMG, VMDK…) | MiX | Rarely needed on phones; can be revisited after 2.2 |
| EncFS / AES Crypt compatibility | MiX | Niche; the vault uses one well-reviewed scheme |
| Ads or paid unlocks | Many | Contradicts the business context |
| Built-in music library or video app | Solid | Hand off to dedicated apps |

---

## 12. Risk register

| Risk | Impact | Mitigation |
|---|---|---|
| Feature creep makes the UI cluttered | Loses the main differentiator | Rules in section 7; every PR states which layer the feature lives in |
| APK size grows with libraries (Media3, Commons Compress, sshj with BouncyCastle, smbj, Tink, Room, Coil) | Slower installs, worse store conversion | Enable R8 (0.5); add each library only in the phase that needs it; record APK size per release |
| Play review rejects permissions (all files access, install packages) | Release blocked | Prepare declarations early (`architecture/PERMISSIONS.md` §7); avoid `QUERY_ALL_PACKAGES` |
| Data loss from a new mutating feature | Destroys trust | All mutations through the engine and journal; trash by default; disposable-fixture tests; process-kill tests |
| Background limits on Android 14/15 | Long transfers stopped | Journal + resume; evaluate user-initiated data transfer jobs (0.1) |
| Network security mistakes | Credential or file exposure | Keystore credentials first; host-key and certificate checks; servers off by default |
| No local Android SDK on the developer machine | Slow feedback | CI is the build authority; keep CI fast; add emulator job to PR runs when stable |
| Shizuku project maintenance | Feature breaks | Optional Labs feature only |

---

## 13. First sprint (concrete starting point)

If work starts tomorrow, do these in order. Each is one pull request with CI green and a phone check.

1. **0.2 (part) and 16.2 H1:** add Room with KSP and an empty database; move the two repository `init` reads and `refreshVolumes()` off the main thread; turn on StrictMode in debug builds. Small, unblocks everything.
2. **U2 (Atomic foundations):** tokens, bundled fonts, `AtomicTheme` bridged onto `MaterialTheme`, lint baseline. The whole app switches to the Atomic palette and type in one PR. Can run in parallel with step 3.
3. **0.1:** operation queue + journal + starting the service; fix "second operation cancels the first".
4. **U3 (Atomic atoms):** buttons, text, chips, fields, meters, progress, with screenshot tests.
5. **0.3 + U4:** Navigation Compose routes and the Atomic shell (header, bottom bar, sheets, snackbar) as one piece of work.
6. **1.1:** trash with undo snackbar and Trash screen, built Atomic from the start.
7. **1.2:** favourites and recents on Home.
8. **0.4:** Keystore credentials (before any Phase 3 work).

---

## 14. Sources

Competitor information:

- Solid Explorer: [Google Play listing](https://play.google.com/store/apps/details?id=pl.solidexplorer2&hl=en), [SourceForge reviews](https://sourceforge.net/software/product/Solid-Explorer/), [TechWiser tips](https://techwiser.com/solid-explorer-tips-tricks/), [TipsMake features](https://tipsmake.com/9-outstanding-features-of-solid-explorer-you-may-not-know), [Slant review](https://www.slant.co/options/6825/~solid-explorer-review), [AlternativeTo](https://www.alternativeto.net/software/solid-explorer/about/)
- MiXplorer: [mixplorer.com](https://mixplorer.com/), [XDA Labs](https://labs.xda-developers.com/store/app/com.mixplorer), [MakeUseOf](https://www.makeuseof.com/android-file-manager-for-power-users-mixplorer/), [Android Community](https://androidcommunity.com/mixplorer-the-file-explorer-app-that-does-everything-you-want-it-to-do-20160512/), [DroidViews](https://www.droidviews.com/mixplorer-is-a-free-file-manager-with-loads-of-features/), [AnExplorer comparison](https://anexplorer.io/compare/mixplorer)
- Files by Google: [Google Play listing](https://play.google.com/store/apps/details?id=com.google.android.apps.nbu.files&hl=en_US), [Wikipedia](https://en.wikipedia.org/wiki/Files_(Google)), [XDA thread on missing features](https://xdaforums.com/t/googles-file-manager-still-lacks-essential-functionality-in-2025.4713588/), [Gadget Hacks on search](https://android.gadgethacks.com/news/whats-really-broken-in-your-file-manager-and-why-the-fix-matters-more-than-you-think/), [Android Authority: Samsung My Files vs Files](https://www.androidauthority.com/samsung-my-files-vs-files-by-google-3593503/)
- Total Commander: [ghisler.com Android page](https://www.ghisler.com/android.htm), [Android help](https://www.ghisler.com/android/help.htm), [Wikipedia](https://en.wikipedia.org/wiki/Total_Commander)
- Material Files: [GitHub](https://github.com/zhanghai/MaterialFiles), [F-Droid](https://f-droid.org/packages/me.zhanghai.android.files/)
- Cx File Explorer: [Google Play listing](https://play.google.com/store/apps/details?id=com.cxinventor.file.explorer&hl=en_US), [Gadget Hacks](https://android.gadgethacks.com/news/cx-file-explorer-why-this-legendary-app-beats-files/)
- Amaze: [GitHub](https://github.com/teamamaze/amazefilemanager), [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/com.amaze.filemanager)
- Owlfiles: [APKPure listing](https://apkpure.com/owlfiles-file-manager/com.skyjos.apps.fileexplorerfree)
- Samsung My Files: [Samsung recycle bin support page](https://www.samsung.com/in/support/apps-services/the-updated-recycle-bin-feature-in-my-files-on-galaxy-devices-with-one-ui-6/), [PCNexus network storage guide](https://www.pcnexus.net/2026/03/set-up-network-storage-samsung-my-files-android.html)
- Overviews: [Android Authority list](https://www.androidauthority.com/file-manager-explorer-apps-android-279800/), [AnExplorer best of 2026](https://anexplorer.io/compare/best-android-file-manager)

Platform:

- [Play Console: All files access permission](https://support.google.com/googleplay/android-developer/answer/10467955?hl=en), [Android: Manage all files](https://developer.android.com/training/data-storage/manage-all-files)
- Shizuku and `/Android/data`: [XDA guide for Android 14](https://xdaforums.com/t/access-android-data-and-android-obb-on-android-14-with-shizuku.4644152/), [XDA thread on Shizuku file managers](https://xdaforums.com/t/psa-what-free-android-file-managers-are-integrated-with-shizuku-given-that-unrootable-phones-still-need-read-write-access-to-protected-storage.4787917/)

Research method note: vendor pages could not be opened directly from the research environment, so feature lists come from search-engine summaries of the pages above. Re-check any specific claim (especially prices and policy text) against the live page before quoting it publicly.

---

## 15. Signature features: what makes Atomic File Manager more advanced

Matching competitors feature for feature is not enough to win; MiXplorer already has more features than anyone will ever list. These are the features where Atomic File Manager does something **no app in section 2 does**, or does it in a way the others cannot because of how they are built. Each one points to the phase that builds it.

| # | Feature | What the user gets | Why competitors don't have it | Phase |
|---|---|---|---|---|
| 1 | **Undo everything** | Every move, rename, batch rename, trash, extract and sync can be undone from a snackbar (10 s) or the Operations screen (24 h) | None of the ten apps researched offers undo for file operations; most have no recycle bin either | 0.1, 1.1 |
| 2 | **Crash-proof operations** | A copy interrupted by a crash, reboot or the app being killed shows "Resume or discard" on next launch; nothing is half-copied silently | Competitors run copies as in-memory tasks; a kill means a partial file and no record | 0.1 |
| 3 | **Verified copies** | Every copy is checked by SHA-256 against the source before the original is touched (already built: `VerifiedFileTransfer`) | Not offered by any competitor as a default | Done |
| 4 | **Transfer Bubbles** | Collect files from many folders and volumes into up to 7 floating bubbles, then drop them anywhere in one go | Unique to Atomic File Manager; dual pane is the closest equivalent and only works on wide screens | Seven-bubble update pending validation |
| 5 | **Preview before every bulk action** | Batch rename, cleanup, sync and extract show exactly what will change (old → new names, files to delete, space freed) before anything runs | Competitors apply bulk actions directly | 2.1, 2.6, 4.1 |
| 6 | **One index, everything instant** | Search, categories, storage analysis, duplicates and "recent" all come from one shared, incremental index: results in under 300 ms | Competitors scan separately per feature (Files by Google search is a known weak spot) | 1.4, 16.4 |
| 7 | **Cleanup that explains itself** | Every suggestion says *why* ("APK for an app you already installed", "not opened in 90 days"), how much space it frees, and goes to trash, so it can be undone | Files by Google suggests but doesn't explain; cleaners delete permanently | 2.6 |
| 8 | **Share without location** | Share sheet option that strips GPS and camera metadata from photos before sending | No file manager in the research offers it as a share option | 4.2 |
| 9 | **Three hiding levels + vault** | Fast Obscure (instant), Hide from Gallery, Private Storage, and an AES-256 vault behind biometrics | Competitors offer one mode (Safe folder or encryption) | Done + 2.5 |
| 10 | **Quick Peek** | Press and hold any image or video to preview it without opening a viewer | Unique interaction (already built) | Done |
| 11 | **Storage change report** *(new)* | "What changed since last week": which folders grew, what new large files appeared | Needs a persistent index with history, which competitors don't keep | 1.4 + 16.4 |
| 12 | **Smart destinations** *(new)* | Copy and move pickers show recent and frequent destinations first | Cheap with the journal; competitors make you navigate from the root every time | 0.1 + 1.2 |
| 13 | **Permission doctor** | One screen that says in plain words what access is missing and fixes it with one tap (FR-2.10) | Competitors show raw Android permission screens and errors | 1 (with 0.3) |
| 14 | **Guarded Wi-Fi share** | Share a folder to a PC browser with a PIN, read-only by default, auto-stops | Competitors expose open FTP servers | 3.4 (owner decision) |
| 15 | **Command palette** | Type "compress", "trash" or "theme" to run any action or open any setting | Not available in any Android file manager researched | 4.4 |

**The one-sentence pitch these add up to:** *the only file manager where every action can be previewed, verified and undone.*

---

## 16. Reliability and optimization plan

"Reliable and optimized" has to be measured, not claimed. This section lists the hotspots found in the current code (October 2026), the fix for each, and the checks that keep them fixed.

### 16.1 Measure first

Nothing in this section counts as done without a number.

| Tool | Purpose | Where |
|---|---|---|
| Macrobenchmark (`benchmark` module, currently disabled) | Cold start, folder scroll jank, search latency | Re-enable in Phase 0.5; run in CI on the emulator job |
| Baseline Profile (`androidx.profileinstaller` + generated profile) | Precompiles startup and Browse scroll code; typically a large cold-start improvement for Compose apps | Phase 0.5 |
| `StrictMode` (debug builds only) | Crashes the debug build on disk or network access on the main thread, so regressions are caught immediately | Phase 0.5 |
| JankStats | Logs slow frames on device during phone checks (`adb logcat -s AtomicJank`; `ui/util/DebugJank.kt`) | Debug builds; done |
| APK size report | Fail CI if the release APK grows more than an agreed amount without a note | CI |

**Budgets** (repeated from 7.5 so they live with the plan):

| Metric | Budget |
|---|---|
| Cold start to interactive Home | < 1 s on a mid-range phone |
| First rows of a 10,000-entry folder | < 300 ms |
| Frames over 32 ms while scrolling Browse | 0 in the benchmark |
| Indexed search, first results | < 300 ms |
| LAN folder open | < 2 s first time, < 500 ms after (connection reuse) |
| Release APK size | Agreed after R8 is on; each phase states its increase |

### 16.2 Hotspots found in the current code

| # | Hotspot | Evidence | Fix | Phase |
|---|---|---|---|---|
| H1 | Disk access on the main thread at start-up | `MainActivity.refreshVolumes()` runs from `LaunchedEffect` on the main thread; `StorageVolumes.enumerate` calls `StatFs` and `listFiles` | Run on `Dispatchers.IO`, show cached volumes instantly, update when ready | First sprint |
| H2 | SQLite reads on the main thread | `TransferBubbleDatabaseHelper` and `HiddenFilesDatabaseHelper` read in repository `init` | Move to IO (0.2), later to Room with `Flow` queries | 0.2 |
| H3 | No code shrinking | `isMinifyEnabled = false` in release | Enable R8 and resource shrinking with keep rules; re-run all tests on the shrunk build | 0.5 |
| H4 | No baseline profile | No `profileinstaller` or profile file | **Partly done:** `profileinstaller` ships (installs the library profiles Compose already bundles) and `BaselineProfileGenerator` exists in `:benchmark`. Generating and committing `app/src/main/baseline-prof.txt` needs an emulator run | 0.5 |
| H5 | Two separate full storage walks | `PhoneFileIndex` (categories) and `StorageAnalyzerUseCase` (analysis) each walk every folder | **Partly done:** categories show the search index's files at once and the walk only confirms them (no empty screen). Replacing the walk outright waits for MediaStore-generation freshness (16.4), so new camera photos are never missed | 1.4 |
| H6 | Storage analysis had no saved result | `StorageAnalyzerUseCase` rescanned every time | **Done (in memory):** a result under 10 minutes old is shown again with "scanned N min ago"; Rescan and cleanup walk again. Kept across app restarts and incremental rescans: not done | 1.4 |
| H7 | File lists show icons, not thumbnails | `FileListItem` draws `Icon` only | Add Coil 3 thumbnails sized to the row, memory + disk cache, cancelled when scrolled off-screen | 0.5 / 1.5 |
| H8 | Copy buffer was 64 KB | `FileOperationsEngine.BUFFER_SIZE` | **Done:** 256 KB. `transferTo` rejected: copies hash the source while streaming, and a kernel copy would need a second full source read to verify. On-device timing on SD and USB still to record | 0.1 |
| H9 | Very large screen files | `BrowseScreen.kt` had 1,438 lines, `MainActivity.kt` 958 | **Partly done:** `BrowseScreen.kt` split into Screen, Headers, BottomBar, Dialogs and Panes (largest now 528 lines), behaviour unchanged. Recomposition measurement (Layout Inspector counts) and splitting `MainActivity` remain | 0.3 |
| H10 | Old media APIs | `VideoView`, `MediaPlayer` in previews | Media3 with proper release on lifecycle stop | 1.5 |

Already good, keep it that way: listings stream in chunks of 200 (`ListingChunkSize.kt`); list items have stable keys (`key = { it.id.raw }`); category filtering and sorting run off the main thread; `PhoneIndexSnapshot` avoids structural `equals` on huge lists; images are downsampled with `inSampleSize`; verified copy hashes the source while copying (two passes, not three).

### 16.3 Browsing and UI smoothness

- Format sizes and dates in the view model once per listing, not in every row on every recomposition.
- Use `derivedStateOf` for "is this row selected" and selection counts so selecting one file does not recompose every row.
- Keep `FileNode` and UI state classes stable (immutable, no mutable collections) so Compose can skip unchanged rows.
- Thumbnails: decode at the row's pixel size, never full size; memory cache sized to about 1/8 of the app heap; disk cache for video frames and APK icons.
- Grid view uses `LazyVerticalGrid` with the same keys and thumbnail pipeline.
- Large result sets (search, categories with 100,000 files) are paged from the database instead of held in one list.

### 16.4 One shared index (the biggest single optimization)

Today, categories and storage analysis each walk the whole phone. Phase 1.4 replaces both with one index that every feature reads:

```
Walk (once, incremental) ──► search_index (Room + FTS4)
                                   │
        ┌──────────┬───────────────┼───────────────┬──────────────┐
     Search    Categories     Storage analysis   Duplicates    Recents / change report
```

- **Incremental refresh:** skip folders whose modified time has not changed since the last walk; on API 30+ use `MediaStore.getGeneration` to detect media changes without walking; Atomic File Manager's own operations update the index directly when they finish.
- **Full rescan:** only on demand, or when charging and idle.
- **Duplicates:** group by size first, then hash the first and last 64 KB, then full hash only for remaining candidates (FR-6.8). Most files are ruled out without being read.
- **Cancellation:** every walk checks for cancellation per folder (already done in `PhoneFileIndex`).

### 16.5 File operations: speed and safety

| Area | Plan |
|---|---|
| Same-volume move | Always rename (instant), never copy + delete (already the rule in FR-4.2; verify for SAF and SD) |
| Cross-volume copy | Larger buffer (H8); one copy at a time per physical volume pair, so two copies to the same SD card don't fight over it |
| Verification | Keep SHA-256 verification on by default for SD, USB and network. Note: re-reading a just-written file can be served from the page cache, so it proves the data stream, not the physical card; say this honestly in docs. Offer "Verify: always / large files / off" in Settings for speed |
| Atomic writes | Write to a temporary name, `sync()`, then rename (already done in `VerifiedFileTransfer` staging); use the same pattern for the text editor, archive creation and vault |
| Crash recovery | Journal (0.1) records each finished item before the next starts; start-up offers resume, and cleans up orphaned temporary files |
| Free space | Check destination free space before starting; fail early with a plain message |
| Background limits | Foreground service with notification; evaluate user-initiated data transfer jobs on API 34+ for very long transfers (Android 15 limits `dataSync` services to about 6 hours a day) |

### 16.6 Memory and battery

- No polling anywhere: volume changes come from broadcasts, index updates from MediaStore generation and Atomic File Manager's own operations.
- Background indexing only while charging, or when the user opens a feature that needs it.
- Scans stop when their screen closes (already true for category scans via flow cancellation).
- Bitmaps: downsampled, cached with a size limit, never kept by screens that are not visible.
- Large lists live in the database, not in memory (16.3).

### 16.7 Network performance and reliability (Phase 3)

- One connection pool per server, reused across folders (fixes the "30 s to connect to SMB" complaint about competitors).
- Timeouts on connect and read; automatic retry with backoff for temporary errors; clear message for permanent ones (wrong password, host key changed).
- Resume interrupted downloads and uploads where the protocol allows (FTP `REST`, HTTP `Range` for WebDAV, SFTP offsets).
- List folders in chunks, like local storage, so a large NAS folder shows first rows quickly.

### 16.8 Reliability testing

| Test | What it proves | Status |
|---|---|---|
| Fault-injection backends (`FaultInjectingBackendsTest`) | Full disk, corruption, failures mid-copy are handled | Exists; extend to each new operation type |
| Process-kill tests | Killing the app at each step of a copy, trash or rename loses nothing and offers resume | Copy covered by `ProcessKillTest` (mid-write, before publish, while replacing, after publish; JVM, simulated kill). A real-device kill run is still to do |
| Disposable-fixture device tests | Real file round trips on internal and SD | 12 exist; add one per new mutating feature |
| Monkey / random UI test | No crashes under random taps for 10,000 events | In the manual emulator CI job (release build, seed 42); not yet run |
| Device matrix | API 27, 29, 30, 33, 34, 36; phone, tablet, foldable (`testing/DEVICE_MATRIX.md`) | Only API 34 phone so far |
| Large-data test | 100,000 files, a 4 GB file, a 10,000-entry folder | Missing; needed for the budgets in 16.1 |

### 16.9 Release reliability

- Production signing key (currently debug-signed), R8 on, baseline profile shipped.
- Opt-in crash reporting (FR-11.4) so real-world crashes are seen; Play vitals for ANRs.
- Staged rollout on Play (for example 10 % → 50 % → 100 %), watching crash-free rate before widening.
- Risky features behind Labs flags (8.5) until verified on devices.

### 16.10 Optimization order

1. **Now (first sprint):** H1 (volumes off the main thread), H2 (database off the main thread), StrictMode in debug.
2. **Phase 0.5:** R8, baseline profile, Macrobenchmark in CI, Coil thumbnails.
3. **Phase 0.1:** buffer tuning (H8) together with the operation queue.
4. **Phase 0.3:** split `BrowseScreen` and `MainActivity` (H9) during the navigation work.
5. **Phase 1.4:** shared index (H5, H6) and paging.
6. **Phase 3:** network pooling, retries and resume.
