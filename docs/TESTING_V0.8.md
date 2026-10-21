# v0.8 — Forge and Quilt acceptance

## Forge v0.8 development (21 October 2026)

- [x] Official Forge Maven version discovery adapter and strict modern Forge V2 SHA-1 installer download are implemented.
- [ ] Forge profile read-only inspection and checksum/corruption/legacy V1 regressions — code added, CI verification pending.
- [ ] Shared V2 processor engine works with NeoForge/Forge; official Forge 1.21.1/52.1.20 live installation smoke and launch acceptance pending.
- [ ] Forge `META-INF/mods.toml` identity import, scoped installation, disable/remove and Modrinth operations — code added, CI acceptance pending.
- [ ] Historical Minecraft/Forge version matrix and required Java/argument behavior documented and tested.
- [ ] Desktop Forge selection exposed only after successful representative install, mod and graphical game startup.
- [ ] Portable builds, launcher/regression tests and actual native-platform interaction acceptance passed.

Forge V1 (legacy) is deliberately rejected; this phase never claims universal historical Forge support. Quilt Meta integration and maintainer-confirmed startup remain valid independently.

## Maintainer confirmation — 20 October 2026

The maintainer stated in the project chat that Fabric and Quilt tests had been performed and could be checked off. This is recorded as real loader startup acceptance. No exact OS, Minecraft/loader versions or log links were supplied; none are inferred.

- [x] Quilt discovery/profile integration starts a real Minecraft client through the common coordinator — maintainer-confirmed.
- [x] Quilt profile merge and unavailable-version rejection — deterministic core regression passes.
- [x] Local `quilt.mod.json` identity/version/dependency parsing — deterministic regression passes.
- [ ] Quilt mod import/Modrinth installation/gameplay and loader switching acceptance.
- [ ] Quilt native Windows/Linux/macOS and representative version matrix recorded.
- [ ] NeoForge generated artifact installation validated in v0.7; Forge V2 processor adapter implemented and awaits official live/game test.
- [ ] Real Forge and NeoForge startup acceptance.
- [ ] Legacy Minecraft/Java compatibility policy and representative historical Forge versions tested.

v0.8 stays open while Forge and the remaining shared quality gates are incomplete. Successful Quilt testing does not imply Forge/NeoForge support.
