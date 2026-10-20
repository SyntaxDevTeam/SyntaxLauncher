# SyntaxLauncher Microsoft application

## Registration and review status

- Display name: **SyntaxLauncher**.
- Project-owned public Client ID: `4e52f347-1d66-468f-b379-9168778636dc`.
- Created in Microsoft Entra on **19 October 2026**, after explicit maintainer authorization.
- Supported accounts: personal Microsoft accounts.
- Platform: public/native mobile and desktop application.
- Registered redirect: `http://localhost/callback`; the launcher uses an ephemeral localhost port with S256 PKCE and cryptographic state.
- No client secret was created or embedded.
- Current desktop wiring still requires `SYNTAXLAUNCHER_MICROSOFT_CLIENT_ID`; the recorded project Client ID is not yet the runtime default. This is maintainer/developer configuration, not a player account identifier.
- Submitted once to Minecraft Services **AppID Review** on **19 October 2026**, after the maintainer explicitly confirmed the Microsoft platform policies, Minecraft EULA/Usage Guidelines and submission.
- The form visibly returned **“Thank you for contacting Mojang Studios.”** This proves submission, not authorization.
- **Minecraft Services approval: pending.** No written approval, live premium authentication, refresh or gameplay is claimed.

The official [Minecraft application-review instructions](https://help.minecraft.net/hc/en-us/articles/16254801392141-Java-Edition-Game-Service-API-Review-or-Application-Process) link to [AppID Review](https://aka.ms/mce-reviewappid). The form states that submissions are reviewed weekly and duplicate submissions do not accelerate review. Do not resubmit this AppID merely because approval is pending.

The maintainer supplied the contact address directly in the form. Contact details and private directory/object identifiers are intentionally absent from this repository record. The review team receives them through the authorized form.

## Current source integration status — 20 October 2026

The submitted text below is retained as the submission record. The repository contains account-manager/OS-vault foundations and a bounded callback helper with deterministic tests, but `DesktopLauncherSession` still uses in-memory sessions and `DesktopMicrosoftSignIn` still uses its original callback. These new helpers are not yet wired into the running desktop. Do not treat the submission description as evidence of completed desktop persistence or live authentication. Linux/Windows/macOS native vault acceptance and approved-AppID end-to-end tests remain separate gates.

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
