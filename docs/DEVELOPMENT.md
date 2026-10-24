# SyntaxLauncher Development Guide

> **Current policy — 24 October 2026:** Only Microsoft-authenticated Java Edition players may launch; ownership and profile are verified before each Play. Legacy account files remain readable only for preservation. AppID approval is pending. See [AppID compliance](APPID_COMPLIANCE.md).

## 1. Scope

This document defines the expected engineering workflow and implementation conventions for SyntaxLauncher. AI/agent contributors must also follow `AGENTS.md`.

## 2. Core principles

1. **Metadata-driven Minecraft support.** Do not hardcode launch recipes that Mojang metadata already describes.
2. **Core independent from UI.** Compose Desktop is a presentation layer, not the launcher engine.
3. **Instances before mods.** Mutable game state becomes instance-scoped in v0.2 before loaders/mods arrive.
4. **Official premium auth only.** Premium support uses Microsoft/Xbox/Minecraft authentication; local-only identities cannot be selected, registered or launched.
5. **Managed Java.** The launcher resolves a compatible Java runtime instead of assuming the user's PATH is correct.
6. **Incremental milestones.** Implement only the current release scope plus narrow extensibility seams required for future work.
7. **Recoverable operations.** Downloads/installations should avoid leaving partial files/state presented as complete.
8. **Actionable errors.** Preserve technical causes and present clear user-facing failure categories.

## 3. Planned stack

Initial preferred stack:

- Kotlin/JVM
- Gradle Kotlin DSL
- Compose Desktop
- Kotlin Coroutines
- Ktor Client
- kotlinx.serialization
- JUnit-compatible Kotlin testing

Libraries may change if there is a documented technical reason. Avoid adding large frameworks for functionality already available in Kotlin/JDK or existing project dependencies.

## 4. Suggested repository/module layout

Start small. A reasonable initial layout is:

```text
SyntaxLauncher/
├── launcher-core/
│   └── src/
│       ├── main/kotlin/
│       └── test/kotlin/
├── launcher-desktop/
│   └── src/
│       ├── main/kotlin/
│       └── test/kotlin/
├── docs/
├── AGENTS.md
├── README.md
├── settings.gradle.kts
└── build.gradle.kts
```

If a single Gradle module is simpler for the first implementation, package boundaries must still separate UI from core. Split into modules when it materially improves dependency control rather than for appearance alone.

## 5. Package responsibilities

### `account`

Account identity/session models and account providers. legacy data parsers remain only for migration; game launch requires verified Microsoft credentials. Microsoft-specific authentication belongs to v0.3.

### `minecraft`

Mojang version manifests, version metadata, rules, assets, libraries, natives and version resolution.

### `java`

Java runtime requirements, runtime inventory, download/install/verification and runtime selection.

### `launch`

Launch inputs, normalized launch plan, command construction, process start/monitoring and exit state.

### `network`

Shared HTTP/download infrastructure. Domain services should not each invent their own client/retry/checksum implementation.

### `storage`

Platform paths and persistence primitives.

### `instance`

Introduced in v0.2. Instance metadata, storage, migrations and operations.

### `loader`

Introduced when v0.4 Fabric work justifies it. Loader provider/adapters only.

### `mods`

Introduced in v0.5. Generic mod inventory/operations separate from Modrinth-specific API code.

### `diagnostics`

Introduced in v0.11. Evidence collection, findings and repair plans.

## 6. Coding conventions

- Prefer immutable data classes for resolved metadata/plans.
- Prefer explicit result/error types at domain boundaries over throwing generic exceptions everywhere.
- Use coroutines for network/disk preparation operations; do not block the Compose UI thread.
- Keep file/network side effects behind services that can be substituted in tests.
- Use clear domain names (`ResolvedMinecraftVersion`, `LaunchPlan`, `JavaRuntime`) rather than generic maps.
- Avoid global mutable singleton state.
- Avoid passing UI objects into core services.
- Avoid boolean parameter explosions; prefer options/configuration data classes.
- Keep serialization DTOs separate from domain models when remote/persisted schema shape would otherwise leak broadly.

## 7. Minecraft metadata handling

Minecraft version metadata should be treated as data, not translated into a growing set of version `if` statements.

Resolver responsibilities include:

- downloading/caching metadata;
- resolving inherited metadata where applicable;
- evaluating `rules` against current OS/architecture/features;
- resolving libraries/classifiers;
- selecting native artifacts;
- resolving assets;
- interpreting JVM/game arguments;
- interpreting Java runtime requirements.

