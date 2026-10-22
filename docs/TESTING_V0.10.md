# v0.10 — Modrinth modpack acceptance (combined with v0.9)

## Automated tests (awaiting published CI results)

- [ ] Strict format-1 parser for exact Minecraft version and a single known loader.
- [ ] Reject future formats, path traversal, invalid Windows names, duplicate output and excessive ZIP entries.
- [ ] Download only required client files, keep optional files opt-in and omit server-only members.
- [ ] Verify required downloads against expected byte count, SHA-1 and SHA-512.
- [ ] Apply checked overrides and client-overrides into private game staging.
- [ ] Checksum failures, cancelled downloads and corrupt overrides publish no instance.
- [ ] Existing Vanilla/modded/graphics instances and user saves remain unchanged.
- [ ] Full core+desktop regression tests and portable packaging for Windows/Linux/macOS.

## Joint maintainer session: v0.9 and v0.10

- [ ] v0.9 — Texture pack installation, automatic selection and activation in Minecraft.
- [ ] v0.9 — Shader installation, automatic Iris dependencies and real game startup.
- [ ] v0.9 — OptiFine startup for documented Vanilla 1.21.1 / HD U J1.
- [ ] v0.10 — Use My games → Import Modrinth modpack (.mrpack); install required mods/overrides and start the new game.
- [ ] v0.10 — Confirm optional/server-only files were not silently installed.
- [ ] v0.10 — Verify the original game and its mods/worlds/options stayed unchanged.
- [ ] v0.10 — Reject corrupt/unsupported modpacks; test cancellation and recovery.

**Known limits:** The normal child-first Modrinth modpack browser/one-click installer, optional-file UI, repeat-install UX, export and wider loader/OS matrix are still open. No new normal Release is justified by core code alone.
