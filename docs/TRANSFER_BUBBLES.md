# Transfer Bubbles Architecture

Transfer Bubbles are Atomic File Manager's signature direct-manipulation staging system. They enable users to collect and organize groups of files across multiple folders and storage backends for future batch transfer—without copying bytes or cluttering the filesystem with temporary scratch copies.

The seven-bubble increment is prepared on 28 October 2026; [candidate validation](testing/SEVEN_TRANSFER_BUBBLES_CANDIDATE.md) remains pending until the parent records actual checks.

## 1. Core Principles

- **Virtual Staging Only**: A bubble holds *references* and metadata snapshots (`FileNodeId`, `displayNameSnapshot`, `sizeSnapshot`, `mimeSnapshot`). Adding files to a bubble is instantaneous.
- **Strict Limit of 7 Bubbles**: The user may create up to **7 bubbles simultaneously** (`MAX_BUBBLES = 7`). Creation is admitted atomically in SQLite so concurrent requests cannot exceed the cap; deleting a bubble releases its slot.
- **Full Process & Configuration Survival**: Bubbles and their staged items are persisted in SQLite via `TransferBubbleDatabaseHelper` and managed via `TransferBubbleRepository`. They survive app backgrounding, rotation, and process death.
- **No System Overlay Permission**: Bubbles are strictly in-app UI components anchored to a floating or dockable rail. Atomic File Manager never requests `SYSTEM_ALERT_WINDOW`.

## 2. Domain & Database Models

### `TransferBubble`
```kotlin
data class TransferBubble(
    val id: String,
    val displayName: String,
    val createdAt: Long = System.currentTimeMillis(),
    val sortOrder: Int = 0,
    val items: List<TransferBubbleItem> = emptyList(),
)
```

### `TransferBubbleItem`
```kotlin
data class TransferBubbleItem(
    val id: String,
    val bubbleId: String,
    val fileNodeId: FileNodeId,
    val originalLocation: String,
    val displayNameSnapshot: String,
    val sizeSnapshot: Long,
    val mimeSnapshot: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val isStale: Boolean = false,
)
```

## 3. UI Presentation: `TransferBubbleRail`

- On phones, bubbles sit on an anchored, movable rail along the side of the Browse view.
- Each bubble renders as a high-tonal Material 3 circular pill:
  - Bubble index/badge number (`1` through `7`).
  - Item count badge (e.g. `● 8`).
  - Drop target animation when a file drag hovers over it.

## 4. User Interaction Workflows

### 1. Staging Files (Adding to a Bubble)
- **Drag & Drop**: Drag a single file or a multi-selection stack onto any bubble pill on the rail.
- **Context Menu**: Long-press a file $\to$ *Add to Bubble* $\to$ choose an existing Bubble 1–7 or `+ New Bubble` while fewer than seven exist.
- **Selection Toolbar**: In multi-selection mode, tap the *Add to Bubble* action icon in the top app bar.

### 2. Primary Action: Tap Bubble (Commit Transfer)
When inside any writable folder, clicking a bubble opens `BubbleTransferDialog`:
- Displays: `"Transfer N items to [Current Folder]?"`
- Actions:
  - **Move here**: Transfers all files to destination. Successfully transferred items are removed from the bubble.
  - **Copy here**: Copies all files to destination. By default, items remain staged in the bubble until cleared.
  - **Review files**: Opens the details sheet.
  - **Cancel**: Closes dialog without action.

### 3. Inspection & Management: Long-Press Bubble
Long-pressing any bubble opens `BubbleDetailsSheet`:
- Header displays bubble title, item count, and total known byte size.
- Lazy list of staged files showing names, sizes, and previews.
- **Stale Item Handling**: If a file was deleted or moved externally, the entry displays an **Unavailable** badge rather than failing or crashing the session.
- Individual removal button per item.
- Actions: **Clear bubble** (removes all items) and **Delete bubble**.