When a historical version behaves differently, first determine whether the difference is represented in metadata. Add a targeted compatibility adapter only when metadata cannot express it.

## 8. Filesystem rules

Never assume the current working directory or application install directory is writable.

Use platform-appropriate application directories.

Separate concepts:

- **configuration** — small persisted launcher/user settings;
- **cache** — safely reproducible downloads such as metadata/assets/libraries;
- **runtime** — launcher-managed Java installations;
- **instances** — user game state;
- **logs** — launcher/Minecraft process logs.

Files downloaded from the network should generally be written to a temporary/partial path and moved into final place after successful completion and integrity checks.

## 9. Networking/download rules

All network consumers should use shared infrastructure.

Required behaviors as milestones need them:

- sensible connect/read/request timeouts;
- cancellation support;
- bounded concurrency;
- progress reporting;
- retry only for appropriate transient failures;
- checksum verification when source metadata supplies hashes;
- user-agent identification where APIs expect it;
- rate-limit-aware API adapters;
- no tokens in debug URLs/logging.

A failed checksum is not a successful download.

Download cancellation must leave the coroutine cancelled, without retry/failure/completion events for unfinished artifacts. `DownloadManager` checks cancellation during buffered copying and before publication, then closes the output stream before deleting its `.part` in a bounded `NonCancellable + Dispatchers.IO` cleanup. Only cleanup is shielded; downloads, retry waits and queue workers remain cancellable. Existing target bytes stay intact until verified publication; artifacts already published before cancellation remain usable. Cleanup failures are logged and must not mask the original failure/cancellation. `DownloadCancellationTest` uses controlled progress barriers, temporary directories and MockEngine to verify single-file cancellation/retry, two simultaneous partials and queued-request cancellation without live services.

## 10. Java runtime rules

Do not map Minecraft version strings directly to Java versions in UI code.

The runtime manager should consume the resolved requirement and select/install a matching runtime.

Runtime installations should have enough metadata to answer:

- distribution/source;
- runtime version;
- Java major;
- OS;
- architecture;
- executable path;
- installation/verification state.

User-provided runtime override can be added later but should pass compatibility validation.

## 11. Account rules

### Legacy identity files

Local-only identity rows remain readable for preservation, but cannot be registered, selected or launched. Their UUID compatibility parser never grants credentials. Account changes must not delete game profiles, worlds or settings.

### Microsoft

Required for every playable account:

- browser-based OAuth;
- PKCE where required/applicable;
- no password collection;
- secure token storage;
- token redaction;
- refresh/session expiry handling.

The launch planner should receive a normalized session/account payload rather than performing OAuth itself.

## 12. Instance persistence and migrations

From v0.2, every persisted instance descriptor must include a schema version.

Migration rules:

- migrations are explicit and tested;
- never silently discard unknown user state;
- prefer backup/copy before risky migrations;
- migration failure leaves original data intact where practical;
- worklog notes schema changes.

## 13. Testing strategy

### Unit tests

Use fixtures for:

- Mojang version manifests;
- old/new version JSON;
- OS rules;
- libraries/natives;
- argument placeholders;
- loader metadata;
- mod metadata.

High-value pure logic should run without network access.

### Integration tests

Use sparingly for:

- download pipeline behavior against controlled/local fixtures;
- filesystem installation plans;
- process construction;
- serialization migrations.

External services such as Microsoft or Modrinth should be abstracted so normal tests do not require real credentials or live APIs.

### Manual acceptance

Each milestone has user-level acceptance conditions in `docs/ROADMAP.md` and `docs/MILESTONES.md`. Record performed manual verification in `docs/WORKLOG.md`.

## 14. Logging

Use levels consistently:

- `TRACE` — highly verbose internals useful during diagnosis;
- `DEBUG` — resolver decisions, cache decisions, non-secret metadata;
- `INFO` — meaningful state transitions;
- `WARN` — recoverable abnormal conditions;
- `ERROR` — failed user operation/process requiring attention.

Never log:

- Microsoft access tokens;
- refresh tokens;
- authorization codes;
- passwords;
- complete sensitive request headers.

Launcher logs and raw Minecraft process logs should be distinguishable.

## 15. UI behavior

Compose Desktop UI should:

- reflect a state model exposed by services/view-model/application layer;
- remain responsive during downloads/resolution;
- expose cancellation where an operation supports it;
- not implement download/auth/version parsing logic;
- provide actionable error summaries with access to technical details;
- prevent duplicate conflicting actions while an operation is in a critical state.

## 16. Feature flags and partial features

Do not expose nonfunctional UI controls for future roadmap items merely to make the interface look complete.

If an experimental feature exists behind a development flag, it must not be marked complete on the roadmap until its milestone acceptance criteria are satisfied.

## 17. Dependency policy

Before adding a dependency, ask:

1. Is it necessary?
2. Is the project actively maintained?
3. Is its license compatible?
4. Does the JDK/Kotlin/existing stack already solve the problem?
5. Does it substantially increase binary size or runtime complexity?

Document non-obvious choices in the worklog or architecture document.

## 18. Security baseline

- Do not execute arbitrary downloaded code during launcher preparation unless it is an unavoidable, understood part of a supported loader installation process.
- Validate paths from archives to prevent path traversal/Zip Slip.
- Treat remote metadata as untrusted input.
- Verify hashes/signatures where the upstream provides them.
- Never build shell command strings from unescaped user data; use `ProcessBuilder` argument lists or equivalent.
- Sanitize filenames/instance ids generated from user-visible names.
- Keep secrets out of repository and logs.

## 19. Pull request/change checklist

Before merging or finishing a task:

- [ ] Scope matches requested/current milestone.
- [ ] Architecture boundaries are respected.
- [ ] Tests added/updated where practical.
- [ ] `./gradlew test` passes when code/build changed.
- [ ] `./gradlew build` passes when code/build changed.
- [ ] No secrets/generated runtime data committed.
- [ ] Relevant docs updated.
- [ ] `docs/ROADMAP.md` updated precisely.
- [ ] `docs/WORKLOG.md` entry appended.
- [ ] Remaining limitations/follow-ups documented.


## 20. Launcher updates and tagged releases

The maintainer requested the startup self-update slice of v0.12 early. Follow [UPDATING.md](UPDATING.md) when modifying versioning, bundle manifests, cached startup/rollback, the receipt protocol or publishing. Core update logic must remain independent of Compose; use the central downloader. Tests use local ZIP/HTTP/process fixtures and isolated data directories. The explicit `verifyUpdateBundle` task tests a real packaged archive without executing it. Set the single `launcherVersion` property in the root `gradle.properties` and commit it before releasing. `./gradlew` uses this version for all modules and embedded launcher metadata; `scripts/launcher_version.py` reads it for bundle/publication tooling. GitHub Actions rejects any `v*` release tag whose version differs from the property in the tagged commit, rather than silently overriding it. Native jpackage numeric versions are derived from the product SemVer for macOS compatibility. Publish normal, non-prerelease GitHub Releases automatically after a higher plain `launcherVersion=X.Y.Z` is pushed to `main` and full CI succeeds; a matching manually pushed stable version tag is an alternative. The Stable publishing job rejects prerelease `v...` tags. Successful `main` pushes publish immutable DEV pre-releases; manual workflow dispatch on `main` publishes SNAPSHOT (default) or DEV. Preview tags are `<channel>-<run>.<attempt>-v<canonical-version>` and never become latest stable. The run/attempt and embedded build channel remain separate from product SemVer. Use the Launcher updates dialog to persist the channel for the next startup; test same-version successive DEV updates, explicit return to older Stable and per-channel offline recovery. Each normal release enters the stable updater channel; outstanding manual acceptance must be disclosed. Do not equate a successful fixture test with live hosted cross-platform self-update acceptance.

## Build Explorer publication

The existing build workflow verifies both core and desktop tests and builds portable
applications for Windows x64, macOS ARM64 and Linux x64. A dependent Linux job
publishes all three archives to the `syntaxlauncher` project only after the complete
matrix succeeds. Pull requests never publish or receive the publishing secret.

Repository secret: `BUILD_EXPLORER_TOKEN`. The server stores only its SHA-256 hash
and limits it to this project, application formats and 90 MiB per file. It cannot
manage retention or publish to other projects. Rotate with the miniPORTAL CLI
`bin/issue-build-ci-token.php syntaxlauncher NEW_SECRET_FILE --rotate`, replace
the repository secret and remove the transfer file. Never commit the token.

