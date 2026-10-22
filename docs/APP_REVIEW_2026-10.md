# SyntaxLauncher renewed AppID review — 22 October 2026

## Evidence and limits

The initial request was declined. The email did not disclose an individual reason and does not prove this AppID was classified as malicious or blocked. The official review form explicitly requires authentication and ownership verification before gameplay regardless of server configuration. The former nickname-only/offline workflow was a concrete gap. Missing privacy/terms/publisher information was an additional reviewability gap; there is no evidence that any one of these issues caused the denial. The original launcher logo is preserved following maintainer feedback; no logo-specific rejection was reported.

Current runtime changes remove anonymous gameplay, force session revalidation before every desktop Play, check Minecraft Java entitlements and profile, and enforce an independent core launch gate before downloads/process startup. Empty, unrelated, Bedrock-only, malformed and failed entitlement responses fail closed. No failed check grants offline play. Existing user data is preserved. Legacy account/parser fixtures remain for persistence and historical-format tests; they do not authorize gameplay.

Full local verification: 228 tests discovered, 224 passed, four opt-in live tests skipped; zero failures/errors. `./gradlew --offline test build :launcher-desktop:createDistributable` passed. EN/PL/DE rendering, compact/large windows, original launcher mark and authentication-disabled Play were inspected. These are fixture/native-package checks, not live Microsoft/Minecraft authorization, current Game Pass compatibility, cross-platform native vault acceptance or actual gameplay. Those gates remain open.

## Public application information

| Item | Value |
| --- | --- |
| Display name | SyntaxLauncher |
| Publisher / support | SyntaxDevTeam / WieszczY; wieszczy85@gmail.com |
| Public application/client ID | `4e52f347-1d66-468f-b379-9168778636dc` |
| Home / marketing | https://syntaxdevteam.pl/p/syntaxlauncher |
| Privacy statement | https://syntaxdevteam.pl/p/syntaxlauncher-prywatnosc |
| Terms of use | https://syntaxdevteam.pl/p/syntaxlauncher-warunki-uzytkowania |
| Support / removal | https://syntaxdevteam.pl/p/syntaxlauncher-zarzadzanie-usluga |
| Source / licence | https://github.com/SyntaxDevTeam/SyntaxLauncher / MIT |
| Downloads | https://github.com/SyntaxDevTeam/SyntaxLauncher/releases |
| Platforms | Native desktop Windows, Linux, macOS; broader native acceptance remains open |

Each application page identifies the publisher/contact, states independence from Mojang/Microsoft, and links to the other notices. Website cookie practices have their own miniPORTAL policy; application policies do not purport to cover all website or third-party processing. The current source embeds no client secret. The Entra overview reports one pre-existing secret; it was not read, used or changed. The launcher never sends a client secret in the native OAuth flow.

## Entra fields: necessary, useful and conditional

| Field | Appropriate configuration / rationale |
| --- | --- |
| Name | SyntaxLauncher; prohibited brand words are absent from the application name. Minecraft is used only as a secondary description. |
| Application/client ID | Keep the existing project-owned ID. Do not rotate IDs to evade refusal or reuse another launcher ID. |
| Directory/tenant ID | Obtain from this application's Entra overview; provide privately in the review form, not in public docs. |
| Contact in the form | Must be cross-referenceable in Azure. Keep the signed-in administrator identity distinct from the public support email, and explain both if needed. |
| Supported accounts | Personal Microsoft accounts, matching `/consumers` OAuth and the target players. No need to enable organizational accounts for this native app. |
| Platform / redirects | Public mobile/desktop; registered `http://localhost/callback`, ephemeral port at runtime; code+S256 PKCE/state. Do not add a website redirect just because the app now has a website. |
| Client credentials | A public desktop cannot protect a client secret. Do not create/distribute one or copy an existing one into the launcher. |
| Scopes | `XboxLive.signin offline_access`. No mail, contacts, calendar, files or directory access is needed. Do not add broad Graph permissions to try to cure Minecraft's allowlist denial. |
| Homepage | Published application page above. |
| Privacy / terms | Separate public HTTPS application notices above, surfaced during consent. A generic README is not a substitute for supplying these fields. |
| `info.marketingUrl` / `info.supportUrl` | Application overview and support/removal page above. Preserve other existing `info` values when editing. |
| Logo | Preserve the original launcher mark as requested by the maintainer. Do not replace project branding based on an unconfirmed interpretation; identify any future specific concern explicitly. |
| Description / notes | Explain independent desktop purpose, entitled accounts only, browser PKCE, no secret, current approval/live-test limits, publisher/contact and support URL. Avoid false approval or security-certification claims. |
| Publisher domain | A verified owned domain can improve identity. It is distinct from website hosting, application URL and publisher verification. Requires verified control; do not claim verification merely because pages exist. |
| Publisher verification / MPN | Conditional partner-program verification, not documented as an AppID-form requirement. Requires matching eligible partner identity/domain; do not invent a partner ID or enroll in unrelated paid services. |
| `serviceManagementReference` | Optional reference to an organizational service/asset-management contact record. Not a required fourth policy URL; leave blank without a real service record. Public service docs belong at support URL. |
| Owners | Use legitimate administrators/owners. Do not grant new accounts or broad permissions merely to improve the review packet. |

