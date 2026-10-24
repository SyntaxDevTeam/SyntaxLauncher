# SyntaxLauncher — release readiness (23 October 2026)

This is an **evidence register**, not a claim that every historic milestone is finished. The authoritative acceptance checkboxes remain in [ROADMAP.md](ROADMAP.md). Recreate their numerical summary with `python3 scripts/release_readiness.py`; GitHub Actions also publishes the audit in its job summary. A clean CI result alone does not prove Minecraft gameplay, Microsoft approval or native update behavior.

## Version and publication distinction

- Current canonical product version: `0.9.3-DEV` (Stable `0.9.2`); historical baseline `0.7.1` in root `gradle.properties`. The public GitHub Releases API has a normal `v0.7.1` release from 22 October 2026.
- Roadmap v0.9 and v0.10: accepted **within the bounded code/installation support matrices**, after automated checks, the documented private v0.9 gameplay confirmation and the maintainer's report that the joint v0.9/v0.10 testing session has ended. The user did not specify the platforms and exact combinations used for this final session.
- v0.11 diagnostics/repair implementation and automated CI are complete within TESTING_V0.11.md; wider native/gameplay quality gates remain open.
- v0.12: substantial automatic updater, channel, packaging and cache-integrity code already exists; signing, old→new native update, interruption and user-data preservation testing remain outstanding.
- v1.0: **not approved for general stable release** until all independent P0 and release quality gates are satisfied.

