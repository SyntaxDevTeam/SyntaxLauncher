# SyntaxLauncher

SyntaxLauncher is a modern Minecraft launcher from SyntaxDevTeam, written primarily in Kotlin and developed incrementally from a minimal vanilla launcher into a modular launcher with premium authentication, isolated instances, mod loaders, Modrinth integration, modpacks and diagnostics.

The project has implemented a v0.2 Instance Manager, reached **bounded v0.8 Forge/Quilt and v0.9 graphics acceptance**, and started **v0.10 modpack planning**. Official Forge/NeoForge installer smoke tests and three-platform CI packaging pass; the maintainer confirmed private Forge startup and Forge/Quilt mod workflows. Forge remains restricted to Minecraft 1.21.1 / 52.1.20. v0.9 now includes built-in mods/textures/shaders/OptiFine installation and installed-pack controls within the documented compatibility matrix; the maintainer confirmed private v0.9 gameplay acceptance; the broader native/platform matrix and v0.10 modpacks remain open. Microsoft app approval, v0.3 real-account acceptance and documented clean-machine Vanilla startup acceptance remain pending. The Kotlin/Compose launcher now connects the offline account, Mojang metadata, verified game preparation, automatic Java runtime installation, metadata-driven LaunchPlan and MinecraftProcessManager to an actual desktop Play workflow. A real clean-machine Minecraft launch is not yet verified.

## Project philosophy

**Non-negotiable audience:** children playing Minecraft. The normal experience must be extraordinarily simple: choose an item or ready-made set from the launcher catalog, click Install, then Play. The launcher handles compatible versions and required components. Manual file import and technical settings are advanced tools. This applies to mods, modpacks, shaders, resource packs, OptiFine and future content; a backend alone does not complete the experience.

SyntaxLauncher is intentionally developed milestone by milestone.

The first release will **not** try to compete with mature launchers by implementing every feature at once. Instead, v0.1 establishes a clean launcher core capable of:

- using an offline/non-premium account;
- listing/selecting Minecraft versions;
- resolving Minecraft metadata, assets, libraries and natives;
- selecting and installing the Java runtime required by the chosen Minecraft version;
- building the launch command from metadata;
- starting and monitoring Minecraft;
- exposing a small Compose Desktop interface.

Later features must extend that core rather than replace it.

## v0.2 — Instance Manager (release validation in progress)

The detailed implementation sequence is in [v0.2 Instance Manager phases](docs/INSTANCE_MANAGER_PHASES.md): **Track A — separate Minecraft instances** and **Track B — managing instances**, with P0/P1 priorities, existing coverage, outstanding data-safety work and explicit acceptance criteria.

Instances now provide stable UUID identities and separate game directories for worlds/configs/resource packs, with shared immutable Mojang caches. Core instance tests, desktop compilation and Linux/Windows/macOS packaging passed in [GitHub Actions 39157681455](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39157681455). Real two-world isolation remains to be checked manually. The desktop manager supports creating, selecting, renaming, cloning, editing per-instance Minecraft version, minimum/maximum RAM and advanced JVM arguments through explicit Save/Cancel, moving instances to a recoverable trash directory and restoring deleted instances in-app with their original UUID. Restore refuses an occupied UUID instead of replacing data; corrupted trash entries remain visible as diagnostic warnings without automatic cleanup. Legacy Vanilla remains playable with the original v0.1 game directory; an explicit import copies existing legacy worlds without deleting the source. Instance metadata is versioned, written atomically and preserves unknown properties. Follow the [v0.2 Instance Manager test checklist](docs/TESTING_V0.2.md) before declaring the milestone complete; code compilation alone does not prove the game-state isolation acceptance gate.

The v0.2 Instances workspace now separates searchable instance inventory from profile actions, supports restoring deleted profiles, and reviews source/destination paths and estimated size before clone/import. Long copies show byte/file progress and can be cancelled; incomplete profiles are never published. A process activity marker rejects profile mutations while launcher-managed Minecraft or preparation is active, including after detaching from the launcher. These safeguards still require real-world acceptance.

### v0.2 verification status

The v0.2 implementation includes cross-process per-instance file locks, safe cloning/import to private staging, SHA-256 copy verification, space checks, cancellation, recovery from trash, and an adaptive Instances workspace. A deterministic test runs two actual Java child processes through the launch coordinator and verifies that their game directories do not mix. These automated tests are distinct from real Minecraft gameplay acceptance.

