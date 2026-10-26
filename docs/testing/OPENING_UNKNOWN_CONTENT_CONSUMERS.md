# Selected unknown-content opening — candidate consumers

Prepared 25 October 2026 for existing U06 bounded type detection, authorized by [FEATURE_UPGRADE_PLAN](../roadmap/FEATURE_UPGRADE_PLAN.md#u06-opening-files). This is a test-only proposal, not a reproduced defect, source implementation or accepted milestone. The isolated branch starts at `8086219f41c8458895657ebffeaa2ba2aae78057`, app tree `cdf3f497ad7fb8cf047809ae435bebdae6f3560e`, depending on PR84 and the current same-ID metadata repair. It excludes owner-deferred benchmark63. Parent owns review, scoped formatting, freeze, compilation, unchanged-source RED, later implementation and acceptance.

## Observed path and proposed behavior

The real FilePreviewPane currently obtains externalMimeType from normalized metadata, the Android extension map or application/octet-stream. No content probe disambiguates an explicitly selected opaque file before reader discovery. The plan authorizes bounded sniffing only where needed, keeping extension overrides before MIME/family/global rules and file-icon classification metadata-only. Accepted valid-MIME authority remains unchanged: conflicting content is not permission to override valid explicit metadata.

OpeningUnknownContentComposeTest defines four actual API34 native-graphics consumers:

1. An opaque filename with generic binary MIME and a generated 2x2 PNG in a uniquely owned private-cache file must offer the installed PNG-only reader. Before clicking, discovery must have read and closed its source, with no activity launch or preference update. Explicit selection must use image/png, the intended component, current label/ClipData, exact copied and URI-readback bytes, and temporary read-only flags. Current source is expected to time out waiting for that reader, but no genuine RED is claimed until the parent executes it.
2. Nonmatching tiny binary content keeps a compatible generic reader and never invents a PNG choice. Its explicit handoff retains application/octet-stream and exact bytes.
3. Valid application/pdf metadata remains authoritative even when the fixture bytes are PNG. Reader discovery must not read the content or offer the PNG reader. The actual external handoff uses the declared PDF MIME and unchanged bytes, preserving accepted normalization behavior.
4. An explicit extension Ask rule wins over a MIME-specific external target and global built-in policy. Actual choices remain visible without automatic launch, source reads, preference mutation or exports.

All four are prepared and unrun. Expected filter: `*OpeningUnknownContentComposeTest`, expected count4. The first is the proposed missing-capability case; the other three are controls, not confirmed passing baselines yet. There is no production API invented by these tests and no new dependency or permission.

## Ownership and host boundary

The sandbox replaces AtomicApp's container only for this test, uses one UUID-qualified FTP identity and a controlled backend returning the actual private fixture stream. Every provided stream is tracked for closure, including after bounded composition disposal. Settings and recents use sandbox implementations, and the original container/launch validation are restored. Each case performs at most one explicit export. An owned sentinel and the preexisting export snapshot are preserved; cleanup removes only the captured new copy, sentinel and source file. No shared storage, phone, root, real account or user preference is used.

OwnedExportFileProviderShadow is reused openly for this class only. It replaces host URI mapping/grants with a controlled URI serving the real owned export lazily; copy, discovery, pane decisions, Intent construction and actual bytes remain real. These cases cannot establish Android provider roots, cross-UID access, real remote transport or revoked grants.

## Deliberate limits before implementation

The plan does not specify a numeric prefix budget or signature set. These tiny fixture consumers enforce actual read/closure and no-probe trusted metadata, but cannot establish a strict large-stream byte bound. The parent must define and document the smallest justified budget and add meaningful oversized/short-read/non-seekable/cancellation/stale-source contracts around the chosen behavior. Do not count stream closure alone as proof of bounded reads, or a recognized header as successful decoding. ZIP alone cannot establish DOCX. Wider formats, editor/gallery recovery, provider revisions and grant-loss remain separate.

No Gradle, formatter, APK, device or CI run was performed by this author. No GREEN, integration, full U06, main/release, advanced eight-plugin or percentage credit follows. Existing milestone percentages, bundled plugin v0.2 labels, permission choices and deferred benchmarking remain unchanged.

## Confirmed unchanged-production baseline — OPEN-B02, 25 October

Independent read-only review finds no blocker for freeze-and-run. The scoped formatter changes only line wrapping, retaining all assertions and the original pre-format bytes in `build/audit-tools/opening-unknown-preformat.kt`. Actual frozen source is root `7c3ed20dbcd68a3fea3614d1244358cb7444066f`, app tree `1694e5abcd11d64457e08b7cbed40a6d21ebff5a`, production main tree `cc00d88bcb8bdfa117bf28e830319a90270deef8` (identical to separate PR85). Frozen test SHA256 is `8FB5E212F636F65A1E5BBFA5D500780338DE725146758DFAF2727D4526DB6D1C`.

The real run executes **four cases: one intended missing PNG-reader timeout, three passing controls, zero errors/skips**, total2m10s, suite41.103s, exit1. Valid explicit PDF authority/no discovery read, generic binary actual handoff and explicit extension Ask precedence pass. Logs/exit: `build/audit-tools/opening-unknown-red-validation.*`; exact source manifest: `opening-unknown-red-source.json`; copied XML: `opening-unknown-red-results.xml`. The missing reader is now reproduced, not merely inferred from source. There is no detector implementation/GREEN, strict prefix-budget proof or native detection result yet. The separately reviewed design must establish budget/stream/lifecycle contracts before implementation; preserve these frozen four consumers.