**Verification update (23 October):** GitHub Actions [39901800185](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39901800185) **passed** core/desktop Gradle tests, official NeoForge/Forge installer checks, Linux/Windows/macOS app-image and update-bundle generation, all six-asset DEV publication and Build Explorer upload. The public DEV tag is [dev-180.1-v0.7.1](https://github.com/SyntaxDevTeam/SyntaxLauncher/releases/tag/dev-180.1-v0.7.1), published with six assets. An earlier [run 39900923246](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39900923246) failed DEV tag creation with HTTP 403; that was a historical incident, **not an unresolved blocker** for the subsequent verified DEV publication. Normal Stable remains v0.7.1. This evidence is not equivalent to live Minecraft game startup, native GUI/usability certification or Microsoft entitlement approval.

## Release-blocking evidence

| Gate | Current evidence | What remains |
| --- | --- | --- |
| Minecraft premium / AppID | Microsoft integration has deterministic coverage, native OS credential support, Entra/AppID submission records | Mojang allowlist approval and actual licensed Java Edition login+launch |
| Native distribution | GitHub CI produces Linux/Windows/macOS app images and six update artifacts | Clean-machine installation/first run, keyboard/DPI/focus, signing or notarization policy |
| Data continuity | Instance UUID isolation, atomic rollback/staging and updater fallback fixtures | Real old→new update and interruption tests across supported native targets |
| Launch/gameplay | v0.9 private graphics confirmation, v0.10 full cold-install and original export smoke | Representative documented native Vanilla/Fabric/Quilt/NeoForge/Forge game boots and Modrinth end-to-end logs |
| Diagnostics | Read-only log classification + explicit shared preparation repair now implemented | v0.11 CI, realistic faulty game/Java/mod cases, safe repair and privacy validation |
| Security and privacy | Checksummed downloads, path safety, sensitive process output handling, native vault primitives | Final secrets/logging threat review, signing/trust decision, dependency/license audit |

## Policy for closing previous phases

A milestone may be declared **feature implemented** when implementation and deterministic tests pass. Historical unchecked tasks that explicitly require real Minecraft boot, specific OS acceptance, Mojang approval, edge-case recovery or a future compatibility expansion must not be checked just because a newer stage is implemented. When test evidence exists, record exact OS/game/loader/build and scenario in the corresponding `TESTING_V*.md` file before closing the checkbox. Unsupported historical Forge V1 or arbitrary OptiFine combinations may remain documented exclusions rather than becoming mandatory unbounded support claims.

## Final sequence

1. Verify v0.11 diagnostics in current GitHub CI and exercise representative game errors; complete accurate Java/mod inventory and safe repair coverage.
2. Stabilize v0.12: native updater/channel switching, user-data recovery, installation/uninstall behavior, signed/notarized release distribution and release automation.
3. Obtain Minecraft Services AppID authorization; validate premium Java entitlement and session recovery in real gameplay.
4. Publish support matrices, privacy/license notices, migration and uninstall instructions; run exact clean-install and upgrade tests on Windows/Linux/macOS.
5. Run `python3 scripts/release_readiness.py --enforce v1.0`. Only after all quality gates are verified, update the single canonical SemVer and let tested release automation publish the stable v1.0.

**Do not ship by simply editing a version number.** A normal `launcherVersion` increase on `main` can initiate automatic GitHub Stable publication; only do that as an explicit release decision backed by green CI and the intended acceptance scope.

## Current implementation extension — 23 October 2026

The maintainer deferred premium/Mojang acceptance while requesting v0.11/v0.12 implementation before final corrections. Current health diagnostics extends the published classifier with managed-file integrity, Java/main-class/classpath and mod inventory, safe report copy, guarded/cancellable repair and EN/PL/DE compact-screen tests. v0.12 adds rotating redacted launcher logs, settings/log/storage access, disk admission, dependency locks, installation/removal/retention policy and candidate release notes. See TESTING_V0.11.md and TESTING_V0.12.md for exact scope. The earlier CI baseline above predates these extensions; current local and hosted outcomes are recorded in WORKLOG/chat. Final native installs/upgrades, signing/notarization, focus/DPI/gameplay and deferred premium acceptance remain independent gates.

## Stable 0.9.2 — explicit maintainer release request

The maintainer explicitly approved Stable 0.9.2 on 23 October 2026 after the embedded profile/settings changes were prepared and locally verified. The canonical version change is retained from the maintainer's local edit. This is a bounded pre-1.0 release, not a declaration that all v0.11/v0.12 native or v1.0 gates are complete. Stable publication must be verified after the version-bump commit passes all three package jobs. Final commit/CI/release URLs and six-asset availability are reported in chat.

New scope: selected-game RAM settings with Save/Close, one header profile entry, version-before-game-type sidebar order, embedded profile inventory and wizard, filtered complete packs, inline extras selection and exact community links. Microsoft approval remains deferred; no new premium/native gameplay/signing claim is made.

Verified publication: [Stable v0.9.2](https://github.com/SyntaxDevTeam/SyntaxLauncher/releases/tag/v0.9.2), commit `e5b4daaf2d6208316b452a11e7eb478b9c03a18f`, [successful workflow 40005510889](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/40005510889). All six non-empty platform assets are uploaded; the release is public and not prerelease. The maintainer advanced the working development version to 0.9.3-DEV during publication; Stable remains 0.9.2.

## Native installer preview acceptance — 24 October 2026

[DEV 188.1 / 0.9.3-DEV](https://github.com/SyntaxDevTeam/SyntaxLauncher/releases/tag/dev-188.1-v0.9.3-DEV) is public from commit `cfcbe79cae2bfb44e1c1e05707a004af1d0148f5`. [Workflow 40013941601](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/40013941601) passed core/desktop verification, all three platform jobs, complete preview publication and Build Explorer publication. The release contains 12 non-empty assets: five native installers, their SHA-256 inventory, three portable archives and three updater ZIPs. The downloaded five-entry inventory agrees with GitHub's public SHA-256 asset digests. Stable remains 0.9.2.

- Windows x64: WiX 7.0.0 MSI installation, full payload hashes, Start menu registration, same-core duplicate rejection, repair and app-only removal with data preservation passed.
- macOS ARM64: DMG verification, mounting, Applications link, bundle copy/identity and temporary app removal passed.
- Linux x64: DEB install, registered/validated desktop entry, Xvfb startup and app-only removal passed; Fedora 44 RPM dependency-resolved installation/removal preserved data; AppImage extraction and Xvfb startup passed.

This closes preparation and automated lifecycle/publication acceptance. Interactive clean-machine GUI/trust checks, actual prior-version upgrades, RPM graphical startup, additional distributions and signing/notarization remain open. The later UI requests concerning the portable EXE icon, footer alignment, profile deletion and Vanilla packs entry are separate follow-up work and are not covered by this installer acceptance.

## Sequential closure — 24 October 2026

The historical paragraphs above retain their dated evidence. Current implementation closure is in [MILESTONE_CLOSURE.md](MILESTONE_CLOSURE.md), supported targets and actual game reports in [SUPPORT_MATRIX.md](SUPPORT_MATRIX.md), and publisher prerequisites in [SIGNING.md](SIGNING.md). Old build-only pending entries are reconciled against code and tests; real manual gates remain open. The maintainer confirms Linux/Windows 10 Fabric start and world save for Minecraft 1.21.11, 26.2 and 26.3, without exact loader versions. Official newer Forge installations passed without claiming graphical startup. CI now runs packaged-runtime Mojang HTTPS on each target and rejects plain Stable 1.x or later while the final checklist remains open. No new Stable is authorized.
