# Testing Strategy

## 1. What we are actually protecting

This app moves and deletes irreplaceable user data. The cost of a bug is not a bad review; it
is someone's photos. So the test strategy is weighted by consequence, not by coverage vanity:

| Risk | Consequence | Test weight |
|---|---|---|
| Data loss during move/delete | Irreversible | Highest — exhaustive |
| Silent corruption during copy | Irreversible, discovered late | Highest — exhaustive |
| Wrong storage backend chosen | Feature appears broken | High |
| Permission handled wrong for an API level | App unusable on that version | High |
| Search misses files | Frustration | Medium |
| Glass renders wrong | Cosmetic | Low — visual only |

A coverage percentage is not a goal. **Every invariant in
`../architecture/FILE_OPERATIONS.md` §2 must have a named test.** That is the goal.

## 2. The pyramid

| Layer | Tool | Count target | Runs on |
|---|---|---|---|
| Unit — domain | JUnit 5 + Turbine + MockK | ~400 | JVM, every commit |
| Unit — ViewModel | JUnit 5 + `runTest` + fakes | ~120 | JVM, every commit |
| Integration — data | Robolectric + temp dirs + in-memory Room | ~150 | JVM, every commit |
| Instrumented — storage | AndroidX Test on emulator | ~80 | API 28, 29, 30, 33, 36 |
| UI — Compose | `createAndroidComposeRule` | ~90 | API 30 + 36 |
| E2E journeys | Compose UI test, real files | 12 | API 29 + 36 |
| Screenshot | Roborazzi | ~60 | JVM, every commit |
| Manual | Device matrix | See `DEVICE_MATRIX.md` | Per release |

## 3. Domain tests

Pure Kotlin, no Android. Every use case, every error mapping, every sort comparator, every
path-safety check.

Fakes, not mocks, for `StorageBackend`: an `InMemoryBackend` implementing the full interface
over a map, plus fault-injecting decorators — `FailAfterNBytes`, `DenyWrite`, `SlowBackend`,
`VanishingSource`. These make failure paths testable deterministically, which is the only way
the copy invariants ever get exercised.

Required domain test groups:

* `CopyFilesUseCaseTest` — success, collision each policy, cancellation, source vanishes
  mid-copy, target volume full, verification mismatch, recursive destination rejected.
* `MoveFilesUseCaseTest` — same-volume rename path, cross-volume copy-then-delete path, and
  the critical one: **source is never deleted when the copy or verification fails.**
* `DeleteFilesUseCaseTest` — trash path, permanent path, partial failure reporting.
* `PathSafetyTest` — Zip Slip corpus, `..` traversal, absolute entries, symlink entries,
  Unicode normalisation attacks, null bytes, Windows drive prefixes.
* `SortComparatorTest` — natural numeric ordering, locale collation, folders-first, ties.
* `ErrorMapperTest` — every `IOException` subtype and errno maps to a `FileError`, with no
  `else -> Unknown` fallthrough passing silently.

## 4. Data-layer tests

Robolectric with real temporary directories. These are the tests that catch the difference
between what the docs say and what the platform does.

* `FileSystemBackendTest` — listing, streaming chunk boundaries at exactly 200 / 201 / 0 items,
  deep nesting, names with spaces, emoji, RTL text, 255-byte names.
* `SafBackendTest` — `DocumentFile` tree traversal, the known performance cliff on large
  directories, revoked-grant handling.
* `MediaStoreBackendTest` — projections, `IS_PENDING`, `RecoverableSecurityException` paths.
* `FileNodeIdTest` — round-trip encode/decode for all three prefixes; malformed ids rejected.
* Room migration tests — every migration, both directions where supported, with a populated
  schema. `exportSchema = true`, schemas committed to the repo.

## 5. UI tests

Compose UI tests use **semantics, never coordinates**. A test that taps at (540, 1200) is not a
test, it is a screenshot with extra steps.

Required UI test groups:

* Each screen renders each of its four canonical states: loading, content, empty, error — and
  the **no-access** state is verified to be visually and semantically distinct from empty.
* Selection mode: enter, select, select all, action-bar enable/disable by `AccessFlags`,
  survives rotation.
* Navigation: deep folder, back stack, breadcrumb jumps, predictive back.
* Accessibility assertions run in every UI test as a shared rule: every clickable node has a
  content description, every touch target is ≥ 48dp, no node has an empty label.
