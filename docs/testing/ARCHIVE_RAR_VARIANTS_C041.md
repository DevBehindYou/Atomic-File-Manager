# RAR variants and entry integrity — C041 slice 1

Claude Code task **C041** (U11, ARC1/ARC-T01 increment). Base: feature integration `89140e3f3d4a746e6a907912042911cb10c8f6a3`. Recorded 6 November 2026 UTC (7 November, Asia/Calcutta). Host-verified only.

## Findings

| ID | Finding | Evidence on unchanged logic | Change |
| --- | --- | --- | --- |
| ARC-B01 | **Every RAR made by current WinRAR showed as damaged.** WinRAR 7 writes only RAR5 (`-ma4` was removed). The bundled junrar 8.1.1 reads RAR 1.5–4.x only, so these files fell through to `CorruptedArchive` ("may be damaged"). | Three real RAR 7.23 fixtures (plain, `-p`, `-hp`) give `CorruptedArchive` for both inspect and extract. The control without the signature check fails 3/3. | The RAR5 signature `52 61 72 21 1A 07 01 00` is detected before junrar runs. Inspect and extract report `UnsupportedFormat("RAR5 archive")`. The preview says: "This is a RAR5 archive. Atomic opens RAR 4 and older; use Open with for this one." Nothing is extracted. |
| ARC-B02 | **A damaged RAR 4 entry extracted as success.** `Archive.getInputStream` extracts on a background thread behind a pipe and drops junrar's own `CrcErrorException`. The reader therefore saw a normal end of stream, and corrupted bytes were published. | A stored RAR 4 archive with one data byte flipped (header CRC still valid) returned `ItemResult.Success`. | `RarStream` computes CRC32 over the bytes it returns and checks the stored CRC at the end of the entry. A mismatch throws, so the existing extraction path reports `CorruptedArchive` and publishes nothing to the target. |

## Fixtures

- RAR5: made locally with the installed `Rar.exe` 7.23 (`rar a -m3 -tsm- -tsc- -tsa- [-pAtomicFixture | -hpAtomicFixture]`) over one 19-byte `hello.txt`. They are embedded as Base64 in `ArchiveRarVariantsTest`, at 80, 143 and 238 bytes. Nothing was downloaded.
- RAR 4: no installed tool can write it any more. The test builds the documented RAR 2.9–4.x stored layout itself (marker, main header, method `0x30` file blocks with header and data CRCs, and the end block). junrar lists and extracts it with folders, and the bytes match.

## Validation (local, `host-gradle` lease held)

| Command | Result |
| --- | --- |
| `./gradlew ktlintFormat :app:testDebugUnitTest --tests '*ArchiveRarVariantsTest' --tests '*ArchiveFormatsTest' --tests '*ArchiveInspectionSafetyTest' --tests '*ArchiveIntegrityTest' --tests '*ArchiveOperationsTest'` | 43 tests: RarVariants 5, Formats 9, InspectionSafety 6, Integrity 18, Operations 5. 0 failed, 0 skipped. |
| First run (RAR5 fix only, no CRC check) | 43 run, 1 failed: the CRC case returned `Success`. This is retained as the ARC-B02 reproduction. |
| Control: RAR5 signature check disabled | 5 run, 3 failed (all RAR5 fixtures). |
| `./gradlew ktlintCheck detekt :app:testDebugUnitTest --tests '*ArchiveRarVariantsTest'` | BUILD SUCCESSFUL in 1m 8s. |

## Still open (ARC-T01 / ARC1)

- **RAR5 reading.** It needs a different engine or an Open with handoff. Engine evaluation belongs with ARC-T03; this slice only makes the failure honest.
- **Compressed RAR 4 and multi-volume RAR.** These need archives from an older tool or an owner-provided sample. Stored RAR 4 is the only RAR 4 variant verified here.
- **Encrypted 7z.** Commons Compress raises `PasswordRequiredException`, which still maps to "damaged". There is no local tool to create a fixture, because 7-Zip is not installed. This is a fixture or tool blocker; the mapping will be added once there is a failing fixture.
- Device and provider runs were not performed. No ARC group credit is claimed.
