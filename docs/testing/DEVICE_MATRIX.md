> **28 October 2026 platform decision:** the owner requested Android 9 / API 28 as the application and benchmark minimum. Android 8.1 / API 27 is no longer supported by new builds. Earlier dated API27 audit and test evidence remains historical; this decision does not establish MEGA integration or new runtime acceptance.

> Current execution status (13 September 2026): nine tests passed on a rooted Android 14 / API 34 phone with an SD card. See [Phone verification](PHONE_VERIFICATION.md). Other Android versions and form factors remain untested.

# Device and API Matrix

## 1. Why this file exists

`minSdk 28` and `targetSdk 36` spans nine Android releases and three complete rewrites of the
storage model. A feature that works on the emulator you happen to have open proves almost
nothing. This matrix defines what must be verified where, before a release ships.

## 2. Mandatory API levels

| API | Android | Tier | Why this level is non-negotiable |
|---|---|---|---|
| 28 | 9 | Emulator | minSdk floor. Legacy `READ/WRITE_EXTERNAL_STORAGE`, direct `File` access, glass tier C |
| 29 | 10 | Emulator **+ physical if possible** | **The hardest version.** Scoped storage arrives, `requestLegacyExternalStorage` is unavailable to us at targetSdk 36, `MANAGE_EXTERNAL_STORAGE` does not exist. SAF + MediaStore only |
| 30 | 11 | Emulator + physical | `MANAGE_EXTERNAL_STORAGE` arrives; `/Android/data` becomes permanently unreachable; SAF restricts Download and root trees |
| 31 | 12 | Emulator | `RenderEffect` blur → **glass tier B begins**; dynamic colour begins; splash screen API |
| 33 | 13 | Emulator + physical | `RuntimeShader`/AGSL → **glass tier A begins**; granular media permissions; runtime notification permission |
| 34 | 14 | Emulator | Partial media grants (`READ_MEDIA_VISUAL_USER_SELECTED`); foreground-service types enforced |
| 35 | 15 | Emulator | Edge-to-edge enforced; 16 KB page-size guidance |
| 36 | 16 | Emulator + physical | **Play target requirement from 31 Aug 2026.** Final compile/target level |
| 37 | 17 | Emulator | Forward-compatibility smoke test only; not a ship gate |

API 32 is covered by nearest-neighbour testing except where a table below names them.

## 3. Physical device classes

At least one device from each row must be in the release loop.

| Class | Example spec | Purpose |
|---|---|---|
| Low-end | 3 GB RAM, entry SoC, API 29–30, `isLowRamDevice` true | Forces glass tier C, proves tier C is not "broken-looking"; the real jank test |
| Mid-tier | 6 GB RAM, API 33+ | The performance budget baseline in `TEST_STRATEGY.md` §8 |
| Flagship | 8 GB+, API 36 | Tier A refraction at 120 Hz; thermal behaviour under sustained copy |
| Removable storage | Any device with a working SD slot | The only way to test SD write, eject-mid-copy, and remount |
| USB OTG | Any device + USB-C drive | The only way to test OTG mount, permission, and unplug |
| Large screen | Tablet or foldable, API 33+ | Dual-pane layouts, fold posture, window resize |

Emulators cannot substitute for the last three rows. SD and OTG behaviour is
**[device-variable]** and vendor-modified.

## 4. Feature × API verification grid

Legend: ✅ must verify · ➖ not applicable at this level · ⚠ known constraint to confirm

| Feature | 28 | 29 | 30 | 31 | 33 | 34 | 36 |
|---|---|---|---|---|---|---|---|
| Legacy `File` browse of shared storage | ✅ | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ |
| SAF tree grant + persist across reboot | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| MediaStore listing + write | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| All Files Access flow | ➖ | ➖ | ✅ | ✅ | ✅ | ✅ | ✅ |
| `/Android/data` correctly reported unreachable | ➖ | ⚠ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Granular media permissions | ➖ | ➖ | ➖ | ➖ | ✅ | ✅ | ✅ |
| Partial media grant + Manage flow | ➖ | ➖ | ➖ | ➖ | ➖ | ✅ | ✅ |
| Notification runtime permission | ➖ | ➖ | ➖ | ➖ | ✅ | ✅ | ✅ |
| Foreground service `dataSync` type | ✅ | ✅ | ✅ | ✅ | ✅ | ⚠ | ✅ |
| Glass tier C (scrim) | ✅ | ✅ | ✅ | ➖ | ➖ | ➖ | ➖ |
| Glass tier B (`RenderEffect` blur) | ➖ | ➖ | ➖ | ✅ | ➖ | ➖ | ➖ |
| Glass tier A (AGSL refraction) | ➖ | ➖ | ➖ | ➖ | ✅ | ✅ | ✅ |
| Dynamic colour | ➖ | ➖ | ➖ | ✅ | ✅ | ✅ | ✅ |
| Predictive back | ➖ | ➖ | ➖ | ➖ | ✅ | ✅ | ✅ |
| Edge-to-edge, no opt-out | ➖ | ➖ | ➖ | ➖ | ➖ | ➖ | ✅ |
| HEIF decode | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| AVIF decode | ➖ | ➖ | ➖ | ✅ | ✅ | ✅ | ✅ |
| `PdfRenderer` preview | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |

