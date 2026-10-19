# File thumbnails and row actions

Implemented on 2026-10-19. Host/device validation is pending integration.

Browse already requested thumbnails, but the old loader accepted only local file IDs. Category,
search, favourites, recents and storage-intelligence rows used fixed type icons. These rows now
share a thumbnail leading slot. Images/videos on local paths, SAF document URIs and MediaStore
URIs use Coil requests at tile pixel size, with identity/mtime/size revision keys. Missing or
generic MIME types use MediaStore image/video collection identity when available, including
extensionless display names, then fall back to filename type detection. Explicit non-media MIME
types remain authoritative. Decode failures retain the type icon.

Local folder icons show up to four direct media children. The IO scan examines at most 64 entries,
never recurses, closes its directory stream, skips symlinks and hidden entries, and respects
inherited `.nomedia` markers. An additional IO preflight checks at most 128 absolute normalized
ancestors from the root, rejecting dot directories, symlinks, unknown access failures and deeper
paths before enumerating content. Revoked readable access immediately removes a miniature;
metadata changes reset its state before another peek. SAF/cloud/network folders retain their folder icon; they are not downloaded or
enumerated for decoration. A bounded peek can miss media beyond its scan window.

Per-file and per-folder More boxes and their unused sheet were removed as requested. Long press
continues to select rows; the selection toolbar and TalkBack custom actions remain available.

Regression coverage checks source mapping, revision keys, bounded scanning and stream closure,
privacy, cancellation, fallback rendering, thumbnail row clicks and long-press selection without
the More box. A host without symlink support may skip that filesystem-specific case; report any
skip separately. Phone checks must confirm actual image/video decode and selection toolbar.

The follow-up adds generic-MIME MediaStore collection inference, inherited marker/symlink
preflight, ancestor bounds, late-completion permission revocation and stale-preview reset
regressions. These are source-level additions pending integrated Gradle and device execution.
Checks describe point-in-time metadata; concurrent external filesystem changes are not locked.