## API purpose and data minimization

1. Microsoft `/consumers/oauth2/v2.0/authorize` and `/token`: user-mediated public-client sign-in and renewal; system browser sees the password.
2. Xbox `/user/authenticate`, XSTS `/xsts/authorize`: establish the Xbox identity accepted by Minecraft Services.
3. Minecraft `POST /authentication/login_with_xbox`: obtain a Minecraft token for the authenticated player.
4. Minecraft `GET /entitlements/mcstore`: require a Java entitlement (`game_minecraft` or `product_minecraft`). An empty or Bedrock-only response does not qualify.
5. Minecraft `GET /minecraft/profile`: require the verified Java UUID and name; OAuth success is not ownership proof.

The production Play path requests a fresh session using the same chain before each launch. No session is published after failed ownership/profile validation. Access tokens remain in memory; refresh credentials use native OS vaults with no plaintext fallback. Account inventory stores names/UUIDs/types only. Local Sign out removes credential before identity; failure remains visible. Game processes receive the normal token/profile locally. The default premium process starter discards raw stdout/stderr, but game/mod-owned logs remain a separate privacy concern and are not automatically uploaded.

## Sources checked on 22 October 2026

- [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines): essential independence, direct contact, disclaimer, naming, assets and distribution rules.
- [Minecraft EULA](https://www.minecraft.net/en-us/eula): legitimate access, authorized download sources, mods and distribution; separate player/Microsoft terms.
- [Official review instructions](https://help.minecraft.net/hc/en-us/articles/16254801392141) and [live AppID Review form](https://aka.ms/mce-reviewappid): ownership/authentication regardless of server configuration, prohibited app-name words, fields, weekly review and no benefit from duplicate submissions. The help page was unavailable in the text fetcher; the live form was read directly in the browser.
- [Microsoft application privacy/terms links](https://learn.microsoft.com/en-us/entra/identity-platform/howto-add-terms-of-service-privacy-statement).
- [Microsoft application manifest](https://learn.microsoft.com/en-us/entra/identity-platform/reference-microsoft-graph-app-manifest) and [application resource](https://learn.microsoft.com/en-us/graph/api/resources/application): informational URLs, description/notes and optional service management reference.
- [Publisher domain](https://learn.microsoft.com/en-us/entra/identity-platform/howto-configure-publisher-domain) and [publisher verification](https://learn.microsoft.com/en-us/entra/identity-platform/mark-app-as-publisher-verified): separate optional trust mechanisms with prerequisites.
- [Maintainer source for Minecraft Services entitlement API usage](https://github.com/Voxelum/minecraft-launcher-core-node/blob/master/packages/user/mojang.ts): independent client implementation, not an official Mojang specification. Endpoint behavior must still receive approved-AppID live acceptance.

## Publication and submission record

- Website pages: all four published and checked in the browser for headings/content, direct contact and cross-links. Project/support pages report the confirmed renewed submission. The text-only fetcher could not retrieve the new pages; browser rendering was verified.
- Entra: homepage/privacy/terms, marketing/support URLs, description, notes and original logo saved; persistence verified by reopening the manifest. Unused default Graph User.Read removed; no broader permissions added. Optional service reference remains blank. Publisher/domain/partner verification is not claimed.
- Repository: implementation b915ad2 pushed to main after explicit maintainer approval; full local verification passed. [Hosted CI run 39836343471](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39836343471) passed compile/tests and is completing installer/package/publication checks; final status is reported in chat/local delivery packet.
- Renewed AppID form: submitted once on 22 October 2026; visible receipt: **Thank you for contacting Mojang Studios.** Earlier refusal and revised code were disclosed. Await the new decision; do not submit duplicates.
