# Third AppID request — review preparation, 25 October 2026

**DRAFT FOR MAINTAINER REVIEW. No form has been submitted in this preparation. No approval or live account acceptance is claimed.**

## Official requirements checked

The live [AppID Review form](https://aka.ms/mce-reviewappid) was read in the authenticated desktop browser on 25 October 2026 (all nine questions; no answers entered). Its stated gates are authentication and game ownership before gameplay/server/site access, no bypass of licence or safety checks regardless of server configuration, and a new application name excluding Mojang, Minecraft, Microsoft, Live, Xbox, Discord and Hypixel. SyntaxLauncher passes that name comparison. It asks for EULA/Usage Guidelines acknowledgement, contact cross-referenced in Azure, request type, application name, Client ID, Tenant ID, website, valid justification and optional additional information. It says reviews occur weekly and duplicate submissions do not accelerate them. There is no ID@Xbox or mandatory multitenant claim in this form. This is not proof that no other internal review criteria exist.

[Official EULA](https://www.minecraft.net/en-us/eula) and [Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) were checked: independent tools must not imply endorsement, original Minecraft content must not be redistributed without permission, publisher/contact and a prominent independence notice matter. This bounded technical comparison is not legal certification. Microsoft documents [personal-accounts-only audiences](https://learn.microsoft.com/en-us/entra/identity-platform/v2-supported-account-types); personal-only registration is not itself evidence of misconfiguration for the consumers endpoint.

## Source review and changes

Baseline main: `438cfda8443bdc16de2bc043b25b14bb790cc6dd`, [CI #201](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/40099337828). Hosted job inspection confirms verify, Windows and macOS succeeded; Linux failed in the installer lifecycle, preventing publication. The smoke file contained an unterminated MIT grep, an orphan copyright path and a duplicate lifecycle after DEB removal. Repair retains one DEB install/start/remove, one Fedora RPM install/remove and one AppImage extraction/start, with metadata and data-preservation assertions. CI now checks shell syntax before the expensive build.

| Control | Inspected implementation / meaningful fixtures | Result and limit |
| --- | --- | --- |
| Every Play | DesktopLauncherSession requests AccountManager.session(requireFresh=true) before coordinator preparation | Cached identity never authorizes Play; failed refresh cannot fall back |
| Core boundaries | MinecraftLaunchCoordinator and LaunchPlanBuilder require PremiumAccount, msa, nonzero token, client ID, session and unexpired credentials; final check before process creation | Stops accidental unauthenticated entry points. Source can be modified by its owner; this is not remote attestation |
| OAuth | MicrosoftOAuth + DesktopMicrosoftSignIn: consumers endpoint, system browser, S256 PKCE, random state, loopback callback, no password collection/client secret | Deterministic OAuth tests; native browser/consent acceptance still open |
| Xbox/XSTS | Explicit successful responses, token presence, matching user hash, no XSTS identity fallback | Failed-stage tests stop downstream requests |
| Ownership/profile | MinecraftPremiumAuthentication: successful mcstore Java entitlement (game_minecraft/product_minecraft), then valid Java profile | Empty/unrelated/Bedrock-only/error/malformed replies and invalid profile fail; Game Pass/purchase behavior not live tested |
| Token expiry | Token lifetime now starts conservatively before the Minecraft token request, not after ownership/profile network calls | New advancing-clock test covers latency, safety-margin exhaustion and actual expiry; later checks cannot extend token validity |
| Preservation | AccountManager decodes legacy rows read-only, lists Microsoft entries only; account/session tests preserve old files and game directories | No player files, worlds or credentials were accessed or migrated during this audit |
| Secrets/logging | Secret Service/Keychain/DPAPI; no plaintext fallback; redacted session/request representations; process arguments not logged; sensitive stdout/stderr discarded; safe diagnostics | Native vault behavior and third-party game/mod logs still require acceptance; no real tokens used |
| Downloads/content | Official Mojang metadata, client/assets/runtime hashes; loader upstreams; Modrinth metadata/CDN and modpack hashes; OptiFine official download without bundled binary | Upstream integrity is not code safety or a blanket content licence; arbitrary user mods/pack overrides need their own rights |
| Branding/installers | Shared footer contact/privacy/terms/independence notice, independent gateway icon; MSI publisher SyntaxDevTeam, AppStream developer/MIT, DEB copyright, RPM MIT | Packaging fixtures/native lifecycle evidence are distinct from signing/notarization and clean-machine gameplay |

The existing untracked UpdateOfficialSmokeTest is retained under the repository's standing publication instruction. It is opt-in, uses a public DEV asset and SHA-256, creates only a temporary directory and is **not** a Microsoft/entitlement test. Default runs skip it.

## Assets and public declarations

[Branding provenance](../launcher-desktop/src/main/resources/branding/README.md) records the generated gateway (22 October), landscape from a supplied reference (19 October), workshop (23 October), and native icon conversions. No Minecraft game installation is bundled by the packaging path. No claim is made that AI generation proves copyright/trademark clearance; the reference landscape and any third-party catalog art require continued rights review. The obsolete block/orbit description in README was replaced with the actual gateway presentation. No new artwork was generated.

Live site reads initially returned HTTP 403 to the fetch tools; the desktop browser successfully read all four application pages. Privacy, terms and support already describe authenticated-only gameplay, secure storage, recipients, children/guardians and preservation. The product page and its summary still advertised offline/non-premium gameplay and contradicted those pages and main. It was corrected through the existing miniPORTAL editor and read back publicly on 25 October. It now states Microsoft-only gameplay, preserved legacy data, two unsuccessful requests, no submitted third request, no approval/live-test claim and experimental historical builds. Unrelated miniPORTAL authentication features were not changed.

## Entra observations (25 October, existing signed-in portal)

- Display name: SyntaxLauncher; public Client ID: `4e52f347-1d66-468f-b379-9168778636dc`; directory ID visible in portal: `5284ffcd-785b-4192-b241-dcf40258a5b4`.
- Supported accounts: personal accounts only; native/mobile desktop redirect `http://localhost/callback`. Code uses an ephemeral localhost port.
- Publisher domain displayed: syntaxdevteam.pl. Publisher verification section offers adding an MPN ID and shows no publisher display name: **verified-publisher badge is not established**. This differs from domain display and Mojang allowlisting.
- Homepage, terms and privacy fields match the four documented application pages. Existing internal notes already describe Microsoft-only operation; stale revised-review-pending wording was updated to two unsuccessful requests, third-request preparation and live acceptance outstanding. Read-back confirmed the saved note.
- API permissions pane contains no configured entries; the code requests XboxLive.signin/offline_access dynamically. This observation does not establish successful service consent. No permissions, account audience, credentials or security settings were expanded.
- The full manifest was read using the editor's ordinary Select All/Copy, and only relevant metadata was inspected. It confirms PersonalMicrosoftAccount, the expected app ID/name, independent entitled-player description, native localhost redirect, marketing/support/privacy/terms URLs, requiredResourceAccess=[], disabledByMicrosoftStatus=null and isFallbackPublicClient=null. None of these fields establishes Minecraft allowlisting.
- The credential inventory contains one registered password credential and zero key credentials. No secret value was read, printed or used; the launcher code embeds/uses no client secret. The maintainer should decide whether the registered credential serves another integration before any removal. No credential was changed. The displayed Entra icon visually matches the blue gateway; byte-for-byte equivalence was not tested.
- Public support email is in the saved notes. The form says it cross-references contact with Azure: the maintainer must confirm the chosen submission email is verifiably associated with the app/owner before sending. No directory owner/contact was added.

## Maintainer review and remaining acceptance

1. Confirm contact/address identity and the actual acknowledgement of the complete EULA and Usage Guidelines. An agent must not tick that declaration on the maintainer's behalf.
2. Review exact final SHA and CI/artifact manifest in the accompanying local review packet. Use that immutable source/build, never an older dual-mode preview or a moving latest link. Hosted CI status is checked after push, not inferred from #200.
3. Review generated-art/reference rights, actual submission contact; formal publisher verification, signing/notarization and final native trust acceptance remain open. Neither MPN nor ID@Xbox is asserted to be a published mandatory AppID gate.
4. Keep live owned/unentitled-account, subscription/child-account, refresh rotation/revocation, restart and online-mode gameplay tests marked NOT RUN until permitted and actually executed. No other application's Client ID may be substituted.
5. Only after separate explicit approval send one revised request using the prepared text, explaining the history honestly. No guarantee of allowlisting or prediction of the rejection reason is possible.

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

## Local verification for this preparation

- `./gradlew --no-daemon test build :launcher-desktop:createDistributable --rerun-tasks`: PASS, all 20 tasks executed, 2m16s; 284 JUnit cases, 278 passed, six opt-in network skips, zero failures/errors. No live account tests.
- `python3 -m unittest discover -s scripts -p 'test_*.py'`: PASS, 28 cases.
- `python3 -m unittest discover -s tests -p 'test_*.py'`: PASS, six Build Explorer cases.
- `bash -n packaging/smoke-linux.sh` and macOS equivalent: PASS. Native install/uninstall lifecycle is reserved for commit-specific hosted jobs; local system packages were not installed/removed.
- Final source SHA, hosted result, immutable build URLs/checksums and the fully pinned form draft are assembled after publication in the local review packet. This avoids making a document claim its own not-yet-created commit hash.