**One-time release-gate exception (19 October 2026):** The maintainer explicitly waived the v0.2 manual Minecraft/visual acceptance requirement solely for proceeding to v0.3. These tests were NOT executed and must NOT be represented as passed. This waiver is not valid for future milestones; the checklist remains available for regression testing.

## v0.3 — Microsoft/Premium Accounts

The desktop now has a v0.3 development account picker with Offline and in-memory Microsoft Java sessions, native browser PKCE login, account removal and the shared premium/offline launch pipeline. Configure SYNTAXLAUNCHER_MICROSOFT_CLIENT_ID with your own registered public application ID. Secure persistence, real Minecraft approval/testing and full UI acceptance are still outstanding; no tokens are persisted to files. A properly authorized Microsoft public-client app registration will be required. Premium sign-in must never request a Microsoft password or hardcode someone else's application ID.

## Pick Vanilla or play with mods

Start with **Vanilla** or **With mods** directly on the home screen. Vanilla keeps your existing game selected. With mods reuses a saved Fabric/Quilt game when available; the first time, a short setup chooses a compatible Minecraft and mod-loader version for you. Confirm **Choose mods** to create a separate game and open Modrinth discovery immediately. Your Vanilla worlds are never converted or copied by this action.

The current game version is preferred. If it has no compatible loader, setup and the instance creation/settings forms select the newest Minecraft version supported by that loader, using the official loader catalog and Mojang release dates. The fallback selects the newest offered loader version and displays an explanation before Save/Create; compatible existing choices are preserved. Offline/errors offer Retry and Cancel. **More options** exposes a name, Minecraft version and Fabric/Quilt details; **My games** retains the full instance manager for existing profiles, cloning/import and advanced settings. Existing profiles and loader selections keep their format. Returning to either mode reuses its previous selection within the session; the active game is saved across launcher restarts.

## v0.4 — Fabric loader (startup accepted; remaining release checks open)

Instances can now store an optional Vanilla/Fabric loader selection with the chosen compatible
Fabric version. The instance creation and settings dialogs query the official Fabric Meta API
and show compatible versions or an actionable error. The shared launch coordinator resolves
the loader metadata before downloading Fabric libraries through the verified download pipeline,
while continuing to use the Mojang version's own client, assets, Java runtime and process manager.
Vanilla and existing schema-1 instances remain valid; immutable libraries are shared, and
mutable game state remains per instance.

The first v0.4 [GitHub Actions validation](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39317269516) passed core/desktop tests, native packages
on Linux/Windows/macOS, and Build Explorer publication. The maintainer confirmed real Fabric and Quilt startup tests on 20 October 2026. Cross-platform instance/loader switching, fault cases and the library-integrity policy remain separate release checks. Loader choice is stored per instance; library downloads
occur on the first Fabric launch rather than when the profile settings are saved. See
[the v0.4 manual checklist](docs/TESTING_V0.4.md).

OptiFine remains a later graphics enhancement rather than the first loader: its installer and
Forge-specific compatibility constraints are different from Fabric's public launcher-profile API.

## v0.5 — Local Mod Manager (development; acceptance pending)

Fabric instances now offer an **Instances → Mods** workspace for importing local JAR files,
reviewing detectable `fabric.mod.json` id/name/version/dependencies, turning mods on/off
(reversible `.jar.disabled` rename), and removing files to each instance's
`game/.syntaxlauncher-mod-trash` for manual recovery. The original import source is preserved;
SHA-256 and file names are tracked from disk, and enabled mod ID duplicates and basic
missing/conflicting dependencies appear as warnings.

Operations are isolated to the instance's own `game/mods` folder and use the same
cross-process activity lock as the launch/clone functions. This phase does not download
dependencies or treat locally parsed metadata as a complete compatibility resolver.
Only import JARs from trusted sources: Minecraft executes enabled mod code.
See [the v0.5 verification checklist](docs/TESTING_V0.5.md).
Fabric and Quilt startup were confirmed by the maintainer on 20 October 2026; local mod-management gameplay and UX acceptance remain separate.

## Automatic GitHub Releases

**v0.7.0 publication blocker:** The 21 October 2026 version-change CI passed tests and Linux/Windows/macOS packaging, but GitHub rejected the release creation with HTTP 403 (`Resource not accessible by integration`). `launcherVersion=0.7.0` is in main, but the public Release is still absent. See [UPDATING.md](docs/UPDATING.md) for the optional write-authorized release token fix.


