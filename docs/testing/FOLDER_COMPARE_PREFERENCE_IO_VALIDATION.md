# Folder compare preference IO

Date: 2026-10-19. Implementation checkpoint; integrated Gradle and phone validation are pending.

The FolderCompare route now constructs, reads, and updates SyncPairStore through the injected IO dispatcher and shared sync-pair mutex. The editable comparison is opened only after the saved mode loads. Failed reads show retry and Back controls without starting a comparison or presenting an editable default mode.

Accepted saves reserve their place before IO dispatch and finish when the screen closes. The closed screen receives neither success acknowledgement nor error feedback. A successful active save acknowledges the captured pair and mode; duplicate taps are rejected while saving. Failed saves retain the previous acknowledged mode and allow retry. Existing direct FolderCompareScreen callers keep their savedMode/onSave contract.

Added tests cover injected construction/read/save dispatch, saved-mode restoration, load failure and retry, save acknowledgement and duplicate rejection, save failure and retry, disposal before read dispatch, accepted save after disposal, and a delayed read racing a newer choice. Cancellation is checked explicitly before publishing a read result or a read failure; a deterministic same-dispatcher test cancels from inside the read before either returning or throwing an ordinary failure. A real SyncPairStore adapter test checks the existing preference format and IO access on Android API 27 and 34. A Compose test exercises the actual save button through unconfirmed, saving, failed/retryable and acknowledged states. Eight controller tests, two adapter API variants and one Compose test await execution in the integrated build.

Local validation: standalone repository ktlint formatting/check and git diff --check passed. No Gradle process, phone interaction, or remote publication was run from this worktree.

Persistence retains the existing SharedPreferences.apply contract: an acknowledged metadata update is visible to subsequent in-process reads, while disk flushing remains asynchronous. This change does not claim crash-safe disk commits or measured frame-time improvement.
