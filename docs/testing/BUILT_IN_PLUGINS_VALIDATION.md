# Built-in plugins inventory — 21 October 2026

Scope: the user's decision to bundle viewers/tools and list them in Settings → In-app plugins. This is an inventory and navigation increment, not a claim that the master specification's advanced viewer functions or cloud integrations are complete.

## Implementation

- Settings entry opens the existing Atomic bottom-sheet style.
- Eight icon cards describe the shipped image, video, audio, PDF, DOCX, code/text, Markdown and archive tools.
- Details show current features and limitations, with Back and Close.
- The modal stays mounted while switching pages; a caller-owned scroll state resets each page to its top.
- User-facing copy is in string resources.
- No installer, download source, new dependency, new permission, file mutation or settings migration.
- Viewers remain available through normal file opening and update with the application.

Decision: [ADR-M15](../architecture/ARCHITECTURE_DECISIONS.md). Follow-on work: [feature and optimization plan](../roadmap/FEATURE_UPGRADE_PLAN.md).

## Verification record

**Host validation passed.** `built-in-plugins-native-validation.log` and its `.exit` marker record `BUILD SUCCESSFUL in 5m 49s`, exit 0.

| Check | Result |
| --- | --- |
| SettingsScreenComposeTest | 4 passed, including native-rendered plugin navigation |
| PreviewTypeTest | 4 passed |
| AtomicMenuComposeTest | 2 passed |
| Focused total | 10 passed, 0 failures/errors/skips |
| ktlint and detekt | Passed |
| Debug APK | Packaged |
| Debug Android Lint | 0 errors; existing 153 warnings and 1 hint remain |

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
SHA-256: `5b752a380ea149f63276c88a3324c6f1841a6e1838d521cdb23fce9abf4b1586`.

Previous full-suite evidence remains scoped to source `4792db8`. The focused run for this change targets SettingsScreenComposeTest, PreviewTypeTest and AtomicMenuComposeTest, plus debug packaging, ktlint, detekt and Android Lint. It does not rerun the complete 917-case suite or certify a new release APK.

The navigation regression checks Settings → PDF details → Back → Archive details → Close, and verifies settings, notifications and cache state remain unchanged.

Initial host attempts did not pass the details assertion. Diagnostic semantics showed the list still visible and a drag-handle tooltip under legacy host rendering. The sheet implementation was simplified to retain one modal; the test was then configured with native graphics and a defined phone-sized window. All original navigation assertions now pass; none was skipped or removed and no timeout was extended. This is host evidence, not proof of a physical touch defect or its repair. See ignored logs under `build/audit-tools/built-in-plugins-*`, including `built-in-plugins-semantics.xml`.

The earlier `built-in-plugins-final-validation.log` stopped without a terminal result across the session interruption. It is not passing evidence. No Java process remained before validation was resumed.

The initial connection check found no device. On 21 October the authorized API34 phone was connected again and the recorded debug APK was installed with `adb install -r`. Settings → In-app plugins displayed all eight cards. PDF details showed the real feature/limitation disclosure; Back returned to the list, Archive manager opened its details, and Close returned to Settings. The app was returned to Home. No real user file, setting or permission was changed by this surface run. Captures: `build/audit-tools/device-preview-tools/plugins-{grid,pdf-details,back,archive-details,closed,final-home}.{png,xml}`. These physical checks close the pending U01 surface flow; broader provider/recovery tests remain separate.

PR #61 CI at `12f007d` passed static analysis but its full 918-case run found a teardown race in BrowseSortLoadingTest: a cancelled Default-dispatcher child returned after `resetMain()`. The fix waits for the ViewModel job to cancel and join before removing test Main. The failure is retained in `plugins-pr61-ci-failed.log` and the downloaded report. New-head CI run `39539280163` passed at `88bb8d0`, including the full unit/APK job. Independent review and a clean trial merge preceded integration merge `742d473` into `codex/feature-upgrade-base`; main/release acceptance remains separate.
