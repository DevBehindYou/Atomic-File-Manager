# Privacy

> **Implementation clarification, 17 October 2026:** the sections below describe the original offline MVP policy/design, not a verified description of the current binary. The manifest now requests INTERNET and ACCESS_NETWORK_STATE for user-directed remote storage, discovery and Wi-Fi sharing. Files and metadata can leave the device when the user requests those features or shares with another app. Settings use SharedPreferences; operation history is stored in Room. The promised crash-reporting flavour, universal log redaction and exact retention guarantees below were not verified in the implementation. No telemetry SDK was identified in the declared dependencies, but this is not a traffic audit. A publishable privacy/Data Safety declaration requires the owner's review against these current features; this audit does not submit or change a store declaration. See [current inventory](architecture/CURRENT_ARCHITECTURE.md) and [audit](architecture/PROJECT_AUDIT.md).

## 1. Position

Atomic File Manager is **local-first**. Browsing, indexing, thumbnailing, hashing, and analysis all
happen on the device. Nothing about the user's files leaves the device.

## 2. Data map

| Data | Where it lives | Leaves device? | Retention |
|---|---|---|---|
| File names, paths, sizes, dates | Device only — read on demand, cached in Room | **No** | Cache cleared on request; index rebuilt on demand |
| File contents | Device only — streamed, never buffered whole | **No** | Not retained |
| Thumbnails | App cache directory | **No** | LRU, 250 MB cap, cleared from Settings |
| Favourites, recents, search history | Room, app-private | **No** | Until the user clears them |
| SAF tree URI grants | Persisted permissions | **No** | Until revoked |
| Settings | DataStore, app-private | **No** | Until uninstall |
| Operation history | Room, names redacted after 24 h | **No** | 7 days |
| Crash reports | **Opt-in only**, separate build flavour | Yes, if enabled | Per provider policy |

## 3. Permissions and why each exists

| Permission | Why | Optional? |
|---|---|---|
| `READ_EXTERNAL_STORAGE` (≤32) | Read user files | Yes — app works on app-private files without it |
| `WRITE_EXTERNAL_STORAGE` (≤28) | Modify user files | Yes |
| `READ_MEDIA_IMAGES/VIDEO/AUDIO` (33+) | Category browsing and thumbnails without All Files Access | Yes |
| `MANAGE_EXTERNAL_STORAGE` (30+) | Core file management across storage | Yes — SAF mode is a complete alternative |
| `POST_NOTIFICATIONS` (33+) | Operation progress notifications | Yes — operations still run |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Keep long operations alive | Required for background operations |
| `INTERNET` | **Not requested at MVP.** Only present in the opt-in crash-reporting flavour | — |

**No** `ACCESS_NETWORK_STATE`, `READ_PHONE_STATE`, `AD_ID`, location, contacts, or camera.

## 4. Crash reporting

* Off by default. A first-run screen asks once, with a clear "No thanks" of equal visual
  weight.
* The reporter is compiled into a separate product flavour. The F-Droid / GitHub flavour has
  **no network permission at all**, so the promise is structurally enforced, not just a toggle.
* Reports contain: stack trace, Android version, device model, app version, glass tier,
  and the anonymised operation type in flight.
* Reports never contain: file names, paths, MIME types of user files, volume labels, URIs,
  or any string derived from user content. Enforced by a redaction pass and a unit test that
  feeds known paths through the reporter and asserts they are absent.

## 5. Logging

See `LOGGING_DIAGNOSTICS.md`. In release builds, file names and paths are hashed
(`SHA-256`, first 8 hex chars) before being logged. Debug builds may log plaintext paths and
must never be shipped.

## 6. AI

No AI feature ships in MVP or V1. Any future AI feature must satisfy all four:

1. It runs **fully on-device**.
2. It is **off by default** and opt-in per feature.
3. It is **useful without a network** — if it needs a server, it is rejected.
4. Its model and index are deleted when the user turns it off.

See `roadmap/FUTURE.md` §AI for the two candidates that pass this bar and the several that
do not.

## 7. Play Data Safety declaration

Declared as: **no data collected, no data shared** in the base flavour. The crash-reporting
flavour declares *Crash logs — collected, not shared, optional, for app functionality*.
Keep the declaration and this document in sync; changing one without the other is a review
failure.

## 8. Third-party SDKs

None that phone home. The full dependency list in `TECH_STACK.md` contains no analytics,
no advertising, and no attribution SDK. Adding one requires updating this document, the
Data Safety form, and the store listing.
