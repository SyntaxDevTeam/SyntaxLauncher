# v0.7 — NeoForge installation acceptance

## Stage 1: official installer acquisition and read-only preflight

- [ ] Official NeoForge Maven metadata and compatible-version mapping verified against currently published releases.
- [ ] SHA-256 sidecar fetch, checksum parsing, verified JAR download and cache reuse validated in Gradle tests.
- [ ] Tampered archive, missing/bad checksum, unsupported installer layout and wrong Minecraft target rejected.
- [ ] Windows/Linux/macOS cache paths and interrupted downloads verified.
- [ ] Real official NeoForge installer checked in preflight (read-only, no processor execution).

## Stage 2: processors, libraries and generated outputs

- [ ] Resolve installer library coordinates and metadata-defined trusted repositories, with file integrity checks.
- [ ] Expand client-side installer data placeholders in a confined staging directory; reject unsafe paths.
- [ ] Execute required processors using managed Java with bounded resource/time limits and captured diagnostics.
- [ ] Check declared outputs, reject incomplete/corrupt generated artifacts and publish only on success.
- [ ] Merge generated NeoForge version metadata with Mojang version for common launch pipeline.
- [ ] Reuse the shared instance activity lock and preserve every existing world/mod when preparation fails.
- [ ] Add deterministic successful/error/rollback MockEngine and process fixtures.

## Stage 3: desktop and actual-game acceptance

- [ ] Offer NeoForge only after its end-to-end installer/launch path is operational.
- [ ] Select compatible versions in instance creation/editing, without breaking Vanilla/Fabric/Quilt.
- [ ] Filter Modrinth by NeoForge, and recognize NeoForge mod metadata in local manager.
- [ ] Test real Minecraft startup using NeoForge and at least one compatible client mod.
- [ ] Test switching between Vanilla, Fabric, Quilt and NeoForge independent profiles.
- [ ] Pass core/desktop automated suites, native packaging and actual Windows/Linux/macOS checks.

**Current scope (21 October 2026):** Stage 1 code is introduced as a core-only API.
It never executes downloaded installers and never exposes an unusable NeoForge
selection in the UI. GitHub CI and real-world acceptance must be recorded before
checking their respective boxes. Stage 2 and Stage 3 are not implemented.