The sole product version is `launcherVersion` in [`gradle.properties`](gradle.properties). A higher plain `X.Y.Z` pushed to `main` publishes Stable after tests and all platform packages pass. Matching manual `vX.Y.Z` tags remain supported. Every successful `main` push also publishes an immutable **DEV** GitHub pre-release, including changes without a product-version bump. Run the workflow manually on `main` with `preview_channel=snapshot` to publish **SNAPSHOT** (or choose `dev` for a manual DEV build). Preview releases never become GitHub's latest stable release.

Choose **Your setup → Launcher updates → Stable / SNAPSHOT / DEV** and restart. Without a saved choice, releases stay on Stable and preview archives keep their embedded DEV/SNAPSHOT channel; updates automatically download, verify, install and start the selected channel at startup. Preview builds use CI run/attempt identity independently of product SemVer, so repeated builds of the same version are detected. Explicit channel switching may install an older launcher; game data stays in its existing directories. Install a build containing this channel picker once before using it. See [publishing and acceptance details](docs/UPDATING.md).

## v0.8 — Forge and Quilt (accepted within the documented matrix)

Forge modern installer V2 shares the staged processor engine with NeoForge. The supported Forge pair is **Minecraft 1.21.1 / Forge 52.1.20**; historical V1 and unverified V2 pairs are rejected, including when selected by an existing profile. The maintainer confirmed private Forge startup and Forge/Quilt mod installation/startup on 21 October 2026. No OS list, exact Quilt/mod versions or logs were supplied, so this is not an all-platform or all-version certification. The existing in-app Forge test warning remains as a conservative scope reminder.

All five modes use the same preparation, Java, launch-plan and process pipeline. The new deterministic coordinator matrix exercises the real adapters against local synthetic metadata and Forge-family processor fixtures; official installer smoke tests and Windows/Linux/macOS packaging also passed in hosted CI. Java requirements come from Mojang metadata; missing requirements fail before downloads. Legacy arguments stay in the shared planner. Full TOML/version-range solving, historical Forge expansion and the broader native/gameplay matrix remain open. See [the v0.8 evidence and support matrix](docs/TESTING_V0.8.md).

## v0.10 — Modpacks (core import development)

The core now supports verified Modrinth format-1 `.mrpack` installation into a **new isolated instance**, including required files, SHA-1/SHA-512 checks, bounded overrides and failure-safe publication. From **My games → Import Modrinth modpack (.mrpack)** choose a pack file; the launcher installs it as a new game and selects it after completion. The ready-made online catalog, optional-file choices and export are separate work. v0.9 and v0.10 will undergo one combined maintainer gameplay test. See [the v0.10 checklist](docs/TESTING_V0.10.md).

## v0.9 — Built-in graphics catalog (bounded acceptance)

For a saved game choose **Choose extras**, pick a compatible item and press **Install**, then **Play**. Textures install and select required resource packs automatically. Supported modded games also show **Shaders**: the launcher installs compatible Iris and its required components, verifies them and selects the shader. Published Iris compatibility is checked for the exact Minecraft version and Fabric/Quilt/NeoForge loader. Unsupported combinations are unavailable; worlds and game versions are never silently converted.

**OptiFine** is currently offered only for **Vanilla Minecraft 1.21.1 / OptiFine HD U J1**. The launcher obtains the official private download and runs its installer automatically using managed Java. It does not bundle/mirror OptiFine or treat it as a generic loader. Other game versions and OptiFine with Forge/Fabric/Quilt/NeoForge are outside this initial support matrix. The real official installer/launch-profile smoke passed; this does not certify graphical gameplay.

**More → My packs** shows installed textures/shaders, descriptions/icons where present, Use/Turn off, texture priority and Remove/Undo. Local ZIP import stays secondary. Removed ZIPs and original settings backups are retained per game; existing different files, worlds and other instances are preserved. Undo covers the last removal in the open dialog. Install badges last for the catalog session; repeated installs revalidate/reuse identical files. Running/preparing games prevent mutation; installations can be cancelled or retried.

The maintainer confirmed private texture activation, shaders with automatic Iris and Vanilla 1.21.1 / OptiFine J1 startup after these changes. No exact OS/loader/Iris/pack matrix was supplied; broader native/platform and extended recovery acceptance remains tracked under v1.0. See [the v0.9 acceptance record](docs/TESTING_V0.9.md). The product version and release status are unchanged.

## v0.10 — Ready-made modpacks (started)

The first phase adds a read-only Modrinth manifest planner with exact game/loader requirements, required/optional client files and strict source/hash/destination validation. It creates no game or files. ZIP import, downloads/overrides, isolated instance publication and the child-first one-action catalog are still to come; modpacks are not exposed as a working category yet.

## v0.7 — NeoForge (v0.7.0 version set; public Release not yet available)

