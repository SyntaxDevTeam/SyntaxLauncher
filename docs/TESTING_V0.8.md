# v0.8 — Forge and Quilt acceptance

## Forge v0.8 development (21 October 2026)

- [x] Official Forge Maven version discovery adapter and strict modern Forge V2 SHA-1 installer download are implemented.
- [x] Forge V2 profile inspection and checksum/corruption/legacy V1 regressions passed in GitHub Actions 39560642818.
- [x] Official Forge 1.21.1/52.1.20 V2 Maven installer and processor smoke passed in CI 39560642818. This tests installation, not graphical Minecraft startup.
- [ ] Forge/NeoForge TOML mandatory/required/incompatible dependencies, server-only filtering, cross-loader warnings and dual-manifest preference implemented; CI regression tests submitted, gameplay acceptance pending.
- [ ] Historical Minecraft/Forge version matrix and required Java/argument behavior documented and tested.
- [ ] Desktop Forge test selection is now available for **only** Minecraft 1.21.1 / Forge 52.1.20 with an explicit in-app compatibility warning; real Minecraft/mod startup acceptance pending.
- [ ] Portable builds, launcher/regression tests and actual native-platform interaction acceptance passed.

Forge V1 (legacy) is deliberately rejected; this phase never claims universal historical Forge support. Quilt Meta integration and maintainer-confirmed startup remain valid independently.

## Maintainer confirmation — 20 October 2026

The maintainer stated in the project chat that Fabric and Quilt tests had been performed and could be checked off. This is recorded as real loader startup acceptance. No exact OS, Minecraft/loader versions or log links were supplied; none are inferred.

- [x] Quilt discovery/profile integration starts a real Minecraft client through the common coordinator — maintainer-confirmed.
- [x] Quilt profile merge and unavailable-version rejection — deterministic core regression passes.
- [x] Local `quilt.mod.json` identity/version/dependency parsing — deterministic regression passes.
- [ ] Quilt mod import/Modrinth installation/gameplay and loader switching acceptance.
- [ ] Quilt native Windows/Linux/macOS and representative version matrix recorded.
- [x] NeoForge and Forge V2 processor-generated artifact installation passed their official Maven smoke tests; real Minecraft/gameplay acceptance remains separate.
- [ ] Real Forge and NeoForge startup acceptance.
- [ ] Legacy Minecraft/Java compatibility policy and representative historical Forge versions tested.

v0.8 stays open while Forge and the remaining shared quality gates are incomplete. Successful Quilt testing does not imply Forge/NeoForge support.

## Forge V2 hosted evidence — 21 October 2026

- **PASS** [GitHub Actions 39560642818](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39560642818): official Minecraft 1.21.1 client and Forge 52.1.20 installer metadata, SHA-1 sidecar, V2 processors, published loader library checks; NeoForge regression also passed.
- Forge's installer JSON uses empty artifact URLs for locally generated Maven objects. The V2 resolver now treats blank URLs as absent while rejecting arbitrary remote endpoints. The synthetic processor fixture also covers this case.
- **OPEN:** game UI actually starting with Forge, Modrinth mod installation and gameplay, cross-platform launcher window compatibility, crashes/recovery, and expanded historical Java/Forge versions.

## Forge/NeoForge mod metadata diagnostics — 21 October 2026

The mod inventory reads only bounded, unambiguous TOML scalar fields and array-of-tables sections. Forge `mandatory=true` and NeoForge `type=required` produce missing-dependency warnings; NeoForge `type=incompatible` produces conflicts with enabled IDs. Optional, discouraged, and server-only requirements do not produce client missing-dependency errors. Version ranges are exposed as metadata but not yet semantically solved against installed version strings. The scanner explicitly does **not** claim complete TOML parsing (multiline strings, escaped syntax and nested constraint expressions need a proper library). When both a Fabric and Forge manifest are present, the matching descriptor is prioritized according to the instance loader. A mismatched manifest produces a warning rather than being considered automatically compatible; Forge metadata selected under NeoForge triggers a softer compatibility warning. Tests for these cases plus enabled-mod toggling and duplicate IDs are included in the core regression suite. 
