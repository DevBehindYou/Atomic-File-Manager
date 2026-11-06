# Issues

Last updated: 2026-10-08

## Open

### ISS-001 — The production signing key is not configured

Status: UNRESOLVED (needs the owner)

Impact: Release APKs are signed with the debug key (`app/build.gradle.kts`, `signingConfig = debug`), so they cannot be published on Play.

Next step:

1. The owner creates an upload key.
2. Store it as CI secrets (names to choose, for example `SIGNING_KEYSTORE_BASE64`, `SIGNING_KEY_PASSWORD`). Never commit it.
3. Add a `release` signingConfig that reads them.

### ISS-002 — Device-only behaviour has never run on hardware

Status: UNRESOLVED

Impact: The following are IMPLEMENTED_UNTESTED on a phone:

- SMB against a live server
- mDNS discovery on a real network
- USB OTG
- Shizuku
- Wi-Fi share from a PC browser
- a real process kill mid-copy
- the baseline profile, monkey run and JankStats

Next step: TASK-001 to TASK-005 in `PENDING.md`.

## Resolved (recent; one line each, the PR has the detail)

| ID | Problem | Root cause | Fix |
|---|---|---|---|
| ISS-010 | R8 release failed on SFTP | sshj references GSS/JAAS classes that Android lacks | `-dontwarn org.ietf.jgss.**`, `javax.security.auth.login.**` (PR #23) |
| ISS-011 | `checkDebugDuplicateClasses` failed | bcprov 1.85.2 next to bcutil 1.84 | Pin all BouncyCastle jars to 1.86 (ADR-005, PR #28) |
| ISS-012 | API 27 smoke test crashed | `RECEIVER_NOT_EXPORTED` permission path on old Android | `RECEIVER_EXPORTED` (ADR-006, PR #33) |
| ISS-013 | Lint `MissingPermission` on notify | No `POST_NOTIFICATIONS` check | Check before notifying (PR #32) |
| ISS-014 | Lint `ContextCastToActivity` | `LocalContext as Activity` | `LocalActivity.current` (PR #32) |
| ISS-015 | `ProcessKillTest` race | `lateinit recovery` was read by the queue's start-up on another thread | `CompletableDeferred` (PR #37) |
| ISS-016 | `ProcessKillTest` mid-write failed after the buffer grew to 256 KB | The 300 KB payload was killed on its first chunk, before the async journal write recorded RUNNING | Payload sized from `BUFFER_SIZE` (×4); old payload failed 1 in 8 local runs, new one 0 in 12 (PR #38) |

## Failed approaches

### FA-001 — A large, fixed test payload for "mid-write"

Attempt: Hard-code the payload size (300,000 bytes) in `ProcessKillTest`.

Why it failed: It silently depended on the 64 KB buffer. A larger buffer turned "mid-write" into "first write" and raced the journal.

Do not repeat: Size kill-point fixtures from `FileOperationsEngine.BUFFER_SIZE`.

### FA-002 — Ending detekt/ktlint and push in one `&&` chain whose earlier output is filtered

Attempt: `ktlint ... | grep -v ... && git commit && git push`.

Why it failed: `grep -v` changes the exit status, so a branch was pushed with ktlint errors (PR #40, fixed in a follow-up commit).

Do not repeat: Check ktlint's own exit code before committing.

### FA-003 — Dropping the category walk in favour of the search index

Not attempted on purpose. See ADR-004: it would hide new camera photos for up to 6 hours.
