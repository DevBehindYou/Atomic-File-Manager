# File thumbnails and row actions

Implemented on 2026-10-19, with device-driven follow-up on 20 October. Current
results are recorded in [preview validation](PREVIEW_TOOLS_VALIDATION.md).

Browse already requested thumbnails, but the old loader accepted only local file IDs. Category,
search, favourites, recents and storage-intelligence rows used fixed type icons. These rows now
share a thumbnail leading slot. Images/videos on local paths, SAF document URIs and MediaStore
URIs use Coil requests at tile pixel size, with identity/mtime/size revision keys. Missing or
generic MIME types use MediaStore image/video collection identity when available, including
extensionless display names, then fall back to filename type detection. Explicit non-media MIME
types remain authoritative. Decode failures retain the type icon.

The API34 manual run exposed a native `MediaMetadataRetriever.finalize()` watchdog
crash. Decorative video requests now use a distinct Coil model and provider fetcher,
with at most two concurrent requests, cancellation signals and a 512-pixel bound.
API29+ uses `ContentResolver.loadThumbnail`; API27/28 uses document thumbnails or
the MediaStore thumbnail image cache, without the legacy helper's native-video
fallback. Local paths are matched to indexed video IDs through one exact-path query.
Unindexed videos and providers without thumbnails retain their video icon. The Coil
video decoder dependency is removed. Current integration `4792db8` passes 917 host
cases and all 13 selected API34 surface cases, including decoded image/folder
pixels. Real video thumbnails and a playback frame were also verified manually;
this does not establish universal codec support. The preceding Files selected log retains
one nonfatal Android InsetsSourceControl resource diagnostic; see
[current evidence](FILES_WORKSPACE_VALIDATION.md).

Local folder icons show up to four direct media children. The IO scan examines at most 64 entries,
never recurses, closes its directory stream, skips symlinks and hidden entries, and respects
inherited `.nomedia` markers. An additional IO preflight checks at most 128 absolute normalized
ancestors from the root, rejecting dot directories, symlinks, unknown access failures and deeper
paths before enumerating content. Revoked readable access immediately removes a miniature;
metadata changes reset its state before another peek. SAF/cloud/network folders retain their folder icon; they are not downloaded or
enumerated for decoration. A bounded peek can miss media beyond its scan window.

Per-file and per-folder More boxes and their unused sheet were removed as requested. Long press
continues to select rows; the selection toolbar and TalkBack custom actions remain available.

The tablet follow-up routes top/bottom actions, keyboard shortcuts, Back and mutation dialogs
through the active pane. Activating one pane clears the other pane's selection; ownership is
retained after clearing selection, with focus reset when returning to compact layout. Preview
uses its originating pane's gallery. A focused API 34/w900dp Compose regression exercises real
image-row long presses, right-pane contextual Rename and switching selection back left. Its
execution passes within the 917-case host suite; full tablet hardware acceptance remains pending. Pane clipboards retain their
existing independent behavior; this change does not introduce a shared clipboard.

Regression coverage checks source mapping, revision keys, bounded scanning and stream closure,
privacy, cancellation, fallback rendering, thumbnail row clicks and long-press selection without
the More box. A host without symlink support may skip that filesystem-specific case; report any
skip separately. Phone checks must confirm actual image/video decode and selection toolbar.

The follow-up adds generic-MIME MediaStore collection inference, inherited marker/symlink
preflight, ancestor bounds, late-completion permission revocation and stale-preview reset
regressions. These regressions pass in the integrated 917-case host suite, and generated native image/folder display passes on API34.
Checks describe point-in-time metadata; concurrent external filesystem changes are not locked.

The menu/icon refinement adds distinct theme-aware fallback colours and shapes for 15 file families, with metadata-only classification and retained photo/video pixels and folder-privacy checks. See [latest validation](MENU_REFINEMENT_VALIDATION.md).
