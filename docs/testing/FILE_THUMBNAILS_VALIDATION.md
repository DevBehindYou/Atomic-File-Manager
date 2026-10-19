# File thumbnails and row actions

Implemented on 2026-10-19. Host/device validation is pending integration.

Browse already requested thumbnails, but the old loader accepted only local file IDs. Category,
search, favourites, recents and storage-intelligence rows used fixed type icons. These rows now
share a thumbnail leading slot. Images/videos on local paths, SAF document URIs and MediaStore
URIs use Coil requests at tile pixel size, with identity/mtime/size revision keys. Missing or
generic MIME types fall back to filename type detection. Decode failures retain the type icon.

Local folder icons show up to four direct media children. The IO scan examines at most 64 entries,
never recurses, closes its directory stream, skips symlinks and hidden entries, and respects
`.nomedia`. SAF/cloud/network folders retain their folder icon; they are not downloaded or
enumerated for decoration. A bounded peek can miss media beyond its scan window.

Per-file and per-folder More boxes and their unused sheet were removed as requested. Long press
continues to select rows; the selection toolbar and TalkBack custom actions remain available.

Regression coverage checks source mapping, revision keys, bounded scanning and stream closure,
privacy, cancellation, fallback rendering, thumbnail row clicks and long-press selection without
the More box. A host without symlink support may skip that filesystem-specific case; report any
skip separately. Phone checks must confirm actual image/video decode and selection toolbar.
