# External reader MIME normalization

## Scope

U06 incremental handoff correction, based on accepted integration `2822f8b`. The existing domain `normalizedFileMime` rejects malformed, wildcard and oversized MIME metadata before using the filename extension for routing. External reader preparation previously accepted any nonblank supplied type except `application/octet-stream`. A `report.pdf` labelled `application/` could therefore route as PDF while external discovery received an unusable MIME type.

`ExternalPreviewHelper.externalMimeType` now reuses the existing domain normalizer. Valid explicit types remain authoritative, including a valid image MIME on a `.pdf` filename; case, surrounding whitespace and MIME parameters are normalized as before. Rejected or generic types retain the existing Android extension lookup, then `application/octet-stream` fallback when no extension MIME is available.

The URI preparation, temporary read grants, explicit-component/system-chooser behavior, export byte limit and expiry cleanup are unchanged. There is no byte sniffing, new dependency, permission request, downloaded plugin, provider mutation or platform API change. The minimum SDK remains 27.

## Frozen consumer regressions

`ExternalMimeDiscoveryTest` registers actual exported/enabled `ACTION_VIEW` components with `content` URI filters for PDF and PNG. It prepares a SAF handoff through the production helper, resolves real matching components through `ExternalFileApps`, and verifies the resulting ACTION_VIEW MIME, component, URI/ClipData and read-only grant flags. It does not use Robolectric override-intent matching, which does not distinguish MIME reliably in the pinned version.

Six methods run on API27 and API34: malformed PDF metadata, wildcard PNG metadata, oversized PNG metadata, valid conflicting-MIME authority, generic metadata with a known extension, and invalid metadata with an unknown extension falling back to the generic system chooser. The SAF fixture requires no file reads or backend export; an attempted backend export fails the test.

The initial test-only commit `0c17a266dd374ca687c13854366e0068b3efacfd` precedes the production correction. Before validation, assertion order was strengthened so malformed known-file cases first check actual handler discovery instead of failing first on the helper's MIME value. Final frozen consumer SHA256: `7D76DFC12C4D4723A8EF78541E275E0F77AB534237B7AE104AD6542925364777`. Parent validation owns the unchanged-production RED run and corrected-production GREEN run using that exact consumer, serialized with other Gradle work.

## Validation status

Pending parent-run host tests, static checks, APK compilation and final-head CI. No build, CI or physical-device pass is claimed by this implementation record. Existing file-opening policy, external-helper and handler-discovery regressions should also run. API27/34 Robolectric coverage does not establish the full U06 physical provider/handler acceptance matrix or complete U06.
