# External reader MIME normalization

## Scope

U06 incremental handoff correction. File routing already uses `normalizedFileMime` to reject malformed, wildcard and oversized metadata. External preparation previously forwarded any nonblank type except the generic binary type. A PDF labelled `application/` therefore routed as PDF but lost its external PDF readers; `*/*` could offer unrelated readers.

The external helper now reuses that existing normalizer. Valid explicit MIME remains authoritative, including conflicting PNG metadata on a `.pdf` filename. Invalid or generic metadata uses the existing Android extension lookup, then `application/octet-stream`. URI preparation, read grants, component/chooser behavior, 64 MiB remote export cap and cleanup remain unchanged. No new dependency, permission, plugin download, sniffing or SDK requirement.

## Controlled reproduction and fixture evidence

Six `ExternalMimeDiscoveryTest` methods run on API27/34 using enabled/exported components with actual ACTION_VIEW/content IntentFilters. They verify discovered components, resulting MIME/component/URI/ClipData and read-only grants. Backend export fails the test. The fixture supplies standard PDF/PNG mappings because Robolectric's MIME-map shadow starts empty; component matching itself is not replaced by MIME-blind overrides.

The first run used a bare document URI that cannot satisfy the existing tree-backed SAF helper contract: all 12 failed before callbacks. The second corrected URI run retained 8 actual normalization failures plus 2 missing MIME-map controls. Both initial fixture logs/exit/XML remain preserved and are not claimed as the final baseline. The final unchanged-source run has 12 cases: **8 expected assertion failures, 4 controls passing, 0 errors/skips**, 53s, exit 1. Missing PDF/PNG readers, wildcard overmatching and wrong generic chooser MIME reproduce directly. RED fixture SHA-256: `7C5B9C661CA0EA9A354123D2B607CEF711108E19EF6367140CC0A6508C0E0BE2`.

Initial GREEN stops on one long line; its log/exit is retained. Only that call is wrapped, preserving every assertion; GREEN fixture SHA-256 is `C395A3418FF6AA4954AE2C6ED0516CD1FC92FFEC71122D9F597A3494C34C2711`. Independent source, fixture and native-extension reviews find no blockers.

## Verified host checkpoint — 24 October

Actual build source `8d1cb5483eb829d29433a448c1e6dbb03e7a644e`, app tree `50113b6c1b64614ee24563202c26e9a055fc7ed7`. The affected opening gate passes in **9m46s, exit 0**: all 40 cases pass without failures/errors/skips (12 new MIME, 6 external apps, 4 export-helper, 8 preferences, 5 opening model, 3 preview action and 2 Settings). ktlint, Detekt, debug/AndroidTest packaging and debug Lint pass. JSON results, terminal logs and actual-build manifest remain under `build/audit-tools/external-mime-*`.

Debug APK SHA-256: `738EFDB0B745DEA700E802D6B981A0013DCB493F79BF0CF9C02E00489DE15777`; AndroidTest: `8A039C2A5C99427625AEC358A9B5807EB72F8835E9B85995003DFCA8AA2D2BF6`. Later root HEAD changes at build completion are documentation-only with matching app tree.

## Verified physical checkpoint — 24 October

All **6 selected API34 native cases pass in 28.745s**: four current Storage/Analysis/Browse/session surfaces plus original and malformed-metadata PNG handoffs. Both handoffs generate only their own small app-cache PNG, verify another UID receives exact bytes and only temporary read access, revoke that exact package/URI grant and remove the fixture. The malformed variant confirms MIME normalization during preparation/launch; its receiver is preselected under the expected PNG control, while discovery from malformed supplied metadata is covered by the host consumers.

The installed base hash matches the validated debug APK. Actual selected Home, unchanged all-files access and removal of the initially absent test package are verified, with no cleanup failures. Evidence: `build/audit-tools/device-preview-tools/external-mime-acceptance-a16ee84a-2bd0-472d-867e-418dab2dfc68/`; native output and wrapper/acceptance exit 0. No root probes, benchmark run or user-file mutation occurred.

Final-head CI and guarded feature-integration acceptance remain pending. Physical PDF readers, uninstall/incompatible-handler recovery, revoked provider grants, large/slow remote preparation and bounded sniffing remain separate U06 gates. This is a partial U06 slice; fixed progress remains **15% host verified / 8% accepted**. Advanced eight-plugin modernization and main/release acceptance are not credited.
