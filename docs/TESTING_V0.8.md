# v0.8 — Quilt and Forge acceptance

## Maintainer confirmation — 20 October 2026

The maintainer stated in the project chat that Fabric and Quilt tests had been performed and could be checked off. This is recorded as real loader startup acceptance. No exact OS, Minecraft/loader versions or log links were supplied; none are inferred.

- [x] Quilt discovery/profile integration starts a real Minecraft client through the common coordinator — maintainer-confirmed.
- [x] Quilt profile merge and unavailable-version rejection — deterministic core regression passes.
- [x] Local `quilt.mod.json` identity/version/dependency parsing — deterministic regression passes.
- [ ] Quilt mod import/Modrinth installation/gameplay and loader switching acceptance.
- [ ] Quilt native Windows/Linux/macOS and representative version matrix recorded.
- [ ] Forge and NeoForge processor execution/generated artifacts implemented and tested.
- [ ] Real Forge and NeoForge startup acceptance.
- [ ] Legacy Minecraft/Java compatibility policy and representative historical Forge versions tested.

v0.8 stays open while Forge and the remaining shared quality gates are incomplete. Successful Quilt testing does not imply Forge/NeoForge support.
