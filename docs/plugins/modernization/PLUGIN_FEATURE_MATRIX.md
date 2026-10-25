# Plugin capability matrix — frozen denominator

26 October2026. Parent reviewed the full intake coverage and froze N=42on26October2026. Full intake remains authoritative: groups aggregate work, not single buttons. All nested requirements must pass, or remain explicitly unresolved. Unsupported core requirements keep groups open. Conditional/P2 evaluations may conclude with justified non-adoption but cannot silently delete requests.

Current baseline is source-audited in PLUGIN_AUDIT and each v0.2 record. Existing working features are retained, not reimplemented. No group newly host-verified/accepted by this audit.

| ID | Priority | Required capability (including conditional evaluations) | Exit evidence |
| --- | --- | --- | --- |
| IMG1 | P0 | Focused pinch/double-tap/pan, fit screen/width/original, swipe arbitration, zoom/control/immersive/rotation restoration | Geometry and actual gestures, physical touch |
| IMG2 | P0/P1 | JPEG/PNG/WebP/GIF/BMP/EXIF; actual animation timing/loop/pause/replay and malformed bounds | Decoder/API/animation fixtures and lifecycle |
| IMG3 | P0/P1 | On-demand name/MIME/dimensions/dates/EXIF/camera/color/GPSprivacy; folder-order navigation; share/edit/rotate/delete/More/copy/move/rename/Openwith/wallpaper/favourite | Capability-aware actions, authorizedGPS and metadata/navigation consumers |
| IMG4 | P0/P1 | Crop/90rotate/flip/straighten/resize/aspect/format/quality/reset/save/saveas and color adjustments | Non-destructive copy-first exports, metadata/privacy/original integrity/backend faults |
| IMG5 | P1 | AnimatedWebP/APNG/SVG/TIFF/HEIF/ICO/RAW vetted compatibility and high-resolution/tiled strategy | Static/animated/multipage/edit matrix, engine/API/license/size/memory evaluation |
| IMG6 | P0 gate | Image integration/provider/accessibility/performance | GIF journey,4K/8K/panorama/longanimation/thousands/SDUSB/rapidnavigation/recreation |
| VID1 | P0 | Focus/noisy/calls/source/retry/lifetime | Actual shared player consumers and physical interruptions |
| VID2 | P0/P1 | MP4/MKV/WebM/MOV/M4V/3GP; evaluateAVI/FLV/MPEG/TS; codec/device/legal distinction | H264/HEVC/VP8/VP9/AV1/MPEG4/AAC/MP3/Opus/Vorbis/AC3EAC3 actual fixtures and unsupported limits |
| VID3 | P0 | Play/seek/time/buffer/scrub/±10/folder/speed0.5..2/fullscreen/rotation/aspect/volume/lock/repeat/resume/info | Accessible controls/state and lifecycle tests |
| VID4 | P0/P1 | Embedded/externalSRTVTT, verifiedASSSSA/style/font/position/offset/multipleaudio/tracks/language/retainselection | Actual multi-track/subtitle fixtures and actionable unsupported errors |
| VID5 | P1/P2 | Configurable seek/brightness/volume/control/pinch gestures; PiP/background optin/hardwarecontrols/audiofallback/framecapture/playlist/subtitlediscovery; evaluateABrepeat/framestep/audiodelay/casting | Supported modes with native consumers; evidence-based optional decisions |
| VID6 | P0 gate | Video full integration | MKV journey, corrupt/noaudio/stall/seek/delete/source disconnect/rapid switch/rotation/accessibility/performance |
| AUD1 | P0 | Typed preparation retry/revision ownership/shared focus | Unchanged-source failing then passing actual consumers, no stale/leaked media |
| AUD2 | P0/P1 | MP3/AACM4A/WAV/FLAC/OGGVorbis/Opus, VBR; evaluateWMAALAC | Actual engine/device valid/corrupt/missingtag/seek/end fixtures |
| AUD3 | P0/P1 | Folder playlist/queue/repeat/shuffle/speed/resume/artwork/title/artist/album/duration/format/mini-full | Queue identity/state persistence and accessible consumers |
| AUD4 | P0/P1 | Intentional background/notification/lockscreen/headsetBluetooth/calls/disconnect/notificationremoval/service lifetime | Foregroundservice permissions design and actual interruption journey |
| AUD5 | P1 gate | Safe title/artist/album/artwork/genre/track/year/comment writes beforebatch; optionalboundedvisualizer; audiointegration/performance | Independent format/tagversion/original integrity/backend failures and full audio journey |
| PDF1 | P0 | Plain/password/scanned/malformed/mixedsizes and progressive bounded render/cache | Actual native large1000+page/resource/failure fixtures |
| PDF2 | P0 | Single/continuous/horizontal/vertical modes, zoom/fit/pagejump/count/thumb/rotation/night/resume | Navigation/render quality/position/landscape/accessibility |
| PDF3 | P0/P1 | Text selection/copy/search/nextprevious/highlights/outline/internal-safeexternal links/password | API27compatible engine evaluation and actual text/password fixtures |
| PDF4 | P1/P2 | Bookmarks/highlights/notes/draw/annotations/pageimage/ranges/thumbnails/share/print; evaluatemerge/split/forms/TTS/offlineOCR | Separate view/annotation/structural operations, bounded/privacy/native-size evidence |
| PDF5 | P0 gate | PDF integration/provider/performance | LargePDFjourney, source loss/rotation/restoration/error/resourceclosure/budgets |
| EDT1 | P0 | Save/Saveas/dirty/durable draft/revision conflicts/backend-specific publication | Fault/denial/lowspace/disconnect/interruption/processdeath preserving originals |
| EDT2 | P0 | Undo/redo/findreplace/findall/replaceall/case/wholeword/regex/gotoline/wrap/linenumbers/selection/monospace/readonly/keyboard | Unicode/zerowidthregex/longline/history and actual UI |
| EDT3 | P0 | Bounded KB-to-hundredsMB paged/readonly fallback and practical search | Large/longline fixtures, honest limits and no silent truncation |
| EDT4 | P1 | Maintained grammar for all intake languages; indentation/tabspace/brackets/currentline/folding/comments | Language corpus/API/license/latency and useful controls |
| EDT5 | P1/P2 | Encoding/BOM/lineending preserve-select, JSONXMLformatvalidate; evaluatemulticursor/diff/snippets/tabs | Exact byte integrity/invalidencoding/formatfailures and optional decisions |
| EDT6 | P0 gate | Editor integration | JSONopen/edit/undo/find/save/reopen journey, provider/accessibility/performance/recovery |
| MD1 | P0/P1 | VerifiedCommonMarkGFM headings/emphasisstrike/nestedtasklists/tables/quotes/fences/rules/images/footnotes | Conformance corpus beyond current line/fence subset |
| MD2 | P0 | Safe localrelativeimages/links, remoteasset/unsafeHTML/scheme policy | Untrustedfixtures, no unsolicited network/scripts/privileged bridge |
| MD3 | P0/P1 | TOCheadingjumps/search/copytext-code/position/fontthemes/scrollrestore | Native consumers and restoration/accessibility |
| MD4 | P1/P2 | PreviewEdit/refresh/share/export/unsaved, widesplitsync; evaluateMermaid/math/imagezoom/anchors/internalnavigation | Current draft/source correctness, sandboxing and optional decisions |
| MD5 | P0 gate | Markdown integration | Relativeimage/edit-refresh journey, bounded largedocument/assets/provider/performance |
| DOC1 | P0 | TXT/CSV/RTF/DOCX exactfidelity/zoom/selection/search/sections/reflow/fonts/properties | Verified formats/complex tables-images/Unicode and honest unsupported fallback |
| DOC2 | P1 | XLSXPPTX/ODTODSODP/EPUB/legacyDOCXLSPPT engineevaluation | Native/API/license/size/privacy decisions, no private uploads |
| DOC3 | P1 | Sheets/cellformats-formulas/search/frozenheaders, slidenav-fit-thumbnails, EPUBchapters/reflow/themeTOC; bookmarksposition/printshare | Actual supported formats with format-specific consumers |
| DOC4 | P0 gate | Document integration | DOCXtables-images journey, bounded ZIPXML/assets/externalrefs/malformed/provider/accessibility/performance |
| ARC1 | P0 | ZIPZIP64/TAR/TARGZ/GZIP/BZIP2 browse/read/extract | Valid/corrupt fixtures distinct from extension dispatch |
| ARC2 | P0/P1 | Entryhierarchy/name/size/date/compressedsize/searchsort/preview/selected-allextract/copy/chosen destination | Safe path/selection/provider/action consumers |
| ARC3 | P0/P1 | ZIPcreate/filefolder/compression/progresscancel/background; conditionalencrypted/update/TAR7z/splitwrites | Staging/publication/cancel/retry/lowspace/integrity, honest format capabilities |
| ARC4 | P1/P2 | Evaluate7z/RARRAR5/XZ/Zstd/TARXZ/TARZST/AES/multipart; optionalISO/CAB | Engine/format browse/extract/create/modify/encryptedread-write/multipart fixtures and licenses |
| ARC5 | P0 gate | Archive security/integration/performance | Protectedarchivejourney/traversal/absolute/symlink/bomb-ratio/depthcount/duplicatecase/header/corrupt/lowspace/interrupt/overwrite/processkill, unrelateddata safe, meaningful ETA |

All groups include applicable source/revision/error/cancel/provider/theme/rotation/accessibility and exact-head static/tests/build/review/CI/clean integration gates. Actual device/format/security/performance acceptance is separate from host proof. Image6+Video6+Audio5+PDF5+Editor6+Markdown5+Document4+Archive5=42. Existing U01-U13 denominator unchanged.

Global gate for every applicable group: Atomic adaptive layout, accessibility, theme/landscape, supported plugin preferences, descriptor/format-limit/association/About v0.2 context and accurate fallback must pass. Shared foundations receive no extra numerator units.
