# Product Scope

## 1. In scope — MVP

* Browsing internal shared storage and SD card
* Category browsing (Images, Video, Audio, Documents, Archives, APKs, Downloads)
* Recent files, Favourites / pinned folders
* Search: filename, extension, category, size, date — scoped to a folder tree or all storage
* File operations: copy, move, rename, delete, share, open-with, new folder, new file
* Multi-select and batch operations, with an operation queue and progress
* Storage overview: used/free, by category, largest folders and files
* Preview: image, video, audio, plain text/code/markdown/json
* ZIP: create and extract
* Hidden-file toggle, sorting, view mode (list/grid), folder size calculation
* Full permission flows for API 27 → 37
* The Atomic design system (`design/ATOMIC_DESIGN_SYSTEM.md`), dark/light theme. Decided 7 Oct 2026; replaces the earlier plain Material 3 baseline
* Direct multi-selection drag-and-drop between folders, panes, and Transfer Bubbles
* Up to 7 persistent Transfer Bubbles for virtual staging
* Instagram-style Quick Peek for images and videos with GestureArbiter
* Multiple file hiding modes (Gallery Hide, Fast Obscure, Private Storage)
* Full TalkBack support, reduce motion, font scaling to 200%

## 2. In scope — V1

* Room-backed search index with incremental scanning
* Duplicate file finder (size → partial hash → full hash)
* Large files and empty folders views
* Command palette
* Undo for move/rename/delete-to-trash
* Clipboard manager and folder history
* USB OTG support
* Dual-pane on tablets and unfolded foldables, navigation rail, drag and drop
* PDF preview, APK metadata, checksums (MD5/SHA-1/SHA-256)
* TAR support, ZIP encryption read
* Home screen customisation (which shortcut rows appear)

## 3. Out of scope — permanently or until proven

| Item | Status | Reason |
|---|---|---|
| Root / system partition browsing | Out | Play policy risk, safety risk, tiny audience. Shizuku is an optional Labs add-on (`roadmap/ALL_IN_ONE_PLAN.md` 4.3) |
| FTP / SMB / WebDAV / SFTP client | **In: Phase 3** (decided 7 Oct 2026) | Code already existed; host-key and certificate checks and Keystore credentials are required (plan 3.1) |
| FTP/HTTP **server** | Out, except the guarded Wi-Fi share | Only plan 3.4: local Wi-Fi, one folder, read-only by default, one-time PIN, auto-stop |
| Cloud accounts (Drive, Dropbox) | Future | No in-app logins; cloud apps are reached through Android providers and WebDAV (plan 3.3) |
| Built-in media player beyond preview | Out | A Media3 preview player only; no media library |
| Document editing (docx/xlsx) | Out | Rendering fidelity is a product in itself. Plain-text editing is in (plan 2.3) |
| Themes marketplace, icon packs | Out | Dilutes the design identity |
| Ads, analytics SDKs, referral | Out | Contradicts `PRIVACY.md` |
| Cloud AI features on user files | Out | Contradicts `PRIVACY.md`; see `roadmap/FUTURE.md` §AI |
| `/Android/data` browsing on API 30+ | Impossible | Platform-blocked; see `ANDROID_STORAGE_RESEARCH.md` |

## 4. Non-goals (design)

* Not a "pro tool" aesthetic. No dense toolbars, no permanent 8-icon action row.
* Not a category-only launcher that hides the filesystem. Both views are first class.
* The Atomic design system is the foundation. No fake glass surfaces, blur shaders, or runtime AGSL overhead.
* Not a settings maze. Settings fits on two scroll-screens.

## 5. Scope guards

Any new feature request is tested against three questions:

1. Does it serve Priya, Arjun, Sana or Dev specifically?
2. Can it be reached in ≤ 3 taps from Home without adding a permanent UI element?
3. Does it risk file safety, and if so, is the risk contained by confirmation + undo?

Two "no"s and it goes to `roadmap/FUTURE.md`.
