# SyntaxLauncher Microsoft application

## Current registration and review status — 22 October 2026

- Display name: **SyntaxLauncher**; project-owned public Client ID: `4e52f347-1d66-468f-b379-9168778636dc`.
- Created 19 October 2026. Personal Microsoft accounts; native/public desktop platform; registered redirect `http://localhost/callback`. S256 PKCE, random state and an ephemeral local port are used.
- The public Client ID is now the desktop runtime default. An explicit `SYNTAXLAUNCHER_MICROSOFT_CLIENT_ID` developer override remains available; this is not a player setting or a workaround for refusal.
- The desktop embeds no client secret. Entra overview shows one pre-existing secret; its value was not accessed, used or changed in this task. The public-client OAuth code does not use it.
- Initial form submission: **19 October 2026**, with a visible receipt. **Outcome: declined**, reported directly by the maintainer on 22 October 2026. The generic batch email does not establish that this specific AppID was classified as a bad app or blocked.
- Renewed submission: being prepared after material implementation and documentation changes; the actual receipt/outcome is recorded separately below. Do not mark it submitted until the form confirms it.
- **Allowlist approval and live account sign-in/refresh/gameplay: NOT verified.** Automated HTTP fixtures are not live authorization evidence.

## Material changes for renewed review

The official [AppID Review form](https://aka.ms/mce-reviewappid) explicitly requires authentication and ownership checks before gameplay regardless of server configuration. The old nickname-only workflow did not satisfy that requirement. The denial email gives no individual reason, so this is an evidence-backed compliance gap, not a confirmed explanation from Mojang.

The production desktop no longer offers offline/non-premium gameplay. It requires a saved Microsoft account, requests a fresh session at every Play, checks official Java entitlements and profile, and refuses a failed/unavailable check. The shared core coordinator independently rejects anonymous accounts, legacy authentication, zero/missing tokens and missing public client identity before preparation. Existing worlds and legacy identity records are not deleted. Internal historical account/parser fixtures do not authorize public gameplay.

Authentication uses Microsoft authorization code + S256 PKCE → Xbox user authentication → XSTS → Minecraft token → `GET /entitlements/mcstore` → `GET /minecraft/profile`. Accepted Java entitlement names are `game_minecraft` or `product_minecraft`; unrelated/Bedrock-only/empty/malformed/error responses fail closed. Profile availability is required as a second check. Desktop Play forces renewal even if an in-memory session is still valid.

Public materials identify SyntaxDevTeam and the direct maintainer-selected support address, explain privacy/permissions/removal, state current approval limitations, and prominently disclose independence from Mojang/Microsoft. The original launcher logo is preserved at the maintainer’s request. The generated artwork is not evidence of official endorsement; no individual finding against the logo was supplied by Mojang. No claim that the existing logo independently passes a legal review is made.

See [the complete Entra/review evidence checklist](APP_REVIEW_2026-10.md) for public URLs, recommended and optional fields, tests and remaining gates.

## Historical submission record — 19 October 2026

The text below is retained as the original submission record. Its offline-play description is superseded by the current access policy above. It must not be reused as current justification or as evidence of live authentication. Historical statements about integration readiness were corrected during subsequent work; current source/tests and acceptance records are authoritative.

## Submitted justification

SyntaxLauncher is an independent third-party desktop launcher for Minecraft: Java Edition, developed by SyntaxDevTeam in Kotlin with a Compose Desktop interface for Windows, macOS and Linux. We request approval of this application's own Microsoft Entra public-client AppID so players who are entitled to Minecraft: Java Edition can sign in with their own Microsoft account and launch the game with their verified Java profile.

The application uses the system browser and the Microsoft identity platform authorization-code flow for personal Microsoft accounts, with S256 PKCE, cryptographically random state and a single-use localhost callback on an ephemeral port. Requested OAuth scopes are XboxLive.signin and offline_access. The launcher then uses the official Xbox Live user authentication and XSTS flow, exchanges the resulting identity at POST https://api.minecraftservices.com/authentication/login_with_xbox, and retrieves the authenticated profile from GET https://api.minecraftservices.com/minecraft/profile. These Minecraft Services endpoints are required to obtain the player's Minecraft access token and verified Java username/UUID for the standard game launch/session flow. A successful Microsoft OAuth response alone is not treated as proof of Java ownership; an unavailable Java profile prevents premium launch.

SyntaxLauncher never asks for or stores a Microsoft password, uses no embedded Microsoft sign-in form, embeds no client secret and does not reuse another launcher's AppID. Access tokens remain in memory. Refresh credentials use native OS-protected storage: Secret Service/libsecret on Linux, Keychain on macOS and user-scoped DPAPI on Windows, with no plaintext fallback. Token-bearing objects and authentication errors are redacted; premium child-process stdout/stderr is not persisted by the launcher. Sessions are refreshed when expiring, refreshed identities must retain the same UUID, and local sign-out removes the saved refresh credential and account identity.

We are seeking approval before declaring premium functionality ready for release. The authentication chain, account lifecycle and browser callback have deterministic automated coverage; Linux native credential storage has also been exercised using a disposable synthetic credential. Approved-AppID end-to-end sign-in, live refresh, authenticated gameplay and native Windows/macOS acceptance remain release gates. We do not claim that mocked tests establish successful live authentication or that this request has already been approved.

## Submitted additional information

Project and implementation reference: https://github.com/SyntaxDevTeam/SyntaxLauncher

The same metadata-driven launch pipeline is used for offline and verified Microsoft accounts; premium launch receives the authenticated Minecraft token and profile rather than a fabricated premium identity. The launcher also provides explicitly labelled offline profiles for local play and servers configured to accept offline-mode clients. This mode does not grant Minecraft ownership, impersonate a verified premium account, authenticate to online-mode servers, or bypass Microsoft/Xbox/Minecraft authentication. A failed premium login is not silently converted into offline play.

The launcher does not implement mechanisms to bypass ownership, licence or server authentication checks, and this approval request is limited to the documented Java authentication/profile integration. Game preparation uses official version metadata and verified downloads through a shared network layer; mutable worlds/settings belong to isolated user instances.

Current implementation status and remaining acceptance checks are recorded in docs/ROADMAP.md and docs/TESTING_V0.3.md in the project repository. We can provide implementation details or additional evidence needed for review. This is a new-AppID approval request, not a report against another application.

## After a review decision

Record the actual approval/denial without including private messages, account tokens or callback URLs. If approved, follow [TESTING_V0.3.md](TESTING_V0.3.md) on an owned entitled account: live consent, restart/refresh, profile identity, real premium game launch and online-mode server identity, plus private log/crash review. Complete native Windows/macOS vault/browser acceptance before checking the full v0.3 quality gate. A successful Entra registration or form receipt cannot substitute for these tests.
