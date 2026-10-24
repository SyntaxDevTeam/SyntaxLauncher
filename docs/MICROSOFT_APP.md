# SyntaxLauncher — Microsoft application and Minecraft Services AppID

## Current product policy — 24 October 2026

SyntaxLauncher is an independent Kotlin/Compose desktop launcher for Minecraft: Java Edition on Windows, macOS and Linux. **Gameplay requires a Microsoft account and a freshly verified Java Edition entitlement and profile**. Local-only historical identity records are retained for non-destructive data compatibility but never shown in the account picker or allowed to launch. The launch pipeline rejects missing/non-Microsoft credentials at both session and core layers. Instances/worlds are not linked to the eligibility of an old identity.

Public Microsoft Entra Client ID: `4e52f347-1d66-468f-b379-9168778636dc`.
Application registration: personal Microsoft accounts; native desktop public client, redirect `http://localhost/callback`; system-browser authorization code with PKCE S256 and random state. Requested scopes: `XboxLive.signin offline_access`. No client secret is embedded or used by the launcher.

## Authentication and licence verification

Microsoft OAuth -> Xbox Live user authentication -> XSTS -> Minecraft Services `/authentication/login_with_xbox` -> `/entitlements/mcstore` -> `/minecraft/profile`.

- Entitlement requires `game_minecraft` or `product_minecraft`; empty, malformed, unrelated, Bedrock-only and HTTP error responses fail closed.
- Minecraft Java profile must produce a valid username and UUID; a Microsoft/Xbox identity alone is insufficient.
- Every Play obtains fresh verified credentials; refresh failure, changed UUID, missing token/client ID or failed verification stops launch before game file preparation.
- Refresh secrets are intended for native OS protected storage: Secret Service on Linux, Keychain on macOS, DPAPI on Windows. No unencrypted fallback. The app does not request Microsoft passwords.

**No allowlist approval is established.** Automated fixtures and successful CI do not demonstrate real account acceptance. Windows/macOS/Linux OAuth and vault acceptance, live Microsoft/Java ownership, Game Pass edge cases, token rotation and online-mode session verification remain gates.

## Review and submission evidence

- 19 October 2026: initial AppID request submitted; the maintainer reported a declined outcome.
- 22 October 2026: a renewed request submitted for immutable commit `b915ad282e6a9b32f22e0296ea1165d5e8c9445b`, using the same Client ID and tenant.
- 24 October 2026: a generic Mojang enforcement response was provided to the project. It does not identify a particular rule or prove that this Client ID was classified as malicious. Do not infer approval, specific rejection cause or blocked-app status.
- Any later code or policy modifications must be disclosed accurately in future correspondence. A previously submitted form does not retroactively describe newer commits. The historical original request evidence is in immutable Git history, not a statement of today's available features.

## Public publisher and disclosures

Publisher: **SyntaxDevTeam / WieszczY**. Contact: wieszczy85@gmail.com.

- Project: https://syntaxdevteam.pl/p/syntaxlauncher
- Privacy: https://syntaxdevteam.pl/p/syntaxlauncher-prywatnosc
- Terms: https://syntaxdevteam.pl/p/syntaxlauncher-warunki-uzytkowania
- Support and account removal: https://syntaxdevteam.pl/p/syntaxlauncher-zarzadzanie-usluga
- Source and builds: https://github.com/SyntaxDevTeam/SyntaxLauncher

Check live pages and Entra application metadata for consistency with the current Microsoft-only application. GitHub documentation changes do **not** modify those external sites. Publisher domain/partner verification was not confirmed and is not asserted. Native installer signing/notarization and cross-platform acceptance are also outstanding.

See the actionable [AppID compliance checklist](APPID_COMPLIANCE.md) and [review evidence](APP_REVIEW_2026-10.md).

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**
