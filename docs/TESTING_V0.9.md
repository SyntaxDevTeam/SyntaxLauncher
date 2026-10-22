# v0.9 — Graphics catalog acceptance

## Scope — 21 October 2026

The bounded v0.9 scope is accepted. After these changes the maintainer explicitly confirmed in-game texture activation, a shader with automatically installed Iris, and Vanilla 1.21.1 startup with OptiFine J1. No OS, exact loader/Iris/pack versions or logs were supplied. This is private maintainer acceptance, separate from automated evidence, and does not certify every version/platform or the extended checks below. Earlier Forge/Quilt confirmation still concerns v0.8 only. No release/tag/product-version change is implied. Ready-made modpacks/sets are v0.10.

The mandatory child-first path is Choose extras → choose a compatible item → Install → Play. No file/hash/version/dependency review is required. More → My packs provides installed textures/shaders, optional description/icon, Use/Turn off, texture priority, Remove/Undo and secondary local ZIP import. Local import leaves selection unchanged until Use. Install badges last for the open catalog; repeat installation safely revalidates/reuses identical files.

| Content | Initial supported scope | Automatic behavior |
| --- | --- | --- |
| Textures | Saved Vanilla/modded game; exact published Minecraft compatibility | Required same-type packs, verified ZIP batch, ordered selection |
| Shaders | Fabric/Quilt/NeoForge with an Iris release published for the exact Minecraft/loader pair | Compatible Iris + declared required mods, shader validation and Iris selection |
| OptiFine | Vanilla Minecraft **1.21.1 / HD U J1** only | Official private download, managed Java, official installer, verified generated launch overlay |

Shader discovery currently targets Iris-compatible packs. OptiFine's own shader selection is outside this initial matrix. Forge shaders, arbitrary OptiFine versions, mixing OptiFine/Iris and OptiFine on modded instances are not offered. No silent game downgrade, loader conversion or world copying occurs. Cross-type pack dependencies and richer cross-project version-range conflicts fail safely rather than putting files into the wrong directory. Untracked existing Iris requires review instead of automatic replacement.

## Data and recovery contracts

ZIPs stay intact under instance game/resourcepacks or game/shaderpacks. Validation checks path/name/CRC/size and root resource metadata or nonempty shaders files. Limits: 512 MiB archive, 2 GiB expanded payload, 100,000 entries, 1 MiB pack metadata. Description is bounded; optional PNG bytes/dimensions are bounded before desktop decoding. Folder packs remain unmanaged warnings.

All mutations use the shared inactive-instance lock. Batch publication checks expected game/loader and required renderer hashes. Existing different files and other instances are preserved. Texture selection changes only options.txt resourcePacks, retaining unknown lines, prior order and LF/CRLF; explicit priority moves change order only when requested. Iris uses ISO-8859-1 Java Properties/Unicode escapes and preserves unknown property values. Exact original bytes are backed up under .syntaxlauncher-pack-settings before atomic publication. Invalid/redirected/changed settings fail safely.

Remove deselects and moves a ZIP into .syntaxlauncher-pack-trash; failed settings publication restores the move. Undo refuses occupied destinations and recovery outside that instance's trash. The last-removal receipt is retained only while My packs is open. Closing the dialog retains removed files/backups on disk without promising a persistent in-app recovery list. A failed reactivation after successful Undo leaves a restored inactive ZIP available for Use/retry. Renderer preparation can leave verified required mods installed if a later step fails; it never labels an unprepared shader installation successful.

