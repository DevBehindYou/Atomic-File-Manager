# Storage Architecture — the `FileNode` abstraction

The single most important design decision in this project: **no code above the data layer
ever handles a `File`, a `Uri`, or a `Cursor`.** Everything is a `FileNode`.

---

## 1. The model

```kotlin
/** A file, folder, or document, wherever it physically lives. */
@Immutable
data class FileNode(
    val id: FileNodeId,            // stable, backend-qualified identity
    val name: String,              // "report.pdf"
    val displayName: String,       // name without extension where useful, or a friendly volume name
    val mimeType: String?,         // resolved; null when unknown
    val size: Long,                // bytes; -1 when unknown (uncomputed folder)
    val modifiedAt: Long,          // epoch millis; 0 when unknown
    val isDirectory: Boolean,
    val isHidden: Boolean,
    val parentId: FileNodeId?,     // null at a volume root
    val storageType: StorageType,
    val access: AccessFlags,
    val childCount: Int?,          // null when not yet counted
    val extras: NodeExtras?,       // lazily-attached: duration, dimensions, apk package, ...
)

@JvmInline
value class FileNodeId(val raw: String)
// Encodings, always parseable, never guessed:
//   "file:/storage/emulated/0/Download/a.pdf"
//   "saf:content%3A%2F%2Fcom.android.externalstorage.documents%2Ftree%2F..."
//   "media:external:images:10432"

enum class StorageType { INTERNAL_PRIVATE, INTERNAL_SHARED, SD_CARD, USB, MEDIA_INDEX, VIRTUAL }

@Immutable
data class AccessFlags(
    val readable: Boolean,
    val writable: Boolean,
    val deletable: Boolean,
    val renamable: Boolean,
)
```

### Why an id string and not a `Uri`

`Uri` is an Android type; putting it in the domain breaks the dependency rule and makes the
domain untestable on the JVM. `FileNodeId` is an opaque string with a documented grammar.
Only the backends parse it.

### `AccessFlags` is not decoration

On API 30+ a node can be readable but not writable (a MediaStore item owned by another app),
or listed but not openable. The UI **disables** actions based on these flags rather than
letting them fail. Backends must populate them honestly, not optimistically.

---

## 2. The backend interface & capability model

Instead of checking concrete backend types (e.g. `is FileSystemBackend`), callers query capabilities:

```kotlin
data class StorageCapabilities(
    val canRead: Boolean = true,
    val canWrite: Boolean = false,
    val canCreate: Boolean = false,
    val canDelete: Boolean = false,
    val canRename: Boolean = false,
    val supportsAtomicMove: Boolean = false,
    val supportsRandomAccess: Boolean = false,
    val supportsTrash: Boolean = false,
    val supportsHideMedia: Boolean = false,
    val supportsObfuscate: Boolean = false,
    val supportsServerSideCopy: Boolean = false,
)

interface StorageBackend {
    val type: BackendType
    val capabilities: StorageCapabilities

    fun canHandle(id: FileNodeId): Boolean

    suspend fun getNode(id: FileNodeId): FileResult<FileNode>
    fun listChildren(id: FileNodeId): Flow<FileResult<List<FileNode>>>
    suspend fun openInput(id: FileNodeId): FileResult<InputStreamProvider>
    suspend fun openOutput(parent: FileNodeId, name: String, mime: String?): FileResult<OutputTarget>
    suspend fun createDirectory(parent: FileNodeId, name: String): FileResult<FileNode>
    suspend fun delete(id: FileNodeId): FileResult<Unit>
    suspend fun rename(id: FileNodeId, newName: String): FileResult<FileNode>
    suspend fun moveWithin(id: FileNodeId, newParent: FileNodeId): FileResult<FileNode>?
    suspend fun exists(parent: FileNodeId, name: String): Boolean
    suspend fun freeSpace(id: FileNodeId): Long
}
```

`listChildren` returns a `Flow` of results, not a `suspend fun`, so a 40,000-entry directory
can emit in chunks of 200 and the UI paints immediately.

### The three backends

| Backend | Handles | Notes |
|---|---|---|
| **`FileSystemBackend`** | `file:` ids | Fastest. Available on API 28 always, and on API 30+ when All Files Access is granted. Uses `File.listFiles()` + `java.nio` where beneficial. Never used on API 29 for shared storage. |
| **`SafBackend`** | `saf:` ids | Works everywhere from API 21. **Listing uses a single bulk `ContentResolver.query()` against `buildChildDocumentsUriUsingTree`**, projecting exactly the columns needed — never `DocumentFile.listFiles()`, which issues one query per child and is 10–50× slower. |
| **`MediaStoreBackend`** | `media:` ids | Read-optimised index for categories, recents, largest-files and name search on the primary volume. Deletes go through `createDeleteRequest` on API 30+. General writes never go here. |

### Backend selection

