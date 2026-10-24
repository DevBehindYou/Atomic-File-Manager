# Opening-handler recovery contracts — 24 October 2026

This U06 increment adds acceptance coverage for already implemented behavior. It changes no production router, viewer, permission, preference schema or provider. No defect or RED/fix sequence is claimed: the original five frozen consumers pass against the existing router before assertions are strengthened.

## Consumers and source boundary

Original preparation commits152eb2b/9981627 use the real FilePreviewPane/FileOpeningPane, actual reader discovery, settings repository state, helper and Android launch path in a Robolectric AtomicApp sandbox. The valid tree document URI is content://opening-fixture/tree/root/document/report. No real provider, user file, installed device app or phone preference is touched.

The initial five API34 consumers pass without failures/errors/skips in2m42s, exit0; initial CRLF SHA256 `17DA609F039D68921A99D48BD2F03926248806D32AC2396E61D14A80B3D88C4A`. Logs/XML: `build/audit-tools/opening-handler-recovery-first-validation.log/.exit` and `opening-handler-recovery-first-test-results.xml`.

They verify:

1. A missing saved reader shows recovery choices, does not launch, and preserves the existing rule and unrelated settings.
2. An installed but incompatible saved reader is not offered/launched, while a compatible reader and built-in option remain available.
3. Once launches the selected compatible reader and preserves the saved rule and other preferences.
4. Always preserves rules until a successful launch, then changes only the intended extension rule.
5. Removing a reader after discovery causes a real launch rejection, leaves the pane/choices open and preserves rules. The pinned ShadowApplication.checkActivities(true) plus explicit ActivityNotFoundException control prevents a fabricated successful launch.

Successful-launch assertions inspect ACTION_VIEW, exact component/MIME/data and ClipData URI, read permission and no write grant. Root test-only commit `b756dc3906a9e6bf0a8657506955a2af9ce9a77f` additionally prohibits persistable and prefix grants; no prior assertion was removed. Final CRLF test SHA256 `722AA1361993D15E2C6A905C37DF7A84A61FA5A0A1F24B40F19895605769B7CC`. Independent initial and delta reviews find no blockers.

## Affected host gate

Actual source `b756dc3906a9e6bf0a8657506955a2af9ce9a77f`, app tree `bd06b3517d387df409684b4a6eb5675611dae394`. All **49 cases, zero failures/errors/skips**, ktlint and Detekt pass in **5m29s, exit0**: five recovery, 12 MIME discovery, six external-reader, four export-helper, eight opening-preference, five routing, three preview-external action, two opening-Settings and four general Settings cases.

Terminal logs, copied XML and JSON are under `build/audit-tools/opening-handler-recovery-*`. Filters: *FileOpening*Test, *ExternalMimeDiscoveryTest, *ExternalFileAppsTest, *ExternalPreviewHelperTest, *PreviewExternalActionTest and *SettingsScreenComposeTest. APK packaging and Android Lint are not part of this local test-only gate; current-head full CI must complete before integration.

The controlled launch and preference assertions are host contracts, not actual external-reader access, durable writes through process death, revoked SAF permission or a physical uninstall/reinstall result. No new physical run is claimed. Earlier source-specific native handoff/surface results remain in their own records and must not be relabelled as this suite's device acceptance.

## Remaining work and publication

This test-only slice does not close U06: remote preparation progress/explicit cancellation, bounded sniffing where needed, broader providers/real reader/grant-loss and release gates remain open. The next remote preparation work must preserve the64MiB cap, cancellation cleanup, temporary read-only grants and ownership of already handed-off copies. Unknown/stale metadata length must not create a false percentage.

The focused branch must match the tested app tree, integrate the current accepted dependencies and exclude owner-deferred benchmark #63. [PR #83](https://github.com/DevBehindYou/Atomic-File-Manager/pull/83) is published with matching app tree; current-head CI and guarded integration are pending. Keep phone permissions unchanged and benchmarking deferred. Fixed progress remains **15% host verified / 8% accepted**; no new complete milestone, advanced-plugin or main/release credit is earned.