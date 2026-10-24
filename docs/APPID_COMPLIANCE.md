# Minecraft Services AppID — current compliance and verification

**Status: implementation changes submitted to GitHub; AppID allowlist approval NOT confirmed.**
This document describes the intended Microsoft-only game launch policy, not a Mojang certification.
Code review, automated fixtures, and successful packaging are not substitutes for an approved AppID or live Microsoft account tests.

## Required launch invariant (all OSes, all instances, all server configurations)

- **Only** Microsoft accounts with a verified Minecraft: Java Edition entitlement and Java profile can launch Minecraft through SyntaxLauncher.
- Login takes place in the system browser using Microsoft OAuth authorization-code with S256 PKCE and random `state`, then Xbox user authentication, XSTS, Minecraft Services token, `/entitlements/mcstore` (Java entitlement), and `/minecraft/profile` (Java UUID/name).
- Every Play requires a fresh refresh/exchange and ownership/profile verification; sign-in alone, the presence of a cached username, and an Xbox token alone do not prove Java ownership.
- The UI does not offer local-only player creation or selection. Missing ID, expired/failed refresh, unverified identity, missing entitlement, invalid profile, zero/blank access token or missing client ID block launch **before game preparation**.
- The core coordinator and launch-plan builder also reject non-Microsoft identities and invalid credentials, independently of UI state. No automatic identity substitution or session fallback is permitted.
- Historic identity rows remain on disk for backup only. They are never exposed as playable accounts; worlds, instances, resource packs, mods, saved settings and their directories must never be deleted as an authentication migration side effect.
- Verification code must not print access/refresh tokens, full command lines, credentials, private support logs or user data to CI artifacts.

## Evidence and regression gates

| Gate | How to verify | Status |
| --- | --- | --- |
| No unauthenticated launch entry point | Check account picker, session Play, core coordinator and LaunchPlanBuilder | Code review and tests; hosted CI required |
| Java entitlement and profile | Xbox/XSTS -> Minecraft token -> Java entitlement -> profile | Automated fixtures implemented; real AppID acceptance **pending** |
| Existing player/world data retention | Load older account inventory, reject legacy launch, check unchanged saved instance worlds | Regression fixtures, hosted CI required |
| Native vault & OAuth PKCE | Windows DPAPI, macOS Keychain, Linux Secret Service; system browser | Native and real-account acceptance **pending** |
| Live purchased-account gameplay | Sign in, restart, token rotation, signed-in online-mode server, Java profile | **Blocked pending Mojang AppID approval** |
| Packaging/release | GitHub Actions Windows/Linux/macOS; no credential leakage | See commit-specific CI run, not inferred from previous runs |
| Public pages & Entra metadata | Recheck and synchronize displayed product scope, owner/contact, privacy and terms | **External verification required** |
| Brand compliance | Clear independent publisher identity, contact and prominent non-affiliation disclaimer | Confirm on actual UI, store/listings and landing pages |

## Remaining external and product risks

1. Mojang reviews AppID submissions at its discretion and does not provide a detailed rejection reason. Passing the above gates **cannot guarantee approval**.
2. The project website, privacy/terms/support pages and Microsoft Entra registration must be reviewed against the new authenticated-only build. Updating repository Markdown does **not** automatically update deployed web pages or Entra metadata.
3. The public Client ID is an identifier, not a secret or a substitute for allowlisting. Do not rotate it to evade review, use another launcher's ID, or claim Mojang endorsement.
4. Review domain ownership/publisher identity separately from Microsoft Entra publisher verification. Neither verification is assumed complete.
5. Mods and modpacks may execute arbitrary third-party code: do not redistribute proprietary Minecraft client files or bundle unauthorized game assets. Review download origins and licenses.
6. Verify official Mojang/Microsoft terms and the live AppID form before each new application, especially if their conditions change.

## Official materials

- AppID Review: https://aka.ms/mce-reviewappid
- Minecraft Usage Guidelines: https://www.minecraft.net/en-us/usage-guidelines
- Minecraft EULA: https://www.minecraft.net/en-us/eula
- Microsoft Entra terms/privacy registration: https://learn.microsoft.com/en-us/entra/identity-platform/howto-add-terms-of-service-privacy-statement
- Technical implementation and submission summary: [MICROSOFT_APP.md](MICROSOFT_APP.md)
- Review checklist and unresolved evidence: [APP_REVIEW_2026-10.md](APP_REVIEW_2026-10.md)

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

## Continuation audit — 24 October 2026

- Prerequisite settings CI: [40078116601](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/40078116601), exact source `5dfb7df12e29f8d09b613aa903d84652ed1b89ca`, completed **success**. Authenticated-only implementation is separate commit [06d292f](https://github.com/SyntaxDevTeam/SyntaxLauncher/commit/06d292fa302b80925e4e1690bf37062aea0f0d0e); subsequent test corrections remain in Git history.
- Removed the XSTS user-hash fallback. Xbox/XSTS require successful HTTP status, nonempty/nonzero tokens and matching explicit user identities. OAuth rejects unsuccessful responses and empty/zero access tokens. Minecraft token lifetime is mandatory and must exceed the refresh safety margin before entitlement/profile lookup.
- Launch credentials carry the verified expiry time. Coordinator rejects expired input, the plan builder rechecks after preparation, and coordinator checks once more immediately before process creation. No cached or synthetic lifetime is substituted.
- Removed unused local-player translation keys and corrected live settings/sign-in/diagnostic guidance in EN/PL/DE to require Microsoft Java accounts. Translation and rendered player-manager regressions prevent advertising unauthenticated play.
- Regressions stop at each rejected exchange, including denied Java profile, invalid profile data, absent ownership and rejected refresh despite an otherwise valid cached session. AccountManager writes only Microsoft identities; its historical local-row decoder remains read-only preservation compatibility.
- Reviewed the official EULA and Usage Guidelines on this date: independent tool branding and no unauthorized distribution remain release-review concerns. This bounded repository audit is not a legal certification or a full repository security scan.
- The AppID form redirected to Microsoft Forms and could not be read by the audit browser. The four linked public product/privacy/terms/support pages were also inaccessible through that browser; their content is **not verified**, and no update to them is claimed. Entra registration metadata and native OS vault behavior remain externally unverified.
- Current architecture, UI design instructions, developer guide, milestones, roadmap and acceptance checklists now describe Microsoft-only gameplay. Versioned release notes are explicitly historical; application chronology and the append-only worklog remain intact. No history rewrite is performed.

Local final verification: full tests/build/Linux distributable passed (282 cases: 277 passed, five opt-in network skips, zero failures/errors); 25 release-tool tests passed. Hosted verification remains commit-specific.

Approval, live entitled-account sign-in/refresh/gameplay, public site/Entra consistency, native vault/consent and signing acceptance remain open. Passing this revision's automated CI cannot guarantee Mojang approval.
