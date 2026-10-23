# SyntaxLauncher — release readiness (23 October 2026)

This is an **evidence register**, not a claim that every historic milestone is finished. The authoritative acceptance checkboxes remain in [ROADMAP.md](ROADMAP.md). Recreate their numerical summary with `python3 scripts/release_readiness.py`; GitHub Actions also publishes the audit in its job summary. A clean CI result alone does not prove Minecraft gameplay, Microsoft approval or native update behavior.

## Version and publication distinction

- Current documented canonical product version before the next intentional release bump: `0.7.1` in root `gradle.properties`. The public GitHub Releases API has a normal `v0.7.1` release from 22 October 2026.
- Roadmap v0.9 and v0.10: accepted **within the bounded code/installation support matrices**, after automated checks, the documented private v0.9 gameplay confirmation and the maintainer's report that the joint v0.9/v0.10 testing session has ended. The user did not specify the platforms and exact combinations used for this final session.
- Development v0.11: local crash-diagnostic classification and explicit managed-file verification are now introduced, pending their own CI and native/manual quality gates.
- v0.12: substantial automatic updater, channel, packaging and cache-integrity code already exists; signing, old→new native update, interruption and user-data preservation testing remain outstanding.
- v1.0: **not approved for general stable release** until all independent P0 and release quality gates are satisfied.

**Verification update (23 October):** GitHub Actions [39901800185](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39901800185) passed core/desktop compile and tests plus official installer smoke. Cross-platform packaging and publisher jobs of the current commit were not yet complete at this checkpoint. A prior [run 39900923246](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39900923246) produced all three platform packages and Build Explorer upload, but the DEV tag publishing step failed with HTTP 403 for the repository publication token. This is an authorization problem, not evidence of a packaging failure. The repository owner must fix the `SYNTAXLAUNCHER_RELEASE_TOKEN` Contents write permission/organization approval before preview publications can succeed.

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