Pushes to `main` publish Snapshot builds using the Gradle product version. Tags
`vX.Y.Z` publish Release builds; prerelease tags publish Snapshot builds. The shared
GitHub run ID groups platforms into one release; rerunning the same run replaces
its platform artifacts. Publication is serial, validates all three files first,
and includes SHA-256, size, commit and OS/architecture metadata. Server-side
visibility is immediate for each successfully uploaded file; a network failure
midway can leave a partial release until the job is rerun.

Run `python3 -m unittest discover -s tests -p 'test_*.py'` for publisher tests.
For metadata-only checks, download the three workflow archives into `dist/` and
run `python3 scripts/publish_build_explorer.py --dry-run` with the standard GitHub
run environment. Native installer signing and clean-machine gameplay acceptance
remain separate roadmap items.

## Version catalog cache

`MinecraftVersionCatalog` uses the shared HTTP client with a 15-second request deadline. Desktop supplies `cacheDir/minecraft/version_manifest_v2.json`; tests may omit the path. Each fetch requests fresh data, validates JSON and catalog consistency, then publishes the raw Mojang document through a unique sibling temporary file and atomic replacement where supported. This is disposable remote metadata, not a launcher settings schema. Invalid/missing caches are never silently treated as an empty catalog. Failed refreshes can use a re-parsed valid cache, cancellation propagates, and cache-write failures are logged without discarding a valid online result. Disk work runs on the IO dispatcher. Deterministic MockEngine tests cover restart reuse, replacement, malformed/HTTP failures, corruption, unavailable storage and cancellation. Cached catalog availability never bypasses fresh Microsoft authentication at Play.

## Desktop resource and dialog APIs

