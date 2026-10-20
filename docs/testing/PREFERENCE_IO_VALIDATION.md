# Preference IO optimization — 19 October 2026

This follows the five main-thread disk-read flows observed in the [phone surface test](DEVICE_SURFACE_TEST_2026-10-19.md), audit finding A19. Production implementation is `257ba74`; the coroutine-error assertion correction is `849e03f`.

## Implementation

| Flow | Change |
|---|---|
| Folder sorting | Suspend metadata contract; preferences open/read/write on injected IO. Initial listing waits for its sort snapshot, newer user choices win over late reads, and accepted writes complete in order after pane disposal. |
| Bubble position | Lazy IO store retains the existing float keys. Gestures update UI immediately; pending snapshots and revisions prevent delayed reads or old rails from replacing newer coordinates. Finite accepted saves survive disposal. |
| Saved servers | Deferred store construction and serialized IO read/mutate/snapshot operations. Initial loading is explicit; duplicate saves/removals are gated and active-screen failures remain visible. Password encryption behavior and persisted format are retained. |
| Saved folder syncs | Deferred initial/resume reads and accepted removals on IO. Shared ordering survives screen replacement; repository writer locks protect read-modify-write operations across store instances. |
| Home quick-access folders | Context directory and public-path resolution occurs on IO, retaining folder order, fallback IDs and cancellation propagation. |

Shared preference names, keys and encoded values are unchanged. Accepted changes still use `SharedPreferences.apply()`; completing the small metadata operation does not establish synchronous disk durability or a cross-process transaction. Canceled readers stop normally; accepted writers finish their finite persistence section and suppress callbacks to disposed screens.

## Verification record

- Kotlin compilation, ktlint and detekt: passed in the initial integrated run.
- First complete JVM/Robolectric run: 695 tests, 694 passed, one new assertion failed. The failed assertion required exception object identity across coroutine suspension; both reported exceptions had the same type and message. Kotlin [stack-trace recovery](https://kotlinlang.org/docs/coroutines-debugging.html#stack-trace-recovery) copies exceptions and retains the original as a cause. The corrected test checks type, message and original cause without disabling debug mode or weakening application behavior.
- Final full JVM/Robolectric rerun: 695 tests passed, zero failures or errors. Custom Lint: 28 tests passed. Debug and release Android Lint, debug APK and debug instrumentation APK packaging passed. Logs are under ignored `build/audit-tools/preference-io-*.log`.
- Android 14/API 34 phone retest: Home, Files, Storage and Settings revisited successfully. All four selected navigation/recreation instrumentation tests passed. The five original flow frames were absent from the captured app log; three cold-start disk-read warnings remain in the baseline settings repository. This is a surface check, not provider, mutation or performance acceptance.

The 32 added cases cover physical IO dispatch, persisted-format compatibility, delayed reads versus gestures/sort changes, pending writes across screen replacement, disposal before dispatch, failure delivery, concurrent store writers and existing capacity/fallback behavior. Release APK rebuilding and remote required checks remain separate gates.

## Subsequent implementation and remaining acceptance

The later [FolderCompare preference change](FOLDER_COMPARE_PREFERENCE_IO_VALIDATION.md) implements asynchronous initialization and save acknowledgement, and [settings startup](SETTINGS_STARTUP_VALIDATION.md) addresses the remaining startup warnings. Those changes have their own validation scope; the 695-test checkpoint above does not validate them. Broader provider, process-kill, performance and production-signing acceptance remain separate roadmap work.

The isolated Kotlin plugin cleanup was subsequently completed and integrated at `69b46ca`: root Android and Kotlin plugin aliases share their classloader, and the custom Lint module inherits the Kotlin plugin version. Configuration, app/benchmark compilation and 28 custom Lint tests passed in its focused check without the duplicate-plugin warning. Toolchain/dependency versions are unchanged. The earlier failed candidates are historical diagnostics, not the current source.
