# Plugin capability matrix — frozen denominator

26 October 2026. Parent reviewed the full intake coverage and froze N=42 on 26 October 2026. Full intake remains authoritative: groups aggregate work, not single buttons. All nested requirements must pass, or remain explicitly unresolved. Unsupported core requirements keep groups open. Conditional/P2 evaluations may conclude with justified non-adoption but cannot silently delete requests.

Current baseline is source-audited in PLUGIN_AUDIT and each v0.2 record. Existing working features are retained, not reimplemented. No group newly host-verified/accepted by this audit.

| ID | Priority | Required capability (including conditional evaluations) | Exit evidence |
| --- | --- | --- | --- |
| IMG1 | P0 | Focused pinch/double-tap/pan, fit screen/width/original, swipe arbitration, zoom/control/immersive/rotation restoration | Geometry and actual gestures, physical touch |
| IMG2 | P0/P1 | JPEG/PNG/WebP/GIF/BMP/EXIF; actual animation timing/loop/pause/replay and malformed bounds | Decoder/API/animation fixtures and lifecycle |
| IMG3 | P0/P1 | On-demand name/MIME/dimensions/dates/EXIF/camera/color/GPSprivacy; folder-order navigation; share/edit/rotate/delete/More/copy/move/rename/Openwith/wallpaper/favourite | Capability-aware actions, authorizedGPS and metadata/navigation consumers |
| IMG4 | P0/P1 | Crop/90rotate/flip/straighten/resize/aspect/format/quality/reset/save/saveas and color adjustments | Non-destructive copy-first exports, metadata/privacy/original integrity/backend faults |
| IMG5 | P1 | AnimatedWebP/APNG/SVG/TIFF/HEIF/ICO/RAW vetted compatibility and high-resolution/tiled strategy | Static/animated/multipage/edit matrix, engine/API/license/size/memory evaluation |
| IMG6 | P0 gate | Image integration/provider/accessibility/performance | GIF journey; 4K/8K/panorama/long animations/thousands of images/SD/USB/rapid navigation/recreation |
| VID1 | P0 | Focus / noisy-output / calls/source/retry/lifetime | Actual shared player consumers and physical interruptions |
| VID2 | P0/P1 | MP4 / MKV/WebM/MOV/M4V/3GP; evaluate AVI / FLV / MPEG / TS; codec/device/legal distinction | H.264 / HEVC / VP8 / VP9 / AV1 / MPEG-4 / AAC / MP3 / Opus / Vorbis / AC3/EAC3 actual fixtures and unsupported limits |
| VID3 | P0 | Play/seek / time / buffer / scrub/±10/folder / speed 0.5–2×/fullscreen/rotation/aspect/volume/lock/repeat/resume/info | Accessible controls/state and lifecycle tests |
| VID4 | P0/P1 | Embedded/external SRT/VTT, verified ASS/SSA; style/font/position/offset; multiple audio tracks/languages and retained selection | Actual multi-track/subtitle fixtures and actionable unsupported errors |
| VID5 | P1/P2 | Configurable seek / brightness / volume / controls / pinch gestures; PiP/background opt-in/hardware controls/audio fallback/frame capture/playlist/subtitle discovery; evaluate A–B repeat / frame stepping / audio delay / casting | Supported modes with native consumers; evidence-based optional decisions |
| VID6 | P0 gate | Video full integration | MKV journey, corrupt input / no audio / stalls / seek / delete / source disconnect / rapid switching/rotation/accessibility/performance |
| AUD1 | P0 | Typed preparation retry/revision ownership/shared focus | Unchanged-source failing then passing actual consumers, no stale/leaked media |
| AUD2 | P0/P1 | MP3 / AAC/M4A / WAV / FLAC / OGG/Vorbis / Opus, VBR; evaluate WMA/ALAC | Actual engine/device valid/corrupt/missing tags/seek/end fixtures |
| AUD3 | P0/P1 | Folder playlists/queue/repeat/shuffle/speed/resume; artwork/title/artist/album/duration/format; mini/full player | Queue identity/state persistence and accessible consumers |
| AUD4 | P0/P1 | Intentional background / notification/lock screen / headset/Bluetooth / calls / disconnect / notification removal / service lifetime | Foreground service permissions design and actual interruption journey |
| AUD5 | P1 gate | Safe title/artist/album/artwork/genre/track/year/comment writes before batch; optional bounded visualizer; audio integration/performance | Independent format / tag version / original integrity/backend failures and full audio journey |
| PDF1 | P0 | Plain/password/scanned/malformed/mixedsizes and progressive bounded render/cache | Actual native large 1000+ page/resource/failure fixtures |
| PDF2 | P0 | Single/continuous/horizontal/vertical modes, zoom/fit/page jump / count / scroll thumb/rotation/night/resume | Navigation/render quality/position/landscape/accessibility |
| PDF3 | P0/P1 | Text selection/copy/search/next-previous/highlights/outline/internal and safe external links/password | API 27compatible engine evaluation and actual text/password fixtures |
| PDF4 | P1/P2 | Bookmarks/highlights/notes/draw/annotations/page-to-image/ranges/thumbnails/share/print; evaluate merge/split/forms/TTS/offline OCR | Separate view/annotation/structural operations, bounded/privacy/native-size evidence |
| PDF5 | P0 gate | PDF integration/provider/performance | Large PDF journey, source loss/rotation/restoration/error/resource closure/budgets |
| EDT1 | P0 | Save / Save as / dirty state / durable draft/revision conflicts/backend-specific publication | Fault/denial/low space/disconnect/interruption/process death preserving originals |
| EDT2 | P0 | Undo/redo/find/replace / find all / replace all / case / whole word / regex / go to line / wrap / line numbers/selection/monospace/read-only/keyboard | Unicode/zero-width regex/long line/history and actual UI |
| EDT3 | P0 | Bounded KB to hundreds of MB paged/read-only fallback and practical search | Large/long line fixtures, honest limits and no silent truncation |
| EDT4 | P1 | Maintained grammar for all intake languages; indentation/tab/space/brackets/current line/folding/comments | Language corpus/API/license/latency and useful controls |
| EDT5 | P1/P2 | Encoding/BOM/lineending preserve-select, JSON/XML formatting/validation; evaluatemultiple cursors/diff/snippets/tabs | Exact byte integrity/invalid encoding/format failures and optional decisions |
| EDT6 | P0 gate | Editor integration | JSON open/edit/undo/find/save/reopen journey, provider/accessibility/performance/recovery |
| MD1 | P0/P1 | Verified CommonMark/GFM headings/emphasis/strike/nested and task lists/tables/quotes/fences/rules/images/footnotes | Conformance corpus beyond current line/fence subset |
| MD2 | P0 | Safe local relative images/links, remote assets/unsafe HTML/scheme policy | Untrusted fixtures, no unsolicited network/scripts/privileged bridge |
| MD3 | P0/P1 | TOC/heading jumps/search/copy text/code/position/font/themes/scroll restoration | Native consumers and restoration/accessibility |
| MD4 | P1/P2 | Preview/Edit/refresh/share/export/unsaved, synchronized wide-screen split; evaluate Mermaid/math/image zoom/anchors/internal navigation | Current draft/source correctness, sandboxing and optional decisions |
| MD5 | P0 gate | Markdown integration | Relative image/edit-refresh journey, bounded large document/assets/provider/performance |
| DOC1 | P0 | TXT/CSV/RTF/DOCX exact fidelity/zoom/selection/search/sections/reflow/fonts/properties | Verified formats/complex tables-images/Unicode and honest unsupported fallback |
| DOC2 | P1 | XLSX/PPTX / ODT/ODS/ODP/EPUB/legacy DOC/XLS/PPT engine evaluation | Native/API/license/size/privacy decisions, no private uploads |
| DOC3 | P1 | Sheets/cell formats/formulas/search/frozen headers, slide navigation/fit/thumbnails, EPUB chapters/reflow/themeTOC; bookmarks/position/print/share | Actual supported formats with format-specific consumers |
| DOC4 | P0 gate | Document integration | DOCX tables/images journey, bounded ZIP/XML/assets/external references/malformed/provider/accessibility/performance |
| ARC1 | P0 | ZIP/ZIP64/TAR/TAR.GZ/GZIP/BZIP2 browse/read/extract | Valid/corrupt fixtures distinct from extension dispatch |
| ARC2 | P0/P1 | Entry hierarchy/name/size/date/compressed size/search/sort/preview/selected/all extraction/copy/chosen destination | Safe path/selection/provider/action consumers |
| ARC3 | P0/P1 | ZIP creation/files/folders/compression/progress/cancel/background; conditional encrypted/update/TAR/7z/split writes | Staging/publication/cancel/retry/low space/integrity, honest format capabilities |
| ARC4 | P1/P2 | Evaluate 7z / RAR/RAR5 / XZ / Zstandard / TAR.XZ / TAR.ZST / AES / multipart; optional ISO/CAB | Engine/format browse/extract/create/modify/encrypted read/write/multipart fixtures and licenses |
| ARC5 | P0 gate | Archive security/integration/performance | Protected archive journey/traversal/absolute/symlink/bombs/ratios/depth/count/duplicate/case collisions/header/corrupt/low space/interrupt/overwrite/processkill, unrelated data safe, meaningful ETA |

All groups include applicable source/revision/error/cancel/provider/theme/rotation/accessibility and exact-head static/tests/build/review/CI/clean integration gates. Actual device/format/security/performance acceptance is separate from host proof. Image 6 + Video 6 + Audio 5 + PDF 5 + Editor 6 + Markdown 5 + Document 4 + Archive 5 = 42. Existing U01-U13 denominator unchanged.

Global gate for every applicable group: Atomic adaptive layout, accessibility, theme/landscape, supported plugin preferences, descriptor/format-limit/association/About v0.2 context and accurate fallback must pass. Shared foundations receive no extra numerator units.