Brand PNG/JPEG files remain in `src/main/resources/branding` for the native package, classpath tools and existing image-dimension regressions. Use `rememberBrandingPainter` instead of deprecated `androidx.compose.ui.res.painterResource`: the helper remembers a `BitmapPainter` by path, closes the classpath stream and decodes with `org.jetbrains.compose.resources.decodeToImageBitmap`, following [JetBrains’ classpath migration example](https://github.com/JetBrains/compose-multiplatform-core/pull/1457). Native instance/mod windows use `DialogWindow` with the existing dialog state and close guards. Material3 and resources dependencies use explicit coordinates because the old Compose Gradle accessors are deprecated; keep the resources version aligned with the Compose plugin and retain the resolved Material3 version when changing notation.

## Interface localization (EN / PL / DE)

`desktop/i18n/UiStrings` loads UTF-8 `resources/i18n/{en,pl,de}.properties` bundles once. Use stable keys with numbered `{0}` placeholders; format runtime values through `strings.text(key, arguments…)` rather than embedding translated content in core. `LocalStrings` scopes language to the application composition. `LauncherLanguageHost` loads the system/saved choice off the UI thread and publishes a new localizer only after an atomic successful preference save. A save failure leaves the current language active and presents a translated retry explanation. No JVM-wide locale mutation is used.

`LauncherPreferencesStore.loadLanguage/saveLanguage` adds `ui_language` to schema 1 without changing existing profile fields. Missing keys follow system language; unsupported locale tags display English without rewriting the stored value. Older readers preserve the key as unknown. Language saves and normal preference saves share the same mutex and preserve each other's fields. Unsupported schemas are rejected before write. Tests cover legacy reading, unknown fields/languages/schemas, restart, normal saves, invalid writes, placeholders and all supported locale resources.

Keep user data, project names and upstream descriptions verbatim. Core log strings remain diagnostic. Use typed launch/update stages for localized progress rather than parsing English log messages. Add translated primary controls, errors and accessibility labels when adding screens; detailed upstream/domain diagnostics may retain their original language. Resource-parity and Compose language-switch/render regressions run with `./gradlew test`. Generated wide/narrow EN/PL/DE QA images are under `launcher-desktop/build/reports/ui/`; native keyboard/font/DPI acceptance remains separate.

Home render regressions must scroll both the compact page and its independent settings panel. `SemanticsActions.ScrollBy` starts asynchronous animation: render until the container’s vertical scroll range reaches its maximum instead of sleeping a fixed number of frames. Re-read the target semantics node and compare its clipped bounds with its measured size so a few visible pixels cannot count as a usable control. For CI reproduction, supply the same `launcherBuildNumber`, full `launcherCommit` and `launcherUpdateChannel` Gradle properties as the failing job.

## Simple modded-game setup

`ModLoaderRegistry.compatibleGame` performs read-only compatibility discovery for quick setup and the instance creation/settings forms. It preserves a compatible current game; otherwise it intersects provider-supported games with the official Mojang catalog ordered by descending release time, then verifies the newest candidate through the provider. Fabric/Quilt query their game indexes; NeoForge filters using its existing Maven compatibility mapping and Forge matches exact official Minecraft coordinates for modern V2 builds. Quilt loader-discovery HTTP 404 means no compatible versions; profile 404 and all other HTTP/network errors remain failures. A 20-second UI deadline and composition cancellation bound discovery. A fallback selects the first (newest upstream) loader entry, including previews, and explains the draft version adjustment in EN/PL/DE; unchanged games retain their selected compatible loader or prefer stable. No game/loader versions are hardcoded by fallback. The older bounded `desktop/ModdedGameSetup.kt` helper remains available to callers without registry discovery.

`QuickModdedGameDialog` keeps suggestions and optional advanced drafts in memory. An explicit Minecraft selection in quick setup disables automatic fallback until the next loader selection. Instance creation/settings apply the requested automatic compatibility adjustment to drafts; Save/Create remains explicit. Only confirmation calls the existing instance repository; duplicate confirmation is guarded and close/cancel is disabled while saving. Successful creation selects the new profile and opens Local Mods with Modrinth discovery initially visible. No legacy/Vanilla data is copied or modified and existing schemas remain unchanged. Home mode changes reuse saved Fabric/Quilt profiles and restore the previous Vanilla selection within the session. Existing advanced manager and normal mod-management entry points remain available. Tests cover read-only discovery, mode switching, persisted active choice, cancellation/retry, duplicate clicks, profile/world preservation and compact EN/PL/DE setup renders.

## Native launcher startup and child diagnostics

Desktop JVM options enable native access for Skiko/JNA in both Gradle runs and packaged application images. `ProcessUpdatedLauncherStarter` removes only `_JPACKAGE_LAUNCHER` from the inherited environment, preserving display and other ordinary variables; a separate jpackage image must build its JVM command from its own configuration. [OpenJDK’s Linux launcher source](https://github.com/openjdk/jdk/blob/45a329790b844139010c95f981c72ad43b08369a/src/jdk.jpackage/linux/native/libapplauncher/LinuxLauncherLib.cpp) explains the inherited-marker branch. Retain private child startup logs and show their path plus the exit code when handoff fails. A missing channel preference must use the same embedded-build fallback in the startup wiring and channel picker; explicitly saved selections remain authoritative, including an intentional return to older Stable.


## v0.8 acceptance and v0.9 graphics catalog

Use `LoaderPipelineMatrixTest` for the common coordinator contract across all real Vanilla/Fabric/Quilt/NeoForge/Forge adapters. It supplies synthetic network metadata, compiles and runs a local fixture processor for both installer flavors, intercepts final process launch with `java -version`, and checks selected runtime, arguments, cache reuse and per-instance save preservation. It complements official opt-in installer tests and private maintainer acceptance; it is not a real Minecraft boot. The supported Forge pair and legacy rejection/runtime policy are recorded in `TESTING_V0.8.md`.

v0.8 is accepted only within [its recorded support matrix](TESTING_V0.8.md). The maintainer separately confirmed the bounded v0.9 private graphics workflows after implementation; no detailed native matrix was supplied. Keep broader platform/extended recovery validation open under v1.0.

The normal path is Choose extras → compatible item → Install → Play. `ModrinthPackCatalog` handles resource/shader project types; `IrisRendererSetup` installs the compatible renderer/dependencies through the normal Modrinth mod pipeline. Do not replace a manually installed renderer or silently convert Vanilla to a modded game. `LocalPackManager` offers list/importBatch/setSelected/moveResourcePack/remove/restore under the inactive-instance lock. My packs exposes these actions; local ZIP import stays secondary and does not activate the pack automatically. Preserve intact ZIPs, unrecognized folder packs, unknown settings, exact-byte backups and other instances. The last Undo receipt lasts only while the dialog is open; removed files/backups remain on disk without automatic cleanup.

OptiFine is supported initially only on Vanilla Minecraft 1.21.1 / HD U J1. Use the official private download/installer, never bundle or mirror its binaries. The JDK-only Java worker must remain loadable from both the Gradle classes directory and packaged core JAR without Kotlin/Compose dependencies. The generated schema-1 graphics sidecar is additive: no sidecar means ordinary launch; reject future schemas without losing unknown data. Validate stored SHA-1/size and safe cache paths at every enhanced launch. Keep OptiFine outside the loader registry. Use the installed-pack Turn off OptiFine action to recover from an incompatible edited game profile.

Normal `./gradlew --offline test build` uses deterministic HTTP/ZIP/filesystem/process fixtures. The separate live smoke is `SYNTAXLAUNCHER_OPTIFINE_LIVE_SMOKE=1 ./gradlew --offline :launcher-core:test --tests '*OptiFineOfficialSmokeTest' --rerun-tasks`. It downloads official private artifacts, runs the real installer in a temporary game, checks the common profile overlay and disables it; it does **not** start graphical Minecraft. Fixture dependencies must already be cached for --offline. DownloadManager uses a streaming response lifetime and optional maximumBytes for sources lacking Content-Length; exceeding the cap is nonretryable, deletes partial staging and preserves an existing destination.

Verify translated catalog and My packs rendering/interaction at 820×650 and 400×400; PNGs stay under launcher-desktop/build/reports/ui. Test selection, priorities, Undo, busy-game guards, cancellation and malformed settings/archives, changed renderer hashes, unknown schemas, unsupported game/loader combinations and preservation of user data. Build `:launcher-desktop:createDistributable` to verify worker packaging. Actual game rendering/activation and native keyboard/focus/DPI acceptance remain separate checks in [TESTING_V0.9.md](TESTING_V0.9.md).

## v0.10 modpack installation

The primary path is Modpacks → Install → selected new game → Play; local `.mrpack` import and original-pack export remain secondary. Use the shared HTTP/downloader, metadata/runtime/loader services and `prepareForInstallation`, never a second launch recipe or a hardcoded Java/game version. `createPrepared` must publish only after complete successful preparation without blocking other repository operations during downloads.

Normal deterministic tests cover planner/archive/download/catalog/prepare/export contracts, whole-set SHA-1/SHA-512/size checks, client override precedence, optional/server omission, exact-version refusal, CRC/path/collision checks, cancellation/failure cleanup and prior-world/config preservation. No normal test depends on upstream availability. `SYNTAXLAUNCHER_MODPACK_LIVE_SMOKE=1 ./gradlew --offline :launcher-core:test --tests '*ModpackOfficialSmokeTest' --rerun-tasks` separately downloads the pinned real Simply Optimized 1.21.1-6.0 pack and prepares fresh Minecraft/Fabric/managed Java, then checks byte-identical original export. It can download hundreds of MiB into temporary directories and never starts graphical Minecraft or uses an account.

Keep the schema-1 modpack receipt additive and read unknown future schemas without rewriting/discarding them. Original export must never scan the current game directory or silently claim local edits are represented. Reinstall/new release means a new UUID, preserving the old game. See [TESTING_V0.10.md](TESTING_V0.10.md) for precise support/size/optional/export limitations and the separate broader native/gameplay gate.

## Diagnostics, hardening and dependency locks — 23 October 2026

Read TESTING_V0.11.md, TESTING_V0.12.md and INSTALLATION.md. Do not export raw game logs or argument/session objects. Diagnostic signatures return fixed codes and confidence/source categories, never captured raw text. File hashes establish corruption; log signatures only suggest causes. Repair must use common preparation under the existing job guard/activity lease and never delete user worlds or silently disable mods.

Launcher messages use `SafeLogMessage` in both console/file appenders; `%nopex` prevents a second automatic raw stack trace. The log path is set before service creation in Main; tests use temporary fallback storage. Keep raw log access local and bounded. Gradle locks the common JVM graph; platform-specific Compose/Skiko module families are intentionally ignored by locks and remain versioned by the pinned distribution. Refresh lockfiles using `--write-locks`, inspect changes, then build all supported targets. Microsoft work is deferred while awaiting Mojang, without treating mocks as live approval.

## Profile workflow UI regression checks

ProfileWizardTest covers conditional Vanilla/modded/pack/extras branches, compatibility adjustment, draft-only review, translated 400×400 layouts, duplicate confirmation and cancellation. LauncherHomeRenderTest verifies that Game profiles changes the same root page, real world/config names are read without mutation, sidebar RAM and duplicate navigation disappear, Game type follows Game version, and per-profile/legacy memory save versus Close retains isolation and persistence. LauncherFooterTest injects a local URI handler to verify exact destinations without opening external pages. ModpackInstallerTest verifies loader-filtered pack discovery, chosen names and rejection before publication for a different family. Generated screenshots remain under build/reports/ui.

## Native installer builds

After `:launcher-desktop:createDistributable`, run `python3 scripts/package_installers.py --image launcher-desktop/build/compose/binaries/main/app/SyntaxLauncher --platform linux-x64 --output launcher-desktop/build/installers` on Linux. Use `windows-x64` on Windows and `macos-arm64` plus the `.app` image suffix on macOS. Set JAVA_HOME to the exact JDK used for the Compose image; jpackage rejects images from other JDK versions. Linux needs dpkg/fakeroot, rpm/rpmbuild and the ordinary jpackage dependencies. Packaging retains the matching JDK maintainer-script templates and prepares standard XDG menu directories before their desktop-registration commands, including on minimal systems. AppImage tools/runtime are pinned to official versioned assets plus SHA-256 and run without a FUSE build mount. Windows requires the pinned WiX 7.0.0 .NET tool and `WixToolset.UI.wixext/7.0.0`; it deliberately does not use jpackage's legacy WiX authoring. The maintainer explicitly accepted the WiX v7 OSMF EULA and confirmed eligibility on 23 October 2026; CI records that acceptance before loading extensions. The explicit MSI component authoring uses stable path-derived GUIDs and HKCU key paths for per-user installation, a stable UpgradeCode, major-upgrade sequencing, an explicit same-core duplicate-install guard and a real wizard. DEB/RPM derive a prerelease/build revision ordered below Stable revision 1. macOS jpackage creates the DMG from the already-built branded bundle.

CI runs `packaging/smoke-{linux,macos}.sh` or `packaging/smoke-windows.ps1` on disposable native runners. Do not run system-package lifecycle smoke scripts on a personal machine: their intended environment is the disposable CI runner. DEB and AppImage launch under Xvfb with isolated XDG locations; RPM installs/removes with real dependency resolution in Fedora 44; MSI checks complete payload hashes, repairs a removed launcher and checks Start menu/removal/data preservation; DMG verifies, mounts read-only and tests application copy/removal. Native focus/gameplay, signing/notarization and prior-version upgrades remain separate acceptance. `python3 -m unittest discover -s scripts -p 'test_*.py'` checks package identity/version bounds, XML payload/registry/menu behavior, executable AppDir arguments/symlinks, and fail-closed publication for missing/corrupt installers.

All five installers must be available before `scripts/verify_installers.py --directory <assets> --write` writes the installer checksum inventory. Preview publication uses `--installers` to verify it before creating any tag/release. Existing portable-only publication callers remain compatible; current CI requires installers.

## Packaged network and production gates

`python3 scripts/verify_native_network.py --image launcher-desktop/build/compose/binaries/main/app/SyntaxLauncher --platform linux-x64` starts the actual image's runtime, fetches public Mojang catalog and checksum-verified metadata, and requires a fresh private completion receipt. It bypasses player/account/updater/UI initialization; the receipt also works for Windows GUI executables without redirected stdout. CI runs the matching platform form before packaging.

`SYNTAXLAUNCHER_FORGE_LIVE_SMOKE=1 ./gradlew :launcher-core:test --tests '*ForgeOfficialSmokeTest' --rerun-tasks` uses official game/client/Forge artifacts and Mojang-managed Java for four reference pairs. Optionally set `SYNTAXLAUNCHER_FORGE_SMOKE_TARGETS` to comma-separated `Minecraft:Forge` entries. This is installation acceptance, not graphical game startup.

CI runs `python3 scripts/release_readiness.py --enforce-production`: plain Stable 1.x or later requires a fully closed v1.0 checklist. Pre-1.0 and prerelease builds remain testable. See [signing prerequisites](SIGNING.md) before enabling production signing.

Native managed-Java acceptance: `SYNTAXLAUNCHER_JAVA_LIVE_SMOKE=1 ./gradlew :launcher-core:test --tests '*ManagedJavaOfficialSmokeTest' --rerun-tasks`. Actual platform execution, verified offline reuse and corruption repair run only in isolated temporary state. CI runs the same case on all three published native targets, retaining JUnit XML in `managed-java-<platform>` artifacts. A passing Java test does not replace graphical Minecraft or clean-machine trust acceptance.
