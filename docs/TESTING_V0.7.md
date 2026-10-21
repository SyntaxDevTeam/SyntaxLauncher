# v0.7 — NeoForge installation acceptance

## Stage 1: official installer acquisition and read-only preflight

- [ ] Official NeoForge Maven metadata and compatible-version mapping verified against currently published releases.
- [x] Synthetic SHA-256 sidecar fetch, checksum parsing, verified JAR download and cache reuse validated in Gradle core tests.
- [ ] Tampered archive, missing/bad checksum, unsupported installer layout and wrong Minecraft target rejected.
- [ ] Windows/Linux/macOS cache paths and interrupted downloads verified.
- [ ] Real official NeoForge installer checked in preflight (read-only, no processor execution).

## Stage 2: processors, libraries and generated outputs

- [ ] 🚧 Processor library coordinate resolution and allow-listed Maven downloads implemented; official source and artifact matrix acceptance open.
- [ ] Expand client-side installer data placeholders in a confined staging directory; reject unsafe paths.
- [ ] 🚧 Execute client processors in a child JVM selected from the resolved runtime, bounded by a time limit; cancellation/log persistence and official process acceptance open.
- [ ] Check declared outputs, reject incomplete/corrupt generated artifacts and publish only on success.
- [ ] 🚧 Overlay generated NeoForge metadata on the base client and route through common launch pipeline; actual Minecraft bootstrap/launch acceptance open.
- [ ] Reuse the shared instance activity lock and preserve every existing world/mod when preparation fails.
- [ ] 🚧 Added deterministic successful Java processor and mismatch fixtures; rollback, partial-publish and cancellation cases open.

## Stage 3: desktop and actual-game acceptance

- [ ] 🚧 NeoForge selector is visible; do not label this path production-ready until full official smoke and gameplay pass.
- [ ] Select compatible versions in instance creation/editing, without breaking Vanilla/Fabric/Quilt.
- [ ] Filter Modrinth by NeoForge, and recognize NeoForge mod metadata in local manager.
- [ ] Test real Minecraft startup using NeoForge and at least one compatible client mod.
- [ ] Test switching between Vanilla, Fabric, Quilt and NeoForge independent profiles.
- [ ] Pass core/desktop automated suites, native packaging and actual Windows/Linux/macOS checks.

**Current scope (21 October 2026):** Stage 1 code is introduced as a core-only API.
It never executes downloaded installers and never exposes an unusable NeoForge
selection in the UI. GitHub CI and real-world acceptance must be recorded before
checking their respective boxes. Stage 2 and Stage 3 are not implemented.

## Hosted verification record

- The core has both synthetic processor tests and an opt-in official NeoForge 21.1.213 / Minecraft 1.21.1 Maven integration test.
- GitHub Actions runs the real integration test on main before creating native packages. A passing test demonstrates installation, not a real graphical Minecraft startup.
- Processor code is downloaded executable third-party Java; the staged filesystem provides data isolation, **not an operating-system sandbox**.
- No stable v0.7 release is authorized solely by passing unit tests or package builds. Real game launch, compatible mods and supported platform matrix must still be accepted.
