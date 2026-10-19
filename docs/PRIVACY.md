# Privacy

Source review: 19 October 2026. This document describes the current source tree; it is not a traffic audit, legal certification, or submitted store declaration. See the [current architecture](architecture/CURRENT_ARCHITECTURE.md) and [project audit](architecture/PROJECT_AUDIT.md).

## Current behavior

Atomic File Manager performs local browsing, indexing, hashing, and analysis on the device. It also supports user-directed remote storage, network discovery, Wi-Fi sharing, and sharing with other apps. Files, names, paths, connection details, and credentials can leave the device when those features require them. There is no implemented promise that all files and metadata remain on the device.

The main manifest requests `INTERNET` and `ACCESS_NETWORK_STATE`. The current Gradle configuration has debug, release, and benchmark build types; it does not define an offline or opt-in crash-reporting product flavour. No analytics, advertising, attribution, or crash-upload SDK was identified in the declared app dependencies. This source inspection does not establish what every dependency or operating-system component may transmit.

## Data stored by the app

| Data | Current storage and handling | Retention evidence |
|---|---|---|
| Search metadata | Room search index stores names, node IDs/paths, parent IDs, MIME types, sizes, and dates. | Rebuilds replace older generations; no universal time-based expiry was found. |
| Favourites and recents | App-private Room tables; recent media can also be queried from MediaStore. | Feature-specific updates; no common expiry guarantee established. |
| Operation history | Room journal stores raw source/destination node IDs, progress, status, and error text. These fields may contain full paths or names. | A finished-row pruning DAO method exists, but no production caller was found. Neither 24-hour redaction nor seven-day deletion is implemented. |
| Hidden items and transfer bubbles | Separate app-private SQLite databases retain item metadata and locations; hiding also has a recovery journal. | Records change with the respective feature. Metadata is not universally encrypted or redacted. |
| Settings and saved locations | SharedPreferences stores settings, per-folder sort choices, sync pairs, search-index state, and other feature preferences. SAF grants are held through Android's persisted URI permissions. | Until changed, removed through a supported feature, or app data is removed; no universal timed expiry. |
| Saved network servers | App-private SharedPreferences stores host, port, protocol, username, remote path, and server label. | Saved servers can be deleted. These configuration fields are not password-encrypted. |
| Saved network passwords | AES-256-GCM ciphertext in SharedPreferences, using an Android Keystore key. Legacy values are migrated when read; failed encryption removes the saved password. | Until the password/server is removed or app data is removed. Hardware-backed key storage is device-dependent. |
| Private vault files | Tink streaming encryption; the keyset is stored in preferences and configured to use an Android Keystore wrapping key. Older unencrypted private items are still supported. | Restore/delete/export actions govern file handling. Gallery hiding and fast obscuring are separate features, not vault encryption. |
| Thumbnails and temporary file data | Coil image caching, preview caches, and archive spool files can hold derived images or file bytes. The app does not configure the previously promised 250 MB Coil cache cap. | Cache behavior is implementation-specific; no universal size or retention promise is established. |

The main manifest sets `android:allowBackup="false"`. This is the app's declared backup setting, not a verified guarantee across every device's backup or transfer behavior. Removing app data does not erase files already shared, exported, or stored outside app-private storage.

## Network and sharing boundaries

Remote-storage requests go to the server selected by the user. Transport protection depends on the chosen protocol and server configuration: plain FTP and HTTP WebDAV are not encrypted transports. FTPS uses platform certificate trust and hostname verification; SFTP host fingerprints are retained in the app's known-host preferences.

Wi-Fi sharing starts when requested and binds to the detected Wi-Fi IPv4 address. It uses HTTP, a six-digit session PIN, and random browser-session cookies; it does not provide HTTPS transport encryption. Authenticated browsers can access the selected folder, and uploads are optional. The session stops after the configured idle period (15 minutes by default) or five wrong PIN attempts. Its notification displays the folder name, URL, and PIN. Those values are user-visible session information, not diagnostics logs.

Explicit share actions grant another app read access through Android's sharing flow. The Copy Path action writes the displayed path to the system clipboard. Subsequent handling by recipient apps, servers, or browsers is outside this app's storage controls.

## Permissions declared in the main manifest

| Permission | Current purpose or declaration |
|---|---|
| `READ_EXTERNAL_STORAGE` through API 32 | Read shared files on supported Android versions. |
| `WRITE_EXTERNAL_STORAGE` through API 29 | Legacy shared-storage writes, subject to Android's storage rules. |
| `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, `READ_MEDIA_AUDIO` | Access the respective media categories. |
| `MANAGE_EXTERNAL_STORAGE` | Broad file-management access when granted. SAF supports linked-folder access with a different scope; it is not an identical replacement for every operation. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_DATA_SYNC` | Foreground file-operation service. |
| `POST_NOTIFICATIONS` | Operation and Wi-Fi-share notifications. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Remote storage, discovery, connectivity checks, and Wi-Fi sharing. |
| `REQUEST_DELETE_PACKAGES` | Open the system uninstall flow from the app manager. |

The manifest queries launchable apps. This inventory covers the main source manifest; a release review should also inspect its final merged manifest and optional integrations such as Shizuku.

## Diagnostics and release claims

See [Logging and Diagnostics](LOGGING_DIAGNOSTICS.md) for the implemented log sites and the limits of this review. There is no implemented universal logger/redaction layer, diagnostics export ring buffer, or opt-in crash-upload flow. Do not describe release logs as automatically hashing every name and path.

No AI feature was identified in the current implementation. Future feature proposals in the roadmap are not current privacy guarantees.

The repository does not establish the current Play Data Safety submission. The owner must review any published policy or store declaration against the shipped features, merged permissions, dependencies, and actual data flows. This audit does not submit or certify that declaration.

## Source references

- `app/src/main/AndroidManifest.xml`; `app/build.gradle.kts`
- `AtomicApp.kt` for component wiring, image loader, and debug StrictMode
- `data/database/room/OperationJournal.kt`, `SearchIndexTable.kt`, and `AtomicDatabase.kt`
- `data/repository/SharedPreferencesSettingsRepository.kt`, `HiddenFilesRepositoryImpl.kt`
- `data/backend/network/NetworkCredentialsStore.kt`, `PasswordCipher.kt`, `PrefsKnownHosts.kt`
- `data/vault/VaultKeys.kt`; `data/share/ShareServer.kt`, `WifiShareController.kt`

Kotlin paths above are relative to `app/src/main/kotlin/com/devbehindyou/atomicfilemanager/`.
