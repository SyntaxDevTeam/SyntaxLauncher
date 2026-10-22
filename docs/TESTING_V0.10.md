# v0.10 — Modrinth modpacks

## Accepted scope — 22 October 2026

The implemented scope is client `.mrpack` installation from the catalog or secondary local import into a **new game**, with required files, game/loader/Java preparation and original-definition export. The v0.10 installation/isolation/failure criteria are satisfied by deterministic tests and the real cold-install evidence below. This is not a certification of graphical gameplay or all native platforms; those checks stay under v1.0.

Normal flow: home **Modpacks** (also available without a saved game), or **Choose extras → Modpacks** → choose a pack → **Install** → the finished new game is selected → **Play**. The child never chooses Minecraft/loader/dependency versions or manages archive paths. The shared catalog has search/popularity, loading/empty/error/retry, installation progress and cancellation. **More → Import modpack** is secondary.

## Compatibility and file policy

The format-1 Minecraft index specifies exact requirements. Existing Mojang and Vanilla/Fabric/Quilt/NeoForge/Forge services check them; Forge keeps its existing bounded 1.21.1 / 52.1.20 matrix. No silent game/loader substitution occurs. A project must be a client-capable modpack; catalog file/index game/loader disagreement fails. Search can include projects whose latest pack requires an unsupported exact combination; Install reports this without publishing a game rather than silently choosing another version.

Required client files are installed automatically. Optional client files and dedicated-server files/overrides are excluded; optional-file selection is not offered in this initial scope. Common overrides are applied first, then client overrides. An override may replace a pack-downloaded file in this new staged game; it never replaces files in existing games. Download sources use the manifest's HTTPS mirrors from the initial Modrinth/GitHub/raw GitHub/GitLab allowlist; redirects follow the centralized client's normal behavior. Every required downloaded payload is verified against byte count, SHA-1 and SHA-512; mirror fallback remains bounded by the declared list and downloader retry policy.

Limits: 512 MiB archive, 4 MiB UTF-8 index, 100,000 ZIP entries, 2 GiB total expanded archive, 10,000 referenced files, 512 MiB per referenced file, 8 GiB referenced total, up to eight mirrors per file. ZIP payload CRC/sizes are checked. Portable safe paths reject traversal, absolute/drive paths, reserved names, launcher-private metadata destinations, case collisions and file/parent conflicts. ZIP permissions/link attributes are never materialized as links. Game publication checks space for archive/pack payload/overrides with a margin; additional game/runtime downloads can still fail on a full disk and leave the profile unpublished.

## Publication, cancellation and mutable data

`createPrepared` reserves a new UUID and `.creating-<uuid>/game` directory. The long preparation callback does not retain the repository mutex, so other games can still be listed/leased. Pack downloads/configs and the common metadata-driven Minecraft/loader/managed Java setup complete before instance metadata and the directory are published. No account is needed and no game process is started by installation. A game appears in inventory only after success. Failed/cancelled private staging is removed; verified shared caches may remain for retry. Crash/interruption staging remains hidden rather than falsely ready.

Every install/reinstall/new release creates a new UUID. There is no in-place pack update or automatic world/config merge: older worlds/configs remain in their existing game. Unrelated games, source archives and existing export destinations are not overwritten. Cancellation during final publication may race with a fully completed commit; the guarantee is no visible partial profile, not deletion of a successfully committed game.

## Original-definition export and state

For a pack-installed game open **Choose extras → More → My packs → More → Export original pack**. The exporter verifies the retained original archive and copies it byte-identically to a new `.mrpack` destination outside games. It does not collect the current game directory, add player-created worlds, accounts, downloaded mod binaries or local edits. Author-supplied overrides, including any content already in the original pack, remain intact and subject to the author's redistribution terms. Export is the original pack definition, not an export of arbitrary modified/locally assembled instances; local modifications are intentionally not represented.

The additive game/.syntaxlauncher-modpack directory stores source.mrpack and receipt.json with schemaVersion 1, source SHA-512, original index/unknown metadata and the explicit excluded-optional policy. Existing ordinary instances have no receipt and need no migration. Unknown receipt schemas and changed source archives refuse export without rewriting unknown data. Installation never updates another instance's receipt.

## Automated and live evidence

- [x] `ModrinthModpackPlannerTest`: deterministic exact component mapping, required/optional/server-only roles, metadata retention, strict encoding/format/size/source/hash/path/collision validation.
- [x] `ModpackInstallerTest`: actual shared game/Java preparation with synthetic HTTP inputs, catalog one-action install, unpublished staging, client override precedence, optional/server omission, repeated independent installs, unchanged worlds/configs, byte-identical original export, occupied destination refusal and no embedded downloaded JAR/user worlds in export.
- [x] Invalid required SHA-512, unsupported exact loader, CRC corruption, traversal/private metadata/case/file-parent collisions, preparation failure and cancellation publish no incomplete profile; cancellation does not block another profile's listing/launch lease.
- [x] Future receipt schema, changed original archive and active-instance export refuse safely.
- [x] Existing loader/coordinator/mod/resource/shader/OptiFine and update regressions pass after sharing preparation with installation.
- [x] ContentCatalogTest and LauncherHomeRenderTest: one-click packs without a pre-existing game, compatibility failure without Installed state, secondary local import, duplicate guards, EN/PL/DE at wide/400×400, reachable home pack action and retained Play hierarchy. Catalog and home screenshots reviewed locally.
- [x] `./gradlew --offline test build :launcher-desktop:createDistributable --rerun-tasks`: full local tests/build and Linux app-image packaging pass. The packaged image includes the existing standalone OptiFine worker and modpack services.
- [x] Real public Simply Optimized **1.21.1-6.0**, Modrinth project **BYfVnHa7**, published version **12lXTVh0**, exact **Fabric 0.17.3**, 18 referenced files: fresh private download/install, Minecraft libraries/assets, managed Java 21 preparation and byte-identical original export passed on Linux. The test took approximately 88 seconds. No graphical Minecraft, real account or user world was used. Local report: launcher-core/build/reports/official-modpack-smoke.xml (generated, not committed).

Reproduce the separate online test with `SYNTAXLAUNCHER_MODPACK_LIVE_SMOKE=1 ./gradlew --offline :launcher-core:test --tests '*ModpackOfficialSmokeTest' --rerun-tasks`. Gradle dependencies must be cached; public artifacts are downloaded into fresh temporary directories and may consume hundreds of MiB. Normal `./gradlew --offline test build` skips this and the three earlier opt-in installer tests and does not require upstream availability.

## Explicit follow-ups

Broader actual Play/gameplay and native focus/DPI/platform acceptance remains in v1.0; a successful installation is not a real graphics startup. Other pack formats, optional-file choices, arbitrary modified-instance export, in-place updates/world merges and expanded Forge compatibility are outside this initial scope. Original-pack export does not authorize redistribution beyond the author's terms.
