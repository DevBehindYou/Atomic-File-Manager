# Opening source metadata refresh — candidate validation

Prepared 25 October 2026 as an existing U06 reliability follow-up. This is a source candidate, not complete U06, advanced-plugin or release acceptance. Parent baseline evidence is authoritative; this author runs no Gradle or ADB.

## Reproduced boundary

The parent's final unchanged-production six-consumer run at root206e25e/app tree44bdb9c414450bf9100da8fe9489e67a9f0087fc has three intended failures (same-ID stale MIME in policy/fallback and missing replacement-MIME reader), three passing controls (different-ID replacements and a selected gallery image's real handoff), no errors/skips, 2m32s total /39.233s suite. The parent retains exact XML/logs under opening-source-revision-six-red-* and the frozen six-consumer CRLF SHA256 FE360E748B9E16305D013598E8125D065CEBFAB890ABBB3824009275508B476F. No frozen assertion is changed by this source candidate.

## Source behavior

A small immutable PreviewSourceSelection stores the last explicit selection with its original parent snapshot. A changed parent snapshot replaces the displayed node when that selection still refers to the parent's ID. A different-ID gallery or editor save-as selection remains selected across original-parent metadata refreshes; an editor's same-ID source notification is preserved while its parent snapshot remains unchanged. A new parent ID selects that new parent. This avoids resetting the whole viewer whenever metadata changes and does not rewrite the pager or editor.

Both opening entry points use the current effective source for header/handoff state. Reader lists/loading/query effects are keyed by effective MIME, so a same-ID MIME correction replaces incompatible choices. The original preferred-target and one-automatic-attempt snapshots stay scoped to their original file ID; Once/Always persistence still requires the existing successful-launch boundary and captures the current source/rule. There is no new automatic retry or preference write.

Preparation ownership uses the screen ID and actual selected source metadata (ID, name, MIME, size and modification time). Changing those actual handoff fields disposes the old request; original-parent metadata changes alone do not cancel a different-ID selected export. Lazy image/video extras do not restart an export. Gallery/editor source notifications still request immediate disposal when the actual selected metadata changes. The existing cap, cleanup, acknowledged-copy retention, temporary read-only launch flags, engine ownership and minimum API27 remain unchanged.

## Validation still required

No compilation, host GREEN, packaging, Lint, device run or CI is claimed by the source author. Parent must independently review the small selection/key mechanism, run the frozen six consumers plus applicable opening/viewer/request contracts, and record exact source/tree/artifacts. The sixth actual UI control guards gallery selection preservation; broad editor draft recovery, gallery sibling freshness, provider-side revision detection, in-flight provider mutation, permission loss and real transport acceptance remain separate. An unchanged/unknown provider timestamp cannot prove unchanged content. The host URI-boundary shadow remains explicit; actual Android FileProvider readback has separate scoped native evidence.

Source branch starts from PR84 head3e58de7 and excludes deferred benchmark63. PR84's current reviewed dependency must be integrated before this follow-up can merge. Fixed progress remains15% host verified /8% accepted until complete milestone gates pass. No plugin version, remote installer, permission, signing or benchmark decision changes.