A processor-backed NeoForge installer adapter is now connected to the existing
instance and launch pipeline. The desktop can discover compatible NeoForge Maven
versions and save them in instance metadata. Before game launch, the common
coordinator first prepares the official Vanilla client and Java runtime, then
downloads the official SHA-256-verified NeoForge installer, stages processor
dependencies and generates the required client libraries in a separate working
directory. A generated runtime profile is merged back into the shared launch
pipeline. Local mod inventory can recognize the first NeoForge mods.toml record,
and the existing Modrinth loader filter follows the selected NeoForge instance.

**Acceptance scope:** automated installer and official Maven smoke tests passed on
21 October 2026. The maintainer reported that testing is complete, but did not
provide an OS/version matrix or logs. This is a v0.7 development-line build,
not a certification of every NeoForge/Minecraft/mod combination. Cancellation,
recovery and broader loader compatibility remain tracked in the roadmap. Do not use this stage with your only
world save or assume filesystem staging is a security sandbox. See
[the v0.7 acceptance checklist](docs/TESTING_V0.7.md).

## v0.6 — Browse and install popular Modrinth mods (development)

Open **Instances → Mods → Discover on Modrinth** for a Fabric instance. The discover
view loads the **most downloaded** compatible mods from Modrinth dynamically
(filtered by the instance's actual Minecraft version and loader), offers a search box,
project summaries, a readable description and a link to the project's Modrinth page.
No fixed ranking is bundled with SyntaxLauncher.

**Review installation** resolves a compatible published version and required Modrinth
dependencies recursively before showing the user which JAR files will be installed.
**Install to this instance** downloads the reviewed set through SyntaxLauncher's shared
download infrastructure, verifies each upstream SHA-1 and expected byte count, and
publishes the entire batch under the instance activity lock. Filename and detected mod-id
collisions never replace existing files. Modrinth remains the source of project descriptions,
download metadata and files; only trusted Modrinth CDN URLs are accepted.

This is the initial v0.6 catalog/install slice, **not** a completed v0.6 release.
Modrinth-installed JARs now record project/version identity in a versioned, instance-local
sidecar bound to actual JAR SHA-256; enable/disable keeps this identity. The Local Mods
screen includes **Check Modrinth updates**: it lists newer compatible versions without
touching installed files and explains unmatched local JARs. Managed updates now offer per-mod and batch confirmation, SHA-1-verified download,
SHA-256 provenance checks, a persistent backup of previous JARs and an explicit
last-update rollback action. Updates requiring missing/different dependencies are
deliberately blocked pending a full dependency reconciliation workflow.
Optional-dependency choice, richer conflicts and final UI/game acceptance remain open. Fabric and Quilt use the shared browsing/installing workflow. NeoForge has processor-backed installation; Forge's common V2 processor integration is in v0.8 testing. Local JAR import remains available. See
[the v0.6 test plan](docs/TESTING_V0.6.md). Fabric and Quilt startup acceptance was confirmed by the maintainer on 20 October 2026; remaining mod-management and platform checks stay open.

## Current implementation

The current v0.1 implementation includes:

- Gradle Kotlin DSL multi-module structure;
- `launcher-core` separated from `launcher-desktop`;
- Kotlin/JVM 21 toolchain configuration;
- Compose Desktop presentation layer;
- Coroutines, Ktor Client and kotlinx.serialization dependencies;
- SLF4J/Logback logging foundation;
- common `Account` boundary and `OfflineAccount` implementation;
- offline username validation and deterministic Minecraft-compatible offline UUID generation;
- Mojang version manifest DTOs, parser and catalog service;
- selected-version metadata resolver with SHA-1 verification;
- release/snapshot classification;
- Mojang OS/architecture/feature rule evaluation;
- library/native/asset planning;
- centralized verified download infrastructure with cache reuse, `.part` staging, atomic publication, retries, cancellation, bounded concurrency and progress events;
- game-file preparation for client JAR, libraries, natives, asset index, asset objects and logging configuration;
- safe native extraction with Zip Slip protection;
- automatic Mojang Java runtime discovery and installation driven by the Minecraft `javaVersion.component` metadata;
- official Mojang runtime-index and per-runtime manifest resolution for Windows, Linux and macOS platform mappings;
- verified runtime file downloads through the same shared downloader;
- managed runtime reuse without a network request when a compatible verified installation already exists;
- runtime staging, executable-bit handling, manifest link creation, completion markers and final `java -version` verification;
- support for the macOS `jre.bundle/Contents/Home/bin/java` layout;
- `MinecraftLaunchCoordinator` integrating verified selected-version metadata, game preparation, Java Runtime Manager, launch planning and ProcessManager, with cancellation and typed progress stages;
- `DesktopLauncherSession` owning background launches independently of Compose recomposition and detaching from a running game on window close;
- the real `Play Minecraft` and `Stop Minecraft` UI actions, download/Java stage progress, crash state, PID/exit code and separate game-process log paths;
- controlled `MockEngine` pipeline test sources with fake Java manager and a local Java child process;
- normalized `LaunchAuthentication` for offline sessions without coupling the launch builder to a raw nickname;
- metadata-driven `LaunchPlanBuilder` for modern JVM/game arguments and legacy `minecraftArguments` fallback;
- strict placeholder substitution for Mojang account/version/assets/natives/classpath/auth/launcher fields;
- platform-aware classpath construction, RAM JVM arguments and downloaded logging-config injection;
- structured `LaunchPlan` output containing runtime, working directory, main class, JVM args, game args and the final argument-vector command without shell concatenation;
- typed launch-plan failures for missing artifacts and unresolved future placeholders;
- deterministic launch-plan test sources covering modern and legacy argument construction, offline identity, logging, paths containing spaces and unknown-placeholder rejection;
- `MinecraftProcessManager` with native `ProcessBuilder` execution, per-run stdout/stderr log files, structured lifecycle/PID/exit status, duplicate-launch protection and explicit detach/terminate shutdown policy;
- controlled process tests including a real local Java child and synthetic crash/force-stop/detach scenarios;
- initial unit/integration test sources using Ktor `MockEngine` rather than live Mojang services;
- a lightweight Compose screen with nickname validation, release/snapshot selection, version selection, RAM control, loading state and retryable metadata errors.

The remaining v0.1 engineering work is complete cross-platform artifact verification, first real vanilla Minecraft launch and UI polish. Schema-versioned preferences for offline nickname/version/channel/RAM are now implemented but need desktop acceptance. The new Play action is functional in code but not yet validated against a real Mojang download and game startup.

## Continuous integration

The [Build and package SyntaxLauncher](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/workflows/build.yml) workflow runs on pushes to `main`, pull requests and manual dispatch. It verifies core/desktop tests and compilation plus update packaging fixtures, then builds Compose `createDistributable` portable applications for Linux, Windows and macOS. Workflow artifacts contain a tar.gz archive of the runnable app directory for each platform (including executable bits and symlinks); they are not signed native installers. Platform jobs also emit verified schema-1 update ZIPs; a version-change or matching stable-tag job uploads all six platform update/portable assets to an ordinary GitHub Release. Every successful main push also publishes DEV; manual main dispatch can publish SNAPSHOT. CI provisions Gradle 9.8.1 explicitly with `gradle/actions/setup-gradle`; local builds use the committed wrapper. CI status must be green before a v0.1 test release is called build-verified. Native jpackage version numbers are derived automatically from the product version (prerelease suffixes omitted; major `0` mapped to `1` for macOS compatibility). They are not independently maintained; the embedded product version remains authoritative. Local developers can use the official committed `./gradlew :launcher-core:test :launcher-desktop:run` (on Windows use `gradlew.bat`). Platform builds use `./gradlew :launcher-desktop:createDistributable`; output is under `launcher-desktop/build/compose/binaries/main/app/`.

## Product version (single source)

Edit only `launcherVersion` in the root [`gradle.properties`](gradle.properties) to change the SyntaxLauncher product SemVer. Root Gradle modules, embedded desktop build metadata, package/update manifests, the Build Explorer publisher and GitHub Actions use that one value. `BUILD #` and source SHA identify CI artifacts separately; native jpackage numeric versions are derived for operating-system compatibility. An increased plain `launcherVersion=X.Y.Z` on `main` triggers a normal GitHub Release with tag `vX.Y.Z` after the platform build matrix passes. DEV and manually dispatched SNAPSHOT are separate pre-releases and never become latest stable. Explicit stable tags must match the version in their tagged commit. See [UPDATING.md](docs/UPDATING.md).

## Startup updates

Packaged builds check the selected Stable/DEV/SNAPSHOT GitHub release channel before opening the home screen. Eligible updates download with SHA-256 verification, install beside the original application, and become active only after the new window confirms startup. A verified cached version works offline; failed updates preserve the previous launcher, settings and instances. The progress/error screen includes Retry and Continue. IDE/Gradle runs skip automatic updates.

The first build containing the updater must be installed manually. Subsequent releases need the platform-specific update ZIPs and public GitHub asset digests; CI packages/verifies them and publishes Stable on product-version increases or matching stable tags, DEV on successful main pushes, and SNAPSHOT on manual main dispatch. No release was published during implementation. Hosted old→new and Windows/macOS acceptance remain open. See [startup update behavior, trust model and release instructions](docs/UPDATING.md).

## Interface languages

The interface supports **English, Polish and German**. Choose **EN / PL / DE** from the compact language menu beside **Your setup**; the new language applies immediately and is restored on the next startup. First startup follows the system language, falling back to English for unsupported languages. Home, instances, local mods, Modrinth controls, window actions and startup updates use UTF-8 translation resources. Project/mod names, Modrinth author descriptions and technical diagnostics retain their source language.

The language choice is an additive schema-1 `ui_language` setting. Existing accounts, instances and preferences remain readable and unknown fields are preserved. Changing the language does not change Minecraft's own language or any game files.

## Minecraft version list cache

The launcher refreshes Mojang’s version list at startup and stores only successfully parsed, non-empty catalogs in the platform cache directory. If the network or Mojang is unavailable, it uses the last valid cached list. Invalid responses do not replace the cache; an unwritable cache does not block a successful online startup. Without a valid cache, the version picker shows an actionable error and Retry. This does not provide fully offline game launch: selected-version metadata and missing game files can still require network access.

## Preferences

SyntaxLauncher stores the selected offline nickname, Minecraft version/channel, RAM allocation, UI language, active game and launcher update channel in a schema-versioned `settings.properties` file inside the OS configuration directory. Writes use a temporary file and atomic publication where supported. Unknown property keys are preserved; an unsupported schema is rejected without erasing user settings. Microsoft credentials are not part of this storage format.

## Visual identity

The desktop home follows the approved neon-blue concept with a detailed voxel valley, a separate transparent block/arrow foreground, ink-blue panels, cyan outline icons and a prominent gradient Play action. A subtle shadow and cyan halo lift the mark from the scenery; its measured responsive layout preserves the original proportions, keeps the full arrow visible and reserves space away from the heading/profile in compact windows. The 128px app icon is never enlarged as hero artwork. All labels and controls are native Compose. The setup panel has a visible scrollbar; narrow windows stack the hero/Play and setup panel into a scrollable page. The initial window size adapts to the desktop work area, with a 400 × 400 minimum. [Artwork assets and prompts](launcher-desktop/src/main/resources/branding/README.md) document the generated layers.

The SyntaxLauncher product mark is a navy/cobalt/cyan voxel grass-and-dirt block with an upward orbital arrow. The launcher uses a matching dark-blue Compose UI with a bright blue Play accent, quiet secondary surfaces and semantic error colors.

Deterministic desktop render tests cover the ready home, nickname validation/disabled Play, quick instance selection, narrow/short windows and 150% scaling. A live-resize regression checks 27 hero size/density combinations at 100%, 150% and 200%, including both directions across breakpoints, and rejects cropped, distorted or overlapping logo/text bounds. Run `./gradlew :launcher-desktop:test`; generated QA images are under `launcher-desktop/build/reports/ui/`. These tests use mocked metadata and temporary profiles; they do not start Minecraft or access real accounts.

The optimized transparent icon is stored in `launcher-desktop/src/main/resources/branding/syntaxlauncher-icon.png` and is used in the window chrome, launcher header and Linux application package.

### Development build identification and window controls

Packaged CI artifacts now embed the GitHub Actions run number and source commit SHA alongside the independent SemVer release version. The launcher's header and updater identify the actual build as `v0.3.0-dev / BUILD #<run-number> / <short-sha>` (example format, not a fixed number); developer/IDE builds show `LOCAL BUILD` instead of inventing a CI number. CI metadata comes from `GITHUB_RUN_NUMBER` / `GITHUB_SHA` or the `-PlauncherBuildNumber` / `-PlauncherCommit` overrides, never from a runtime GitHub request. Stable update ordering and startup receipts retain product SemVer; DEV/SNAPSHOT ordering uses independent numeric CI run/attempt identity.

The desktop window is undecorated: a branded Compose title/header acts as the drag area, with independent minimize/maximize/restore/close controls overlaid at the top right, including during update startup. Eight small resize grips preserve a 400×400 minimum; window placement/dragging and resizing need manual acceptance on Windows, macOS, and Linux X11/Wayland at common display scales. No change is made to the Minecraft launch or account pipeline. The authoritative [color palette and UI rules](docs/DESIGN.md#syntaxlauncher-blue-identity-october-2026) cover future features.

## v0.1 test builds

The desktop home UI now uses a branded hero, prominent Play CTA and compact setup sidebar instead of the prototype two-card form. The same Minecraft preparation/Java/process pipeline remains in use. Visual acceptance is still pending manual desktop review.


The [2026-10-18 cross-platform CI run](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/39125171338) passed all four jobs, including core tests and Windows/Linux/macOS portable app packaging. These binaries are preview builds, not yet validated with a fresh actual Minecraft launch.


Read [the v0.1 manual testing guide](docs/TESTING_V0.1.md) before testing a downloaded preview build. Extract the GitHub Actions artifact ZIP, then extract the inner `syntaxlauncher-<platform>.tar.gz` to preserve Unix permissions. Test builds do not yet have a signed release installer or a successful documented clean-machine vanilla Minecraft launch.

## Technology

- Kotlin/JVM
- Gradle Kotlin DSL
- Compose Desktop
- Kotlin Coroutines
- Ktor Client
- kotlinx.serialization
- SLF4J + Logback
- JUnit-compatible Kotlin tests

Build files are authoritative for exact dependency versions.

## Release roadmap

| Version | Primary goal |
|---|---|
| **v0.1** | Vanilla + offline account + version selection + automatic Java + game launch |
| **v0.2** | Isolated Minecraft instances |
| **v0.3** | Microsoft / premium accounts |
| **v0.4** | Fabric loader |
| **v0.5** | Local mod manager |
| **v0.6** | Modrinth search, install, updates and dependency resolution |
| **v0.7** | NeoForge |
| **v0.8** | Forge + Quilt |
| **v0.9** | OptiFine + shaders + resource packs |
| **v0.10** | Modpacks |
| **v0.11** | Diagnostics and repair |
| **v0.12** | Production hardening, packaging, CI/CD and self-update |
| **v1.0** | First stable release |

The detailed task-by-task checklist is maintained in [`docs/ROADMAP.md`](docs/ROADMAP.md).

## Architecture at a glance

The launcher is designed around a UI-independent core:

```text
Compose Desktop UI
        ↓
Application/services
        ↓
Minecraft launcher core
        ↓
Metadata / downloads / Java / filesystem / authentication adapters
```

The v0.1 implementation starts with:

```text
SyntaxLauncher/
├── launcher-core/
│   └── src/
│       ├── main/kotlin/pl/syntaxdevteam/syntaxlauncher/core/
│       │   ├── account/
│       │   ├── java/
│       │   ├── launch/
│       │   ├── minecraft/
│       │   ├── network/
│       │   └── storage/
│       └── test/kotlin/
├── launcher-desktop/
│   └── src/main/
│       ├── kotlin/pl/syntaxdevteam/syntaxlauncher/desktop/
│       └── resources/
├── docs/
└── AGENTS.md
```

Future abstractions such as mod loaders should only be introduced when the relevant milestone needs them. The project explicitly avoids speculative overengineering.

See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) for the full architecture specification.

## Accounts

### Offline / non-premium

Available from v0.1.

Offline accounts are intended for local play and servers configured to accept offline clients. SyntaxLauncher will not use offline accounts to bypass authentication on servers operating in online mode.

The current implementation already provides a common account model, offline username validation and deterministic UUID generation using Minecraft's `OfflinePlayer:<username>` convention. The launch layer converts that account into normalized offline authentication/session placeholder values rather than embedding offline-specific branching throughout command construction.

### Microsoft / premium

Planned for v0.3.

Premium authentication will use the official Microsoft → Xbox Live → XSTS → Minecraft Services flow appropriate for a desktop application. SyntaxLauncher must never ask users to type their Microsoft password into the launcher itself.

The v0.1 launch builder already consumes a normalized authentication payload so v0.3 can provide premium access token/profile values without introducing a second premium-only launch engine.

## Java runtime management

SyntaxLauncher manages Java automatically through a core service rather than UI-version tables.

The selected Minecraft version's metadata supplies a Java component and major version. `MojangJavaRuntimeManager` then:

1. maps the detected OS/architecture to Mojang's runtime platform id;
2. reuses an already installed matching managed runtime when one verifies successfully;
3. otherwise fetches Mojang's official runtime index;
4. selects the requested component (`jre-legacy`, `java-runtime-*`, etc.);
5. downloads and SHA-1 verifies the component manifest;
6. stages every runtime file using the common verified `DownloadManager`;
7. creates directories/links and applies executable flags declared by the manifest;
8. verifies the staged runtime using `java -version`;
9. publishes the completed runtime into launcher-managed storage;
10. verifies it again and returns a normalized `JavaRuntime` descriptor to the launch layer.

The runtime is selected from metadata, so support can follow Mojang runtime components such as Java 17, Java 21 and Java 25 without teaching Compose UI about Minecraft-to-Java version mappings.

## Launch planning

`LaunchPlanBuilder` consumes only resolved/prepared inputs:

```text
Account / LaunchAuthentication
        +
PreparedMinecraftVersion
        +
JavaRuntime
        +
LaunchOptions / platform / feature rules
        ↓
LaunchPlan
```

The builder resolves Mojang JVM/game arguments, substitutes supported `${...}` placeholders, applies platform-specific classpath separators, injects prepared assets/natives/libraries/game paths, supplies offline identity/session values, adds RAM and logging configuration arguments and produces one structured argument vector.

It does **not** create one large shell command string. Paths containing spaces remain individual process arguments, which is safer and avoids platform-specific quoting problems. Legacy metadata without an explicit JVM argument list receives a small compatibility fallback for natives/classpath instead of a hardcoded per-version launch recipe.

Unknown future placeholders fail explicitly during planning instead of being passed unresolved into Java. Process creation is intentionally a separate next layer.

## Instances

Starting with v0.2, mutable Minecraft state will be isolated by instance.

Conceptually:

```text
SyntaxLauncher/
├── cache/
│   ├── assets/
│   ├── libraries/
│   └── versions/
├── runtimes/
└── instances/
    ├── vanilla-main/
    │   ├── instance.json
    │   └── game/
    └── fabric-test/
        ├── instance.json
        └── game/
```

This allows worlds, configs, mods and resource packs to remain isolated while safe immutable caches can still be shared.

## Mod loaders and mods

Loader support is intentionally staged:

- Fabric — v0.4
- NeoForge — v0.7
- Forge — v0.8 (official SHA-1-verified V2 installation passed for 1.21.1/52.1.20; in-game startup not yet accepted)
- Quilt — v0.8 (official Quilt Meta v3 profile and instance selection; actual game acceptance pending)

Local mod management arrives in v0.5, followed by Modrinth integration and dependency resolution in v0.6.

OptiFine is planned as an instance component/integration rather than being forced into a generic mod-loader abstraction.

## Design direction

SyntaxLauncher should be visually distinctive and modern without becoming visually heavy. The interface should attract attention through polish, hierarchy and detail rather than excessive effects or permanently visible technical controls.

The canonical UI/UX rules are maintained in [`docs/DESIGN.md`](docs/DESIGN.md).

## Documentation

- [`docs/ROADMAP.md`](docs/ROADMAP.md) — canonical detailed project checklist and quality gates.
- [`docs/MILESTONES.md`](docs/MILESTONES.md) — scope, exclusions and acceptance intent for every planned version.
- [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) — architecture, domain concepts and extension boundaries.
- [`docs/DEVELOPMENT.md`](docs/DEVELOPMENT.md) — engineering conventions, testing, security and implementation workflow.
- [`docs/DESIGN.md`](docs/DESIGN.md) — mandatory visual and interaction direction.
- [`docs/UPDATING.md`](docs/UPDATING.md) — startup update protocol, recovery and publishing.
- [`docs/WORKLOG.md`](docs/WORKLOG.md) — append-only development journal describing completed work and verification.
- [`AGENTS.md`](AGENTS.md) — mandatory rules for AI/agent contributors.

## AI / agent contribution policy

Any AI or autonomous coding agent modifying this repository must follow [`AGENTS.md`](AGENTS.md).

Among other rules, every repository-changing task must:

- update the detailed roadmap accurately;
- mark only actually completed checklist items as complete;
- leave partial work visibly open;
- append a detailed entry to `docs/WORKLOG.md`;
- record tests/build/manual verification;
- document remaining blockers or follow-ups.

This is intended to prevent the project roadmap from drifting away from the real implementation state.

## Current status

**v0.1 — Minimal Vanilla Launcher: in progress**

Implemented so far: project/module foundation, offline account primitives, Mojang version discovery and full selected-version parsing, shared verified download infrastructure, client/library/native/assets preparation, platform-aware managed Mojang Java installation, metadata-driven final launch-plan construction and the first lightweight Compose Desktop screen.

Still required before v0.1 can be considered accepted: Gradle wrapper/full automated build, real clean-machine vanilla Minecraft launch, cross-platform verification, settings persistence, version-manifest cache and manual UI validation.

## License

See [`LICENSE`](LICENSE).