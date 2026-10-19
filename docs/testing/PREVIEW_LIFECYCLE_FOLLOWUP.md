# Preview resource and lifecycle follow-up

The review found that late player preparation or a changed lifecycle owner could enable
playback while the owner was stopped. Initial autoplay now requires a started owner.
The binding immediately pauses an already stopped owner and pauses on stop, without
automatically resuming on start. Closing the binding removes its observer.

PDF and media staging now create the owned temporary file before requesting backend
input. FTP and WebDAV can acquire live streams during `openInput`, so cache creation
failure must happen before that acquisition. Copy failures still close the source and
delete the partial file. PDF zero-length reads remain supported with one loop exit.

Focused regression selectors:

- `*PreviewPlayerLifecycleTest`: initial policy, late preparation, owner changes,
  observer disposal, stop and foreground behavior; no native player required.
- `*MediaPreviewOwnershipTest`: unavailable cache never acquires eager input;
  read and metadata errors close eager streams and remove partial files.
- `*PdfDocumentLoaderTest`: unavailable cache never acquires eager input;
  read and renderer errors close eager streams; zero-length reads preserve the copy.

Validation status: source review, `git diff --check`, and scoped standalone ktlint
formatting completed successfully. Gradle tests, detekt, compilation and device checks
are pending the root agent's serialized validation.