```kotlin
class StorageBackendSelector @Inject constructor(
    private val backends: Map<BackendType, @JvmSuppressWildcards StorageBackend>,
    private val access: StorageAccessManager,
) {
    fun forNode(id: FileNodeId): StorageBackend = backends.values.first { it.canHandle(id) }

    /** Which backend should own a *volume*, given current grants and API level. */
    fun forVolume(volume: StorageVolume): StorageBackend = when {
        volume.type == INTERNAL_PRIVATE                        -> file
        SDK_INT < API_Q                                        -> file          // 27–28
        SDK_INT == API_Q                                       -> saf           // 29: no MES, no legacy
        access.hasAllFilesAccess && volume.hasRealPath         -> file          // 30+ granted
        access.hasTreeGrantFor(volume)                         -> saf           // 30+ SAF fallback
        else                                                   -> saf           // will prompt
    }
}
```

**Every branch here is a documented Android behaviour, not a guess.** See
`../ANDROID_STORAGE_RESEARCH.md` §2.

---

## 3. Repositories

| Repository | Responsibility | Does not |
|---|---|---|
| `StorageRepository` | Enumerate volumes, free/total space, mount state as a `Flow`, volume → root `FileNode` | Know about directories |
| `FileRepository` | `getNode`, `observeDirectory`, `createFolder`, `createFile`, `resolveMime`, folder size computation | Perform batch operations |
| `FileOperationRepository` | Enqueue, observe, cancel, retry operations; persist history | Execute (the service does) |
| `SearchRepository` | Query across MediaStore / index / walk, merged and deduplicated | Own the index schema |
| `ArchiveRepository` | List, extract, create archives with streaming and path validation | Know about the UI |
| `RecentRepository` | Recently modified and recently opened | Duplicate MediaStore |
| `FavoritesRepository` | Pin/unpin, ordering, dead-target detection | Store file contents |
| `StorageAnalysisRepository` | Category totals, largest folders/files, duplicates (V1), cached scans | Delete anything |
| `PreferencesRepository` | Settings via DataStore | Anything else |

**`observeDirectory`** is the workhorse:

```kotlin
fun observeDirectory(
    id: FileNodeId,
    sort: SortSpec,
    showHidden: Boolean,
): Flow<DirectoryState>

sealed interface DirectoryState {
    data object Loading : DirectoryState
    data class Partial(val nodes: List<FileNode>, val loadedSoFar: Int) : DirectoryState
    data class Complete(val nodes: List<FileNode>) : DirectoryState
    data class Failed(val error: FileError, val partial: List<FileNode>) : DirectoryState
}
```

`Partial` is why huge directories never ANR: the first 200 entries reach the UI in
~80 ms and the rest stream in.

---

## 4. Directory watching

| Backend | Mechanism | Caveats |
|---|---|---|
| `FileSystemBackend` | `FileObserver` on the current directory only | Unreliable on some OEMs and for SD cards; **[device-variable]**. Never the only refresh path |
| `SafBackend` | `ContentResolver.registerContentObserver` on the children URI | Providers are inconsistent about notifying |
| `MediaStoreBackend` | `ContentObserver` on the collection URI | Reliable but lags the filesystem |

Because none is dependable, the repository also refreshes on: `ON_RESUME`, pull-to-refresh,
and the completion of any operation whose destination is the visible directory. Watching is
an optimisation, not a correctness mechanism.

---

## 5. Caching

| Cache | Store | Invalidated by |
|---|---|---|
| Directory listing | In-memory LRU, 20 directories | Any operation touching that path, observer, resume |
| Folder size | Room, keyed by node id + mtime | mtime change, explicit refresh |
| Thumbnails | Coil memory + disk | LRU, manual clear |
| Search index (V1) | Room FTS | Incremental scan, MediaStore version token |
| Storage analysis | Room, with `scannedAt` | Manual rescan, 24 h staleness |

---

## 6. Volume enumeration

```kotlin
data class StorageVolumeInfo(
    val id: String,
    val label: String,             // "Internal storage", "SD card", "USB drive"
    val type: StorageType,
    val totalBytes: Long,
    val freeBytes: Long,
    val isRemovable: Boolean,
    val isMounted: Boolean,
    val rootNodeId: FileNodeId?,   // null when no access has been granted yet
    val requiresGrant: Boolean,
)
```

Source: `StorageManager.storageVolumes`. `StorageVolume.directory` on API 30+;
below that, `getExternalFilesDirs()` mapped back to volume roots. **No reflection, ever.**
A volume whose root cannot be resolved is still shown, with `requiresGrant = true` and a
"Grant access" action — never hidden, because hiding it looks like a bug to the user.

Mount changes are observed via `ACTION_MEDIA_MOUNTED` / `UNMOUNTED` / `EJECT` and
`UsbManager` attach/detach, exposed as a `Flow<List<StorageVolumeInfo>>`.

---

## 7. Rules

1. No `java.io.File` above `data.backend`. Lint-enforced.
2. No hardcoded paths. Volume roots come from `StorageManager`.
3. `FileNodeId` is opaque above the data layer; only backends parse it.
4. Every backend method returns `FileResult`, never throws across the boundary.
5. `listChildren` must stream. A `suspend fun` returning `List` is a rejected design.
6. `AccessFlags` must be truthful; the UI trusts them to disable actions.
7. A node's `size` for a directory is `-1` until computed. Never compute it inline.
8. When a backend cannot support an operation natively (e.g. cross-volume move), it returns
   `null`/unsupported and the **operations engine** falls back to copy + verify + delete.
