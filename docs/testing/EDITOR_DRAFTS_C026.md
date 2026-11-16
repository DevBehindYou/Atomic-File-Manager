# Crash-recoverable editor drafts — C026 slice 1

Claude Code task **C026** (EDT1; EDT-T01 increment). Base: feature integration `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Recorded 7 November 2026. Host-verified only. No device run, and no physical process kill.

## Audit before the change

- `TextEditorSession` was memory-only. Its own KDoc said "no draft file, SavedStateHandle, Bundle", and `CODE_TEXT_EDITOR.md` said "Drafts survive Activity recreation in memory, **not process death**".
- Nothing in `ui/components`, `domain/usecase` or `data/repository` matched draft, autosave or recover for the editor. The only "recovery" code is the operation journal (`RecoverySheet`, `OperationRecovery`), which is unrelated.
- The gap: if the process is lost, every unsaved edit is lost with it. A forced release (route disposal, owner cleared) also dropped the text.

## What this slice adds

| Part | File | Behaviour |
| --- | --- | --- |
| Contract | `domain/repository/EditorDraftStore.kt` | `EditorDraft(text, format, sourceRevision, savedAt)`. A read returns `None`, `Found` or `Damaged`. A write returns `Written`, `TooLarge` or `Failed`. |
| Rules | `domain/usecase/TextEditing.kt` → `TextDrafts` | `sourceRevision` is the SHA-256 of the encoding, the line ending and the loaded text. `allows(node, hiddenFolder)` is the vault and hidden policy (see below). |
| Store | `data/repository/TextDraftStore.kt` | One file per draft, named `sha256(FileNodeId.raw).draft`, so the path never appears. A write goes to a unique `.tmp`, is fsynced, then renamed over the old draft. Then it gets an LRU stamp and eviction runs. A trailing SHA-256 covers the header and body. The key is checked when the draft is read back. Temps left by a dead process are swept on the instance's first call. |
| Session | `ui/components/TextEditorSession.kt` | A draft write runs 1 s after the last text change. Edits that only move the selection are not text changes and write nothing. Store calls are serialized by a FIFO `Mutex`, and a call that has started finishes under `NonCancellable`. If the text matches the saved text again, the draft is removed. A successful Save removes the draft and moves the baseline to the saved identity. |
| Offer | `TextEditorDialog.kt`, `editor_strings.xml` | When a draft exists on open, the editor shows the **source** text plus *Recover edits* and *Discard draft*. Editing, Undo, Find and Save stay locked until the user picks one. |
| Wiring | `AtomicApp.kt` | `editorDraftStore` is the last member of `AppContainer` and of `DefaultAppContainer`. It sits away from the `init`/`appScope` block that PR #115 changes. Its directory is `noBackupFilesDir/editor-drafts`, resolved lazily on the I/O dispatcher. |

### Recovery semantics
- **Same source** (the revision matches): Recover puts the draft text in the editor as **one undo step**, so Undo returns to the source text. Nothing is written to the source. Saving still goes through the existing `TextFileEditor.save` path, which stages a temp and swaps with Trash or set-aside.
- **Changed source** (the revision differs): the offer says the file has changed since. Recover loads the text and marks the editor `recoveredOverChangedSource`. The status line then reads "recovered draft; the file changed since". A later draft keeps the **old** revision, so a second crash still shows "changed". Discard keeps the current source. A changed draft is never applied silently.
- **Draft equals the loaded text**: it is removed without asking.
- **Damaged** (truncated, bytes flipped, key mismatch, empty or foreign file): the draft is removed and the status line shows "a damaged draft was removed". The source opens normally.
- **Close**: a user close (clean, or *Discard changes* confirmed) removes the draft. Closing with the offer still unanswered keeps the draft. A forced release keeps the draft and writes the latest text at once if the owner scope is still alive. After process loss, the last debounced write is what remains.

### Decisions on sensitive content
- **Location and backup**: drafts are app-private. They live in `noBackupFilesDir`, which Auto Backup never includes, and the manifest already sets `allowBackup="false"`. Nothing is written next to the source or into shared storage. The manifest, backup rules and permissions are unchanged.
- **Vault and hidden policy**: no draft is ever read or written for a node that is `isHidden` (dot-files such as `.env`). The same applies to any path under a per-folder `.AtomicHidden` folder, under the configured hide folder (`AppSettings.hiddenFolder`, default `Atomic File Manager/Hidden`), or under the vault `files/private_storage`. The check runs on both the raw id and the URL-decoded id. This is conservative: editing a dot-file never keeps a draft. Codex may relax it.
- **Bounds**:
  - Per draft: 2 MiB of UTF-8 body, the same as `TextFileEditor.MAX_EDIT_BYTES`. A larger draft is not kept, any older draft for that file is removed, and the status shows "too large to keep a draft".
  - Count: at most 16 drafts.
  - Total: at most 8 MiB, with LRU eviction. The draft just written is never evicted.
  - Draft text is not encrypted at rest. It relies on app-private storage, as the rest of the app's private data does.

## Failing control

The new session and Compose tests ran with only the session's draft use disabled (`val draftable: TextEditorDrafts? = null`), which is the base memory-only behaviour. The store, policy and API stayed in place. On the unchanged base, the tests cannot compile because no draft API exists.

| Suite | Run | Failed |
| --- | --- | --- |
| `TextEditorDraftSessionTest` | 7 | **6**. The only pass is `without draft wiring the session stays memory-only`. |
| `TextEditorDraftRecoveryComposeTest` | 2 | **2** (the offer is not displayed) |

The control produced 8 of 9 failures. Examples: `expected: <1> but was: <0>` writes after a typing pause, `draftOffer?.sourceChanged expected <true> but was <null>`, and `text_editor_draft_offer is not displayed`. The control edit was reverted.

## Validation (host, shared `flock` Gradle lock)

| Command | Result |
| --- | --- |
| `:app:testDebugUnitTest --tests '*TextEditor*' --tests '*TextEditing*' --tests '*TextDraft*'` (before formatting) | 61 tests, 0 failed, 0 skipped. New tests: TextDraftStore 12, TextDrafts 2, TextEditorDraftSession 7, TextEditorDraftRecoveryCompose 2. Existing editor suites unchanged: TextEditing 6, TextEditingReadFailure 6, Session 7, SessionDialog 1, Retention 4 (API 28/34), History 6, HistorySession 3, HistoryIntegrationCompose 2, UndoRedoCompose 3. |
| Same, plus `*OpeningSourceRevisionComposeTest*`, at the committed head (after the ktlint wrap-only edits) | 67 tests, 0 failed, 0 skipped. That is the 61 above plus OpeningSourceRevisionCompose 6, an `AppContainer`-delegating suite that confirms the new container member compiles and is inherited. |
| `:app:ktlintCheck :app:detekt :app:lintDebug` | BUILD SUCCESSFUL, exit 0. No baseline or suppression was added. |

### What each fault and restart case proves (`TextDraftStoreTest`)
- **Fault at each write phase** (`TEMP_WRITTEN`, `TEMP_SYNCED`, `PUBLISHED`; IOException injected): a new instance reads the previous complete draft for the two pre-rename phases and the new complete draft for `PUBLISHED`. In all three cases no `.tmp` is left.
- **Process loss before the rename**: the full temp, and a torn half of it, are put back as a killed process would leave them. The next instance sweeps both, still reads the previous draft, and holds exactly 1 published draft.
- **Restart**: each read comes from a new `TextDraftStore` over the same directory. The file name is opaque: it does not contain "secret" from `secret plans.txt`.
- **Corrupt or truncated**: truncated by 5 bytes, one bit flipped, another key's file renamed into place, a zero-length file, and plain text. Each one reads `Damaged` exactly once, is removed, and then reads `None`.
- **Bounds**: 17 ASCII bytes, or 9×"é" (18 bytes), against a 16-byte cap gives `TooLarge` and removes the older draft. With 6 files under a count cap of 3, only the 3 most recent remain, including one that was rewritten. The total stays at or under 600 bytes, and an oversize single draft is still kept alone.
- **Original hashes**: the SHA-256 of the source file is unchanged after write, read, discard and rewrite, and the source folder holds only the source.

### Session cases (`TextEditorDraftSessionTest`, real store, real source file)
- **Process loss**: process A edits and gets 1 write after the 1 s pause, then its scope dies without close. Process B (a new store and session) is offered the draft and shows the source text with editing locked. Recover applies the draft with 0 extra writes, and the source hash is unchanged. Undo returns to the source text. Save writes the source through the save path and removes the draft.
- **Source changed between processes**: the draft is offered as changed, Recover marks the editor, and a crash keeps the draft as changed. Discard removes it, and the changed source hash is unchanged.
- **Write count**: 200 edits 100 ms apart give **1** write. A selection-only change gives 0. A burst of 50 edits gives 1 more, for **2** in total. Returning to the saved text removes the draft without a write.
- **Other cases**: damaged draft notice, close intent (user close, forced flush, offer left unanswered), policy-denied (0 store calls) and the too-large notice. Without wiring, the session stays memory-only.

## Remaining
- Hosted CI and Codex review on the PR head.
- Physical process-kill and device tests. They need an authorized owned fixture and are out of scope here.
- The directory fsync after the rename is not done, because the JVM has no portable directory fd. A power loss right after the rename may lose the rename, but never a complete older draft.
- A revision-checked (compare-and-swap) Save that refuses to overwrite a source that changed since load is still EDT-T01. Today a recovered-over-changed draft is marked, and saving it replaces the file.
- Drafts are not encrypted at rest, and a draft for a file that is later hidden or moved stays until LRU evicts it.
- Edits typed inside the 1 s window are lost if the process dies, or if the owner is cleared together with its scope (`onCleared`).
- Native and device acceptance of the offer UI.
