# Cloud Connect official research

Checked 27 October 2026. These are provider API/source findings and engineering recommendations, not verified Atomic integrations. No SDK version or complete dependency licence closure is selected. See [Plan](PLAN.md) for source audit and [Tasks](TASKS.md) for decisions.

| Candidate | Verified official finding | Atomic recommendation / unresolved gate |
| --- | --- | --- |
| Google Drive | Per-file `drive.file` access differs from restricted full-drive scopes [G1]. Android AuthorizationClient requests authorization to Google data [G2]. | Retain conditional Drive-first ordering. Choose the actual access product and registration before claiming whole-drive browsing; evaluate current native authorization rather than legacy sign-in examples. |
| Dropbox | Official mobile guidance supports authorization-code PKCE, short-lived tokens and refresh tokens; App Folder and Full Dropbox are different access models [D1]. | Strong alternative first connector or second abstraction test; app registration and exact scopes remain required. |
| OneDrive | Android redirect registration uses package/signature identity [M1]. Children use drive/item IDs and continuation links [M2]. Upload sessions support conflict options and revision preconditions [M3]. | Useful second provider with delegated user access; verify personal/work account differences and provider-owned SDK token behavior. |
| Box | Current documented authorization-code exchange includes a client secret [B1]. | Secretless public-native PKCE suitability is not established by this research. Do not embed a secret or claim Box unavailable; investigate supported flow separately. |
| MEGA | Official SDK supplies Android Java bindings [E1]; its own licence permits source/binary redistribution under two conditions [E2]. | Native build/session/ABI/size and transitive licence investigation required. Do not confuse SDK licence with the separately licensed MEGAsync application. |

Drive list results expose continuation and incomplete-search metadata [G3]; handle them intentionally rather than treating one response as a whole folder. Microsoft Graph documents 429 Retry-After and backoff [M4]. These support testing explicit paging and retry contracts, not assuming a uniform cloud API.

Provider ranking is an engineering inference based on the observed contracts and setup needs. Existing roadmap conditionally prefers Drive, followed by Dropbox or OneDrive. No owner scope choice or registration is inferred. Box, MEGA and other screenshot brands remain individually evaluated backlog candidates; this document does not assert their current availability or compatibility with Atomic.

## Primary sources

- [G1 — Drive scopes and verification](https://developers.google.com/workspace/drive/api/guides/api-specific-auth). Selected-file and broad-drive permissions are materially different; bundling code does not waive provider authorization requirements.
- [G2 — Android authorization](https://developer.android.com/identity/authorization) and [AuthorizationClient reference](https://developers.google.com/android/reference/com/google/android/gms/auth/api/identity/AuthorizationClient). Verify SDK-supported account and token behavior for the selected implementation.
- [G3 — Drive files.list](https://developers.google.com/workspace/drive/api/reference/rest/v3/files/list). Query, continuation and incomplete-search fields inform listing tests.
- [D1 — Dropbox OAuth](https://docs.dropboxapi.com/dropbox-api/docs/oauth). Public-mobile PKCE and access-model guidance; do not copy server-only secret flows into the APK.
- [M1 — Microsoft Android redirect registration](https://learn.microsoft.com/entra/identity-platform/how-to-add-redirect-uri). Release signature identity matters when configuring native auth.
- [M2 — OneDrive children listing](https://learn.microsoft.com/en-us/graph/api/driveitem-list-children?view=graph-rest-1.0). Per-drive/item addressing and next-link pagination.
- [M3 — OneDrive upload sessions](https://learn.microsoft.com/en-us/graph/api/driveitem-createuploadsession?view=graph-rest-1.0). Explicit conflict policy, ETag preconditions and session transfer semantics.
- [M4 — Graph throttling](https://learn.microsoft.com/en-us/graph/throttling). Retry-After/backoff guidance; Atomic still needs cancellation and retry budgets.
- [B1 — Box OAuth without SDK](https://developer.box.com/guides/authentication/oauth2/without-sdk). Observed secret requirement is a feasibility question, not proof that no alternative exists.
- [E1 — MEGA SDK README](https://github.com/meganz/sdk/blob/master/README.md) and [E2 — SDK licence](https://github.com/meganz/sdk/blob/master/LICENSE). These sources are inspected documentation; no executable plugin is downloaded.

## Before dependency adoption

Pin an exact library revision and inventory transitive licences, notices and native binaries; test API27 support, release R8 rules and measured APK/installed-size impact. Evaluate provider-supported auth and a narrow REST adapter against bringing in a large generated SDK. That is an evaluation, not a selected engine. Keep all connector code bundled and updated through the app.