* Font scale 200% and display size Largest are applied in a parameterised run over the key
  screens; the assertion is that nothing truncates and no control leaves the viewport.

## 6. Process-death and state-restoration tests

Referenced from `../architecture/UI_LAYER.md`. Every screen holding user intent must pass:

```text
1. Navigate to the screen, establish state (selection, query, scroll, zoom, playback position)
2. Force process death:  adb shell am kill com.devbehindyou.atomicfilemanager
3. Relaunch from Recents
4. Assert the state is restored
```

Screens under this rule: Browse (folder, sort, scroll, selection), Search (query, filters,
results re-run), Preview (file id, page index, zoom, playback position), Operations (queue
restored from Room), Permission setup (step position).

The automated form uses `StateRestorationTester` for composable-level restoration plus an
instrumented `SavedStateHandle` round-trip; the manual `am kill` form is in the release
checklist because `StateRestorationTester` does not exercise the real serialisation path.

## 7. File-operation soak tests

Run before every release, instrumented, on a real device:

| Test | Set | Pass condition |
|---|---|---|
| Large single file | 4 GB | Byte-identical, no OOM, progress monotonic |
| Many small files | 50,000 × 4 KB | Completes, no ANR, memory stable |
| Deep nesting | 200 levels | Completes or fails cleanly at the depth cap |
| Mixed set | 10 GB, 20k files | Completes, verification passes on all |
| Interrupted copy | Kill at 50% | No partial target survives, source intact |
| Full destination | Fill to 99% | Pauses with the space message, no corruption |
| Volume ejected mid-copy | SD removed | Fails fast, partial target removed, source intact |
| Concurrent operations | 3 simultaneous | Serialised per volume, all correct |

**Verification** in these tests is a full SHA-256 of source and target, not a size comparison.
Size comparison passes on a truncated-and-padded copy; a hash does not.

## 8. Performance tests

Macrobenchmark, on a mid-tier physical device, gated in CI against
`../NON_FUNCTIONAL_REQUIREMENTS.md` budgets:

| Metric | Budget | Method |
|---|---|---|
| Cold start to first frame | < 500 ms | `StartupTimingMetric` |
| Folder open, 1,000 items | < 300 ms | `TraceSectionMetric` |
| Scroll jank, 5,000 items | < 1% frames over budget | `FrameTimingMetric` |
| Search first result, 100k files | < 1 s | Custom trace |
| Glass tier A frame time | < 8 ms | `FrameTimingMetric`, glass screens |
| Memory, 10k-item folder | < 200 MB PSS | `MemoryUsageMetric` |

A regression over budget fails the build. Baseline Profiles are generated for the startup and
browse journeys and regenerated whenever those paths change materially.

## 9. Screenshot tests

Roborazzi on the JVM. Every component in `../COMPONENT_LIBRARY.md` × {light, dark} ×
{glass tier A, B, C} × {default font, 200% font}. Diffs are reviewed, not auto-accepted.

This is the only practical way to verify that **tier C looks intentional rather than
degraded**, which is a stated product requirement, not a nice-to-have.

## 10. What we deliberately do not test automatically

* Actual visual quality of refraction — no assertion captures "does this look like glass".
  It is a manual matrix item with reference screenshots.
* Codec support — device-variable by definition; we test that failure is handled, not that
  playback succeeds.
* Real SD card and USB OTG behaviour — emulators do not model these faithfully. Manual only.
* Vendor storage quirks — see `DEVICE_MATRIX.md` §5.

## 11. CI gates

| Stage | Gate |
|---|---|
| Every PR | Lint (including the seven custom rules), detekt, unit + Robolectric + screenshot tests |
| Every PR | No new `@Suppress` on the custom Lint rules without a linked issue |
| Merge to main | Instrumented tests on API 28, 29, 30, 33, 36 emulators |
| Nightly | Macrobenchmark against budgets; soak subset |
| Release branch | Full soak, full device matrix, manual checklist |

## 12. Open platform questions promoted to tests

The unresolved items in `../ANDROID_STORAGE_RESEARCH.md` §10 are not left as prose. Each one
becomes a named instrumented test that records the real device behaviour, so the answer is
captured as an executable fact rather than a wiki page that rots. See `DEVICE_MATRIX.md` §6.
