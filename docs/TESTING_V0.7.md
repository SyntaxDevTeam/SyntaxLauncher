# v0.7 — NeoForge installation acceptance

## Stage 1: official installer acquisition and read-only preflight

- [ ] Official NeoForge Maven metadata and compatible-version mapping verified against currently published releases.
- [x] Synthetic SHA-256 sidecar fetch, checksum parsing, verified JAR download and cache reuse validated in Gradle core tests.
- [ ] Tampered archive, missing/bad checksum, unsupported installer layout and wrong Minecraft target rejected.
- [ ] Windows/Linux/macOS cache paths and interrupted downloads verified.
- [x] Official NeoForge 21.1.213 installer checked during execution smoke; V2 preflight was part of the verified pipeline.

## Stage 2: processors, libraries and generated outputs

- [ ] 🚧 Processor library coordinate resolution and allow-listed Maven downloads implemented; official source and artifact matrix acceptance open.
- [ ] Expand client-side installer data placeholders in a confined staging directory; reject unsafe paths.
- [x] Run official 21.1.213 client processors in a child JVM via resolved runtime; bounded execution and synthetic fixture pass. Full cancellation evidence remains open.
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
- [ ] Core/desktop CI and Windows/Linux/macOS *packaging* passed; interactive cross-platform Minecraft gameplay matrix not recorded.

**Current scope (21 October 2026):** The V2 engine executes child-Java processors in staging and the desktop exposes NeoForge instance selection. Official NeoForge 21.1.213 installation smoke, deterministic core/desktop checks, and three-platform packaging passed in CI 39548448140. This does not by itself demonstrate graphical Minecraft boot or all failure scenarios.

## Hosted verification record

- The core has both synthetic processor tests and an opt-in official NeoForge 21.1.213 / Minecraft 1.21.1 Maven integration test.
- GitHub Actions runs the real integration test on main before creating native packages. A passing test demonstrates installation, not a real graphical Minecraft startup.
- Processor code is downloaded executable third-party Java; the staged filesystem provides data isolation, **not an operating-system sandbox**.
- No stable v0.7 release is authorized solely by passing unit tests or package builds. Real game launch, compatible mods and supported platform matrix must still be accepted.

## Maintainer acceptance — 21 October 2026

The maintainer stated in the project conversation: "Testy zakończone przejdź do następnego etapu" (tests finished; proceed to the next stage). This is recorded as an acceptance to move development to v0.8 **without** inventing specific Minecraft logs, loaded mods, operating systems or version pairs. The remaining unchecked manual regression matrix is retained; v0.7 release is intentionally scoped.
