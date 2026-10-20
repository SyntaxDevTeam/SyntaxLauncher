# v0.5 — Local Mod Manager verification

The maintainer deferred manual v0.4 acceptance on 20 October 2026. This **does not mean Fabric boot was tested**.

## Automated
- [ ] GitHub Actions Gradle core/desktop tests and build pass.
- [ ] Instance-scoped import preserves source and other profiles.
- [ ] Enable/disable is a reversible rename with identical SHA-256.
- [ ] Removal moves a mod to game/.syntaxlauncher-mod-trash.
- [ ] Missing dependencies, duplicate active IDs and disabled dependencies are reported.
- [ ] Invalid archives and filename traversal are rejected.

## Manual — not yet executed
- [ ] Create or select a Fabric profile; open Instances → Mods.
- [ ] Import a local, trusted Fabric JAR with appropriate Minecraft/loader compatibility.
- [ ] Confirm JAR appears under only that instance's game/mods directory.
- [ ] Confirm enabling/disabling/removing updates the list, disk and UI without blocking.
- [ ] Launch a real Fabric client with the mod and verify it loads.
- [ ] Launch Vanilla and a second Fabric profile; neither inherits the first profile's mods.
- [ ] Test duplicate filenames, duplicate IDs, missing dependencies, malformed JAR and a mod requiring a newer Fabric loader.
- [ ] Try changing mods while the game runs and after detached launcher exit; mutations must be rejected.
- [ ] Verify Windows/macOS/Linux dialogs, small displays and file chooser.

**Scope:** Fabric metadata is advisory only. Version-range evaluation, nested-JAR diagnostics,
repository-driven dependency resolution, automatic downloads, update matching and dedicated Quilt/Forge
metadata readers are deferred; do not assume a missing warning implies compatibility.
A local JAR can execute arbitrary code once Minecraft loads it, so use trusted sources only.