## 5. Vendor-specific checks

These are **[device-variable]** and have historically broken file managers. Verify on at least
one device per vendor before a major release.

| Vendor | Risk |
|---|---|
| Xiaomi / MIUI–HyperOS | Aggressive background-process killing ends foreground services early; extra "autostart" permission gates |
| Samsung / One UI | Custom SD handling and a separate Secure Folder namespace; My Files interop |
| Oppo / Vivo / Realme (ColorOS, FuntouchOS) | Battery optimisation kills services; permission dialogs differ from stock |
| Huawei / EMUI | No Play Services; SAF picker differences |
| Any vendor | "Optimised" MediaStore indexes that lag behind the filesystem |

For each: run the 4 GB copy soak with the screen off and confirm the operation completes or
fails visibly. Silent termination that leaves a partial file is the failure to hunt.

## 6. Open questions promoted to executable tests

From `../ANDROID_STORAGE_RESEARCH.md` §10. Each is an instrumented test that records what the
device actually does, run across the matrix, with results committed as a table in the test's
KDoc.

| # | Question | Test name | Devices |
|---|---|---|---|
| Q1 | Does a persisted SAF grant to an SD volume survive eject + remount, or must it be re-requested? | `SafGrantSurvivesRemountTest` | SD-capable, API 29 / 33 / 36 |
| Q2 | What is the real `DocumentFile.listFiles()` cost at 1k / 5k / 20k entries per API level? | `SafListingCostBenchmark` | All emulators + mid-tier |
| Q3 | Does MediaStore reflect a file written via SAF, and after what delay? | `MediaStoreVisibilityLagTest` | API 29 / 30 / 33 / 36 |
| Q4 | Which OTG mount paths appear in `StorageManager.storageVolumes` per vendor? | `OtgVolumeEnumerationTest` | OTG device, each vendor |
| Q5 | Does `dataSync` foreground service survive Doze during a 20-minute copy? | `LongCopyDozeTest` | Mid-tier + Xiaomi + Oppo |
| Q6 | At what image dimensions does the tier-A shader drop below 8 ms on low-end hardware? | `GlassShaderFrameBudgetTest` | Low-end, mid-tier, flagship |
| Q7 | Does `RecoverableSecurityException` fire for MediaStore edits on every level 29+? | `MediaStoreRecoverableEditTest` | API 29 / 30 / 33 / 36 |
| Q8 | Are trash/pending MediaStore semantics consistent across vendors? | `MediaStoreTrashSemanticsTest` | One device per vendor |

**A question that stays unanswered is a design decision made by accident.** These tests are
part of Phase 0 in `../roadmap/IMPLEMENTATION_ROADMAP.md`, not a later cleanup.

## 7. Emulator configuration

```text
API 28  — Pixel 2,  x86_64, Google APIs,      2 GB RAM, SD image attached
API 29  — Pixel 3a, x86_64, Google APIs,      3 GB RAM, SD image attached
API 30  — Pixel 4,  x86_64, Google APIs,      4 GB RAM
API 31  — Pixel 5,  x86_64, Google APIs,      4 GB RAM
API 33  — Pixel 6,  arm64/x86_64, Google APIs, 6 GB RAM
API 34  — Pixel 7,  arm64/x86_64, Google APIs, 6 GB RAM
API 35  — Pixel 8,  arm64/x86_64, Google APIs, 8 GB RAM
API 36  — Pixel 9,  arm64/x86_64, Google APIs, 8 GB RAM
API 36  — Pixel Tablet + Pixel Fold profiles (large screen)
API 37  — latest available (smoke only)
```

Glass tiers are additionally exercised by the forced-tier setting
(`../screens/SETTINGS.md` §3) so tier C can be reviewed on a flagship, and by
`isLowRamDevice` emulation on the 2 GB image.

## 8. Per-release manual checklist

Run on: one low-end API 29 device, one mid-tier API 33 device, one API 36 device, one tablet.

1. Fresh install → onboarding → deny everything → app is usable in limited mode with honest messaging.
2. Grant each access type in turn; each grant visibly unlocks the right capability.
3. Revoke access from system settings while the app is backgrounded → return → state is correct, no crash.
4. Browse a 10,000-item folder → scroll to the end → no jank, no OOM.
5. Copy 2 GB across volumes → background the app → lock the screen → confirm completion.
6. Cancel a copy at ~50% → confirm no partial file remains and the source is intact.
7. Eject the SD card mid-copy → confirm clean failure and an accurate message.
8. Delete → trash → restore → verify the file returns to its original path.
9. Search a 100k-file device → first result under 1 s → cancel mid-search.
10. Storage scan → verify the breakdown sums to the used figure within rounding.
11. Preview each media type; force an unsupported codec and confirm the Open-with fallback.
12. Force process death on every screen listed in `TEST_STRATEGY.md` §6.
13. TalkBack pass over every screen; no unlabelled control, nothing unreachable.
14. Font scale 200% + display size Largest over every screen.
15. Glass tiers A, B, C forced in turn: each looks intentional, none looks broken.
16. Rotate and fold/unfold on every screen; no state loss.
17. Dark mode over every screen; contrast holds on glass surfaces.
18. Airplane mode throughout — the base flavour must behave identically, since it has no network permission at all.
