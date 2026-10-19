# Coding Rules

Hard rules. Each has a rationale, because a rule without one gets rationalised away at 2am.

These are engineering policies and targets, not proof that every existing call site complies
or every rule has an automated check. See [Current architecture](architecture/CURRENT_ARCHITECTURE.md)
for the verified implementation. Dependency lifetimes currently use manual `AppContainer`
wiring; these rules do not require a Hilt migration.

---

## Language and framework

1. **Kotlin only.** No Java sources. *(Consistency; Java files bypass our Lint rules.)*
2. **Compose only.** No XML layouts, no Fragments, no `View`-based custom components.
   One `Activity`. *(Two UI systems means two of every problem.)*
3. **Coroutines only** for async. No RxJava, no `AsyncTask`, no raw `Thread`.
4. Target Java/JVM 21, `compileSdk`/`targetSdk` 36, `minSdk` 27; CI uses JDK 21.

## Architecture

5. **No business logic in a `@Composable`.** A composable may branch on state; it may not
   decide *what the state should be*. *(Untestable, unpreviewable, re-executed unpredictably.)*
6. **UI state is an immutable `data class`.** No `MutableState` holding domain data, no
   mutable collections in state. *(Recomposition correctness depends on this.)*
7. **ViewModels never touch Android filesystem APIs.** No `File`, `Uri`, `ContentResolver`,
   `DocumentFile`. Lint-enforced. *(This is the boundary that makes the app testable.)*
8. **Domain has zero `android.*` imports.** Lint-enforced.
9. **Repositories abstract storage implementations.** A feature never knows which backend
   served it. *(Three backends exist precisely so nothing above has to care.)*
10. **No feature imports another feature.** Share via `core.ui` or `domain`.
11. **Use cases only for real logic.** A pass-through use case is deleted.
12. **Dependency injection for everything with a lifetime.** No singletons via `object`,
    no service locators, no `Context` static references.

## Concurrency

13. **No disk or `ContentResolver` I/O on the main thread.** Ever. `withContext(io)`.
14. **Dispatchers are injected**, never referenced statically. *(Tests need to substitute.)*
15. **No `runBlocking`** outside tests. Lint-enforced.
16. **No `GlobalScope`.** Lint-enforced.
17. **Never catch `CancellationException`** without rethrowing. *(Silent cancellation failure
    is the most common and most invisible coroutine bug.)*
18. **Every long loop has a cancellation point** (`ensureActive()` or `yield()`).

## Files and I/O

19. **Streaming only.** Never `readBytes()`, `readText()`, or `toByteArray()` on a user file.
    Buffers are 64–256 KB. *(A 2 GB video will OOM every device.)*
20. **Always `use { }`** for streams and cursors.
21. **Partial outputs are always cleaned up** on failure or cancellation.
22. **Never delete a source before the copy is byte-verified.**
23. **`listFiles() == null` means denied, not empty.** Always distinguish.
24. **No hardcoded paths.** Volumes come from `StorageManager`.
25. **All file operations go through the operations engine.** No ad-hoc `File.delete()` in a
    ViewModel or a use case.

## Permissions

26. **All permission logic lives in `PermissionManager` / `StorageAccessManager`.** No
    `checkSelfPermission` anywhere else.
27. **No `SDK_INT` checks outside the data layer and the glass capability manager.** Features
    ask for capability, not version. Lint-enforced against raw integer comparisons.
28. **Every permission request is preceded by an in-app rationale.**

## Design system

29. **No hardcoded colours, `dp`, `sp`, durations, or blur radii** outside
    `core.designsystem`. Lint-enforced. *(Otherwise the glass tiers and theming silently rot.)*
30. **Components are stateless** and take `state` + `onEvent`.
31. **Every component has previews** for light, dark, font 2×, RTL, small, tablet, and all
    three glass tiers.
32. **Every interactive element has semantics** written in the same commit as the layout.
33. **Glass is chrome only.** Never on list items, backgrounds, or text containers. Never
    more than two layers.

## Errors

34. **The data layer never throws across its boundary.** Everything becomes a `FileResult`.
35. **No bare `catch (e: Exception)`** that swallows. Map or rethrow.
36. **User-facing strings come from resources**, resolved in the composable.
37. **Every error state offers a route forward.**

## Compatibility

38. **API-gated calls use named constants**, never raw integers:
    ```kotlin
    object Api { const val Q = 29; const val R = 30; const val S = 31; const val T = 33; const val U = 34; const val V = 35; const val BAKLAVA = 36 }
    ```
39. **Every API branch is tested on both sides.**
40. **No deprecated API without a documented compatibility reason** in a comment at the call
    site.
41. **Never fake an unavailable capability.** If Android will not do it, say so.

## Dependencies

42. **Every dependency is justified in `TECH_STACK.md`.** No entry, no dependency.
43. **Version catalog only.** No inline versions in a `build.gradle.kts`.
44. **No snapshot or dynamic versions.**

## Testing

45. **Domain and data layers ≥ 80% coverage.**
46. **Every file operation invariant has a test** (see `architecture/FILE_OPERATIONS.md` §10).
47. **Every bug fix ships with the test that would have caught it.**

## Style

48. ktlint + detekt clean. No warnings on `main`.
49. Functions ≤ 60 lines; composables ≤ 100. Beyond that, extract.
50. Name things after what they are, not what they wrap. `SafBackend`, not `SafHelper`.
51. **No duplicated logic.** Two copies of the same decision will diverge, and the divergence
    will be a data-loss bug.