OptiFine comes directly from [the official source](https://optifine.net/downloads), following [its private-use/distribution policy](https://optifine.net/copyright); no binary is bundled or mirrored. This source flow has no upstream checksum/Content-Length guarantee: HTTPS plus a streamed 64 MiB hard limit bounds the installer transfer; generated artifact hashes are local integrity records, not an upstream signature. Mojang metadata/client still use advertised SHA-1/size. The official installer runs in a separate headless managed-Java worker with timeout/cancellation, private staging and retained diagnostic logs. This is not an OS security sandbox.

The additive game/.syntaxlauncher-graphics.json sidecar has schemaVersion 1. Missing means no enhancement; future schemas are refused without changes; unknown root data is preserved on install/disable and original bytes are backed up. Content-addressed generated libraries are verified before enhanced launch. An edited game version/loader requires Turn off OptiFine in My packs; base client/assets/Java/account/process handling remains shared.

## Automated evidence

Normal `./gradlew --offline test build` runs deterministic tests without online services. `:launcher-desktop:createDistributable` also passes locally on Linux; that verifies packaging, not native behavior on all platforms.

- [x] Existing local/resource pack regressions: isolation/restart, whole-batch validation/collision rollback, unsafe archives/CRC/metadata/path/symlink refusal, running-instance guard, repeat install and unchanged user bytes.
- [x] Resource selection: exact-byte backups, unrelated options/LF/CRLF/UTF-8 handling, dependency ordering, explicit priority, disable/remove/recovery and description presentation.
- [x] ShaderCatalogTest: complete mocked Modrinth Iris/required-mod/shader installation, exact game/type/facet filtering, disabled required renderer re-enable, repeat install without re-download, Unicode property retention and backups, corrupt ZIP before renderer installation, renderer failure with no selected ZIP, changed proof refusal, recovery collision/outside-path refusal and other-instance preservation.
- [x] GraphicsProfileTest: base metadata retention, generated argument/library overlay, changed/missing artifacts, unsafe artifact paths, mismatched game/loader, future schema refusal before network/runtime work, disable preserving unknown state and exact original bytes.
- [x] Download regression: size cap without upstream length, no retry on oversize, existing destination preserved and partial file removed; existing integrity/retry/cancellation fixtures still pass with streamed responses.
- [x] ContentCatalogTest and InstalledPacksTest: one-click install, duplicate guard, disabled running game, errors/retry/cancel/cleanup, graphics sections, Use/Turn off, Remove/Undo, source preservation and EN/PL/DE rendering/interaction at 820×650 and 400×400. Generated screenshots stay in launcher-desktop/build/reports/ui; Polish/German compact graphics and installed-pack images reviewed locally.
- [x] Actual official private OptiFine install/overlay smoke on Linux with Java 21: downloaded the official 1.21.1/J1 installer and Mojang inputs, ran its own installer, checked OptiFine libraries/generated tweak arguments through the shared resolver, preserved a save marker, then disabled back to base. **No graphical Minecraft was started.**

Reproduce the separate online smoke with `SYNTAXLAUNCHER_OPTIFINE_LIVE_SMOKE=1 ./gradlew --offline :launcher-core:test --tests '*OptiFineOfficialSmokeTest' --rerun-tasks` (Gradle dependencies must be cached). It uses temporary game/data/cache directories, no real accounts or worlds. Normal tests skip this and the two earlier live installer smokes.

## Acceptance record and broader follow-ups

- [x] Maintainer confirmed actual in-game texture activation for this v0.9 implementation.
- [x] Maintainer confirmed shaders with automatic Iris setup for this v0.9 implementation.
- [x] Maintainer confirmed Vanilla Minecraft 1.21.1 startup with OptiFine J1 for this v0.9 implementation.

The response selected “Tak — podaję sprawdzone zestawy” without supplying additional OS/loader/renderer/pack details. Do not invent that matrix or treat the confirmation as proof of every step below. It permits closing the bounded v0.9 development stage and proceeding to v0.10, without a release/version bump.

Broader acceptance is tracked unchecked under the v1.0 quality gate, moved there explicitly after bounded maintainer acceptance:

- [ ] Record supported-platform OS/Minecraft/loader/Iris/pack versions and evidence. Verify resource dependency order, priority, disable/remove/Undo and restart with the same worlds/settings.
- [ ] Exercise shader switching, disabling, remove/Undo and restart on representative supported published loader/version combinations.
- [ ] Exercise OptiFine disable back to ordinary Vanilla and recovery after an incompatible game/loader edit, preserving the same world.
- [ ] Verify native keyboard focus, scrolling/resizing, longer translations, DPI/small windows, live network interruption/cancel/retry and guards while preparing/running Minecraft across supported platforms.

Persisted catalog project badges, richer pack descriptions and a persistent recovery inventory remain refinements; ready-made modpack/set installation is the next v0.10 development stage.
