# v0.3 — Microsoft Premium authentication test checklist

This checklist separates deterministic CI coverage from real Microsoft/Xbox/Minecraft live acceptance. The one-time v0.2 waiver does not waive any v0.3 checks.

## Prerequisites

1. Register an independent SyntaxLauncher native/public Microsoft Entra application that permits personal Microsoft accounts. Do not reuse another launcher's client ID.
2. Configure a native loopback redirect URI (http://localhost) for the public client. Do not add a client secret to the launcher.
3. **Separate mandatory step:** submit SyntaxLauncher's own Microsoft Entra Client ID to Minecraft/Mojang's AppID approval/allowlisting process. A valid Entra registration, Microsoft OAuth token, Xbox user token and XSTS token do not guarantee access to `https://api.minecraftservices.com/authentication/login_with_xbox`. Without approval the endpoint may return `HTTP 403 Invalid app registration`.
   - Support reference: https://help.minecraft.net/hc/en-us/articles/16254801392141 (confirm current application process on the official support website).
   - A developer reported Mojang Enforcement approval via https://forms.cloud.microsoft/Pages/ResponsePage.aspx?id=v4j5cvGGr0GRqy180BHbR-ajEQ1td1ROpz00KtS8Gd5UNVpPTkVLNFVROVQxNkdRMEtXVjNQQjdXVC4u in August 2026; this is a third-party report, **not independent confirmation** that the form is still accepting submissions.
   - Retain the submission confirmation and approval response without storing account secrets in the repository.
4. Configure SYNTAXLAUNCHER_MICROSOFT_CLIENT_ID to the project's registered public application UUID in the launcher's environment.
5. Test only on accounts you own; never share account tokens or browser callback URLs.

## App registration approval gate

- [ ] The project's own Entra Client ID exists, with correct native/public client settings.
- [ ] SyntaxLauncher AppID has been submitted for Minecraft Services approval.
- [ ] Written confirmation of Minecraft/Mojang approval has been received (not inferred from Entra portal success).
- [ ] `login_with_xbox` returns a valid Minecraft access token, and `/minecraft/profile` returns a verified Java profile for an entitled test account.
- [ ] If Minecraft responds with `Invalid app registration`, report the approval blocker clearly; do not recommend using another launcher's Client ID or bypassing authentication.

## Account selection / OAuth

- [ ] Offline nickname and old v0.2 instances remain selectable and playable.
- [ ] With a configured ID, Add Microsoft opens the default browser (not an embedded password form) and uses a random-port localhost callback.
- [ ] A valid login authenticates Xbox, XSTS, Minecraft Services and fetches the verified Java username and UUID.
- [ ] Cancel/denial, callback CSRF mismatch, browser not available and 180-second timeout produce actionable errors without exposing auth codes.
- [ ] Add a second account and switch between the two; verify proper Minecraft Java identities.
- [ ] Sign out removes a premium session from RAM; restarting currently requires a fresh login (secure OS credential persistence is not implemented).
- [ ] Invalid app approval yields an actionable 403 error without leaking tokens; diagnose permission/registration rather than suggesting another launcher's client ID.
- [ ] A Microsoft account without a Java profile cannot be played as premium.

## Premium launch and security

- [ ] Launch a real client using Premium, join an online-mode server that you own/test on, and verify actual identity.
- [ ] Confirm the same game preparation, Java runtime manager and instance directories are used for offline and premium accounts.
- [ ] Wait until a premium session is close to expiry; verify refresh or clean sign-in retry.
- [ ] Search launcher/game stdout and stderr, game-owned logs, crash reports and launcher diagnostics for tokens; do not share raw sensitive output.
- [ ] Verify Windows, macOS and Linux native distributables and browser callback handling.
- [ ] Verify premium token output is not present in the default process-manager stdout/stderr files.
- [ ] Before claiming v0.3 complete, implement and test OS secure refresh-token persistence or explicitly decide the product will require login every restart.

## Release policy

Do not mark premium sign-in or launch as successfully verified merely because mocked unit tests or CI compile. Record a full consent, login, refresh and authenticated Minecraft launch test on the project's authorized Microsoft app.
