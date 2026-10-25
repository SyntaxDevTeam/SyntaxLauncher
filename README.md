# SyntaxLauncher

**NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.**

Publisher and support: **SyntaxDevTeam / WieszczY**, [wieszczy85@gmail.com](mailto:wieszczy85@gmail.com).

[Project website](https://syntaxdevteam.pl/p/syntaxlauncher) · [Privacy](https://syntaxdevteam.pl/p/syntaxlauncher-prywatnosc) · [Terms](https://syntaxdevteam.pl/p/syntaxlauncher-warunki-uzytkowania) · [Support and account removal](https://syntaxdevteam.pl/p/syntaxlauncher-zarzadzanie-usluga)

> **Accounts and AppID review — 24 October 2026:** SyntaxLauncher is a Microsoft-authenticated Minecraft: Java Edition launcher. Players must sign in and have a verified Java entitlement/profile before every Play. Local-only legacy identity records remain intact on disk for backup but are neither selectable nor playable. The Minecraft Services AppID has **not** been approved; live login/gameplay acceptance remains blocked pending approval. See [AppID compliance](docs/APPID_COMPLIANCE.md).

SyntaxLauncher is a modern Minecraft launcher from SyntaxDevTeam, written primarily in Kotlin and developed incrementally from a minimal vanilla launcher into a modular launcher with premium authentication, isolated instances, mod loaders, Modrinth integration, modpacks and diagnostics.

The project has implemented a v0.2 Instance Manager, reached **bounded v0.8 Forge/Quilt and v0.9 graphics acceptance**, and completed **v0.10 Modrinth modpack installation** within the documented scope. Official Forge/NeoForge installer smoke tests and three-platform CI packaging pass; the maintainer confirmed private Forge startup and Forge/Quilt mod workflows. Forge now discovers modern V2 builds matching the selected Minecraft version; official reference installation tests also cover 1.21.11, 26.2 and 26.3. v0.9 now includes built-in mods/textures/shaders/OptiFine installation and installed-pack controls within the documented compatibility matrix; the maintainer confirmed private v0.9 gameplay acceptance; the broader native/platform matrix remains open. Modrinth modpacks now install into newly prepared games from the built-in catalog. Minecraft Services approval after two unsuccessful requests, v0.3 real-account acceptance and documented clean-machine Vanilla startup acceptance remain pending. The Kotlin/Compose launcher now connects the authenticated account, Mojang metadata, verified game preparation, automatic Java runtime installation, metadata-driven LaunchPlan and MinecraftProcessManager to an actual desktop Play workflow. A real clean-machine Minecraft launch is not yet verified.

## v0.11 / v0.12 — diagnostics, repair and release preparation

Home → **Game health and repair** scans the selected game's managed files, explains common failures with confidence labels and repairs game files/Java without starting Minecraft or deleting worlds/mods/settings. Copy safe report exports a structured local summary without raw logs, credentials or private paths. Settings now includes a bounded launcher-log viewer, actual data locations and update-channel controls. Launcher logs rotate, sensitive message fields are redacted, downloads check free space and the common JVM dependency graph is locked.

Microsoft account acceptance is **deferred while awaiting Mojang AppID approval** (maintainer instruction, 23 October 2026); game launch requires verified Microsoft Java accounts; the implementation remains subject to official AppID allowlisting. Implementation work on diagnostics/hardening is separate from final clean-machine, hosted updater, signing/notarization and native gameplay/DPI acceptance. See [v0.11 verification](docs/TESTING_V0.11.md), [v0.12 final gates](docs/TESTING_V0.12.md) and [installation/backup/removal](docs/INSTALLATION.md).

## Sequential milestone closure — 24 October 2026

[Closure evidence](docs/MILESTONE_CLOSURE.md) reconciles stale implementation tasks and records new inheritance/legacy asset handling, local integrity receipts, optional Modrinth dependencies and matching Forge discovery. [Support matrix](docs/SUPPORT_MATRIX.md) distinguishes native packaging from gameplay: the maintainer confirmed Fabric startup and world saving on Linux and Windows 10 for Minecraft 1.21.11, 26.2 and 26.3; exact Fabric versions were not supplied. [Signing prerequisites](docs/SIGNING.md) remain external. Stable 1.x publication is now blocked by unfinished final gates; this work does not change the product version.

## Windows executable branding

Stable 0.9.2 configured the application icon only for Linux, leaving the Windows portable executable with the default Compose/Kotlin icon. The subsequent installer preparation configures the repository ICO for the Windows application image too. The published DEV 188.1 portable EXE has all six icon images byte-identical to that ICO. Every new Windows build checks the embedded PE icon before publication. Extract a complete new portable archive to replace an older bootstrap EXE; automatic launcher updates keep the original bootstrap file.

## Native installer previews

[DEV build 188.1](https://github.com/SyntaxDevTeam/SyntaxLauncher/releases/tag/dev-188.1-v0.9.3-DEV) provides **Windows x64 MSI (WiX 7.0.0)**, **macOS ARM64 DMG**, and **Linux x64 DEB, RPM and AppImage**, alongside the existing portable archives. Linux packages target Debian/Ubuntu/Mint and compatible RPM systems; AppImage offers a single-file option for other compatible desktop distributions. Formats do not expand the CPU/native-library support matrix. All five installers and their SHA-256 inventory are public; [workflow 40013941601](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/runs/40013941601) passed the three native lifecycle checks. Lifecycle logs are available in its workflow artifacts; signing/notarization and final clean-machine/upgrade acceptance remain open. Stable 0.9.2 is unchanged. See [installation and removal](docs/INSTALLATION.md) and [native build instructions](docs/DEVELOPMENT.md#native-installer-builds).

## Profiles, creation and memory settings

Open **Game profiles** in the header to browse profiles inside the main launcher window. Select a profile to see its game/loader, installed mods, worlds, textures, shaders and configuration count; use Edit per row or create a separate profile with the new wizard. The workshop backdrop distinguishes this page from Home. Each saved profile has a visible **Delete** action with an in-page confirmation. Deleted profiles and their worlds stay in the recoverable trash; **Restore** is available directly in the profile list. Advanced clone/import actions remain available.

The creation wizard branches through Vanilla/With mods, a mod loader when needed, and **complete modpack / individual catalog extras / empty**. Complete packs are filtered and validated for the chosen loader; their own definition sets exact game/dependency versions. Other profiles check compatible versions before final confirmation. Nothing is written before that confirmation; existing worlds stay separate. Individual extras open in the inline catalog after creation; empty profiles download game/Java files on first Play.

Memory allocation now lives under the settings gear for the currently selected game. Save persists that game's setting; Close discards an unsaved draft. Game type sits below Game version on Home. The footer links directly to the SyntaxDevTeam website, Discord and this GitHub repository. On wide windows the Mojang/Microsoft independence notice shares the same row as the links; compact windows wrap it readably. Home only shows **Modpacks** for modded games. Vanilla retains compatible extras through **Choose extras**.

## Project philosophy

**Non-negotiable audience:** children playing Minecraft. The normal experience must be extraordinarily simple: choose an item or ready-made set from the launcher catalog, click Install, then Play. The launcher handles compatible versions and required components. Manual file import and technical settings are advanced tools. This applies to mods, modpacks, shaders, resource packs, OptiFine and future content; a backend alone does not complete the experience.

SyntaxLauncher is intentionally developed milestone by milestone.

The first release will **not** try to compete with mature launchers by implementing every feature at once. Instead, v0.1 establishes a clean launcher core capable of:

- using a modular account identity boundary (current releases require Microsoft authentication);
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

The account manager exposes only authenticated Microsoft identities. Every Play refreshes the official session and verifies Java entitlement and profile; failures block the launch. Historical local profile records and game data are preserved but are not selectable. Approval and live authentication acceptance are pending; see [AppID compliance](docs/APPID_COMPLIANCE.md).

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

The earlier v0.7.0 HTTP 403 publication incident is historical. The release-readiness register records public Stable v0.7.1 and verified six-asset DEV publication in CI 39901800185. The canonical product version is now 0.9.2, explicitly approved by the maintainer for Stable publication with the embedded profile/settings workflow. Verify publication in the release register. See [release evidence](docs/RELEASE_READINESS.md).


The sole product version is `launcherVersion` in [`gradle.properties`](gradle.properties). A higher plain `X.Y.Z` pushed to `main` publishes Stable after tests and all platform packages pass. Matching manual `vX.Y.Z` tags remain supported. Every successful `main` push also publishes an immutable **DEV** GitHub pre-release, including changes without a product-version bump. Run the workflow manually on `main` with `preview_channel=snapshot` to publish **SNAPSHOT** (or choose `dev` for a manual DEV build). Preview releases never become GitHub's latest stable release.

Choose **Your setup → Launcher updates → Stable / SNAPSHOT / DEV** and restart. Without a saved choice, releases stay on Stable and preview archives keep their embedded DEV/SNAPSHOT channel; updates automatically download, verify, install and start the selected channel at startup. Preview builds use CI run/attempt identity independently of product SemVer, so repeated builds of the same version are detected. Saved preferences and channel switches never authorize an automatic rollback: older product versions and older known CI builds are ignored, including cached images. If the selected channel is behind the downloaded app, the app stays on its current build until that channel catches up. Install a build containing this channel picker once before using it. See [publishing and acceptance details](docs/UPDATING.md).

## v0.8 — Forge and Quilt (accepted within the documented matrix)

Forge modern installer V2 shares the staged processor engine with NeoForge. Discovery matches exact official Minecraft coordinates, orders builds numerically and validates the selected installer target. Reference installations cover **1.21.1 / 52.1.20, 1.21.11 / 61.2.4, 26.2 / 65.1.5 and 26.3 / 66.1.0**, using metadata-selected Java. Historical V1 remains unavailable. The maintainer confirmed private Forge startup and Forge/Quilt mod installation/startup on 21 October 2026. No OS list, exact Quilt/mod versions or logs were supplied, so this is not an all-platform or all-version certification. The existing in-app Forge test warning remains as a conservative scope reminder.

All five modes use the same preparation, Java, launch-plan and process pipeline. The new deterministic coordinator matrix exercises the real adapters against local synthetic metadata and Forge-family processor fixtures; official installer smoke tests and Windows/Linux/macOS packaging also passed in hosted CI. Java requirements come from Mojang metadata; missing requirements fail before downloads. Legacy arguments stay in the shared planner. Full TOML/version-range solving, historical Forge expansion and the broader native/gameplay matrix remain open. See [the v0.8 evidence and support matrix](docs/TESTING_V0.8.md).

## v0.9 — Built-in graphics catalog (bounded acceptance)

For a saved game choose **Choose extras**, pick a compatible item and press **Install**, then **Play**. Textures install and select required resource packs automatically. Supported modded games also show **Shaders**: the launcher installs compatible Iris and its required components, verifies them and selects the shader. Published Iris compatibility is checked for the exact Minecraft version and Fabric/Quilt/NeoForge loader. Unsupported combinations are unavailable; worlds and game versions are never silently converted.

**OptiFine** is currently offered only for **Vanilla Minecraft 1.21.1 / OptiFine HD U J1**. The launcher obtains the official private download and runs its installer automatically using managed Java. It does not bundle/mirror OptiFine or treat it as a generic loader. Other game versions and OptiFine with Forge/Fabric/Quilt/NeoForge are outside this initial support matrix. The real official installer/launch-profile smoke passed; this does not certify graphical gameplay.

**More → My packs** shows installed textures/shaders, descriptions/icons where present, Use/Turn off, texture priority and Remove/Undo. Local ZIP import stays secondary. Removed ZIPs and original settings backups are retained per game; existing different files, worlds and other instances are preserved. Undo covers the last removal in the open dialog. Install badges last for the catalog session; repeated installs revalidate/reuse identical files. Running/preparing games prevent mutation; installations can be cancelled or retried.

The maintainer confirmed private texture activation, shaders with automatic Iris and Vanilla 1.21.1 / OptiFine J1 startup after these changes. No exact OS/loader/Iris/pack matrix was supplied; broader native/platform and extended recovery acceptance remains tracked under v1.0. See [the v0.9 acceptance record](docs/TESTING_V0.9.md). The product version and release status are unchanged.

## Release readiness and final stabilization

Run `python3 scripts/release_readiness.py` to view unresolved milestone checkboxes without changing the roadmap. Use `--enforce v0.10` to gate the bounded modpack milestone, or `--enforce v1.0` only when preparing a real stable v1.0 candidate. GitHub Actions posts the informational audit after normal tests. The latest normal GitHub Release/version and the current development milestone are deliberately separate. See [release readiness](docs/RELEASE_READINESS.md).

## v0.11 — Local crash diagnostics and file repair

After a failed Minecraft launch choose **Diagnose the failure**. SyntaxLauncher checks bounded recent local logs, distinguishes probable findings from weak evidence, and offers an explicit **Verify and repair managed files** action for supported integrity/preparation failures. Reports never embed raw log lines or authentication credentials. This stage does not disable mods, reset user settings or remove worlds. See [v0.11 acceptance checklist](docs/TESTING_V0.11.md). Hosted core/desktop tests now pass; native/manual diagnostic acceptance remains pending.

## v0.10 — Ready-made modpacks

Choose **Modpacks** on the home screen, or **Choose extras → Modpacks**, select an adventure and press **Install**. The launcher chooses the published release, reads its exact Minecraft/loader requirements, verifies required files and prepares Minecraft, the loader and Java automatically. The finished game becomes selected on the home screen; press **Play**. No existing saved game is required. Progress and Cancel remain available; unsupported requirements and damaged downloads never produce a falsely completed game.

Every installation, reinstall or newer pack release creates a **new game**. Existing worlds/configuration remain in their original game; there is no destructive in-place modpack update. Required client files are included; optional and dedicated-server files are omitted. The initial format is Modrinth `.mrpack`, with exact published Vanilla/Fabric/Quilt/NeoForge and the existing bounded Forge support. General mod-version-range solving and other pack formats are outside this stage.

**More → Import modpack** and **My games → Import Modrinth modpack** are secondary local-file paths using the same complete installer. For games installed this way, **Choose extras → More → My packs → More → Export original pack** saves the original reconstructable definition, including author-provided overrides, without adding your worlds, local changes, account data or downloaded mod binaries. Existing export destinations are never replaced. Arbitrary modified-instance export is not supported.

The real Simply Optimized 1.21.1-6.0 installation (18 referenced files, Fabric 0.17.3), full game/runtime preparation and byte-identical original export passed in isolated directories. This was not a graphical Minecraft startup. Deterministic tests cover catalog/installation, cancellation/failure cleanup, isolation and translated compact UI; broader gameplay/native acceptance remains tracked under v1.0. See [v0.10 verification and limits](docs/TESTING_V0.10.md).

## v0.7 — NeoForge (bounded support)

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
- common `Account` boundary and historical-format read-only legacy identity parser;
- legacy identity format compatibility (not a game login method);
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
- mandatory Microsoft `LaunchAuthentication` requiring a non-empty verified session;
- metadata-driven `LaunchPlanBuilder` for modern JVM/game arguments and legacy `minecraftArguments` fallback;
- strict placeholder substitution for Mojang account/version/assets/natives/classpath/auth/launcher fields;
- platform-aware classpath construction, RAM JVM arguments and downloaded logging-config injection;
- structured `LaunchPlan` output containing runtime, working directory, main class, JVM args, game args and the final argument-vector command without shell concatenation;
- typed launch-plan failures for missing artifacts and unresolved future placeholders;
- deterministic launch-plan test sources covering modern and legacy argument construction, authenticated identity, logging, paths containing spaces and unknown-placeholder rejection;
- `MinecraftProcessManager` with native `ProcessBuilder` execution, per-run stdout/stderr log files, structured lifecycle/PID/exit status, duplicate-launch protection and explicit detach/terminate shutdown policy;
- controlled process tests including a real local Java child and synthetic crash/force-stop/detach scenarios;
- initial unit/integration test sources using Ktor `MockEngine` rather than live Mojang services;
- a lightweight Compose screen with authenticated account selection, release/snapshot selection, version selection, RAM control, loading state and retryable metadata errors.

The remaining v0.1 engineering work is complete cross-platform artifact verification, first real vanilla Minecraft launch and UI polish. Schema-versioned preferences for Minecraft version/channel/RAM and retained legacy settings are now implemented but need desktop acceptance. The new Play action is functional in code but not yet validated against a real Mojang download and game startup.

## Continuous integration

The [Build and package SyntaxLauncher](https://github.com/SyntaxDevTeam/SyntaxLauncher/actions/workflows/build.yml) workflow runs on pushes to `main`, pull requests and manual dispatch. It verifies core/desktop tests and compilation plus update packaging fixtures, then builds Compose `createDistributable` portable applications for Linux, Windows and macOS. Workflow artifacts contain a tar.gz archive of the runnable app directory for each platform (including executable bits and symlinks); they remain portable applications; native installer packaging is described above. Platform jobs also emit verified schema-1 update ZIPs; a version-change or matching stable-tag job uploads all six platform update/portable assets to an ordinary GitHub Release. Every successful main push also publishes DEV; manual main dispatch can publish SNAPSHOT. CI provisions Gradle 9.8.1 explicitly with `gradle/actions/setup-gradle`; local builds use the committed wrapper. CI status must be green before a v0.1 test release is called build-verified. Native jpackage version numbers are derived automatically from the product version (prerelease suffixes omitted; major `0` mapped to `1` for macOS compatibility). They are not independently maintained; the embedded product version remains authoritative. Local developers can use the official committed `./gradlew :launcher-core:test :launcher-desktop:run` (on Windows use `gradlew.bat`). Platform builds use `./gradlew :launcher-desktop:createDistributable`; output is under `launcher-desktop/build/compose/binaries/main/app/`.

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

SyntaxLauncher stores the selected Microsoft account, Minecraft version/channel, RAM allocation, UI language, active game and launcher update channel in a schema-versioned `settings.properties` file inside the OS configuration directory. Writes use a temporary file and atomic publication where supported. Unknown property keys are preserved; an unsupported schema is rejected without erasing user settings. Microsoft credentials are not part of this storage format.

## Visual identity

The desktop home uses a generated voxel valley background with native Compose controls. The current product mark is the independent blue **open gateway**, shown in the header, native application icon and startup updater. It replaces the earlier block/orbital-arrow concept. The home has no foreground logo. [Artwork provenance and prompts](launcher-desktop/src/main/resources/branding/README.md) identify the current assets and their origins; generated provenance is not trademark clearance.

Deterministic desktop render tests cover Microsoft-only account management, blocked Play without a verified session, instance selection, compact windows, translations and scaling. Run `./gradlew :launcher-desktop:test`; QA images appear under `launcher-desktop/build/reports/ui/`. These fixtures do not authenticate a real account or start Minecraft. Native desktop acceptance remains separate.

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
| **v0.1** | Vanilla + account identity abstraction + version selection + automatic Java + game launch |
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

### Microsoft / premium

Implemented for v0.3 with automated fixtures; real sign-in/gameplay remain blocked pending AppID authorization. Use **Manage players → Add Microsoft**. Saved Microsoft names come from the verified Java profile and cannot be edited locally.

Premium authentication uses the official Microsoft → Xbox Live → XSTS → Minecraft Services flow appropriate for a desktop application. SyntaxLauncher must never ask users to type their Microsoft password into the launcher itself.

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

The builder resolves Mojang JVM/game arguments, substitutes supported `${...}` placeholders, applies platform-specific classpath separators, injects prepared assets/natives/libraries/game paths, supplies verified Microsoft session values, adds RAM and logging configuration arguments and produces one structured argument vector.

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
- Forge — v0.8 (official SHA-1-verified V2 installation passed for 1.21.1/52.1.20; private 1.21.1 startup accepted; newer pairs have installer evidence)
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

Implemented so far: project/module foundation, legacy identity-format compatibility, Mojang version discovery and full selected-version parsing, shared verified download infrastructure, client/library/native/assets preparation, platform-aware managed Mojang Java installation, metadata-driven final launch-plan construction and the first lightweight Compose Desktop screen.

The Gradle wrapper, automated build, settings persistence and version-manifest cache are implemented and tested. Real clean-machine Microsoft-authenticated vanilla gameplay and the remaining native/manual UI acceptance are still required; see docs/MILESTONE_CLOSURE.md and docs/SUPPORT_MATRIX.md for bounded evidence.

## License

See [`LICENSE`](LICENSE).

### Build feedback corrections (22 October 2026)

Home now uses **Game profiles / Profile gry / Spielprofile**. Language and launcher settings are in the top header before build information; the settings gear opens launcher update preferences. The compatible catalog shows project icons with colorful category fallbacks and short localized explanations for common projects. Other upstream descriptions remain labelled in their original language. Optional icon downloads use the shared HTTP client and bounded memory, without affecting installation availability.

Mod notices account for Fabric API’s declared nested modules and provided IDs. Home links directly to the affected profile’s mod list; missing requirements are explained beside their file. These local metadata notices remain advisory, without full version-range reconciliation.

Forge/NeoForge local inventory now reads full TOML syntax, including multiline descriptions, quoted keys, inline tables and multiple bundled mods, within explicit resource limits. Quilt optional dependencies no longer appear as missing required mods; complex conditional/group/version expressions remain explicitly advisory. This improves diagnostics without promising universal mod compatibility. Additional component licenses ship in `third-party/` resources.

Launch preparation now explains download, disk, file-integrity and Java failures in the selected language (EN/PL/DE), with full-width recovery advice and a usable Play retry. Error text does not expose raw exceptions or private paths. CI also verifies actual Mojang-managed Java installation, execution, offline reuse and repair on the distributed platform matrix; graphical game acceptance remains separate.
