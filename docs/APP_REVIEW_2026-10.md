# AppID review readiness — 24 October 2026

## Decision and provenance

Microsoft/Minecraft Services AppID review is **not approved**. The generic enforcement response does not identify a specific defect or establish a bad-app finding against SyntaxLauncher. The first request was submitted 19 October 2026 and reported declined; a renewed request on 22 October described immutable commit `b915ad2`. Do not describe that old reviewed commit as identical to current source. Keep full submissions and exact public facts in immutable Git history.

See [MICROSOFT_APP.md](MICROSOFT_APP.md) for registration and publisher details and [APPID_COMPLIANCE.md](APPID_COMPLIANCE.md) for current source-level controls and residual gates.

## Current product and required evidence

| Category | Requirement | Evidence / open task |
| --- | --- | --- |
| Microsoft identity | System-browser OAuth authorization-code PKCE S256, random state, configured public client and localhost callback | Kotlin implementation and fixture coverage; real end-to-end pending |
| Xbox identity | Authenticated Xbox Live and XSTS chain using user tokens | Kotlin exchange and fixture tests; production AppID acceptance pending |
| Java ownership | Minecraft Services token, Java entitlement `game_minecraft` / `product_minecraft`, verified profile | Fail-closed checks in `MinecraftPremiumAuthentication`; real purchased-account test pending |
| Every game launch | Revalidate account and ownership, never allow an account-less launch or substitute nickname identity | Account picker, `DesktopLauncherSession`, core `MinecraftLaunchCoordinator`, `LaunchPlanBuilder` |
| Legacy player records | Inert old records remain stored but cannot be shown or used to play | Non-destructive persistence test and game data preservation test |
| Secrets | No password, embedded client secret, plaintext token fallback or token logging | Inspect actual platform vault behavior, support logs and crash reports |
| Publisher identity | Publisher/contact/independence disclaimer, accurate Entra metadata, privacy/terms and removal routes | Public site and Entra **must be checked and updated separately** |
| Installer and assets | Verified publisher binaries, Minecraft client download from official sources, mod licence and supply-chain checks | Signing/notarization, clean-machine testing and third-party content checks remain |
| CI | Tests and three-platform packaging | Check GitHub Actions on the **new code commit**; success on an earlier commit does not validate these changes |

## Rejection avoidance versus guarantees

The source changes remove an identifiable licence-validation loophole, but **no engineering checklist can guarantee Mojang allowlisting**. Their decision may consider business/publisher identity, public presentation, app conduct, third-party software distribution, data protection, or factors not disclosed in rejection notices. Do not represent unverified approval, attestations, signing or live-account acceptance as completed.

## Policy references

- https://aka.ms/mce-reviewappid
- https://www.minecraft.net/en-us/usage-guidelines
- https://www.minecraft.net/en-us/eula
- https://learn.microsoft.com/en-us/entra/identity-platform/howto-add-terms-of-service-privacy-statement

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**
