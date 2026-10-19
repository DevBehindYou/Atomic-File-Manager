# Image preview EXIF orientation

`ImagePreviewHelper` now reads optional EXIF orientation and applies every standard orientation, including horizontal/vertical mirrors and the two diagonal transforms. Original image bytes and metadata remain untouched. The existing single-lambda constructors remain compatible; tests and new wiring can inject IO as the second argument.

Backend selection, opening, stream construction, bounds decoding, EXIF and sampled payload decoding all run on IO. Each pass closes its stream. A cancellation-aware wrapper checks reads/skips, checks between passes prevent later work after cancellation, and cancellation is rethrown even if a metadata/decoder library internally swallows a stream exception. Owned bitmaps are recycled on failure, cancellation or replacement; ownership transfers to the caller only after the IO dispatch returns successfully.

Optional EXIF reading has an 8 MiB consumed-byte budget, including skips and rereads; it creates no full-file copy or temporary file. If metadata is unsupported, damaged, unavailable or exceeds that budget, preview still uses normal orientation so formats such as BMP continue working. A rotation cannot be inferred when metadata is unreadable. Pixel decoding errors and typed backend failures remain failures.

The default maximum dimension remains 2048. Non-positive requested sizes fail before backend access. Power-of-two sample calculation uses long arithmetic and a ceiling comparison to avoid overflow or a one-pixel bound overshoot. A final aspect-preserving scale enforces the requested limit if a decoder rounds differently; orientation swaps width/height without changing the largest dimension.

Host tests target APIs 27 and 34 and cover all eight affine corner mappings, unknown orientation, sampling extremes, invalid requested size, backend/provider failures, cancellation, closure, actual injected-worker access, read/skip/reset budgets and retained cancellation. Native instrumentation creates uniquely named app-cache JPEGs with four colored quadrants and each of the eight EXIF values, then checks sampled dimensions, decoded corner colors, the unchanged source digest/EXIF and cleanup. A second native BMP fixture verifies metadata fallback and unchanged bytes. These fixtures do not inspect or alter a user's media.

Validation status: source review and standalone formatting only until the integrating agent runs the serialized Gradle pipeline and selected native fixtures. No host or phone pass is claimed here.
