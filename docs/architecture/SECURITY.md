# Security

> **Verification scope, 17 October 2026:** the controls below are requirements, not a completed security checklist. The [project audit](PROJECT_AUDIT.md) records concrete gaps, including archive publication, journal ordering, upload races, protocol hardening and broad FileProvider roots. CI currently has no dependency-vulnerability job; a green lint/build alone does not certify these requirements.

A file manager is a privileged position. It reads everything the user has, writes anywhere it
can, and hands files to other apps. Each of those is an attack surface.

---

## 1. Threat model

| Threat | Vector | Mitigation |
|---|---|---|
| Zip Slip (path traversal on extract) | Malicious archive entry `../../../` | §3 |
| Zip bomb | Nested/compressed archive that expands to TBs | §3 |
| Malicious filename | Control chars, RTL override, path separators, `..` | §4 |
| Hostile intent | Another app sends a crafted `content://` or `file://` | §5 |
| URI leakage | Sharing a `file://` URI or over-broad grant | §2 |
| Symlink escape | Link pointing outside the operation root | §6 |
| APK install abuse | Silent or misleading install of a sideloaded APK | §7 |
| Temp-file exposure | Extracted or cached content readable by other apps | §8 |
| Accidental destruction | Mis-tap deleting a folder tree | §9 |

## 2. FileProvider and URI hygiene

```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data android:name="android.support.FILE_PROVIDER_PATHS"
               android:resource="@xml/file_paths" />
</provider>
```

Rules:
* **No `file://` URI ever leaves the app.** On API 24+ it throws `FileUriExposedException`;
  below that it is still wrong.
* Grants are per-share, read-only by default, and use
  `FLAG_GRANT_READ_URI_PERMISSION` only. Write grants are given only when the user explicitly
  chooses "Open with (editable)".
* `file_paths.xml` exposes the narrowest possible roots. Never `<root-path path="/" />`.
* When a user shares a file outside the provider's declared roots (e.g. on an SD card
  reached via SAF), pass the **SAF URI directly** with a re-grant, rather than copying to
  cache — copying leaks a second copy of the data.
* Revoke grants when the share flow completes where the platform permits.

## 3. Archive extraction

```kotlin
fun safeResolve(destRoot: File, entryName: String): File {
    val target = File(destRoot, entryName).canonicalFile
    val root = destRoot.canonicalFile
    require(target.path.startsWith(root.path + File.separator)) {
        throw SecurityException("Zip Slip: $entryName")
    }
    return target
}
```

Mandatory guards on every extraction:

1. **Canonical-path containment check** on every entry (above). Rejected entries are skipped
   and reported, and the whole archive is flagged as suspicious in the result.
2. **Absolute path rejection:** entry names starting with `/` or containing a drive letter.
3. **`..` rejection** before canonicalisation, as a cheap first filter.
4. **Expansion ratio cap:** abort if uncompressed/compressed exceeds **100:1** across the
   archive, or if total uncompressed size exceeds free space.
5. **Entry count cap:** 100,000 entries; beyond that, abort with an explanation.
6. **Nesting cap:** archives inside archives are not auto-extracted, ever.
7. **Symlink entries** in TAR are skipped, not created.
8. **Streaming only:** never `entry.readBytes()`.
9. **Extract to a temp directory in the destination volume**, then move into place, so a
   failed extraction never leaves a half-tree at the target name.

## 4. Filename validation

Reject or sanitise before any create/rename/extract:

* Empty, `.`, `..`
* Any of `/ \ : * ? " < > |` (the last seven matter on FAT32/exFAT SD cards)
* Control characters `\u0000`–`\u001F`
* Unicode bidi overrides (`U+202E` etc.) — these are used to disguise `exe.txt` as `txt.exe`.
  Strip them and show the sanitised name to the user before proceeding.
* Trailing spaces or dots (invalid on FAT)
* Names longer than 255 bytes in the target filesystem's encoding
* Reserved names on FAT: `CON`, `PRN`, `AUX`, `NUL`, `COM1-9`, `LPT1-9`

The user is shown the sanitised result and must confirm; we never silently rename.

## 5. Intent handling

* The only exported components are the launcher activity and (if added) an
  `ACTION_VIEW`/`ACTION_GET_CONTENT` handler. Everything else is `exported="false"`.
* Any incoming URI is validated: scheme must be `content://`; `file://` is rejected with a
  clear message. Never resolve an incoming path string against internal storage.
* Never `grantUriPermission` to a package name supplied by an incoming intent.
* Outgoing `ACTION_VIEW` always goes through a chooser, with
  `FLAG_GRANT_READ_URI_PERMISSION` and no write flag unless requested.
* `PendingIntent`s from the operation service use `FLAG_IMMUTABLE` (mandatory at API 31+).

## 6. Symlinks and loops

* Directory walks track visited canonical paths; a repeat is a loop and terminates that branch.
* Copy follows symlinks by default (users expect the file), but **never** follows a link that
  resolves outside the operation's source root during a recursive copy.
* Depth cap of 32 on every recursive walk.

## 7. APK handling

* We display APK metadata (label, package, version, icon) using `PackageManager
  .getPackageArchiveInfo`; we never load code from it.
* Installing goes through `ACTION_VIEW` with the APK MIME type and the system installer.
  We never request `REQUEST_INSTALL_PACKAGES` at MVP — that changes the app's Play risk
  profile and the system installer is the correct, safe path.
* Before opening an APK we show a clear warning naming the package and stating that
  installing apps from outside the Play Store carries risk.

## 8. Temporary files

* All temp work happens in `context.cacheDir` subdirectories, which are app-private.
* Temp files use random names, are deleted in a `finally` block, and are swept on app start.
* Nothing sensitive is ever written to shared storage as a temp artefact.
* Extraction temp directories live on the destination volume (for atomic move) but inside a
  dot-prefixed directory that is cleaned on failure and on next launch.

## 9. Destructive-action safety

| Action | Guard |
|---|---|
| Delete (trash) | Snackbar with Undo; no dialog needed |
| Delete (permanent) | Dialog naming the count and total size; not dismissible by outside tap |
| Delete > 100 items or > 1 GB | Dialog additionally lists the top 3 items by size |
| Overwrite on collision | Explicit choice, never a default |
| Bulk operations from the duplicate finder | Never pre-selects all copies in a group |
| Extract over existing files | Collision policy applies, same as copy |
| Any operation on `/Android` paths | Refused with an explanation |

There is no "don't ask again" for permanent deletion.

## 10. Build and code security

* `android:allowBackup="false"` — auto-backup of a file manager's Room database (favourites,
  trash records pointing at paths) leaks structure to the cloud.
* `android:usesCleartextTraffic="false"`; no network in the base flavour anyway.
* R8 full mode; no reflection into platform internals; no dynamic code loading.
* No `WebView` anywhere in the app.
* Dependencies audited at each release; `gradle dependencyCheck` in CI.
* Debug logging of paths is compiled out of release via a build-config gate, not a runtime
  flag.
